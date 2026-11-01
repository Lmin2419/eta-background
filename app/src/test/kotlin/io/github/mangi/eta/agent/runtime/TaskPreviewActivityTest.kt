package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import android.widget.ImageView
import io.github.mangi.eta.agent.model.AgentModelClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TaskPreviewActivityTest {
    @Test fun openingAndExpandingPreviewDoesNotTakeControlOrRequestImeFocus() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(app.getSystemService(DisplayManager::class.java).getDisplay(0)).setState(Display.STATE_ON)
        val controller = AgentRunController()
        val screen = TaskScreenSession("view", controller,
            object : ScreenToolBackend { override fun execute(name: String, arguments: String): String? = error("Readonly UI must not execute a model tool") },
            { AgentModelClient.ToolResult("{}") })
        TaskPreviewRegistry.register("view", controller, screen)
        val activity = Robolectric.buildActivity(TaskPreviewActivity::class.java,
            Intent(app, TaskPreviewActivity::class.java).putExtra("run_id", "view").putExtra("package", "app.target")).create()
        try {
            val view = activity.get()
            assertTrue(view.window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertTrue(view.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertFalse(view.isFinishing)
            val picture = TaskPreviewActivity::class.java.getDeclaredField("picture").apply { isAccessible = true }.get(view) as ImageView
            assertTrue(picture.performClick())
            view.onConfigurationChanged(android.content.res.Configuration(view.resources.configuration).apply {
                orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            })
            assertFalse(view.isFinishing)
            assertFalse(controller.isPaused)
            assertEquals(0L, controller.resumeGeneration)
        } finally { TaskPreviewRegistry.unregister("view"); activity.destroy() }
    }

    @Test fun lockedPhoneRejectsPreviewBeforeCreatingOrReadingAnImage() {
        val app = RuntimeEnvironment.getApplication()
        val keyguard = app.getSystemService(KeyguardManager::class.java)
        shadowOf(keyguard).setKeyguardLocked(true)
        val activity = Robolectric.buildActivity(TaskPreviewActivity::class.java,
            Intent(app, TaskPreviewActivity::class.java).putExtra("run_id", "private").putExtra("package", "app.target")).create()
        try { assertTrue(activity.get().isFinishing) }
        finally { activity.destroy(); shadowOf(keyguard).setKeyguardLocked(false) }
    }
}
