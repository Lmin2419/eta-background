package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.Notification
import android.content.Context
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36, 37], application = Application::class)
class Stage81NotificationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun task(chat: String? = "chat-1", completed: Boolean = true, target: String? = "app.finished",
        status: AgentOverlayStatus = AgentOverlayStatus.ResultReady) =
        TaskNotificationState("run", chat, "App", status, completed = completed, targetPackage = target)
    private fun notification(task: TaskNotificationState): Notification =
        AgentExecutionService.Companion::class.java.getDeclaredMethod("notification", Context::class.java,
            TaskNotificationState::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(AgentExecutionService.Companion, context, task, 7, false) as Notification

    @Test fun allCompletedOutcomesOpenTheConversationAndKeepThreeIndependentActions() {
        for (status in listOf(AgentOverlayStatus.ResultReady, AgentOverlayStatus.RunFailed, AgentOverlayStatus.Stopped)) {
            val n = notification(task(status = status))
            val body = shadowOf(n.contentIntent).savedIntent
            assertEquals(MainActivity::class.java.name, body.component?.className)
            assertEquals(MainActivity.ACTION_OPEN_CONVERSATION_ID, body.action)
            assertEquals("chat-1", body.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
            assertEquals(3, n.actions.size)
            assertEquals(n.contentIntent, n.actions[0].actionIntent)
            val app = shadowOf(n.actions[1].actionIntent).savedIntent
            assertEquals(CompletedAppActivity::class.java.name, app.component?.className)
            assertEquals("run", app.getStringExtra(CompletedAppActivity.EXTRA_RUN_ID))
            assertEquals("app.finished", app.getStringExtra(CompletedAppActivity.EXTRA_PACKAGE))
            assertEquals(AgentExecutionService.ACTION_REPLY, shadowOf(n.actions[2].actionIntent).savedIntent.action)
            assertEquals(AgentExecutionService.REPLY_TEXT, n.actions[2].remoteInputs.single().resultKey)
        }
    }
    @Test fun runningWithConversationStillOpensPreview() {
        val n = notification(task(completed = false, status = AgentOverlayStatus.Reasoning))
        assertEquals(TaskPreviewActivity::class.java.name, shadowOf(n.contentIntent).savedIntent.component?.className)
        assertEquals(TaskPreviewActivity::class.java.name, shadowOf(n.actions[0].actionIntent).savedIntent.component?.className)
    }
    @Test fun completionWithoutConversationKeepsItsPreviewFallback() {
        for (chat in listOf(null, "", "   ")) {
            val n = notification(task(chat = chat))
            assertEquals(TaskPreviewActivity::class.java.name, shadowOf(n.contentIntent).savedIntent.component?.className)
        }
    }
    @Test fun completionWithoutTargetKeepsItsConversationFallback() {
        val n = notification(task(target = null))
        val intent = shadowOf(n.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals("chat-1", intent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
    }
}
