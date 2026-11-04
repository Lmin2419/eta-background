#!/usr/bin/env python3
"""Eta's private, read-only Scrapling 0.5 JSON-lines adapter (protocol 1).

No listening socket, credentials, arbitrary scripts, package installation, or user
browser profile. The parent owns this process tree and enforces the hard deadline;
SDK code can absorb Python signal exceptions, so the alarm alone is insufficient.
Scrapling is an optional MIT-licensed dependency by D4Vinci, not bundled here.
"""
import contextlib
import importlib.metadata
import json
import logging
import os
import select
import signal
import sys
import time
from urllib.parse import urlparse

VERSION = "0.5"
MAX_BODY = 2 * 1024 * 1024
MAX_CONTENT = 300_000
MAX_REQUEST = 24_000
IDLE_SECONDS = 30
HTTP = None
HTTP_MANAGER = None
BROWSER = None
logging.disable(logging.CRITICAL)


class WorkerError(Exception):
    def __init__(self, code):
        self.code = code


def elapsed(start):
    return max(0, int((time.monotonic() - start) * 1000))


def deadline_signal(_signal, _frame):
    raise WorkerError("WEB_TIMEOUT")


def validate_url(value):
    if not isinstance(value, str) or len(value) > 8192 or any(ord(c) < 32 for c in value):
        raise WorkerError("INVALID_URL")
    url = urlparse(value)
    if url.scheme not in ("https", "http") or not url.hostname:
        raise WorkerError("INVALID_URL")
    if url.username is not None or url.password is not None:
        raise WorkerError("URL_CREDENTIALS_UNSUPPORTED")
    return value


def challenge(headers, content):
    normalized = {str(k).lower(): str(v).lower() for k, v in headers.items()}
    sample = content[:80_000].lower()
    return (normalized.get("cf-mitigated") == "challenge" or
            ("/cdn-cgi/challenge-platform/" in sample and
             ("just a moment" in sample or "cf-chl" in sample)) or
            ("challenges.cloudflare.com/turnstile/" in sample and "cf-turnstile" in sample))


def http_session():
    global HTTP, HTTP_MANAGER
    if HTTP is None:
        if importlib.metadata.version("scrapling") != VERSION:
            raise WorkerError("SCRAPLING_VERSION_MISMATCH")
        from scrapling.fetchers import FetcherSession
        # retries=1 means one attempt in Scrapling; safe redirects reject private
        # destinations. Cookie state belongs only to this task's anonymous session.
        HTTP_MANAGER = FetcherSession(impersonate="chrome", verify=True,
                                      retries=1, follow_redirects="safe", max_redirects=5)
        HTTP = HTTP_MANAGER.__enter__()
    return HTTP


def browser_session(timeout):
    global BROWSER
    if BROWSER is None:
        from scrapling.fetchers import StealthySession
        browser = StealthySession(
            headless=True, timeout=timeout, max_pages=1, network_idle=False,
            wait=0, record_requests=False, google_search=False,
            proxy=(os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy") or
                   os.environ.get("HTTP_PROXY") or os.environ.get("http_proxy") or None),
            additional_args={"ignore_https_errors": False, "permissions": []},
        )
        # Pinned 0.5 integration: Playwright otherwise defaults to disabling the
        # Chromium sandbox. Enforce it even when a PRoot/ROM cannot launch it.
        browser._browser_options["chromium_sandbox"] = True
        if any("no-sandbox" in flag or "disable-setuid-sandbox" in flag
               for flag in browser._browser_options.get("args", [])):
            raise WorkerError("SCRAPLING_BROWSER_UNAVAILABLE")
        try:
            browser.start()
            page = browser.context.new_page()
            try:
                page.goto("about:blank", timeout=min(timeout, 5000))
                if page.evaluate("1 + 1") != 2:
                    raise WorkerError("SCRAPLING_BROWSER_UNAVAILABLE")
            finally:
                page.close()
        except Exception as error:
            with contextlib.suppress(Exception):
                browser.close()
            if isinstance(error, WorkerError) and error.code == "WEB_TIMEOUT":
                raise
            raise WorkerError("SCRAPLING_BROWSER_UNAVAILABLE") from None
        BROWSER = browser
    return BROWSER


def bounded_content(content):
    clipped = len(content) > MAX_CONTENT
    return content[:MAX_CONTENT], clipped


def fetch_http(url, timeout):
    collected = bytearray()
    overflow = False

    def collect(chunk):
        nonlocal overflow
        if len(collected) + len(chunk) > MAX_BODY:
            overflow = True
            raise WorkerError("WEB_RESPONSE_TOO_LARGE")
        collected.extend(chunk)
        return len(chunk)

    start = time.monotonic()
    try:
        response = http_session().get(url, timeout=timeout / 1000,
                                      retries=1, verify=True, content_callback=collect)
    except Exception:
        if overflow:
            raise WorkerError("WEB_RESPONSE_TOO_LARGE") from None
        raise
    timings = {"http": elapsed(start)}
    raw = bytes(collected) if collected else response.body
    if len(raw) > MAX_BODY:
        raise WorkerError("WEB_RESPONSE_TOO_LARGE")
    content = raw.decode(getattr(response, "encoding", None) or "utf-8", errors="replace")
    content, truncated = bounded_content(content)
    blocked = challenge(response.headers, content)
    return {"content": content if 200 <= response.status < 300 else "",
            "final_url": validate_url(response.url), "status": response.status,
            "content_type": response.headers.get("content-type", "text/html"),
            "truncated": truncated, "challenge_detected": blocked,
            "provider": "scrapling_http", "timing_ms": timings}


def fetch_browser(url, timeout, solve):
    start = time.monotonic()
    session = browser_session(timeout)
    timings = {"browser_start": elapsed(start)}
    start = time.monotonic()
    response = session.fetch(url, timeout=timeout, solve_cloudflare=solve,
                             network_idle=False, wait=0)
    # Includes the SDK's solver if enabled; do not falsely label it pure navigation.
    if not solve:
        timings["navigation"] = elapsed(start)
    raw = response.body
    if len(raw) > MAX_BODY:
        raise WorkerError("WEB_RESPONSE_TOO_LARGE")
    content = raw.decode(getattr(response, "encoding", None) or "utf-8", errors="replace")
    content, truncated = bounded_content(content)
    return {"content": content if 200 <= response.status < 300 else "",
            "final_url": validate_url(response.url), "status": response.status,
            "content_type": "text/html; charset=utf-8", "truncated": truncated,
            "challenge_detected": challenge(response.headers, content),
            "provider": "scrapling_browser", "timing_ms": timings}


def handle(request):
    if not isinstance(request, dict) or request.get("protocol") != 1:
        raise WorkerError("SCRAPLING_PROTOCOL_ERROR")
    timeout = max(100, min(int(request.get("timeout_ms", 5000)), 35_000))
    signal.setitimer(signal.ITIMER_REAL, timeout / 1000)
    try:
        if request.get("action") == "probe":
            http_session()  # Imports and creates curl session on the actual ABI.
            browser_ok = False
            if request.get("browser", False):
                try:
                    browser_session(timeout)
                    browser_ok = True
                except WorkerError as error:
                    if error.code == "WEB_TIMEOUT":
                        raise
            return {"version": VERSION, "http": True, "browser": browser_ok,
                    "browser_checked": bool(request.get("browser", False))}
        if request.get("action") != "read":
            raise WorkerError("SCRAPLING_PROTOCOL_ERROR")
        url = validate_url(request.get("url"))
        route = request.get("route")
        if route == "HTTP_FINGERPRINT":
            return fetch_http(url, timeout)
        if route in ("BROWSER", "CLOUDFLARE"):
            if request.get("browser_enabled") is not True:
                raise WorkerError("SCRAPLING_BROWSER_UNAVAILABLE")
            http_session()  # Version gate applies to both routes.
            return fetch_browser(url, timeout, solve=route == "CLOUDFLARE")
        raise WorkerError("SCRAPLING_PROTOCOL_ERROR")
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)


def error_code(error):
    if isinstance(error, WorkerError):
        return error.code
    if isinstance(error, (ImportError, importlib.metadata.PackageNotFoundError)):
        return "SCRAPLING_UNAVAILABLE"
    # Only classify locally; raw exceptions can contain private URLs and headers.
    name = type(error).__name__.lower()
    message = str(error).lower()
    if "timeout" in name or "timed out" in message:
        return "WEB_TIMEOUT"
    if "certificate" in message or "ssl" in name:
        return "WEB_TLS_ERROR"
    if "resolve" in message or "dns" in name:
        return "WEB_DNS_ERROR"
    return "WEB_NETWORK_ERROR"


def close():
    global HTTP, HTTP_MANAGER, BROWSER
    if BROWSER is not None:
        with contextlib.suppress(Exception):
            BROWSER.close()
        BROWSER = None
    if HTTP_MANAGER is not None:
        with contextlib.suppress(Exception):
            HTTP_MANAGER.__exit__(None, None, None)
        HTTP = HTTP_MANAGER = None


def main():
    signal.signal(signal.SIGALRM, deadline_signal)
    try:
        while select.select([sys.stdin], [], [], IDLE_SECONDS)[0]:
            line = sys.stdin.readline(MAX_REQUEST + 1)
            if not line:
                break
            if len(line) > MAX_REQUEST or not line.endswith("\n"):
                break
            request = {}
            try:
                request = json.loads(line)
                # Keep protocol stdout clean, including any third-party prints.
                with contextlib.redirect_stdout(sys.stderr):
                    result = handle(request)
                result.update(ok=True, protocol=1, request_id=request.get("request_id"))
            except Exception as error:
                result = {"ok": False, "protocol": 1,
                          "request_id": request.get("request_id") if isinstance(request, dict) else None,
                          "code": error_code(error)}
            sys.stdout.write(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n")
            sys.stdout.flush()
            if result.get("code") == "WEB_TIMEOUT":
                break  # No reuse after an interrupted SDK operation.
    finally:
        close()


if __name__ == "__main__":
    main()
