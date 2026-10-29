package io.github.mangi.eta.agent.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Cancels a pending completion alert without starting a service or touching the target app. */
internal class TaskNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AgentExecutionService.onNotificationDismissed(context, intent)
    }
}
