package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.core.AgentLogger
import org.junit.Assert.*
import org.junit.Test

class AgentRunTimingTest {
    private val logger = object : AgentLogger {
        override fun debug(message: () -> String) = Unit // Release may strip debug; statistics must still work.
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }

    @Test fun overlappingToolsAndPauseAreMeasuredAsDisjointElapsedTime() {
        var now = 0L
        val timing = AgentRunTiming(logger) { now }
        now = 100; timing.preparationFinished(0)
        now = 200; timing.accept(AgentEvent.ProviderRequestStarted(1))
        now = 400; timing.accept(AgentEvent.AssistantBlockDelta(1, AgentEvent.AssistantBlockKind.THINKING, 0, 1, "x"))
        now = 500; timing.accept(AgentEvent.AssistantReceived(1, 0, "", listOf("a", "b")))
        timing.accept(start("a"))
        now = 600; timing.accept(start("b"))
        now = 700; timing.pauseChanged(true)
        now = 900; timing.pauseChanged(false)
        now = 1_000; timing.accept(end("a", images = 1, bytes = 200))
        timing.accept(AgentEvent.ToolImagesAttached(1, "a", 1, 200)) // Do not double count the same images.
        now = 1_100; timing.accept(end("b"))
        now = 1_200; timing.beginCleanup()
        now = 1_300
        val result = timing.finish()
        assertEquals(1_300L, result.elapsedMs)
        assertEquals(100L, result.preparationMs)
        assertEquals(300L, result.modelMs)
        assertEquals(400L, result.toolsMs)
        assertEquals(200L, result.pausedMs)
        assertEquals(300L, result.otherMs)
        assertEquals(result.elapsedMs, result.preparationMs + result.modelMs + result.toolsMs + result.pausedMs + result.otherMs)
        assertEquals(200L, result.firstDeltaTotalMs)
        assertEquals(1, result.firstDeltaSamples)
        assertEquals(1, result.imageCount)
        assertEquals(200L, result.imageBytes)
        assertEquals(2, result.toolCalls)
        now = 9_000; timing.accept(start("late")); timing.pauseChanged(true)
        assertSame(result, timing.finish())
    }

    @Test fun retryWaitAndCancelledToolCleanupDoNotPretendToBeModelOrToolWork() {
        var now = 0L
        val timing = AgentRunTiming(logger) { now }
        timing.preparationFinished(0)
        timing.accept(AgentEvent.ProviderRequestStarted(1))
        now = 200; timing.accept(AgentEvent.ModelRetryScheduled(1, 1, 3, 2_000, "MODEL_TIMEOUT"))
        now = 2_200; timing.accept(AgentEvent.ProviderRequestStarted(2))
        now = 2_700; timing.accept(AgentEvent.AssistantReceived(2, 0, "", listOf("pending")))
        timing.accept(start("pending"))
        now = 2_800; timing.beginCleanup()
        now = 3_000
        val result = timing.finish()
        assertEquals(2, result.requests)
        assertEquals(1, result.retries)
        assertEquals(700L, result.modelMs)
        assertEquals(100L, result.toolsMs)
        assertEquals(2_200L, result.otherMs)
        assertEquals(1, result.unfinishedTools)
        assertEquals(0, result.firstDeltaSamples)
    }

    @Test fun slowCallsAreBoundedAndNeverCaptureArgumentsOrResultBodies() {
        var now = 0L
        val timing = AgentRunTiming(logger) { now }
        timing.preparationFinished(0)
        repeat(500) { i ->
            timing.accept(AgentEvent.ToolStarted(1, "$i", if (i == 499) "private text!" else "screen_dump", "secret-argument"))
            now += i
            timing.accept(AgentEvent.ToolFinished(1, "$i", "screen_dump", "secret-result", 0, 0, false))
        }
        val summary = timing.finish()
        assertEquals(3, summary.slowTools.size)
        assertEquals(500, summary.failedTools)
        assertEquals(499L, summary.slowTools.first().elapsedMs)
        assertEquals("tool", summary.slowTools.first().name)
        assertFalse(summary.toString().contains("secret"))
    }

    @Test fun toolsBeyondMetadataBudgetStillContributeToElapsedToolPhase() {
        var now = 0L
        val timing = AgentRunTiming(logger) { now }
        timing.preparationFinished(0)
        repeat(257) { timing.accept(start("call-$it")) }
        now = 100
        repeat(256) { timing.accept(end("call-$it")) }
        now = 900; timing.accept(end("call-256"))
        assertEquals(900L, timing.finish().toolsMs)
        assertEquals(0L, timing.finish().otherMs)
    }

    private fun start(id: String) = AgentEvent.ToolStarted(1, id, id, "")
    private fun end(id: String, images: Int = 0, bytes: Int = 0) =
        AgentEvent.ToolFinished(1, id, id, "", images, bytes, true)
}
