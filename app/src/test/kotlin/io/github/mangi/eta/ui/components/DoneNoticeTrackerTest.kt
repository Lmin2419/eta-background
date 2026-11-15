package io.github.mangi.eta.ui.components

import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.TaskNotificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 首页「别的对话已完成」提示条：只报刚结束的、别的对话的任务。 */
class DoneNoticeTrackerTest {
    private fun task(run: String, conversation: String?, completed: Boolean, status: AgentOverlayStatus = AgentOverlayStatus.ResultReady) =
        TaskNotificationState(runId = run, conversationId = conversation, title = "任务 $run", status = status, completed = completed)

    @Test fun cardsAlreadyFinishedWhenTheAppOpensAreNotAnnounced() {
        val tracker = DoneNoticeTracker()
        assertNull(tracker.newlyFinished(listOf(task("r1", "c2", completed = true)), "c1"))
    }

    @Test fun aTaskInAnotherConversationFinishingIsAnnounced() {
        val tracker = DoneNoticeTracker()
        tracker.newlyFinished(listOf(task("r1", "c2", completed = false)), "c1")
        assertEquals("r1", tracker.newlyFinished(listOf(task("r1", "c2", completed = true)), "c1")?.runId)
        assertNull("announced once", tracker.newlyFinished(listOf(task("r1", "c2", completed = true)), "c1"))
    }

    @Test fun theCurrentConversationStoppedTasksAndTasksWithoutAConversationAreNot() {
        val tracker = DoneNoticeTracker()
        tracker.newlyFinished(emptyList(), "c1")
        assertNull(tracker.newlyFinished(listOf(task("r1", "c1", completed = true)), "c1"))
        assertNull(tracker.newlyFinished(listOf(task("r2", null, completed = true)), "c1"))
        assertNull(tracker.newlyFinished(listOf(task("r3", "c2", completed = true, status = AgentOverlayStatus.Stopped)), "c1"))
    }
}
