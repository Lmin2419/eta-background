package io.github.mangi.eta.agent.tool

import io.github.mangi.eta.agent.web.WebRequestException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentWebEnhancementTest {
    @Test fun browserSnapshotKeepsUnknownHttpStatusAndBoundsContent() {
        val text = "正文".repeat(110_000)
        val result = BrowserFetchSnapshot.decode(JSONObject().put("ok", true)
            .put("url", "https://example.test/article").put("title", "标题").put("text", text)
            .put("timing_ms", JSONObject().put("navigation", 12).put("private_url", 99)).toString())
        assertNull(result.status)
        assertEquals("标题", result.title)
        assertEquals(200_000, result.content.length)
        assertTrue(result.truncated)
        assertEquals(mapOf("navigation" to 12L), result.timingMillis)
    }

    @Test fun browserFailureCannotBecomeAReadableSnapshot() {
        for ((input, expected) in listOf("HTTP_404" to "WEB_NOT_FOUND", "WEB_CHALLENGE" to "WEB_CHALLENGE",
            "USER_CONTROL_ACTIVE" to "WEB_CANCELLED", "NAVIGATION_TIMEOUT" to "WEB_TIMEOUT")) {
            try {
                BrowserFetchSnapshot.decode(JSONObject().put("ok", false).put("code", input)
                    .put("text", "not the target page").toString())
                fail("Failure became content")
            } catch (failure: WebRequestException) { assertEquals(expected, failure.code) }
        }
    }

    @Test fun cancelledWaitDoesNotExecuteOrLeakTheGlobalBrowserSlot() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val holder = executor.submit {
            WebBrowserWork.run(5_000, {}) { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
        }
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            var checks = 0
            var calls = 0
            try {
                WebBrowserWork.run(2_000, {
                    checks++
                    if (checks > 1) throw WebRequestException("WEB_CANCELLED", "cancel")
                }) { calls++ }
                fail("Cancelled waiter entered")
            } catch (failure: WebRequestException) { assertEquals("WEB_CANCELLED", failure.code) }
            assertEquals(0, calls)
        } finally { release.countDown(); holder.get(3, TimeUnit.SECONDS); executor.shutdownNow() }
        assertEquals("available", WebBrowserWork.run(1_000, {}) { "available" })
    }
}
