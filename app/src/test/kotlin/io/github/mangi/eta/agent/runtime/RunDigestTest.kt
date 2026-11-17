package io.github.mangi.eta.agent.runtime

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 10 号方案第零期：「导出运行诊断」里最近 20 次运行的数字。 */
@RunWith(RobolectricTestRunner::class)
class RunDigestTest {
    private val summary = RunPerformanceSummary(
        elapsedMs = 42_000, preparationMs = 300, modelMs = 30_000, toolsMs = 11_000, pausedMs = 0, otherMs = 700,
        requests = 9, retries = 1, toolCalls = 8, failedTools = 2, unfinishedTools = 0, imageCount = 3, imageBytes = 90_000,
        firstDeltaTotalMs = 9_000, firstDeltaSamples = 9,
        slowTools = listOf(RunPerformanceSummary.SlowTool("terminal", 6_000), RunPerformanceSummary.SlowTool("tap_element", 2_000)),
        completionReviews = 1,
    )

    @Test fun digestKeepsOnlyNumbersAndToolNames() {
        val digest = RuntimeDiagnostics.runDigest(summary, "ok", gaveUp = false, atMillis = 1_763_340_123_456)
        assertEquals(1_763_340_120_000, digest.getLong("at"))
        assertEquals(9, digest.getInt("requests")); assertEquals(30_000, digest.getLong("model_ms"))
        assertEquals(2, digest.getInt("failed_tools")); assertEquals(1, digest.getInt("completion_reviews"))
        assertEquals("terminal", digest.getJSONArray("slow_tools").getJSONObject(0).getString("name"))
        assertFalse(digest.getBoolean("gave_up"))
        assertEquals("failed", RuntimeDiagnostics.runDigest(summary, "weird", true, 0).getString("outcome"))
    }

    @Test fun keepsTheLatestTwentyAndSurvivesABrokenFile() {
        var raw: String? = "{not json"
        repeat(25) { i -> raw = RuntimeDiagnostics.appendRunDigest(raw, RuntimeDiagnostics.runDigest(summary, "ok", false, i * 60_000L)) }
        val runs = JSONArray(raw)
        assertEquals(20, runs.length())
        assertEquals(5 * 60_000L, runs.getJSONObject(0).getLong("at"))
        assertEquals(24 * 60_000L, runs.getJSONObject(19).getLong("at"))
    }

    @Test fun summaryRoundTripsCompletionReviews() {
        val parsed = RunPerformanceSummary.fromJson(summary.toJson())!!
        assertEquals(1, parsed.completionReviews)
        // 旧记录没有这一项：按 0 读。
        val old = summary.toJson().replace(",\"completion_reviews\":1", "")
        assertTrue(old != summary.toJson())
        assertEquals(0, RunPerformanceSummary.fromJson(old)!!.completionReviews)
    }
}
