"""Adapter contract tests. Stdlib-only cases run without optional dependencies.

Set ETA_SCRAPLING_PYTHON to an installed Python for real HTTP integration tests.
These fixtures do not claim Cloudflare clearance or Android ABI compatibility.
"""
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import time
import types
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
WORKER = ROOT / "app/src/main/assets/eta-web/scrapling_worker.py"
SPEC = importlib.util.spec_from_file_location("eta_scrapling", WORKER)
worker = importlib.util.module_from_spec(SPEC)
sys.dont_write_bytecode = True
SPEC.loader.exec_module(worker)


class WorkerContractTest(unittest.TestCase):
    def tearDown(self):
        worker.close()

    def test_urls_reject_credentials_schemes_and_control_characters(self):
        for url in ["file:///etc/passwd", "https://a:b@example.com", "https://example.com\nmore"]:
            with self.assertRaises(worker.WorkerError):
                worker.validate_url(url)
        self.assertEqual("https://example.com/?q=1", worker.validate_url("https://example.com/?q=1"))

    def test_challenge_detection_does_not_treat_cloudflare_footer_as_challenge(self):
        self.assertFalse(worker.challenge({}, "Powered by Cloudflare; real article"))
        self.assertTrue(worker.challenge({"CF-Mitigated": "challenge"}, ""))
        self.assertTrue(worker.challenge({}, '<title>Just a moment</title><script src="/cdn-cgi/challenge-platform/a.js">'))

    def test_success_body_is_not_invented_from_http_403(self):
        class Response:
            body = b"private server error"
            encoding = "utf-8"
            url = "https://example.com/page"
            status = 403
            headers = {"cf-mitigated": "challenge"}

        class Session:
            def get(self, *_args, **_kwargs):
                return Response()

        with patch.object(worker, "http_session", return_value=Session()):
            result = worker.fetch_http(Response.url, 1000)
        self.assertEqual("", result["content"])
        self.assertTrue(result["challenge_detected"])
        self.assertEqual(403, result["status"])

    def test_http_has_one_attempt_verifies_tls_and_stops_oversized_stream(self):
        class Session:
            def get(self, _url, **kwargs):
                self.options = kwargs
                for _ in range(4):
                    kwargs["content_callback"](b"a" * (worker.MAX_BODY // 2))
                self.fail("unreachable")

        session = Session()
        with patch.object(worker, "http_session", return_value=session):
            with self.assertRaises(worker.WorkerError) as error:
                worker.fetch_http("https://example.com", 1000)
        self.assertEqual("WEB_RESPONSE_TOO_LARGE", error.exception.code)
        self.assertEqual(1, session.options["retries"])
        self.assertTrue(session.options["verify"])

    def test_errors_never_return_private_exception_text(self):
        self.assertEqual("WEB_NETWORK_ERROR", worker.error_code(RuntimeError("token=PRIVATE")))
        self.assertEqual("WEB_TLS_ERROR", worker.error_code(RuntimeError("certificate: PRIVATE")))

    def test_browser_route_requires_explicit_installed_capability(self):
        with self.assertRaises(worker.WorkerError) as error:
            worker.handle({"protocol": 1, "action": "read", "url": "https://example.com", "route": "CLOUDFLARE"})
        self.assertEqual("SCRAPLING_BROWSER_UNAVAILABLE", error.exception.code)

    def test_only_real_cloudflare_route_enables_solver(self):
        with patch.object(worker, "http_session"), patch.object(worker, "fetch_browser", return_value={}) as browser:
            for route, expected in [("BROWSER", False), ("CLOUDFLARE", True)]:
                worker.handle({"protocol": 1, "action": "read", "url": "https://example.com",
                               "route": route, "browser_enabled": True})
                self.assertEqual(expected, browser.call_args.kwargs["solve"])

    def test_browser_probe_keeps_tls_sandbox_and_permissions_constraints(self):
        sessions = []

        class FakePage:
            def goto(self, url, **_kwargs):
                self.url = url

            def evaluate(self, expression):
                return 2 if expression == "1 + 1" else None

            def close(self):
                pass

        class FakeSession:
            def __init__(self, **kwargs):
                self.options = kwargs
                self._browser_options = {"args": []}
                self.context = types.SimpleNamespace(new_page=FakePage)
                sessions.append(self)

            def start(self):
                pass

            def close(self):
                pass

        with patch.dict(sys.modules, {"scrapling.fetchers": types.SimpleNamespace(StealthySession=FakeSession)}):
            session = worker.browser_session(1000)
        self.assertTrue(session._browser_options["chromium_sandbox"])
        self.assertFalse(session.options["additional_args"]["ignore_https_errors"])
        self.assertEqual([], session.options["additional_args"]["permissions"])
        self.assertFalse(session.options["record_requests"])
        self.assertNotIn("user_data_dir", session.options)


@unittest.skipUnless(os.environ.get("ETA_SCRAPLING_PYTHON"), "optional installed Python not specified")
class WorkerIntegrationTest(unittest.TestCase):
    def setUp(self):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path == "/slow":
                    time.sleep(1)
                payload = (b"<html><main>readable fixture</main></html>" if self.path != "/large"
                           else b"a" * (worker.MAX_BODY + 20_000))
                self.send_response(403 if self.path == "/challenge" else 200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(payload)))
                if self.path == "/challenge":
                    self.send_header("cf-mitigated", "challenge")
                self.end_headers()
                try:
                    self.wfile.write(payload)
                except (BrokenPipeError, ConnectionResetError):
                    pass

            def log_message(self, *_args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(2)

    def request(self, suffix, timeout=2000):
        return {"protocol": 1, "request_id": suffix, "action": "read", "timeout_ms": timeout,
                "url": f"http://127.0.0.1:{self.server.server_port}/{suffix}", "route": "HTTP_FINGERPRINT"}

    def run_worker(self, requests):
        # A pipe is the production transport. No HTTP management service is opened.
        result = subprocess.run([os.environ["ETA_SCRAPLING_PYTHON"], str(WORKER)],
                                input="".join(json.dumps(r) + "\n" for r in requests),
                                text=True, capture_output=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr[-500:])
        return [json.loads(line) for line in result.stdout.splitlines()]

    def test_http_data_and_challenge_survive_real_fetcher_protocol(self):
        results = self.run_worker([self.request("article"), self.request("challenge")])
        self.assertEqual(2, len(results))
        self.assertIn("readable fixture", results[0]["content"])
        self.assertTrue(results[0]["ok"])
        self.assertEqual(403, results[1]["status"])
        self.assertTrue(results[1]["challenge_detected"])
        self.assertEqual("", results[1]["content"])

    def test_real_stream_limit_and_whole_operation_timeout(self):
        large = self.run_worker([self.request("large")])[0]
        self.assertEqual("WEB_RESPONSE_TOO_LARGE", large["code"])
        slow = self.run_worker([self.request("slow", timeout=250)])[0]
        self.assertEqual("WEB_TIMEOUT", slow["code"])


if __name__ == "__main__":
    unittest.main()
