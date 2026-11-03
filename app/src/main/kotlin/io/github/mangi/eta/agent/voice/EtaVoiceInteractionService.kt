package io.github.mangi.eta.agent.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.voice.VoiceInteractionService
import io.github.mangi.eta.assistant.OplusPowerWake
import io.github.mangi.eta.core.AssistantContextFlags

class EtaVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        activeService = this
        if (Build.VERSION.SDK_INT >= 37) {
            setInvocationEffectEnabled(true)
        }
        val pending = pendingPowerDeadline
        pendingPowerDeadline = 0
        if (pending > SystemClock.uptimeMillis() && OplusPowerWake.armed(this)) showPowerSession(this)
    }

    override fun onShutdown() {
        if (activeService === this) activeService = null
        pendingPowerDeadline = 0
        super.onShutdown()
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        startActivity(
            Intent(this, EtaVoiceAssistActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun showEtaSession() {
        showSession(Bundle(), AssistantContextFlags.forSdk(Build.VERSION.SDK_INT))
    }

    companion object {
        @Volatile
        private var activeService: EtaVoiceInteractionService? = null
        private var pendingPowerDeadline = 0L

        internal fun requestPowerSession(context: Context) {
            if (activeService != null) {
                showPowerSession(context)
                return
            }
            // Allow an already-binding voice service to finish. Never rebind the system assistant,
            // replay a stale request, launch the OEM UI, or repair configuration from this callback.
            val deadline = SystemClock.uptimeMillis() + 1500L
            pendingPowerDeadline = deadline
            val app = context.applicationContext
            Handler(Looper.getMainLooper()).postDelayed({
                if (pendingPowerDeadline == deadline) {
                    pendingPowerDeadline = 0
                    OplusPowerWake.record(app, "ASSISTANT_SERVICE_NOT_READY")
                }
            }, 1500L)
        }

        private fun showPowerSession(context: Context) {
            val ok = runCatching { requestSession() }.getOrDefault(false)
            OplusPowerWake.record(context, if (ok) "SESSION_REQUESTED" else "ASSISTANT_SERVICE_NOT_READY")
        }

        internal fun requestSession(): Boolean {
            val service = activeService ?: return false
            service.showEtaSession()
            return true
        }
    }
}

class EtaVoiceAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EtaVoiceInteractionService.requestSession()
        finish()
    }
}
