package io.github.mangi.eta.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.coui.EtaPageRecordingDemand
import io.github.mangi.eta.ui.components.coui.demandPageRecording
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** 整页记录按需开：用到整页模糊的弹层挂着时登记，移除后撤销（没有弹层时不再每帧多录一遍整页）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [36], application = android.app.Application::class)
class PageRecordingDemandTest {
    @Test fun demandFollowsTheOverlaysThatAreOnScreen() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val demand = EtaPageRecordingDemand()
        val dialog = mutableStateOf(false)
        val menu = mutableStateOf(false)
        val panel = Stage3TestPanel(controller.get()) {
            Box {
                if (dialog.value) Box(Modifier.size(10.dp).demandPageRecording(demand))
                if (menu.value) Box(Modifier.size(10.dp).demandPageRecording(demand))
            }
        }
        try {
            panel.advance(2)
            assertEquals(0, demand.users)
            dialog.value = true
            panel.advance(2)
            assertEquals(1, demand.users)
            menu.value = true
            panel.advance(2)
            assertEquals(2, demand.users)
            dialog.value = false
            menu.value = false
            panel.advance(2)
            assertEquals(0, demand.users)
        } finally {
            panel.close()
            controller.pause().stop().destroy()
        }
    }
}
