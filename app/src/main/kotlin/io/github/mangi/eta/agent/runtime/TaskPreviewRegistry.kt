package io.github.mangi.eta.agent.runtime

import android.app.KeyguardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import java.util.concurrent.ConcurrentHashMap

/** Same-process UI handles only. The runtime and the engine remain the lifecycle authorities. */
internal object TaskPreviewRegistry {
    private data class Entry(val controller: AgentRunController, val screen: TaskScreenSession)
    private val entries = ConcurrentHashMap<String, Entry>()
    fun register(runId: String, controller: AgentRunController, screen: TaskScreenSession) {
        entries[runId] = Entry(controller, screen)
    }
    fun unregister(runId: String) { entries.remove(runId) }
    fun controller(runId: String): AgentRunController? = entries[runId]?.controller
    fun isBackground(runId: String): Boolean = entries[runId]?.screen?.currentLocation() == TaskExecutionLocation.BACKGROUND
    fun mainDisplayAvailable(context: Context): Boolean =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(0)?.state == Display.STATE_ON &&
            context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false
}
