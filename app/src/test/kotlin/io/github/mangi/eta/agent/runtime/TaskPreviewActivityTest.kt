package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Button
import android.widget.ImageView
import io.github.mangi.eta.agent.model.AgentModelClient
import kotlinx.coroutines.channels.ReceiveChannel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.json.JSONObject
import java.time.Duration

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
            val image = Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            view.displayFrame(image, JSONObject().put("displayId", 7).put("width", 1080).put("height", 2400).put("rotation", 0), "read-only")
            val hint = view.previewUi.hint
            assertTrue(hint.text.startsWith("AI 正在执行"))
            controller.pause()
            TaskPreviewActivity::class.java.getDeclaredMethod("updateControls").apply { isAccessible = true }.invoke(view)
            assertTrue(hint.text.startsWith("任务已暂停"))
            controller.resume()
            TaskPreviewActivity::class.java.getDeclaredMethod("updateControls").apply { isAccessible = true }.invoke(view)
            assertTrue(hint.text.startsWith("AI 正在执行"))
            view.displayFrame(null, null, "clear")
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

    @Test fun manualControlHasAnIndependentExpandButtonAndBlankFramesCannotReceiveInput() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(app.getSystemService(DisplayManager::class.java).getDisplay(0)).setState(Display.STATE_ON)
        val activity = Robolectric.buildActivity(TaskPreviewActivity::class.java,
            Intent(app, TaskPreviewActivity::class.java).putExtra("run_id", "manual-ui").putExtra("package", "app.calculator")).create()
        val view = activity.get()
        fun field(name: String) = TaskPreviewActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
        try {
            val geometry = JSONObject().put("displayId", 7).put("width", 1080).put("height", 2400).put("rotation", 0)
            val blank = Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            view.displayFrame(blank, geometry, "blank")
            assertNull((field("picture").get(view) as ImageView).drawable)
            assertFalse(view.previewUi.controls.primaryEnabled && view.previewUi.controls.primary == PreviewAction.TakeOver)
            assertTrue(blank.isRecycled)
            val real = Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            view.displayFrame(real, geometry, "real")
            assertTrue(view.previewUi.controls.primaryEnabled && view.previewUi.controls.primary == PreviewAction.TakeOver)
            field("manual").setBoolean(view, true)
            assertTrue(view.previewUi.controls.zoomEnabled)
            TaskPreviewActivity::class.java.getDeclaredMethod("toggleSize").apply { isAccessible = true }.invoke(view)
            assertTrue(field("expanded").getBoolean(view))
            assertTrue(view.previewUi.expanded)
            assertTrue(field("manual").getBoolean(view))
            view.displayFrame(null, null, "clear")
            assertTrue(real.isRecycled)
        } finally { activity.destroy() }
    }

    @Test fun aFreshFrameDoesNotEraseThePreviousInputFailure() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(app.getSystemService(DisplayManager::class.java).getDisplay(0)).setState(Display.STATE_ON)
        val activity = Robolectric.buildActivity(TaskPreviewActivity::class.java,
            Intent(app, TaskPreviewActivity::class.java).putExtra("run_id", "input-feedback").putExtra("package", "app.target")).create()
        val view = activity.get()
        fun field(name: String) = TaskPreviewActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
        try {
            field("manual").setBoolean(view, true)
            val g = JSONObject().put("displayId", 7).put("width", 1080).put("height", 2400).put("rotation", 0)
            fun nextFrame(key: String) = view.displayFrame(
                Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }, g, key)
            nextFrame("7:1000:1:640")
            val oldGeneration = field("frameGeneration").getLong(view)
            val refreshRequests = field("frameRequests").get(view) as ReceiveChannel<*>
            assertTrue(refreshRequests.tryReceive().isFailure)
            view.acceptInputResult(JSONObject().put("ok", false).put("code", "INPUT_NOT_READY"))
            assertTrue(refreshRequests.tryReceive().isSuccess)
            val hint = view.previewUi.hint
            val failure = hint.text.toString()
            assertTrue(failure.contains("目标窗口未就绪"))
            assertNull(field("geometry").get(view))
            val stale = Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            view.displayFrame(stale, g, "pre-input", oldGeneration)
            assertTrue(stale.isRecycled)
            view.onConfigurationChanged(android.content.res.Configuration(view.resources.configuration).apply {
                orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            })
            val sameBuffer = Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            view.displayFrame(sameBuffer, g, "7:1000:1:960")
            assertTrue(sameBuffer.isRecycled)
            assertNull(field("geometry").get(view))
            assertFalse(view.previewUi.controls.secondaryEnabled && PreviewAction.Back in view.previewUi.controls.secondary)
            nextFrame("7:1001:2:640")
            assertEquals(failure, hint.text.toString())
            assertTrue(view.previewUi.controls.secondaryEnabled && PreviewAction.Back in view.previewUi.controls.secondary)
            view.acceptInputResult(JSONObject().put("ok", true))
            assertTrue(refreshRequests.tryReceive().isSuccess)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(801))
            nextFrame("7:1001:2:640") // A no-op input can leave the same buffer after the bounded wait.
            assertTrue(hint.text.startsWith("可操作"))
            view.displayFrame(null, null, "clear")
        } finally { activity.destroy() }
    }
}
