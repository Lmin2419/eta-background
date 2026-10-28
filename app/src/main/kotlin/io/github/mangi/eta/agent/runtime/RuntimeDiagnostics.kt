package io.github.mangi.eta.agent.runtime

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.eta.shadow.ShadowTools
import io.github.mangi.eta.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, explicitly allowlisted diagnostics. No prompts, package names, IDs, keys or screenshots. */
internal object RuntimeDiagnostics {
    private data class Entry(val elapsed: Long, val event: String, val state: String)
    private val events = ArrayDeque<Entry>()
    private val states = setOf("ready", "acknowledged", "retrying", "WaitingForApp", "ReleasingScreen",
        "OpeningApp", "RestoringDisplay", "checkpoint_failed")

    @Synchronized fun record(event: String, state: String) {
        if (event !in setOf("screen_phase", "screen_cleanup", "entry_persistence") || state !in states) return
        events.addLast(Entry(SystemClock.elapsedRealtime(), event, state))
        while (events.size > 120) events.removeFirst()
    }

    fun snapshot(context: Context, engineSnapshot: () -> JSONObject = ShadowTools::diagnosticSnapshot): String {
        val entries = synchronized(this) { events.toList() }
        val manager = context.getSystemService(NotificationManager::class.java)
        return JSONObject()
            .put("version", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("physicalDisplayOn", context.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)?.state == android.view.Display.STATE_ON)
            .put("notificationsEnabled", AgentExecutionService.taskNotificationsEnabled(context))
            .put("liveUpdatesAllowed", if (AgentExecutionService.usesSystemTaskSurface())
                runCatching { manager.canPostPromotedNotifications() }.getOrDefault(false) else false)
            .put("engine", engineSnapshot())
            .put("events", JSONArray().apply {
                entries.forEach { put(JSONObject().put("elapsedMs", it.elapsed).put("event", it.event).put("state", it.state)) }
            }).toString(2)
    }
}
