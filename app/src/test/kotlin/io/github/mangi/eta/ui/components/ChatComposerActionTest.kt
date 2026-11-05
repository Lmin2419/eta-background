package io.github.mangi.eta.ui.components

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.*
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
class ChatComposerActionTest {
    @Test fun fourStatesAndCompactionGuard() {
        assertEquals(ComposerAction.Microphone, resolveComposerAction(false, false, false))
        assertEquals(ComposerAction.Send, resolveComposerAction(false, false, true))
        assertEquals(ComposerAction.Stop, resolveComposerAction(true, false, false))
        assertEquals(ComposerAction.Queue, resolveComposerAction(true, false, true))
        for (content in listOf(false, true)) {
            assertEquals(ComposerAction.Disabled, resolveComposerAction(false, true, content))
            assertEquals(ComposerAction.Stop, resolveComposerAction(true, true, content))
        }
    }

    @Test fun tapQueuesHoldArmsAndReleaseCommitsOnlyOnce() = withButton { panel, events, _ ->
        val point = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }.boundsInRoot.center
        var time = SystemClock.uptimeMillis()
        panel.touch(MotionEvent.ACTION_DOWN, point.x, point.y, time); panel.advance(2)
        panel.touch(MotionEvent.ACTION_UP, point.x, point.y, time); panel.advance(2)
        assertEquals(listOf("click"), events)
        events.clear(); time = SystemClock.uptimeMillis()
        panel.touch(MotionEvent.ACTION_DOWN, point.x, point.y, time); panel.advance(45)
        assertEquals(listOf("armed"), events)
        panel.touch(MotionEvent.ACTION_UP, point.x, point.y, time); panel.advance(2)
        assertEquals(listOf("armed", "released:true"), events)
    }

    @Test fun moveOutsideAndSystemCancellationNeverCommit() = withButton { panel, events, _ ->
        val point = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }.boundsInRoot.center
        for (action in listOf(MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL)) {
            events.clear(); val time = SystemClock.uptimeMillis()
            panel.touch(MotionEvent.ACTION_DOWN, point.x, point.y, time); panel.advance(45)
            panel.touch(action, point.x + 120f, point.y + 120f, time); panel.advance(2)
            panel.touch(MotionEvent.ACTION_UP, point.x, point.y, time); panel.advance(2)
            assertEquals(listOf("armed", "released:false"), events)
        }
    }

    @Test fun changingActionCancelsArmedGestureAndDisabledButtonHasNoAction() = withButton { panel, events, action ->
        val point = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }.boundsInRoot.center
        val time = SystemClock.uptimeMillis()
        panel.touch(MotionEvent.ACTION_DOWN, point.x, point.y, time); panel.advance(45)
        action.value = ComposerAction.Disabled; panel.advance(2)
        panel.touch(MotionEvent.ACTION_UP, point.x, point.y, time); panel.advance(2)
        assertEquals(listOf("armed", "released:false"), events)
        val button = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }
        assertFalse(button.config[SemanticsActions.OnClick].action!!.invoke())
        assertNull(button.config.getOrNull(SemanticsActions.OnLongClick))
    }

    @Test fun accessibleLongClickIsAnExplicitSingleAction() = withButton { panel, events, _ ->
        val button = panel.nodes().first { it.config.getOrNull(SemanticsActions.OnLongClick) != null }
        assertTrue(button.config[SemanticsActions.OnLongClick].action!!.invoke())
        assertEquals(listOf("accessible"), events)
    }

    private fun withButton(block: (Stage3TestPanel, MutableList<String>, androidx.compose.runtime.MutableState<ComposerAction>) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val events = mutableListOf<String>(); val action = mutableStateOf(ComposerAction.Queue)
        val panel = Stage3TestPanel(controller.get()) {
            ComposerActionButton(action.value, "Queue", "Interrupt and send", { events += "click" },
                { events += "armed" }, { events += "released:$it" }, { events += "accessible" })
        }
        try { panel.advance(10); block(panel, events, action) }
        finally { panel.close(); controller.pause().stop().destroy() }
    }
}
