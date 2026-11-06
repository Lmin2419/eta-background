package io.github.mangi.eta.ui.model

import android.app.Application
import io.github.mangi.eta.agent.overlay.AgentOverlayPhase
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.BackgroundScreenPhase
import io.github.mangi.eta.agent.runtime.TaskNotificationState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TaskVisualStatusTest {
    @Test fun overlayAndHistoryOutcomesAgreeIncludingStoppedRuns() {
        assertEquals(listOf(TaskVisualStatus.Running, TaskVisualStatus.Paused, TaskVisualStatus.Success, TaskVisualStatus.Failed),
            AgentOverlayPhase.entries.map { it.toTaskVisualStatus() })
        assertEquals(listOf(TaskVisualStatus.Running, TaskVisualStatus.Success, TaskVisualStatus.Failed, TaskVisualStatus.Failed),
            RunStatusUi.entries.map { it.toTaskVisualStatus() })
        assertEquals(listOf(TaskVisualStatus.Running, TaskVisualStatus.Success, TaskVisualStatus.Failed, TaskVisualStatus.Attention),
            ToolActivityStatusUi.entries.map { it.toTaskVisualStatus() })
    }

    @Test fun notificationPauseAndQueueAreDistinctFromProgress() {
        val pausedStatuses = listOf(AgentOverlayStatus.Paused, AgentOverlayStatus.PreviewPaused,
            AgentOverlayStatus.AppTakeoverPaused)
        pausedStatuses.forEach { assertEquals(it.toString(), TaskVisualStatus.Paused, card(it).toTaskVisualStatus()) }
        listOf(AgentOverlayStatus.Queued, AgentOverlayStatus.WaitingForGui).forEach {
            assertEquals(TaskVisualStatus.Queued, card(it).toTaskVisualStatus())
            assertEquals(TaskVisualStatus.Paused, card(it).copy(paused = true).toTaskVisualStatus())
        }
        val progressing = listOf(AgentOverlayStatus.Preparing, AgentOverlayStatus.Reasoning,
            AgentOverlayStatus.WaitingForModel, AgentOverlayStatus.SupplementReceived,
            AgentOverlayStatus.RunningTool("tap"), AgentOverlayStatus.HostedToolRunning("search"),
            AgentOverlayStatus.GeneratingAnswer, AgentOverlayStatus.Stopping,
            AgentOverlayStatus.Continuing, AgentOverlayStatus.Finishing) +
            BackgroundScreenPhase.entries.map { AgentOverlayStatus.BackgroundScreen(it) }
        progressing.forEach {
            assertEquals(it.toString(), TaskVisualStatus.Running, card(it).toTaskVisualStatus())
            assertEquals(TaskVisualStatus.Paused, card(it).copy(paused = true).toTaskVisualStatus())
        }
    }

    @Test fun terminalResultsOutrankStalePauseFlagsAndReplyDeliveryFailures() {
        val results = mapOf(AgentOverlayStatus.ResultReady to TaskVisualStatus.Success,
            AgentOverlayStatus.RunFailed to TaskVisualStatus.Failed, AgentOverlayStatus.Stopped to TaskVisualStatus.Failed)
        results.forEach { (status, expected) ->
            listOf(false, true).forEach { paused ->
                listOf(false, true).forEach { replyFailed ->
                    assertEquals(expected, card(status).copy(completed = true, paused = paused, replyFailed = replyFailed).toTaskVisualStatus())
                }
            }
        }
    }

    @Test fun incompleteOutcomeAndContinuationProblemsNeverInventSuccessOrKeepSpinning() {
        assertEquals(TaskVisualStatus.Attention, card(AgentOverlayStatus.Reasoning).copy(completed = true).toTaskVisualStatus())
        assertEquals(TaskVisualStatus.Attention, card(AgentOverlayStatus.ContinuationUnavailable).copy(completed = true).toTaskVisualStatus())
        assertEquals(TaskVisualStatus.Attention, card(AgentOverlayStatus.ContinuationUnavailable).toTaskVisualStatus())
        assertEquals(TaskVisualStatus.Running, card(AgentOverlayStatus.Reasoning).copy(replyFailed = true).toTaskVisualStatus())
    }

    private fun card(status: AgentOverlayStatus) = TaskNotificationState("run", "conversation", "Task", status)
}
