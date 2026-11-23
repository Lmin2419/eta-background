package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34], application = Application::class)
class AgentExecutionServiceTest {
    @Test
    @Config(sdk = [33, 36])
    fun agentRunPromotionEndsWhileTerminalSessionRemains() {
        val context = RuntimeEnvironment.getApplication()
        val leaseId = "test:terminal-session"
        assertTrue(AgentExecutionService.acquire(context, leaseId) {})
        AgentExecutionService.updateTasks(context, listOf(TaskNotificationState(
            runId = "test:agent", conversationId = null, title = "test", status = AgentOverlayStatus.Reasoning,
        )))
        shadowOf(Looper.getMainLooper()).idle()
        val controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        try {
            val notification = requireNotNull(shadowOf(controller.get()).lastForegroundNotification)
            assertEquals(Build.VERSION.SDK_INT >= 36, notification.extras.getBoolean("android.requestPromotedOngoing"))
            if (Build.VERSION.SDK_INT >= 36) {
                assertEquals(Notification.ProgressStyle::class.java.name, notification.extras.getString(Notification.EXTRA_TEMPLATE))
                assertEquals(context.getString(R.string.task_short_thinking), notification.extras.getString("android.shortCriticalText"))
            }

            AgentExecutionService.updateTasks(context, emptyList())
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            val remaining = context.getSystemService(NotificationManager::class.java).activeNotifications.single().notification
            assertFalse(remaining.extras.getBoolean("android.requestPromotedOngoing"))
            assertFalse(remaining.extras.containsKey("android.shortCriticalText"))
            assertTrue(remaining.flags and Notification.FLAG_ONGOING_EVENT != 0)
        } finally {
            AgentExecutionService.release(leaseId)
            shadowOf(Looper.getMainLooper()).idle()
            controller.destroy()
        }
    }

    /** 结束时的竞态：任务列表先把卡片去掉，「完成」随后才到——不能留下一张停在「生成回答中」的卡片。 */
    @Test
    @Config(sdk = [36])
    fun finishRaceNeverLeavesARunningCardBehind() {
        val context = RuntimeEnvironment.getApplication()
        val runId = "test:finish-race"
        val earlier = "test:finish-race-earlier"
        // 和真机一样：通知栏里先有一张别的任务的卡片（上一个被停止的任务），编号不再是默认的那个。
        assertTrue(AgentExecutionService.acquire(context, "run:$earlier") {})
        val first = TaskNotificationState(runId = earlier, conversationId = "c-race", title = "后台帮我看一下",
            status = AgentOverlayStatus.RunningTool("tap"))
        AgentExecutionService.updateTasks(context, listOf(first))
        assertTrue(AgentExecutionService.acquire(context, "run:$runId") {})
        val running = TaskNotificationState(runId = runId, conversationId = "c-race", title = "继续",
            status = AgentOverlayStatus.GeneratingAnswer, step = 6)
        AgentExecutionService.completeTask(context, first.copy(status = AgentOverlayStatus.Stopped))
        AgentExecutionService.release("run:$earlier")
        AgentExecutionService.updateTasks(context, listOf(running))
        shadowOf(Looper.getMainLooper()).idle()
        val controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        try {
            assertTrue(shadowOf(controller.get()).lastForegroundNotification!!.extras.getBoolean("android.requestPromotedOngoing"))
            val runningId = shadowOf(controller.get()).lastForegroundNotificationId
            AgentExecutionService.updateTasks(context, emptyList())
            AgentExecutionService.release("run:$runId")
            AgentExecutionService.completeTask(context, running.copy(status = AgentOverlayStatus.ResultReady, streamPreview = "好了"))
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
            val active = context.getSystemService(NotificationManager::class.java).activeNotifications
            // 「已完成」落回原来那条通知上（原地更新），而不是另开一条、把运行卡片留在通知栏。
            val mine = active.filter { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.contains("继续") == true }
            assertEquals(listOf(runningId), mine.map { it.id })
            assertTrue(active.none { it.notification.extras.getBoolean("android.requestPromotedOngoing") })
            assertTrue(active.none { it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 })
            assertEquals(2, active.size)
        } finally {
            dismissCompleted(context, runId)
            dismissCompleted(context, earlier)
            controller.destroy()
        }
    }

    /** 两个任务同时在跑：一个先完成，它的卡片原地变成已完成，另一个的运行卡片不受影响。 */
    @Test
    @Config(sdk = [36])
    fun completingOneTaskLeavesTheOtherRunningCardAlone() {
        val context = RuntimeEnvironment.getApplication()
        val runId = "test:complete-new-id"
        val other = "test:still-running"
        assertTrue(AgentExecutionService.acquire(context, "run:$runId") {})
        assertTrue(AgentExecutionService.acquire(context, "run:$other") {})
        val running = TaskNotificationState(runId = runId, conversationId = "c-done", title = "整理文件",
            status = AgentOverlayStatus.GeneratingAnswer, step = 3)
        val second = TaskNotificationState(runId = other, conversationId = "c-other", title = "另一件事",
            status = AgentOverlayStatus.Reasoning)
        AgentExecutionService.updateTasks(context, listOf(running, second))
        shadowOf(Looper.getMainLooper()).idle()
        val controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        try {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            val manager = context.getSystemService(NotificationManager::class.java)
            val liveIds = manager.activeNotifications.filter { it.notification.extras.getBoolean("android.requestPromotedOngoing") }.map { it.id }.toSet()
            assertEquals(2, liveIds.size)
            AgentExecutionService.completeTask(context, running.copy(status = AgentOverlayStatus.ResultReady, streamPreview = "好了"))
            AgentExecutionService.release("run:$runId")
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
            val active = manager.activeNotifications
            val done = active.filter { it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 }
            assertEquals(1, done.size)
            assertTrue(done.single().id in liveIds)
            assertEquals(2, active.size)
            // 剩下唯一的运行卡片是另一件事的。
            assertEquals(1, active.count { it.notification.extras.getBoolean("android.requestPromotedOngoing") })
        } finally {
            AgentExecutionService.updateTasks(context, emptyList())
            AgentExecutionService.release("run:$other")
            dismissCompleted(context, runId)
            shadowOf(Looper.getMainLooper()).idle()
            controller.destroy()
        }
    }

    /** 运行时已经没有这个任务：通知上点停止要把卡片收掉，不能一直卡在「运行中」。 */
    @Test
    @Config(sdk = [36])
    fun stopOnAStaleCardRetiresIt() {
        val context = RuntimeEnvironment.getApplication()
        val runId = "test:stale-stop"
        val lease = "test:stale-lease"
        assertTrue(AgentExecutionService.acquire(context, lease) {})
        AgentExecutionService.updateTasks(context, listOf(TaskNotificationState(runId = runId, conversationId = "c-stale",
            title = "继续", status = AgentOverlayStatus.GeneratingAnswer, step = 6)))
        shadowOf(Looper.getMainLooper()).idle()
        val controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        try {
            val stop = android.content.Intent(context, AgentExecutionService::class.java)
                .setAction(AgentExecutionService.ACTION_STOP_RUN).putExtra("run_id", runId)
            controller.withIntent(stop).startCommand(0, 2)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            assertTrue(AgentExecutionService.taskSnapshots.value.none { it.runId == runId })
            val active = context.getSystemService(NotificationManager::class.java).activeNotifications
            assertTrue(active.none { it.notification.extras.getBoolean("android.requestPromotedOngoing") })
        } finally {
            AgentExecutionService.release(lease)
            shadowOf(Looper.getMainLooper()).idle()
            controller.destroy()
        }
    }

    private fun dismissCompleted(context: android.content.Context, runId: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.forEach { notification ->
            AgentExecutionService.onNotificationDismissed(context, android.content.Intent("io.github.mangi.etax.action.DISMISS_COMPLETED_TASK")
                .putExtra("run_id", runId).putExtra("reply_notification_id", notification.id))
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** 实况（流体云）卡片变成已完成时撤掉重发，不原地更新：ColorOS 不收起被改回普通的实况通知。 */
    @Test
    fun liveCardsAreRepostedWhenTheyComplete() {
        val running = TaskNotificationState(runId = "r", conversationId = "c", title = "t", status = AgentOverlayStatus.GeneratingAnswer)
        val done = running.copy(status = AgentOverlayStatus.ResultReady, completed = true)
        assertTrue(AgentExecutionService.mustRepostFresh(running, done))
        assertTrue(AgentExecutionService.mustRepostFresh(running.copy(status = AgentOverlayStatus.RunningTool("tap")), done))
        assertFalse("paused cards are not promoted", AgentExecutionService.mustRepostFresh(running.copy(paused = true), done))
        assertFalse("queued cards are not promoted", AgentExecutionService.mustRepostFresh(running.copy(status = AgentOverlayStatus.Queued), done))
        assertFalse(AgentExecutionService.mustRepostFresh(done, done.copy(streamPreview = "x")))
        assertFalse(AgentExecutionService.mustRepostFresh(null, done))
        assertFalse(AgentExecutionService.mustRepostFresh(running, running.copy(step = 3)))
    }

    @Test
    fun foregroundExecutionUsesOnlyServiceTypesSupportedByThePlatform() {
        val controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        try {
            val service = controller.get()
            assertNotNull(shadowOf(service).lastForegroundNotification)
            val notification = shadowOf(service).lastForegroundNotification
            assertNotNull(notification.smallIcon)
            val channel = service.getSystemService(android.app.NotificationManager::class.java)
                .getNotificationChannel(notification.channelId)
            assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
            assertEquals(null, channel.sound)
            assertEquals(
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE,
                service.foregroundServiceType,
            )
        } finally {
            controller.destroy()
        }
    }
}
