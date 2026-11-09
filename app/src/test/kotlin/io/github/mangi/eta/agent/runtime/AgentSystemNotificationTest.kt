package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import io.github.mangi.eta.R
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
    @org.junit.Before @org.junit.After fun clearRetainedCardsBetweenTests() {
        dismissRetainedTaskCardsForTest(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun runningBackgroundCardOpensConversationAndBodyOpensPreview() {
        val host = Robolectric.buildService(AgentExecutionService::class.java).create()
        val service = host.get()
        val manager = service.getSystemService(NotificationManager::class.java)
        try {
            AgentExecutionService.acquire(service, "running-chat", onStop = {})
            val task = task("running-chat").copy(targetPackage = "app.background")
            AgentExecutionService.updateTasks(service, listOf(task))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            val card = manager.activeNotifications.single().notification
            // 点通知正文仍进入后台虚拟屏预览。
            assertEquals(TaskPreviewActivity::class.java.name, shadowOf(card.contentIntent).savedIntent.component?.className)
            assertEquals(listOf(R.string.overlay_open_conversation, R.string.overlay_pause, R.string.overlay_stop_task).map(service::getString),
                card.actions.map { it.title.toString() })
            // 「打开会话」按钮即使有后台屏也回到胖小胖的会话，而不是虚拟屏。
            val open = card.actions[0]
            open.actionIntent.send()
            val intent = shadowOf(service.application).nextStartedActivity
            assertEquals(io.github.mangi.eta.ui.MainActivity::class.java.name, intent.component?.className)
            assertEquals(io.github.mangi.eta.ui.MainActivity.ACTION_OPEN_CONVERSATION_ID, intent.action)
            assertEquals(task.conversationId, intent.getStringExtra(io.github.mangi.eta.ui.MainActivity.EXTRA_CONVERSATION_ID))
            assertEquals(task, AgentExecutionService.task(task.runId))
            assertEquals(listOf(AgentExecutionService.ACTION_PAUSE_RUN, AgentExecutionService.ACTION_STOP_RUN),
                card.actions.drop(1).map { shadowOf(it.actionIntent).savedIntent.action })
        } finally {
            AgentExecutionService.release("running-chat")
            AgentExecutionService.updateTasks(service, emptyList())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            host.destroy(); manager.cancelAll()
        }
    }

    @Test fun completedCardOpensConversationAndItsExplicitOpenAppActionHandsOff() {
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
            assertEquals(MainActivity::class.java.name, destination.component?.className)
            assertEquals(MainActivity.ACTION_OPEN_CONVERSATION_ID, destination.action)
            assertEquals("chat-app-click", destination.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
            assertNull(shadowOf(service.application).nextStartedActivity)
            finished.notification.contentIntent.send()
            assertEquals(MainActivity::class.java.name, shadowOf(service.application).nextStartedActivity.component?.className)
            assertEquals(3, finished.notification.actions.size)
            assertFalse(finished.notification.contentIntent == finished.notification.actions[1].actionIntent)
            assertEquals(CompletedAppActivity::class.java.name, shadowOf(finished.notification.actions[1].actionIntent).savedIntent.component?.className)
            assertEquals(target, shadowOf(finished.notification.actions[1].actionIntent).savedIntent.getStringExtra(CompletedAppActivity.EXTRA_PACKAGE))
            assertNotNull(finished.notification.getLargeIcon())
            val reply = finished.notification.actions[2]
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
            finished.notification.actions[0].actionIntent.send()
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

    @Test fun rejectedReplyKeepsTheCompletedResultAndItsRetryAction() {
        val host = Robolectric.buildService(AgentExecutionService::class.java).create()
        val service = host.get()
        val manager = service.getSystemService(NotificationManager::class.java)
        val result = task("reply-feedback", AgentOverlayStatus.ResultReady)
            .copy(completed = true, streamPreview = "查询成功")
        val main = shadowOf(Looper.getMainLooper())
        var dismiss: Intent? = null
        try {
            AgentExecutionService.acquire(service, "reply-feedback-test", onStop = {})
            AgentExecutionService.completeTask(service, result)
            main.idle()
            val original = manager.activeNotifications.single()
            dismiss = shadowOf(original.notification.deleteIntent).savedIntent
            val reply = original.notification.actions.single { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
            val intent = Intent(shadowOf(reply.actionIntent).savedIntent)
            RemoteInput.addResultsToIntent(reply.remoteInputs, intent, Bundle().apply {
                putCharSequence(AgentExecutionService.REPLY_TEXT, "继续处理")
            })
            shadowOf(service.application).declareActionUnbindable(AgentRuntimeWire.ACTION_BIND)
            service.onStartCommand(intent, 0, 1)
            main.idle()

            assertEquals(result.copy(replyFailed = true), AgentExecutionService.task(result.runId))
            val archived = result.copy(streamPreview = "归档后的查询结果")
            AgentExecutionService.completeTask(service, archived)
            main.idle()
            val failedReply = manager.activeNotifications.single()
            assertEquals(original.id, failedReply.id)
            assertEquals(archived.copy(replyFailed = true), AgentExecutionService.task(result.runId))
            val text = failedReply.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            assertTrue(text.contains(service.getString(R.string.task_notice_success)))
            assertTrue(text.contains(archived.streamPreview))
            assertTrue(text.contains(service.getString(R.string.task_notice_reply_failed)))
            assertTrue(failedReply.notification.actions.any { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY })
        } finally {
            dismiss?.let { AgentExecutionService.onNotificationDismissed(service, it) }
            AgentExecutionService.release("reply-feedback-test")
            AgentExecutionService.updateTasks(service, emptyList())
            main.idleFor(Duration.ofSeconds(1))
            host.destroy()
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
                assertEquals(3, card.actions.size)
                assertTrue(card.actions.drop(1).all { shadowOf(it.actionIntent).savedIntent.getStringExtra("run_id") == chat!!.removePrefix("chat-") })
                assertEquals(chat, shadowOf(card.actions[0].actionIntent).savedIntent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
                assertFalse(card.actions.any { shadowOf(it.actionIntent).savedIntent.action == AgentExecutionService.ACTION_NEXT_TASK })
            }
            val firstDestination = cards.getValue("chat-a").notification.contentIntent
            AgentExecutionService.updateTasks(service, listOf(a, b, c.copy(streamPreview = "单独更新第三项")))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertSame(cards.getValue("chat-a").notification,
                manager.activeNotifications.single { it.id == cards.getValue("chat-a").id }.notification)
            assertEquals(AgentExecutionService.ACTION_RESUME_RUN,
                shadowOf(cards.getValue("chat-b").notification.actions[1].actionIntent).savedIntent.action)
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
            assertEquals(taskNotificationCopy(service.resources, c.copy(streamPreview = "新进度"), null).body,
                remainingC.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
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
