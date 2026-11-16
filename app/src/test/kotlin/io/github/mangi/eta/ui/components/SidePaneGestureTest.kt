package io.github.mangi.eta.ui.components

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 侧栏推开手势与横向滚动（etax/design/07 第四期）：代码块、表格里的横滑先给内容，
 * 只有在没有横向滚动的地方横滑才拉出侧栏。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
class SidePaneGestureTest {
    private class Result(val opened: Boolean, val scroll: Int)

    /** 在 y 处从 x=80px 向右拖 600px；内容里第 200–320dp 是一条可横向滚动的长「代码块」。 */
    private fun swipeRight(yDp: Int, initialScroll: Int = 0): Result {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var visible by mutableStateOf(false)
        var opened = false
        val scroll = ScrollState(0)
        val panel = Stage3TestPanel(controller.get()) {
            ConversationSidePaneScaffold(
                state = ConversationPaneUiState(emptyList(), null, ""), visible = visible, backHandlerEnabled = true,
                onOpen = { opened = true; visible = true }, onDismiss = { visible = false }, onSearchChange = {},
                onConversationSelected = {}, onConversationRename = {}, onConversationExport = {}, onConversationDelete = {},
                onOpenSettings = {}, onOpenModelProviders = {}, onOpenTools = {}, onOpenSkills = {}, onOpenCharacters = {},
                onOpenPermissions = {},
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(200.dp))
                    Row(Modifier.fillMaxWidth().height(120.dp).horizontalScroll(scroll)) { Box(Modifier.width(1600.dp).height(120.dp)) }
                    Box(Modifier.fillMaxWidth().height(300.dp))
                }
            }
        }
        try {
            panel.advance(5)
            if (initialScroll > 0) { kotlinx.coroutines.runBlocking { scroll.scrollTo(initialScroll) }; panel.advance(2) }
            val density = controller.get().resources.displayMetrics.density
            val root = panel.root() as View
            val location = IntArray(2).also(root::getLocationInWindow)
            val y = location[1] + yDp * density
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, at: Long) {
                val event = MotionEvent.obtain(down, at, action, x, y, 0)
                root.dispatchTouchEvent(event); event.recycle()
            }
            send(MotionEvent.ACTION_DOWN, 80f, down)
            for (step in 1..12) { panel.advance(1); send(MotionEvent.ACTION_MOVE, 80f + step * 50f, down + step * 16L) }
            send(MotionEvent.ACTION_UP, 680f, down + 13 * 16L)
            panel.advance(60)
            return Result(opened, scroll.value)
        } finally { runCatching { panel.close() }; runCatching { controller.pause().stop().destroy() } }
    }

    @Test fun swipingOnPlainContentOpensTheSidePane() {
        assertTrue(swipeRight(yDp = 100).opened)
    }

    @Test fun swipingOnAScrolledCodeBlockScrollsTheCode() {
        val result = swipeRight(yDp = 260, initialScroll = 900)
        assertFalse("the side pane must not open while the code block can scroll back", result.opened)
        assertTrue("the code block scrolls back (scroll=${result.scroll})", result.scroll < 900)
    }

    @Test fun swipingOnACodeBlockAtItsStartLeavesTheSidePaneClosed() {
        val result = swipeRight(yDp = 260)
        assertFalse("horizontal swipes inside a scrollable block belong to the block", result.opened)
        assertEquals(0, result.scroll)
    }
}
