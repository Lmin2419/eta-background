package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.view.Display
import io.github.mangi.eta.agent.model.AgentModelClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class Stage8HintTest {
    @Test fun everyHintTypeRefreshesOrPersistsByIdentityInEveryLanguage() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(app.getSystemService(DisplayManager::class.java).getDisplay(0)).setState(Display.STATE_ON)
        val controller = AgentRunController()
        val screen = TaskScreenSession("hint-types", controller,
            object : ScreenToolBackend { override fun execute(name: String, arguments: String): String? = error("Readonly test must not execute a tool") },
            { AgentModelClient.ToolResult("{}") })
        TaskPreviewRegistry.register("hint-types", controller, screen)
        val host = Robolectric.buildActivity(TaskPreviewActivity::class.java,
            Intent(app, TaskPreviewActivity::class.java).putExtra("run_id", "hint-types").putExtra("package", "app.test")).create()
        val view = host.get()
        val update = TaskPreviewActivity::class.java.getDeclaredMethod("updateControls").apply { isAccessible = true }
        val failure = TaskPreviewActivity::class.java.getDeclaredMethod("previewFailure", JSONObject::class.java).apply { isAccessible = true }
        try {
            view.displayFrame(Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) },
                JSONObject().put("displayId", 7).put("width", 1080).put("height", 2400).put("rotation", 0), "frame")
            val refreshable = setOf(PreviewHintType.ReleasedActive, PreviewHintType.ReadOnlyCompleted,
                PreviewHintType.ReadOnlyPaused, PreviewHintType.ReadOnlyRunning, PreviewHintType.ReadOnlyPlain)
            for (language in listOf("zh-CN", "zh-TW", "en")) {
                val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
                for (type in PreviewHintType.entries) {
                    val translated = resources.getString(type.resourceId)
                    assertTrue("$language $type", translated.isNotBlank())
                    if (language.startsWith("zh")) assertTrue("$type: $translated", translated.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } <= 18)
                    view.previewUi.hint.type = type
                    view.previewUi.hint.text = translated
                    update.invoke(view)
                    if (type in refreshable) assertEquals(PreviewHintType.ReadOnlyRunning, view.previewUi.hint.type)
                    else { assertEquals(type, view.previewUi.hint.type); assertEquals(translated, view.previewUi.hint.text) }
                }
            }
            val errors = mapOf(
                "PREVIEW_CONNECTION_LOST" to PreviewHintType.ConnectionLost, "TASK_NOT_SETTLED" to PreviewHintType.TaskDraining,
                "INPUT_DRAIN_PENDING" to PreviewHintType.CommandBusy, "COMMAND_BUSY" to PreviewHintType.CommandBusy,
                "COMMAND_DRAINING" to PreviewHintType.CommandBusy, "PREVIEW_OBSERVATION_REQUIRED" to PreviewHintType.Refreshing,
                "STALE_OBSERVATION" to PreviewHintType.Refreshing, "MANUAL_CONTROL" to PreviewHintType.OtherOwner,
                "PREVIEW_UNAVAILABLE" to PreviewHintType.PageReleased, "APP_TAKEN_OVER" to PreviewHintType.PageReleased,
                "INPUT_NOT_READY" to PreviewHintType.InputNotReady, "MANUAL_LEASE_EXPIRED" to PreviewHintType.LeaseExpired,
                "UNKNOWN" to PreviewHintType.Failure)
            errors.forEach { (code, type) -> assertEquals(code, type, (failure.invoke(view, JSONObject().put("code", code)) as PreviewHintMessage).type) }
            val diagnostic = failure.invoke(view, JSONObject().put("code", "UNKNOWN").put("message", "backend diagnostic")) as PreviewHintMessage
            assertEquals("backend diagnostic", diagnostic.text)
            controller.pause()
            for (code in listOf("PREVIEW_UNAVAILABLE", "APP_TAKEN_OVER")) {
                assertEquals(PreviewHintType.MovedManual, (failure.invoke(view, JSONObject().put("code", code)) as PreviewHintMessage).type)
            }
            view.previewUi.hint.type = PreviewHintType.ReadOnlyRunning
            update.invoke(view); assertEquals(PreviewHintType.ReadOnlyPaused, view.previewUi.hint.type)
        } finally { view.displayFrame(null, null, "clear"); TaskPreviewRegistry.unregister("hint-types"); host.destroy() }
    }
}
