package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.TaskNotificationState
import io.github.mangi.eta.ui.screens.tasks.TaskFrameSource
import io.github.mangi.eta.ui.screens.tasks.TaskThumbnail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskThumbnailLifecycleTest {
    @Test fun offscreenHiddenAndStoppedSurfacesNeverContinuePolling() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val visible = mutableStateOf(true); val offscreen = mutableStateOf(true)
        var calls = 0
        val source = TaskFrameSource { _, _, _, _ -> calls++; JSONObject().put("ok", false) }
        val task = TaskNotificationState("preview-test", "chat", "Task", AgentOverlayStatus.Reasoning)
        val panel = Stage3TestPanel(controller.get()) {
            TaskThumbnail(task, visible.value, Modifier.offset(y = if (offscreen.value) 2000.dp else 0.dp).size(72.dp, 156.dp), background = true, frameSource = source)
        }
        try {
            panel.advance(160); assertEquals(0, calls)
            offscreen.value = false; panel.advance(10); assertEquals(1, calls)
            panel.advance(140); assertEquals(2, calls)
            visible.value = false; panel.advance(160); assertEquals(2, calls)
            visible.value = true; panel.advance(10); assertEquals(3, calls)
            controller.pause().stop(); panel.advance(160); assertEquals(3, calls)
        } finally { panel.close(); controller.destroy() }
    }

    @Test fun leavingDuringAnInFlightReadDoesNotStartAnotherRequest() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val visible = mutableStateOf(true); val blocked = CompletableDeferred<JSONObject>()
        var calls = 0
        val source = TaskFrameSource { _, _, _, _ -> calls++; withContext(NonCancellable) { blocked.await() } }
        val panel = Stage3TestPanel(controller.get()) {
            TaskThumbnail(TaskNotificationState("blocked", "chat", "Task", AgentOverlayStatus.Reasoning), visible.value,
                Modifier.size(72.dp, 156.dp), background = true, frameSource = source)
        }
        try {
            panel.advance(10); assertEquals(1, calls)
            visible.value = false; panel.advance(10)
            blocked.complete(JSONObject().put("ok", true).put("unchanged", true))
            panel.advance(200); assertEquals(1, calls)
        } finally { blocked.complete(JSONObject()); panel.close(); controller.pause().stop().destroy() }
    }
}
