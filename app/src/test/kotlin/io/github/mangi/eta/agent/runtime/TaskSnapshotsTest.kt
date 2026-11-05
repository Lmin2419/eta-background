package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.content.Intent
import android.os.Looper
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TaskSnapshotsTest {
    @Test fun snapshotsPreservePreviousValuesAndTrackPauseCompletionAndDismissal() {
        val context = RuntimeEnvironment.getApplication()
        val first = TaskNotificationState("snapshot-a", "a", "A", AgentOverlayStatus.Reasoning)
        val second = first.copy(runId = "snapshot-b", conversationId = "b", title = "B")
        AgentExecutionService.updateTasks(context, listOf(first, second))
        shadowOf(Looper.getMainLooper()).idle()
        val initial = AgentExecutionService.taskSnapshots.value.filter { it.runId.startsWith("snapshot-") }
        assertEquals(listOf(first.runId, second.runId), initial.map { it.runId })
        assertNotNull(initial.first().firstPublishedAtElapsedMs)
        AgentExecutionService.updateTasks(context, listOf(first.copy(paused = true, status = AgentOverlayStatus.Paused), second))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(initial[0].paused)
        assertTrue(AgentExecutionService.taskSnapshots.value.first { it.runId == first.runId }.paused)
        AgentExecutionService.completeTask(context, first.copy(status = AgentOverlayStatus.ResultReady))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(AgentExecutionService.taskSnapshots.value.first { it.runId == first.runId }.completed)
        assertNotNull(AgentExecutionService.taskSnapshots.value.first { it.runId == first.runId }.completedAtMillis)
        AgentExecutionService.updateTasks(context, emptyList())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(first.runId), AgentExecutionService.taskSnapshots.value.filter { it.runId.startsWith("snapshot-") }.map { it.runId })
        val dismiss = AgentExecutionService::class.java.getDeclaredField("ACTION_DISMISS_COMPLETED").apply { isAccessible = true }.get(null) as String
        val ids = AgentExecutionService::class.java.getDeclaredField("notificationIds").apply { isAccessible = true }.get(null) as Map<*, *>
        AgentExecutionService.onNotificationDismissed(context, Intent(dismiss).putExtra("run_id", first.runId).putExtra("reply_notification_id", ids[first.runId] as Int))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(AgentExecutionService.taskSnapshots.value.none { it.runId.startsWith("snapshot-") })
    }

    @Test fun workerUpdatesArePublishedOnlyAfterTheMainThreadProcessesThem() {
        val context = RuntimeEnvironment.getApplication()
        val before = AgentExecutionService.taskSnapshots.value
        val worker = Thread { AgentExecutionService.updateTasks(context, listOf(TaskNotificationState("worker", null, "Task", AgentOverlayStatus.Queued))) }
        worker.start(); worker.join()
        assertEquals(before, AgentExecutionService.taskSnapshots.value)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("worker", AgentExecutionService.taskSnapshots.value.filterNot { it.completed }.single().runId)
        AgentExecutionService.updateTasks(context, emptyList())
    }
}
