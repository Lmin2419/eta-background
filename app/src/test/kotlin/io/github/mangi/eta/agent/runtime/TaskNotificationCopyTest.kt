package io.github.mangi.eta.agent.runtime

import android.app.Application
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TaskNotificationCopyTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources
    private fun card(status: AgentOverlayStatus = AgentOverlayStatus.Reasoning) = TaskNotificationState("notice", "chat", "Task", status)
    @Test fun titleKeepsAppPrefixAndNeverBreaksSurrogatePairs() {
        assertEquals("App · Task", taskNotificationCopy(resources, card(), "App").title)
        assertEquals("Task", taskNotificationCopy(resources, card(), null).title)
        val long = taskNotificationCopy(resources, card().copy(title = "😀".repeat(50)), "App").title
        assertTrue(long.startsWith("App · ")); assertTrue(long.length <= 60)
        assertFalse(long.dropLast(1).last().isHighSurrogate())
    }
    @Test fun runningUsesKnownStepButNeverStreamsThoughtIntoNotification() {
        val card = card().copy(streamPreview = "private reasoning")
        val withStep = taskNotificationCopy(resources, card, null, 7).body
        assertTrue(withStep.endsWith(resources.getString(R.string.task_notice_step, 7)))
        assertFalse(withStep.contains("private reasoning"))
        val noStep = taskNotificationCopy(resources, card, null).body
        assertFalse(noStep.contains("7")); assertFalse(noStep.contains(" · "))
    }
    @Test fun terminalStatusPrecedesStalePauseAndReplyFollowsStatus() {
        val copy = taskNotificationCopy(resources, card(AgentOverlayStatus.ResultReady).copy(
            completed = true, paused = true, replyFailed = true, streamPreview = "Result 3.14 at https://a.b/c. More details"), null, elapsedMs = 125000)
        assertEquals(listOf(resources.getString(R.string.task_notice_success), resources.getString(R.string.task_notice_reply_failed),
            resources.getString(R.string.task_notice_duration, "2:05"), "Result 3.14 at https://a.b/c.").joinToString(" · "), copy.body)
        assertFalse(taskNotificationCopy(resources, card(AgentOverlayStatus.ResultReady), null).body.contains("0:00"))
    }
    @Test fun attentionPausedQueuedAndFailureUseTheCorrectTemplate() {
        assertEquals(resources.getString(R.string.task_notice_attention) + " · Code required。",
            taskNotificationCopy(resources, card(AgentOverlayStatus.ContinuationUnavailable).copy(streamPreview = "Code required。Other"), null).body)
        assertEquals(resources.getString(R.string.task_notice_paused), taskNotificationCopy(resources, card(AgentOverlayStatus.Paused), null).body)
        assertEquals(resources.getString(R.string.task_notice_queued), taskNotificationCopy(resources, card(AgentOverlayStatus.Queued), null).body)
        assertEquals(resources.getString(R.string.task_notice_failed) + " · Error",
            taskNotificationCopy(resources, card(AgentOverlayStatus.RunFailed).copy(streamPreview = "Error\nstack details"), null).body)
    }
}
