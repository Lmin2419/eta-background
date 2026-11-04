package io.github.mangi.eta.agent.tool

import io.github.mangi.eta.agent.runtime.ToolTimingMetadata
import io.github.mangi.eta.agent.web.WebFetchUpgradeResult
import io.github.mangi.eta.agent.web.WebHttpTransport
import io.github.mangi.eta.agent.web.WebRequestException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** One heavy agent browser operation at a time, including time spent waiting for a slot. */
internal object WebBrowserWork {
    private val permit = Semaphore(1, true)

    fun <T> run(timeoutMillis: Long, checkCancellation: () -> Unit, operation: (Long) -> T): T {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis.coerceIn(1, 60_000))
        var acquired = false
        try {
            while (!acquired) {
                checkCancellation()
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw WebRequestException("WEB_BUSY", "浏览器资源正忙，本次操作未执行；请稍后再试")
                acquired = permit.tryAcquire(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS)
            }
            checkCancellation()
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (remaining <= 0) throw WebRequestException("WEB_BUSY", "等待浏览器资源超时，本次操作未执行")
            return operation(remaining)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw WebRequestException("WEB_CANCELLED", "网页操作已取消")
        } finally {
            if (acquired) permit.release()
        }
    }
}

/** Converts only a bounded observed document; WebView does not expose successful HTTP status codes. */
internal object BrowserFetchSnapshot {
    fun decode(content: String): WebFetchUpgradeResult {
        val result = JSONObject(content)
        if (!result.optBoolean("ok")) {
            val original = result.optString("code")
            val code = when (original) {
                "HTTP_404", "HTTP_410" -> "WEB_NOT_FOUND"
                "HTTP_401" -> "WEB_LOGIN_REQUIRED"
                "HTTP_403" -> "WEB_ACCESS_DENIED"
                "HTTP_429" -> "WEB_RATE_LIMITED"
                "NAVIGATION_TIMEOUT", "OPERATION_TIMEOUT" -> "WEB_TIMEOUT"
                "CANCELLED", "USER_CONTROL_ACTIVE" -> "WEB_CANCELLED"
                else -> original.ifBlank { "WEB_ENHANCEMENT_FAILED" }
            }
            throw WebRequestException(code, result.optString("message").ifBlank { "浏览器尚未取得目标正文" })
        }
        val url = WebHttpTransport.validateUrl(result.optString("url")).toString()
        val text = result.optString("text")
        if (text.isBlank()) throw WebRequestException("WEB_EMPTY_CONTENT", "浏览器未取得可读取的正文")
        return WebFetchUpgradeResult(
            content = text.take(200_000), finalUrl = url, contentType = "text/markdown",
            status = if (result.has("http_status")) result.optInt("http_status").takeIf { it in 100..599 } else null,
            provider = "builtin_browser", title = result.optString("title").take(512).ifBlank { null },
            truncated = result.optBoolean("source_truncated") || result.optBoolean("truncated") || text.length > 200_000,
            timingMillis = ToolTimingMetadata.fromToolResult(content),
        )
    }
}
