package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36, 37], application = Application::class)
class AgentSystemNotificationTest {
    @Test fun completedCardOpensItsOwnTargetAppOnlyOnUserTap() {
        val controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        val service = controller.get()
        val manager = service.getSystemService(NotificationManager::class.java)
        val target = "app.finished"
        val activity = android.content.pm.ActivityInfo().apply {
            packageName = target; name = "$target.MainActivity"; exported = true
            applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = target; enabled = true }
        }
        val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(target)
        shadowOf(service.packageManager).addResolveInfoForIntent(launcher,
            android.content.pm.ResolveInfo().apply { activityInfo = activity })
        try {
            AgentExecutionService.acquire(service, "app-click", onStop = {})
            val running = task("app-click").copy(targetPackage = target)
            AgentExecutionService.updateTasks(service, listOf(running))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            val original = manager.activeNotifications.single()
            AgentExecutionService.completeTask(service, running.copy(completed = true, status = AgentOverlayStatus.ResultReady))
            shadowOf(Looper.getMainLooper()).idle()
            val finished = manager.activeNotifications.single()
            assertEquals(original.id, finished.id)
            val destination = shadowOf(finished.notification.contentIntent).savedIntent
            assertEquals(CompletedAppActivity::class.java.name, destination.component?.className)
            assertEquals(target, destination.getStringExtra(CompletedAppActivity.EXTRA_PACKAGE))
            assertEquals("app-click", destination.getStringExtra(CompletedAppActivity.EXTRA_RUN_ID))
            assertNull(shadowOf(service.application).nextStartedActivity)
            finished.notification.contentIntent.send()
            assertEquals(CompletedAppActivity::class.java.name, shadowOf(service.application).nextStartedActivity.component?.className)
            assertEquals(2, finished.notification.actions.size)
            val reply = finished.notification.actions[0]
            assertEquals(Notification.Action.SEMANTIC_ACTION_REPLY, reply.semanticAction)
            assertEquals(AgentExecutionService.REPLY_TEXT, reply.remoteInputs.single().resultKey)
            assertFalse(reply.actionIntent.isImmutable)
            val input = android.content.Intent()
            android.app.RemoteInput.addResultsToIntent(reply.remoteInputs, input,
                android.os.Bundle().apply { putCharSequence(AgentExecutionService.REPLY_TEXT, "继续收菜") })
            reply.actionIntent.send(service, 0, input)
            assertNull("通知回复不能打开 Activity", shadowOf(service.application).nextStartedActivity)
            val continuation = shadowOf(reply.actionIntent).savedIntent
            assertEquals(AgentExecutionService::class.java.name, continuation.component?.className)
            assertEquals(AgentExecutionService.ACTION_REPLY, continuation.action)
            assertEquals("chat-app-click", continuation.getStringExtra(AgentExecutionService.EXTRA_CONVERSATION_ID))
            finished.notification.actions[1].actionIntent.send()
            assertEquals("chat-app-click", shadowOf(service.application).nextStartedActivity
                .getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
            assertEquals(original.id, manager.activeNotifications.single().id)
        } finally {
            AgentExecutionService.release("app-click")
            AgentExecutionService.updateTasks(service, emptyList())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            controller.destroy()
            manager.cancelAll()
        }
    }

    private fun task(id: String, status: AgentOverlayStatus = AgentOverlayStatus.Reasoning, paused: Boolean = false) =
        TaskNotificationState(id, "chat-$id", "任务$id", status, "进度$id", paused)

    @Test fun concurrentTasksHaveSeparateCardsAndIndependentChatAndControls() {
        val controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        val service = controller.get()
        val manager = service.getSystemService(NotificationManager::class.java)
        try {
            AgentExecutionService.acquire(service, "test-native", onStop = {})
            val a = task("a")
            val b = task("b", AgentOverlayStatus.Paused, true)
            val c = task("c", AgentOverlayStatus.GeneratingAnswer)
            AgentExecutionService.updateTasks(service, listOf(a, b, c))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            val cards = manager.activeNotifications.associateBy {
                shadowOf(it.notification.contentIntent).savedIntent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID)
            }
            assertEquals(setOf("chat-a", "chat-b", "chat-c"), cards.keys)
            cards.forEach { (chat, entry) ->
                val card = entry.notification
                assertEquals(chat != "chat-b", card.extras.getBoolean("android.requestPromotedOngoing"))
                // API 36 accepts the standard request extra; its framework eligibility
                // helper does not implement the newer 36.1+/37 promotion contract.
                if (android.os.Build.VERSION.SDK_INT >= 37) {
                    assertEquals(chat != "chat-b", card.hasPromotableCharacteristics())
                }
                assertEquals(2, card.actions.size)
                assertTrue(card.actions.all { shadowOf(it.actionIntent).savedIntent.getStringExtra("run_id") == chat!!.removePrefix("chat-") })
                assertFalse(card.actions.any { shadowOf(it.actionIntent).savedIntent.action == AgentExecutionService.ACTION_NEXT_TASK })
            }
            val firstDestination = cards.getValue("chat-a").notification.contentIntent
            AgentExecutionService.updateTasks(service, listOf(a, b, c.copy(streamPreview = "单独更新第三项")))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertSame(cards.getValue("chat-a").notification,
                manager.activeNotifications.single { it.id == cards.getValue("chat-a").id }.notification)
            assertEquals(AgentExecutionService.ACTION_RESUME_RUN,
                shadowOf(cards.getValue("chat-b").notification.actions[0].actionIntent).savedIntent.action)
            // Completion updates A in place immediately while B and C keep running.
            AgentExecutionService.completeTask(service, a.copy(completed = true, status = AgentOverlayStatus.ResultReady))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(3, manager.activeNotifications.size)
            val completedA = manager.activeNotifications.single { it.id == cards.getValue("chat-a").id }
            assertFalse(completedA.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(cards.getValue("chat-a").notification.smallIcon.resId, completedA.notification.smallIcon.resId)
            AgentExecutionService.updateTasks(service, listOf(b, c.copy(streamPreview = "新进度")))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals(3, manager.activeNotifications.size)
            assertEquals("chat-a", shadowOf(firstDestination).savedIntent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
            assertEquals("chat-c", shadowOf(shadowOf(service).lastForegroundNotification.contentIntent)
                .savedIntent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
            val remainingC = manager.activeNotifications.single { it.id == cards.getValue("chat-c").id }
            assertTrue(remainingC.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("新进度"))
        } finally {
            AgentExecutionService.release("test-native")
            AgentExecutionService.updateTasks(service, emptyList())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            controller.destroy()
            manager.cancelAll()
        }
    }

    @Test fun completionUpdatesOriginalCardImmediatelyAndSurvivesForegroundRelease() {
        val controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        val service = controller.get()
        val manager = service.getSystemService(NotificationManager::class.java)
        try {
            AgentExecutionService.acquire(service, "test-completion", onStop = {})
            AgentExecutionService.updateTasks(service, listOf(TaskNotificationState("run-complete", "chat-complete", "测试",
                AgentOverlayStatus.GeneratingAnswer, "处理中")))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            val originalId = manager.activeNotifications.single().id
            AgentExecutionService.publishRunCompletion(service, "run-complete", "chat-complete", "已完成", "查询完成")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, manager.activeNotifications.size)
            assertFalse(manager.activeNotifications.single().notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(originalId, manager.activeNotifications.single().id)
            AgentExecutionService.release("test-completion")
            AgentExecutionService.updateTasks(service, emptyList())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            val completed = manager.activeNotifications.single()
            assertEquals(originalId, completed.id)
            assertFalse(completed.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(completed.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("查询完成"))
            assertEquals(Notification.ProgressStyle::class.java.name, completed.notification.extras.getString(Notification.EXTRA_TEMPLATE))
            assertEquals("chat-complete", shadowOf(completed.notification.contentIntent).savedIntent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
            // Repeated release callbacks must not remove the result that replaced the foreground card.
            AgentExecutionService.release("test-completion")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals(originalId, manager.activeNotifications.single().id)
            manager.cancel(originalId)
            AgentExecutionService.publishRunCompletion(service, "run-complete", "chat-complete", "已完成", "迟到的归档通知")
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(manager.activeNotifications.isEmpty())
        } finally {
            AgentExecutionService.release("test-completion")
            AgentExecutionService.updateTasks(service, emptyList())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            controller.destroy()
            manager.cancelAll()
        }
    }
}
