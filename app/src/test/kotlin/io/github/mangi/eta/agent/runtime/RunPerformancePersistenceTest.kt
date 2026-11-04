package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.overlay.AgentOverlayState
import io.github.mangi.eta.agent.overlay.applyEvent
import io.github.mangi.eta.ui.app.AgentRunMessageProjector
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.SystemNoticeCode
import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RunPerformancePersistenceTest {
    private val summary = RunPerformanceSummary(100, 10, 50, 20, 5, 15, 1, 0, 2, 0, 0, 1, 123, 40, 1, emptyList())

    @Test fun summarySurvivesExistingArchiveCodecWithoutChangingTerminalOrResultSelection() {
        val event = AgentEvent.RunPerformance(summary)
        assertEquals(event, AgentEventJsonCodec.decode(AgentEventJsonCodec.encode(event)))
        val completedOverlay = AgentOverlayState.Initial.applyEvent(AgentEvent.RunFinished(1, 2))
        assertSame(completedOverlay, completedOverlay.applyEvent(event))
        val projector = AgentRunMessageProjector { 0L }
        val messages = listOf(AgentMessageUi("assistant-run-1-0", "final answer", isStreaming = false))
        val projected = projector.applyEvent("run", event, messages)
        assertEquals(SystemNoticeCode.RunPerformance, (projected.last() as SystemNoticeMessageUi).code)
        assertSame(projected, projector.applyEvent("run", event, projected))
        assertEquals(0, AgentRunMessageProjector.resultTargetIndex("run", projected, includeNotices = true))
        assertTrue(projector.resetForReplay("run", projected).isEmpty())
        val applied = AgentRunMessageProjector.applyResult("run", projected, "complete answer", null)
        assertEquals("complete answer", (applied.first() as AgentMessageUi).content)
        assertEquals(projected.last(), applied.last())
    }

    @Test fun malformedOrOversizedStatisticsCannotBecomeRestoredMessages() {
        assertNull(RunPerformanceSummary.fromJson("x".repeat(8193)))
        assertNull(RunPerformanceSummary.fromJson(summary.toJson().replace("\"version\":1", "\"version\":2")))
        assertNull(AgentEventJsonCodec.decode("""{"type":"run_performance","summary":"{}"}"""))
        assertEquals(summary, RunPerformanceSummary.fromJson(summary.toJson()))
    }

    @Test fun timingMetadataSurvivesIpcAndArchiveButPrivateMetricNamesDoNot() {
        val event = AgentEvent.ToolFinished(1, "call", "fetch_url", "ok", 0, 0, true,
            timingMs = mapOf("http" to 200L, "extraction" to 10L))
        assertEquals(event, AgentRuntimeWire.eventFromBundle(AgentRuntimeWire.eventToBundle(event)))
        assertEquals(event, AgentEventJsonCodec.decode(AgentEventJsonCodec.encode(event)))
        val unsafe = event.copy(timingMs = event.timingMs + ("private query" to 100L))
        assertEquals(event, AgentEventJsonCodec.decode(AgentEventJsonCodec.encode(unsafe)))

        val measured = summary.copy(toolTimingsMs = mapOf("http" to 900_000L), timedToolCalls = 3, firstDeltaIncludesTools = true)
        assertEquals(measured, RunPerformanceSummary.fromJson(measured.toJson()))
        val oldJson = JSONObject(summary.toJson()).apply {
            remove("tool_timings_ms"); remove("timed_tool_calls"); remove("first_delta_includes_tools")
        }
        val restored = RunPerformanceSummary.fromJson(oldJson.toString())!!
        assertEquals(summary, restored)
        assertFalse(restored.firstDeltaIncludesTools)
        assertTrue(restored.details(false).contains("text/reasoning delta"))
    }
}
