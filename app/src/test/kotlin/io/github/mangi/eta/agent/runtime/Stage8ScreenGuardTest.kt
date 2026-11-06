package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.KeyguardManager
import android.hardware.display.DisplayManager
import android.view.Display
import io.github.mangi.eta.agent.tool.ToolExecutionDecision
import kotlin.jvm.internal.Ref
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class Stage8ScreenGuardTest {
    @Test fun realExecutorGuardRejectsLockedMainDisplayButAllowsNonGuiWork() {
        val app = RuntimeEnvironment.getApplication()
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(0)
        val keyguard = app.getSystemService(KeyguardManager::class.java)
        val gui = GuiControlCoordinator()
        val control = AgentRunController()
        val run = gui.openRun("screen-guard", control)
        val executor = AgentRuntimeRunExecutor(app, { AgentRuntimePolicy.Permissions(false, false, thinking = true) },
            { it }, gui, { _, _ -> }, { _, _, _ -> })
        // Invoke the real beforeToolExecution callback without starting a network model run.
        // Select by signature, not the compiler's unstable lambda number. This leaves the
        // production guard byte-identical and never dispatches an actual input or root RPC.
        val callback = AgentRuntimeRunExecutor::class.java.declaredMethods.single {
            it.returnType == ToolExecutionDecision::class.java && it.parameterTypes.contentEquals(arrayOf(
                AgentRuntimeRunExecutor::class.java, Ref.ObjectRef::class.java, String::class.java))
        }.apply { isAccessible = true }
        fun decide(tool: String) = callback.invoke(null, executor, Ref.ObjectRef<EntrySurfaceGuard>(), tool) as ToolExecutionDecision
        try {
            for ((state, locked) in listOf(Display.STATE_OFF to false, Display.STATE_OFF to true, Display.STATE_ON to true)) {
                shadowOf(display).setState(state); shadowOf(keyguard).setKeyguardLocked(locked)
                for (tool in listOf("tap", "type_text", "swipe", "launch_app", "open_uri")) {
                    assertEquals("$state/$locked/$tool", "SCREEN_LOCKED", (decide(tool) as ToolExecutionDecision.Reject).code)
                    assertFalse(control.isPaused); assertFalse(control.isCancelled)
                }
                for (tool in listOf("web_search", "fetch_url")) {
                    assertEquals(tool, ToolExecutionDecision.Allow, decide(tool))
                }
                assertNull(gui.state.value.ownerRunId)
            }
        } finally {
            run.close(); shadowOf(display).setState(Display.STATE_ON); shadowOf(keyguard).setKeyguardLocked(false)
        }
    }
}
