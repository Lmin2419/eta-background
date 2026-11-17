package io.github.mangi.eta.ui.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.mangi.eta.ui.components.EtaOverlayBackHandler
import io.github.mangi.eta.ui.components.Stage3TestPanel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 首页返回键（WorkbenchBack）挂在首页内容之前：页面里后注册的返回处理（多选、看图、弹层）必须仍然先拿到返回，
 * 即使首页那个处理在它们之后才从「关」变成「开」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WorkbenchBackPriorityTest {
    @Test
    fun innerHandlersStillWinAfterHomeHandlerToggles() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var homeEnabled by mutableStateOf(false)
        var selectionActive by mutableStateOf(true)
        var homeBacks by mutableIntStateOf(0)
        var selectionBacks by mutableIntStateOf(0)
        val panel = Stage3TestPanel(controller.get()) {
            NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = homeEnabled,
                onBackCompleted = { homeBacks++ })
            Box { EtaOverlayBackHandler(selectionActive) { selectionBacks++; selectionActive = false } }
        }
        try {
            panel.advance(5)
            // 首页处理在页面处理注册之后才打开。
            homeEnabled = true
            panel.advance(5)
            controller.get().onBackPressedDispatcher.onBackPressed(); panel.advance(5)
            assertEquals(1, selectionBacks)
            assertEquals(0, homeBacks)
            controller.get().onBackPressedDispatcher.onBackPressed(); panel.advance(5)
            assertEquals(1, selectionBacks)
            assertEquals(1, homeBacks)
            // 关了再开一次，页面处理重新出现时仍然优先。
            homeEnabled = false; panel.advance(3); homeEnabled = true; selectionActive = true; panel.advance(5)
            controller.get().onBackPressedDispatcher.onBackPressed(); panel.advance(5)
            assertEquals(2, selectionBacks)
            assertEquals(1, homeBacks)
        } finally { panel.close(); controller.pause().stop().destroy() }
    }
}
