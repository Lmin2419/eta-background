package io.github.mangi.eta.agent.runtime

import android.content.Context
import android.content.Intent
import android.os.Looper
import org.robolectric.Shadows.shadowOf

/** Retained completions outlive an FGS; cancelAll alone does not deliver Android's deleteIntent. */
internal fun dismissRetainedTaskCardsForTest(context: Context) {
    shadowOf(Looper.getMainLooper()).idle()
    @Suppress("UNCHECKED_CAST")
    val ids = AgentExecutionService::class.java.getDeclaredField("notificationIds").apply {
        isAccessible = true
    }.get(null) as Map<String, Int>
    AgentExecutionService.taskSnapshots.value.filter { it.completed }.forEach { task ->
        ids[task.runId]?.let { id ->
            AgentExecutionService.onNotificationDismissed(context,
                Intent("io.github.mangi.etax.action.DISMISS_COMPLETED_TASK")
                    .putExtra("run_id", task.runId).putExtra("reply_notification_id", id))
        }
    }
    shadowOf(Looper.getMainLooper()).idle()
}
