package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.overlay.*
import io.github.mangi.eta.agent.voice.*
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Stage6SurfacesTest {
    @Test fun composeAndroidViewPassesRealFingerDragAndCancelToOriginalGeometry() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(app.getSystemService(DisplayManager::class.java).getDisplay(0)).setState(Display.STATE_ON)
        val owner = Robolectric.buildActivity(TaskPreviewActivity::class.java,
            Intent(app, TaskPreviewActivity::class.java).putExtra("run_id", "compose-finger").putExtra("package", "test.app")).create()
        val activity = owner.get()
        fun field(name: String) = TaskPreviewActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
        val picture = field("picture").get(activity) as TaskPreviewImageView
        val image = Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        activity.displayFrame(image, JSONObject().put("displayId", 7).put("width", 1080).put("height", 2400).put("rotation", 0), "finger")
        field("manual").setBoolean(activity, true)
        TaskPreviewActivity::class.java.getDeclaredMethod("updateControls").apply { isAccessible = true }.invoke(activity)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(host.get(), dark = true) {
            Box(Modifier.width(255.dp).height(450.dp)) {
                EtaPreviewWindow(activity.previewUi, picture, activity::performPreviewAction, {}, {})
            }
        }
        try {
            panel.advance(25)
            val bounds = panel.nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_frame" }.boundsInRoot
            val downTime = SystemClock.uptimeMillis()
            panel.touch(MotionEvent.ACTION_DOWN, bounds.center.x, bounds.center.y, downTime)
            panel.advance(2)
            val point = field("down").get(activity) as Pair<*, *>
            assertEquals(540f, point.first as Float, 2f)
            assertEquals(1200f, point.second as Float, 2f)
            repeat(10) { index ->
                panel.touch(MotionEvent.ACTION_MOVE, bounds.center.x + index * 2, bounds.center.y + index * 3, downTime)
                panel.advance()
            }
            assertNotNull(field("gestureGeometry").get(activity))
            panel.touch(MotionEvent.ACTION_CANCEL, bounds.center.x + 20, bounds.center.y + 30, downTime)
            panel.advance(2)
            assertNull(field("down").get(activity)); assertNull(field("gestureGeometry").get(activity))
            assertFalse(field("busy").getBoolean(activity)) // Cancellation never dispatches input.
            panel.capture("stage6_preview_manual")
        } finally {
            panel.close(); host.pause().stop().destroy(); owner.destroy()
        }
    }

    @Test fun voiceLightDarkAndLargeTextKeepAllControlsReachable() {
        listOf(false to 1f, true to 1f, false to 2f).forEach { (dark, scale) ->
            val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val state = EtaVoiceUiState(messages = listOf(UserMessageUi("u", "Summarize this screen"),
                AgentMessageUi("a", "Here are the key points.\n\nYou can open the full conversation to continue.")))
            val panel = Stage3TestPanel(host.get(), dark, scale) {
                EtaVoicePanel(speechLevel = { .4f }, state = state, input = "", speech = EtaSpeechState(),
                    recognitionEnabled = true, entranceComplete = true, onEntranceFinished = {}, onContentReady = {},
                    onInputFocus = {}, onMicrophone = {}, onCancelSpeech = {}, onFinishSpeech = {}, onDownloadModel = {},
                    onOpenSpeechSettings = {}, onKeyboard = {}, inputFocusRequestKey = -1, canOpenConversation = true,
                    handoffRunning = false, exitRequested = false, onInputChange = {}, onSuggestionClick = {},
                    onSubmit = {}, onStop = {}, onClose = {}, onOpenConversation = {})
            }
            try {
                panel.advance(70)
                val descriptions = panel.nodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                assertTrue(descriptions.contains(host.get().getString(io.github.mangi.eta.R.string.voice_tap_to_speak)))
                assertTrue(descriptions.contains(host.get().getString(io.github.mangi.eta.R.string.overlay_open_conversation)))
                panel.nodes().filter { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(
                    host.get().getString(io.github.mangi.eta.R.string.voice_tap_to_speak)) == true }.forEach {
                    assertTrue(it.boundsInRoot.top >= 0); assertTrue(it.boundsInRoot.bottom <= (panel.root() as android.view.View).height)
                }
                panel.capture("stage6_voice_${if (dark) "dark" else if (scale == 2f) "font200" else "light"}")
            } finally { panel.close(); host.pause().stop().destroy() }
        }
    }

    @Test fun capsuleViewPauseStopAndSupplementRemainSeparate() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var viewed = 0; var paused = 0; var stopped = 0; var supplement = false
        val panel = Stage3TestPanel(host.get()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.width(360.dp)) {
                    AgentOverlayCapsule(AgentOverlayState(thought = "Reviewing the current page"), true, false, {}, {},
                        { paused++ }, {}, { stopped++ }, { supplement = it }, {},
                        onOpenPreview = { viewed++ })
                }
            }
        }
        try {
            panel.advance(50)
            fun tap(resource: Int) {
                val label = host.get().getString(resource)
                val node = panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
                val center = node.boundsInRoot.center; val down = SystemClock.uptimeMillis()
                panel.touch(MotionEvent.ACTION_DOWN, center.x, center.y, down); panel.advance()
                panel.touch(MotionEvent.ACTION_UP, center.x, center.y, down); panel.advance(2)
            }
            tap(io.github.mangi.eta.R.string.task_notice_view)
            tap(io.github.mangi.eta.R.string.overlay_pause)
            tap(io.github.mangi.eta.R.string.action_stop)
            assertEquals(1, viewed); assertEquals(1, paused); assertEquals(1, stopped); assertFalse(supplement)
            panel.capture("stage6_capsule_expanded")
        } finally { panel.close(); host.pause().stop().destroy() }
    }
}
