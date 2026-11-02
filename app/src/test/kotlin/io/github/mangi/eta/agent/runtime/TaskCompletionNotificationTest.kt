package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.hardware.display.DisplayManager
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 37], application = Application::class)
class TaskCompletionNotificationTest {
    private lateinit var controller: ServiceController<AgentExecutionService>
    private lateinit var service: AgentExecutionService
    private lateinit var manager: NotificationManager

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        SettingsDataStore.init(app)
        setEnabled(true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(app.getSystemService(DisplayManager::class.java).getDisplay(0)).setState(Display.STATE_ON)
        controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        service = controller.get()
        manager = service.getSystemService(NotificationManager::class.java)
        AgentExecutionService.acquire(service, "completion-test", onStop = {})
    }

    @After fun tearDown() {
        pendingJobs().values.toList().forEach(Job::cancel)
        AgentExecutionService.release("completion-test")
        AgentExecutionService.updateTasks(service, emptyList())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        controller.destroy()
        manager.cancelAll()
    }

    @Test fun completionReusesCardAndProvidesThreeIndependentActions() {
        val a = task("one")
        val b = task("two")
        start(a, b)
        val original = card("one")
        finish(a)
        // The final card is immediate; reading the setting must not delay status or actions.
        assertEquals(original.id, card("one").id)
        assertEquals(3, card("one").notification.actions.size)
        settleAlerts()
        val completed = card("one").notification
        assertEquals(2, manager.activeNotifications.size)
        assertEquals(original.id, card("one").id)
        assertEquals(original.notification.smallIcon.resId, completed.smallIcon.resId)
        assertEquals(AgentExecutionService.COMPLETION_CHANNEL, completed.channelId)
        assertFalse(completed.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(service.getString(R.string.task_completion_notice_success), completed.extras.getString(Notification.EXTRA_SUB_TEXT))
        val channel = manager.getNotificationChannel(completed.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertEquals(Notification.VISIBILITY_PRIVATE, completed.visibility)
        assertNull(completed.fullScreenIntent)
        assertNull(shadowOf(service.application).nextStartedActivity)
        val actions = completed.actions
        assertEquals(listOf(R.string.overlay_open_conversation, R.string.background_open_app, R.string.background_continue_instruction)
            .map(service::getString), actions.map { it.title.toString() })
        assertEquals("chat-one", shadowOf(actions[0].actionIntent).savedIntent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
        assertEquals("one", shadowOf(actions[1].actionIntent).savedIntent.getStringExtra(CompletedAppActivity.EXTRA_RUN_ID))
        assertEquals("app.one", shadowOf(actions[1].actionIntent).savedIntent.getStringExtra(CompletedAppActivity.EXTRA_PACKAGE))
        assertNotEquals(completed.contentIntent, actions[1].actionIntent)
        assertEquals(TaskPreviewActivity::class.java.name, shadowOf(completed.contentIntent).savedIntent.component?.className)
        assertEquals(AgentExecutionService.ACTION_REPLY, shadowOf(actions[2].actionIntent).savedIntent.action)
        assertEquals(AgentExecutionService.REPLY_TEXT, actions[2].remoteInputs.single().resultKey)
        assertTrue(card("two").notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertNotEquals(AgentExecutionService.COMPLETION_CHANNEL, card("two").notification.channelId)

        // Both tasks can finish independently; a subsequent result update must not alert again.
        finish(b, AgentOverlayStatus.Stopped)
        settleAlerts()
        assertEquals(service.getString(R.string.task_completion_notice_stopped), card("two").notification.extras.getString(Notification.EXTRA_SUB_TEXT))
        finish(a.copy(streamPreview = "final archive text"))
        settleAlerts()
        assertTrue(card("one").notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        AgentExecutionService.release("completion-test")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf(original.id, card("two").id), manager.activeNotifications.map { it.id }.toSet())
        assertTrue(card("one").notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test fun legacyCompletionCannotRaceTheRuntimeCommit() {
        val active = task("authority").copy(runtimeAuthoritative = true)
        start(active)
        AgentExecutionService.publishRunCompletion(service, active.runId, active.conversationId!!, "已完成", "model finished")
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(AgentExecutionService.task(active.runId)!!.completed)
        assertEquals(active.status, AgentExecutionService.task(active.runId)!!.status)
        finish(active)
        assertTrue(AgentExecutionService.task(active.runId)!!.completed)
        AgentExecutionService.publishRunCompletion(service, active.runId, active.conversationId, "失败", "late")
        assertEquals(AgentOverlayStatus.ResultReady, AgentExecutionService.task(active.runId)!!.status)
    }

    @Test fun disablingAlertsKeepsCardsAndNewTasksReadTheCurrentSetting() {
        val a = task("off")
        start(a)
        val id = card("off").id
        finish(a)
        setEnabled(false) // A change during coalescing applies before any banner request.
        settleAlerts()
        assertEquals(id, card("off").id)
        assertNotEquals(AgentExecutionService.COMPLETION_CHANNEL, card("off").notification.channelId)
        assertTrue(card("off").notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(3, card("off").notification.actions.size)

        setEnabled(true)
        val b = task("on")
        start(b)
        finish(b)
        settleAlerts()
        assertEquals(AgentExecutionService.COMPLETION_CHANNEL, card("on").notification.channelId)
        assertNotEquals(AgentExecutionService.COMPLETION_CHANNEL, card("off").notification.channelId)
    }

    @Test fun blockedCompletionChannelDoesNotHideOriginalResult() {
        val a = task("blocked")
        start(a)
        val originalChannel = card("blocked").notification.channelId
        manager.createNotificationChannel(manager.getNotificationChannel(AgentExecutionService.COMPLETION_CHANNEL).apply {
            importance = NotificationManager.IMPORTANCE_NONE
        })
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(AgentExecutionService.COMPLETION_CHANNEL).importance)
        finish(a)
        settleAlerts()
        assertEquals(originalChannel, card("blocked").notification.channelId)
        assertTrue(card("blocked").notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test fun physicalScreenOffAndLockDoNotQueueAnAlertForUnlock() {
        val a = task("screen-off")
        start(a)
        val display = service.getSystemService(DisplayManager::class.java).getDisplay(0)
        shadowOf(display).setState(Display.STATE_OFF) // A virtual display can keep global power interactive.
        finish(a)
        settleAlerts()
        shadowOf(display).setState(Display.STATE_ON)
        val b = task("locked")
        start(b)
        shadowOf(service.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        finish(b)
        settleAlerts()
        shadowOf(service.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(2, manager.activeNotifications.size)
        assertTrue(manager.activeNotifications.all { it.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 })
        assertTrue(manager.activeNotifications.none { it.notification.channelId == AgentExecutionService.COMPLETION_CHANNEL })
    }

    @Test fun dismissalDuringCompletionAndLateArchiveDoNotResurrectCard() {
        val a = task("dismiss")
        start(a)
        val id = card("dismiss").id
        finish(a)
        val delete = card("dismiss").notification.deleteIntent
        assertEquals(TaskNotificationDismissReceiver::class.java.name, shadowOf(delete).savedIntent.component?.className)
        manager.cancel(id)
        delete.send()
        shadowOf(Looper.getMainLooper()).idle()
        settleAlerts()
        assertTrue(manager.activeNotifications.isEmpty())
        AgentExecutionService.publishRunCompletion(service, a.runId, a.conversationId!!, "已完成", "late archive")
        finish(a.copy(streamPreview = "late final result with different content"))
        settleAlerts()
        assertTrue(manager.activeNotifications.isEmpty())
        AgentExecutionService.updateTasks(service, listOf(a.copy(status = AgentOverlayStatus.WaitingForModel)))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertNull("A late running snapshot cannot resurrect a dismissed completed task", AgentExecutionService.task(a.runId))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun failureHasItsOwnLabelAndNoAppTaskOmitsInvalidAppAction() {
        val a = task("failed").copy(targetPackage = null)
        start(a)
        finish(a, AgentOverlayStatus.RunFailed)
        settleAlerts()
        val completed = manager.activeNotifications.single().notification
        assertEquals(service.getString(R.string.task_completion_notice_failed), completed.extras.getString(Notification.EXTRA_SUB_TEXT))
        assertEquals(2, completed.actions.size)
        assertEquals("chat-failed", shadowOf(completed.actions[0].actionIntent).savedIntent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID))
        assertEquals(Notification.Action.SEMANTIC_ACTION_REPLY, completed.actions[1].semanticAction)
    }

    private fun task(id: String) = TaskNotificationState(id, "chat-$id", "Task $id", AgentOverlayStatus.Reasoning,
        "preview $id", targetPackage = "app.$id")

    private fun start(vararg tasks: TaskNotificationState) {
        AgentExecutionService.updateTasks(service, tasks.toList())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    }

    private fun finish(task: TaskNotificationState, status: AgentOverlayStatus = AgentOverlayStatus.ResultReady) =
        AgentExecutionService.completeTask(service, task.copy(completed = true, status = status))

    private fun card(runId: String) = manager.activeNotifications.single {
        val intent = shadowOf(it.notification.contentIntent).savedIntent
        intent.getStringExtra(CompletedAppActivity.EXTRA_RUN_ID) == runId ||
            intent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID) == "chat-$runId"
    }

    private fun setEnabled(enabled: Boolean) = runBlocking {
        AppearanceSettingsRepository.update { it.copy(taskCompletionAlertsEnabled = enabled) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun pendingJobs(): Map<String, Job> = AgentExecutionService::class.java.getDeclaredField("completionJobs")
        .apply { isAccessible = true }.get(null) as Map<String, Job>

    private fun settleAlerts() {
        val main = shadowOf(Looper.getMainLooper())
        main.idleFor(Duration.ofMillis(150))
        val deadline = System.nanoTime() + 5_000_000_000L
        while (pendingJobs().isNotEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(5)
            main.idle()
        }
        assertTrue("Completion preference read did not finish", pendingJobs().isEmpty())
    }
}
