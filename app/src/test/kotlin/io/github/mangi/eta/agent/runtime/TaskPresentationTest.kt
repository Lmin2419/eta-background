package io.github.mangi.eta.agent.runtime

import android.app.Application
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TaskPresentationTest {
    private fun run(): RuntimeRunRegistry.Run {
        val req = AgentRuntimeWire.RunRequest("run", "test", AgentModelClient.ModelConfig(
            baseUrl = "https://example.invalid", apiKey = "test", model = "test", systemPrompt = ""), emptyList())
        return RuntimeRunRegistry.Run(req, AgentRuntimeSession("run"))
    }

    @Test fun allSurfacesWaitForTheSameTerminalCommitEvenAfterModelCompletion() {
        val run = run(); run.queued = false; run.prepared(run.request)
        run.overlayState = run.overlayState.copy(status = AgentOverlayStatus.ResultReady)
        assertEquals(AgentOverlayStatus.Finishing, run.presentationStatus())
        val committing = CountDownLatch(1); val persist = CountDownLatch(1)
        val worker = thread {
            run.session.complete(AgentRuntimeWire.RunResult("run", true, "done", null)) {
                committing.countDown(); persist.await()
            }
        }
        try {
            assertTrue(committing.await(2, TimeUnit.SECONDS))
            assertEquals(AgentRuntimeWire.RunPhase.Committing, run.snapshot().phase)
            run.session.controller.pause()
            assertEquals(AgentOverlayStatus.Finishing, run.presentationStatus())
            assertNull(run.session.result)
            assertFalse(run.session.cancel("已停止"))
            persist.countDown(); worker.join(2000)
            assertEquals(AgentOverlayStatus.ResultReady, run.presentationStatus())
            assertFalse(run.session.emit(AgentEvent.RunFailed("late")))
            assertEquals(AgentOverlayStatus.ResultReady, run.presentationStatus())
        } finally { persist.countDown(); worker.join(2000) }
    }

    @Test fun queuePreparationGuiWaitAndPauseUseRuntimePriority() {
        val run = run()
        assertEquals(AgentOverlayStatus.Queued, run.presentationStatus())
        run.queued = false
        assertEquals(AgentOverlayStatus.Preparing, run.presentationStatus())
        run.prepared(run.request); run.guiStatus = GuiControlStatus.Waiting
        assertEquals(AgentOverlayStatus.WaitingForGui, run.presentationStatus())
        run.session.controller.pause(forAppTakeover = true)
        assertEquals(AgentOverlayStatus.AppTakeoverPaused, run.presentationStatus())
        assertTrue(run.session.cancel("已停止"))
        assertEquals(AgentOverlayStatus.Stopped, run.presentationStatus())
    }

    @Test fun cancelledQueuedTaskIsStoppingUntilItsTerminalCommit() {
        val run = run()
        run.session.controller.cancel()
        assertEquals(AgentRuntimeWire.RunPhase.Stopping, run.snapshot().phase)
        assertEquals(AgentOverlayStatus.Stopping, run.presentationStatus())
        run.session.complete(AgentRuntimeWire.RunResult(run.runId, false, "", "已停止"))
        assertEquals(AgentOverlayStatus.Stopped, run.presentationStatus())
    }
}
