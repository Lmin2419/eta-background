package io.github.mangi.eta.agent.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Short-lived binding for admission only. The existing Runtime owns the subsequent task. */
internal class NotificationReplyConnection(
    private val context: Context,
    private val sourceRunId: String,
    private val conversationId: String,
    private val text: String,
    private val complete: (Boolean) -> Unit,
) : ServiceConnection {
    private val handler = Handler(Looper.getMainLooper())
    private var pending = true
    private var bound = false
    private val timeout = Runnable { finish(false) }

    fun start() {
        handler.postDelayed(timeout, 30_000)
        bound = runCatching {
            context.bindService(Intent(context, AgentRuntimeService::class.java).setAction(AgentRuntimeWire.ACTION_BIND),
                this, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) finish(false)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (!pending) return
        AgentRuntimeService.submitNotificationReply(sourceRunId, conversationId, text, { pending }, ::finish)
    }
    override fun onServiceDisconnected(name: ComponentName?) = finish(false)
    override fun onNullBinding(name: ComponentName?) = finish(false)
    override fun onBindingDied(name: ComponentName?) = finish(false)
    fun close() = finish(false)

    private fun finish(accepted: Boolean) {
        if (!pending) return
        pending = false
        handler.removeCallbacks(timeout)
        if (bound) runCatching { context.unbindService(this) }
        bound = false
        complete(accepted)
    }
}
