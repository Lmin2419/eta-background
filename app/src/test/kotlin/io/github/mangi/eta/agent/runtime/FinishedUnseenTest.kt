package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.ui.components.UnseenLevel
import io.github.mangi.eta.ui.components.unseenSummary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 09 第一期 1.1：跑完了还没看的对话留下圆点，打开才清掉。 */
class FinishedUnseenTest {
    @After fun reset() { FinishedUnseen.resetForTest(); ForegroundConversation.id = null }

    private fun task(conversation: String?, status: AgentOverlayStatus = AgentOverlayStatus.ResultReady, replyFailed: Boolean = false) =
        TaskNotificationState("r-$conversation", conversation, "任务", status, completed = true, replyFailed = replyFailed)

    @Test fun finishedConversationsAreRememberedUntilOpened() {
        FinishedUnseen.record(task("c1"))
        FinishedUnseen.record(task("c2", AgentOverlayStatus.RunFailed))
        assertEquals(mapOf("c1" to false, "c2" to true), FinishedUnseen.state.value)
        FinishedUnseen.clear("c1")
        assertEquals(mapOf("c2" to true), FinishedUnseen.state.value)
    }

    @Test fun theConversationOnScreenStoppedTasksAndTasksWithoutAConversationAreNotRemembered() {
        ForegroundConversation.id = "c1"
        FinishedUnseen.record(task("c1"))
        FinishedUnseen.record(task("c3", AgentOverlayStatus.Stopped))
        FinishedUnseen.record(task(null))
        assertEquals(emptyMap<String, Boolean>(), FinishedUnseen.state.value)
    }

    @Test fun theHistoryButtonShowsTheMostUrgentDot() {
        assertNull(unseenSummary(emptyMap(), emptySet(), emptySet(), null))
        assertEquals(UnseenLevel.Done, unseenSummary(mapOf("c1" to false), emptySet(), emptySet(), null))
        assertEquals(UnseenLevel.Done, unseenSummary(emptyMap(), setOf("desktop-a"), emptySet(), null))
        assertEquals(UnseenLevel.Failed, unseenSummary(mapOf("c1" to false, "c2" to true), emptySet(), emptySet(), null))
        assertEquals(UnseenLevel.Attention, unseenSummary(mapOf("c2" to true), emptySet(), setOf("desktop-b"), null))
        // 正在看的对话不算。
        assertNull(unseenSummary(mapOf("c1" to true), emptySet(), emptySet(), "c1"))
    }
}
