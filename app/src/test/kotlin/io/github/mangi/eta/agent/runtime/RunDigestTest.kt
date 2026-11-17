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

    /** 11 号方案第零期：失败原因（只记码）、用到屏幕的轮数、只看不动的轮数、观察过期次数。 */
    @Test fun timingCountsFailureCodesAndScreenRounds() {
        var clock = 0L
        val timing = AgentRunTiming(io.github.mangi.eta.core.AndroidAgentLogger) { clock }
        fun tool(round: Int, id: String, name: String, ok: Boolean, code: String? = null) {
            timing.accept(AgentEvent.ToolStarted(round, id, name, ""))
            clock += 10
            timing.accept(AgentEvent.ToolFinished(round, id, name, "", 0, 0, success = ok, failureCode = code))
        }
        tool(1, "a", "observe_screen", true)
        tool(2, "b", "tap_element", false, "OBSERVATION_STALE")
        tool(3, "c", "observe_screen", true)
        tool(4, "d", "tap_element", true)
        tool(5, "e", "read_file", false, "FILE_NOT_FOUND")
        tool(6, "f", "wait", false, "secret text; not a code")
        val finished = timing.finish()
        assertEquals(mapOf("OBSERVATION_STALE" to 1, "FILE_NOT_FOUND" to 1, "UNKNOWN" to 1), finished.failureCodes)
        assertEquals(5, finished.guiRounds)
        assertEquals(3, finished.observeOnlyRounds)
        assertEquals(1, finished.staleFailures)
        val digest = RuntimeDiagnostics.runDigest(finished, "ok", gaveUp = false, atMillis = 0)
        assertEquals(1, digest.getJSONObject("failure_codes").getInt("OBSERVATION_STALE"))
        assertEquals(3, digest.getInt("observe_only_rounds"))
        assertEquals(1, digest.getInt("stale"))
        // 写进运行统计后还能读回来。
        assertEquals(finished.failureCodes, RunPerformanceSummary.fromJson(finished.toJson())!!.failureCodes)
    }

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
