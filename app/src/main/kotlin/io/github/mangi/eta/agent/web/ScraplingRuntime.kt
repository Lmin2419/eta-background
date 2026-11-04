package io.github.mangi.eta.agent.web

import android.content.Context
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import io.github.mangi.eta.agent.terminal.ShellProcessSupervisor
import io.github.mangi.eta.agent.terminal.TerminalExecutionContext
import io.github.mangi.eta.agent.terminal.linuxPackageProfileReady
import io.github.mangi.eta.agent.terminal.shellQuote
import io.github.mangi.eta.core.AgentLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import org.json.JSONObject

internal data class ScraplingCapabilities(
    val http: Boolean,
    val browser: Boolean,
    val browserChecked: Boolean,
    val version: String?,
)

/**
 * Optional local provider; there is deliberately no installer in this read path.
 * A task reuses one anonymous HTTP worker. Its owned Linux process exits after
 * 30 idle seconds; the APK contains only the adapter, never a Python/browser tree.
 */
internal class ScraplingRuntime(
    private val context: Context,
    private val executionContext: TerminalExecutionContext,
    private val logger: AgentLogger,
    rootAvailable: () -> Boolean,
) : AutoCloseable {
    private val supervisor = ShellProcessSupervisor(rootAvailable = rootAvailable)
    private val closed = AtomicBoolean(false)
    private val exchangeLock = ReentrantLock()
    @Volatile private var worker: Worker? = null

    fun read(request: WebFetchUpgradeRequest): WebFetchUpgradeResult {
        WebHttpTransport.validateUrl(request.url)
        val heavy = request.preferredRoute != WebFetchRoute.HTTP_FINGERPRINT
        if (heavy && !installed(LinuxPackageProfiles.WEB_BROWSER)) {
            throw WebRequestException("SCRAPLING_BROWSER_UNAVAILABLE", "增强浏览器组件尚未安装；普通网页读取仍可用")
        }
        val response = exchange(
            JSONObject().put("action", "read").put("url", request.url)
                .put("route", request.preferredRoute.name).put("browser_enabled", heavy),
            timeoutMillis = request.timeoutMillis,
            heavy = heavy,
        )
        val finalUrl = response.optString("final_url")
        WebHttpTransport.validateUrl(finalUrl)
        val timings = linkedMapOf<String, Long>()
        response.optJSONObject("timing_ms")?.let { values ->
            TIMING_KEYS.forEach { key ->
                if (values.has(key)) values.optLong(key, -1).takeIf { it >= 0 }?.let { timings[key] = it }
            }
        }
        return WebFetchUpgradeResult(
            content = response.optString("content"),
            finalUrl = finalUrl,
            contentType = response.optString("content_type", "text/html"),
            status = response.optInt("status"),
            provider = response.optString("provider", "scrapling"),
            truncated = response.optBoolean("truncated"),
            challengeDetected = response.optBoolean("challenge_detected"),
            timingMillis = timings,
        )
    }

    /** User-invoked diagnostic. A marker does not establish that the ABI/sandbox works. */
    fun probe(timeoutMillis: Long = 5_000, checkBrowser: Boolean = false): ScraplingCapabilities {
        val browser = checkBrowser && installed(LinuxPackageProfiles.WEB_BROWSER)
        val response = exchange(
            JSONObject().put("action", "probe").put("browser", browser), timeoutMillis, heavy = browser,
        )
        return ScraplingCapabilities(
            http = response.optBoolean("http"),
            browser = response.optBoolean("browser"),
            browserChecked = response.optBoolean("browser_checked"),
            version = response.optString("version").takeIf(String::isNotBlank),
        )
    }

    private fun installed(profile: io.github.mangi.eta.agent.terminal.LinuxPackageProfile): Boolean =
        executionContext.linuxReady && executionContext.linuxRootfsPath?.let {
            linuxPackageProfileReady(File(it), profile)
        } == true

    private fun exchange(request: JSONObject, timeoutMillis: Long, heavy: Boolean): JSONObject {
        ensureActive()
        if (!installed(LinuxPackageProfiles.WEB_HTTP)) {
            throw WebRequestException("SCRAPLING_UNAVAILABLE", "尚未安装可选网页增强组件；可在 Linux 环境设置中安装")
        }
        if (timeoutMillis < 100) throw WebRequestException("WEB_TIMEOUT", "网页读取时间预算已用完")
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis.coerceAtMost(35_000))
        var permit = false
        var locked = false
        try {
            // No queue of expensive browsers. The existing native browser remains
            // available when this optional worker is busy with another task.
            if (heavy) {
                permit = HEAVY_WORKER.tryAcquire()
                if (!permit) throw WebRequestException("SCRAPLING_BUSY", "另一个任务正在使用增强浏览器")
            }
            while (!locked) {
                ensureActive()
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw WebRequestException("WEB_TIMEOUT", "等待网页增强操作超过时间预算")
                locked = exchangeLock.tryLock(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS)
            }
            ensureActive()
            val active = liveWorker()
            try {
                val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                if (remaining < 100) throw WebRequestException("WEB_TIMEOUT", "网页增强组件启动超时")
                val requestId = UUID.randomUUID().toString()
                request.put("protocol", 1).put("request_id", requestId).put("timeout_ms", remaining)
                val bytes = (request.toString() + "\n").toByteArray(Charsets.UTF_8)
                if (bytes.size > 24_000) throw WebRequestException("INVALID_URL", "网页请求过长")
                // A stuck Linux startup may not drain stdin. Keep the writer
                // off the deadline thread; closing this owned tree unblocks it.
                thread(name = "eta-web-request", isDaemon = true) {
                    try {
                        active.process.outputStream.write(bytes)
                        active.process.outputStream.flush()
                    } catch (_: Exception) {
                        active.responses.offer("")
                    }
                }
                while (true) {
                    ensureActive()
                    val remainingNanos = deadline - System.nanoTime()
                    if (remainingNanos <= 0) throw WebRequestException("WEB_TIMEOUT", "网页增强请求超时")
                    val reply = active.responses.poll(minOf(remainingNanos, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS)
                        ?: continue
                    if (reply.isEmpty()) throw WebRequestException("SCRAPLING_UNAVAILABLE", "网页增强进程已退出")
                    val result = ScraplingProtocol.decode(reply, requestId)
                    if (!result.optBoolean("ok")) {
                        throw WebRequestException(
                            ScraplingProtocol.safeErrorCode(result.optString("code")),
                            "网页增强未取得可用正文，请按错误类型处理",
                        )
                    }
                    logger.info("Scrapling action=${request.optString("action")} outcome=completed heavy=$heavy")
                    return result
                }
            } catch (failure: Exception) {
                // Never replay a request after an uncertain process failure.
                stopWorker(active)
                throw failure
            } finally {
                if (heavy) stopWorker(active)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw WebRequestException("WEB_CANCELLED", "网页增强已取消")
        } catch (failure: Exception) {
            if (failure is WebRequestException) throw failure
            throw WebRequestException("SCRAPLING_UNAVAILABLE", "网页增强进程不可用")
        } finally {
            // A browser profile is scoped to one request. HTTP connections may
            // remain warm, but a second task cannot retain another Chromium tree.
            if (locked) exchangeLock.unlock()
            if (permit) HEAVY_WORKER.release()
        }
    }

    private fun liveWorker(): Worker {
        worker?.let { current ->
            if (current.process.isAlive) return current
            supervisor.reapProcess(current.process)
            supervisor.retireExitedProcess(current.process)
            worker = null
        }
        val adapter = context.assets.open("eta-web/scrapling_worker.py").bufferedReader().use { it.readText() }
        val command = "PLAYWRIGHT_BROWSERS_PATH=/opt/eta/web-browsers " +
            "PYTHONUNBUFFERED=1 /opt/eta/web-python/bin/python -u -c ${shellQuote(adapter)}"
        val process = supervisor.startShellProcess(
            identity = executionContext.linuxIdentity,
            command = command,
            mergeStderr = false,
            environment = executionContext.linuxEnvironment,
            linuxRootfsPath = executionContext.linuxRootfsPath,
            // This reader needs no shared user files.
            linuxSharedMounts = emptyList(),
        ) ?: throw WebRequestException("SCRAPLING_UNAVAILABLE", "无法启动所选 Linux 环境的网页增强组件")
        val next = Worker(process)
        worker = next
        thread(name = "eta-web-response", isDaemon = true) {
            try {
                process.inputStream.buffered().use { input ->
                    while (!closed.get()) {
                        val line = ScraplingProtocol.readLine(input) ?: break
                        if (!next.responses.offer(line)) break
                    }
                }
            } catch (_: Exception) {
                // Raw helper exceptions/URLs never enter persistent logs.
            } finally {
                next.responses.offer("")
            }
        }
        thread(name = "eta-web-stderr", isDaemon = true) {
            runCatching { process.errorStream.use { input ->
                val buffer = ByteArray(4096)
                while (input.read(buffer) >= 0) { /* drain; do not persist remote/private output */ }
            } }
        }
        return next
    }

    private fun stopWorker(target: Worker) {
        if (!target.stopping.compareAndSet(false, true)) return
        if (worker === target) worker = null
        supervisor.terminateAndReap(target.process)
        supervisor.unregisterProcess(target.process)
        target.responses.offer("")
    }

    private fun ensureActive() {
        if (closed.get() || Thread.currentThread().isInterrupted) {
            throw WebRequestException("WEB_CANCELLED", "网页增强已取消")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        supervisor.beginClosing()
        worker?.let(::stopWorker)
        supervisor.takeRemainingProcesses().forEach { process ->
            supervisor.terminateAndReap(process)
            supervisor.unregisterProcess(process)
        }
    }

    private class Worker(val process: Process) {
        val responses = LinkedBlockingQueue<String>(2)
        val stopping = AtomicBoolean(false)
    }

    private companion object {
        val HEAVY_WORKER = Semaphore(1)
        val TIMING_KEYS = setOf("http", "browser_start", "navigation", "extraction")
    }
}

internal object ScraplingProtocol {
    private const val MAX_RESULT_BYTES = 2 * 1024 * 1024
    private val ERROR_CODES = setOf(
        "WEB_TIMEOUT", "WEB_TLS_ERROR", "WEB_DNS_ERROR", "WEB_NETWORK_ERROR", "WEB_RESPONSE_TOO_LARGE",
        "INVALID_URL", "URL_CREDENTIALS_UNSUPPORTED", "SCRAPLING_UNAVAILABLE", "SCRAPLING_VERSION_MISMATCH",
        "SCRAPLING_BROWSER_UNAVAILABLE", "SCRAPLING_PROTOCOL_ERROR",
    )

    fun safeErrorCode(value: String): String = value.takeIf { it in ERROR_CODES } ?: "SCRAPLING_PROTOCOL_ERROR"

    fun decode(line: String, requestId: String): JSONObject {
        val result = try { JSONObject(line) } catch (_: Exception) {
            throw WebRequestException("SCRAPLING_PROTOCOL_ERROR", "网页增强返回格式不正确")
        }
        if (result.optInt("protocol") != 1 || result.optString("request_id") != requestId) {
            throw WebRequestException("SCRAPLING_PROTOCOL_ERROR", "网页增强返回与当前请求不匹配")
        }
        if (result.optBoolean("ok") && result.has("content") &&
            (result.optString("content").length > 300_000 || result.optInt("status") !in 100..599)
        ) throw WebRequestException("SCRAPLING_PROTOCOL_ERROR", "网页增强返回超过限制")
        return result
    }

    fun readLine(input: InputStream): String? {
        val output = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) return if (output.size() == 0) null else throw WebRequestException("SCRAPLING_PROTOCOL_ERROR", "网页增强返回不完整")
            if (value == '\n'.code) return output.toString(Charsets.UTF_8.name())
            if (output.size() >= MAX_RESULT_BYTES) throw WebRequestException("SCRAPLING_PROTOCOL_ERROR", "网页增强返回超过限制")
            output.write(value)
        }
    }
}
