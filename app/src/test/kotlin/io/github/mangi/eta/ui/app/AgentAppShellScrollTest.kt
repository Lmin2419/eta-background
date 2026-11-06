package io.github.mangi.eta.ui.app

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.navigation.AppRoute
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Text

/**
 * The home page draws its own top bar. A collapsing-bar scroll connection that no top bar
 * ever measures has an unbounded collapse range and swallowed every upward finger drag,
 * so the conversation could be scrolled back in history but never forward again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentAppShellScrollTest {
    @Test fun homeConversationScrollsForwardAndBackWithAFinger() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        lateinit var list: LazyListState
        val panel = Stage3TestPanel(controller.get()) {
            AgentAppShell(
                currentRoute = AppRoute.Home, isCurrentRoute = true, conversationPaneState = null,
                isConversationPaneOpen = false, onBack = {}, onOpenConversationPane = {}, onDismissConversationPane = {},
                onSearchConversations = {}, onNewConversation = {}, onOpenCommonTasks = {}, onOpenTerminal = {},
                onLaunchKimiWeb = {}, kimiWebLabel = "", canStopKimiWeb = false, onStopKimiWeb = {}, onRefreshKimiWeb = {},
                onOpenBrowser = {}, onSelectConversation = {}, onConversationRename = {}, onConversationExport = {},
                onConversationDelete = {}, onOpenTools = {}, onOpenSkills = {}, onOpenCharacters = {},
                onOpenPermissions = {}, onOpenSettings = {}, onOpenModelProviders = {},
                homeTopBar = { Box(Modifier.fillMaxWidth().height(56.dp)) },
            ) { padding ->
                list = rememberLazyListState()
                LazyColumn(Modifier.fillMaxSize().padding(padding), state = list) {
                    items(100) { index -> Text("message $index", Modifier.fillMaxWidth().height(80.dp)) }
                }
            }
        }
        try {
            panel.advance(10)
            // Finger moves up: newer messages come into view.
            drag(panel, fromY = 1400f, toY = 400f)
            val afterForward = list.firstVisibleItemIndex
            assertTrue("an upward drag must scroll the conversation forward (index $afterForward)", afterForward > 0)
            // Finger moves down: earlier messages come back, and forward scrolling still works afterwards.
            drag(panel, fromY = 400f, toY = 1400f)
            val afterBack = list.firstVisibleItemIndex
            assertTrue("a downward drag must scroll back ($afterBack < $afterForward)", afterBack < afterForward)
            drag(panel, fromY = 1400f, toY = 400f)
            assertTrue("forward scrolling must keep working after going back", list.firstVisibleItemIndex > afterBack)
        } finally { panel.close(); controller.pause().stop().destroy() }
    }

    private fun drag(panel: Stage3TestPanel, fromY: Float, toY: Float, x: Float = 400f) {
        val downTime = SystemClock.uptimeMillis()
        panel.touch(MotionEvent.ACTION_DOWN, x, fromY, downTime)
        panel.advance()
        val steps = 20
        for (step in 1..steps) {
            panel.touch(MotionEvent.ACTION_MOVE, x, fromY + (toY - fromY) * step / steps, downTime)
            panel.advance()
        }
        panel.touch(MotionEvent.ACTION_UP, x, toY, downTime)
        panel.advance(30)
    }
}
