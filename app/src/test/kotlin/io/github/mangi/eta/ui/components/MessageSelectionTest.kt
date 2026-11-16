package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 09 第五期 5.1：消息多选。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageSelectionTest {
    @org.junit.Before fun setUp() = io.github.mangi.eta.ui.app.DeviceModeScreens.initEnvironment()

    private val messages = listOf(
        UserMessageUi("u1", "第一个问题"),
        ThinkingMessageUi("t1", "想一想", isStreaming = false),
        AgentMessageUi("a1", "第一个回答\n"),
        UserMessageUi("u2", "第二个问题"),
        AgentMessageUi("a2", "第二个回答"),
    )

    @Test fun plainTextFollowsConversationOrderWithSpeakerPrefixes() {
        val text = selectionPlainText(messages, setOf("a2", "u1", "t1", "a1"), "我", "胖小胖")
        assertEquals("我：第一个问题\n\n胖小胖：第一个回答\n\n胖小胖：第二个回答", text)
        assertEquals("", selectionPlainText(messages, emptySet(), "我", "胖小胖"))
        // 角色对话用角色名。
        assertEquals("我：第二个问题", selectionPlainText(messages, setOf("u2"), "我", "小橘"))
    }

    @Test fun stateStartsTogglesAndClears() {
        val state = MessageSelectionState()
        assertFalse(state.active)
        state.start("a1"); assertEquals(setOf("a1"), state.selected)
        state.toggle("u1"); state.toggle("a1"); assertEquals(setOf("u1"), state.selected)
        state.clear(); assertNull(state.selected)
        state.toggle("u1"); assertNull("toggle outside selection mode does nothing", state.selected)
        state.start(); assertEquals(emptySet<String>(), state.selected)
    }

    @Test fun longPressEntersTapTogglesAndBackExits() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val state = MessageSelectionState()
        val panel = Stage3TestPanel(activity) {
            EtaAppOverlayHost {
                CompositionLocalProvider(LocalMessageSelection provides state) { Stage3ChatFixture(messages) }
            }
        }
        fun texts() = panel.nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
        fun center(text: String) = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true }.boundsInRoot.center
        fun tap(text: String) {
            val p = center(text); val down = android.os.SystemClock.uptimeMillis()
            panel.touch(android.view.MotionEvent.ACTION_DOWN, p.x, p.y, down); panel.advance(2)
            panel.touch(android.view.MotionEvent.ACTION_UP, p.x, p.y, down); panel.advance(10)
        }
        try {
            panel.advance(40)
            // 长按回答 → 菜单里的「多选」进入多选并选中这一条。
            val p = center("第二个回答"); val down = android.os.SystemClock.uptimeMillis()
            panel.touch(android.view.MotionEvent.ACTION_DOWN, p.x, p.y, down); panel.advance(45)
            panel.touch(android.view.MotionEvent.ACTION_UP, p.x, p.y, down); panel.advance(35)
            panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == activity.getString(R.string.message_selection_menu) } == true })
            panel.advance(30)
            assertEquals(setOf("a2"), state.selected)
            // 多选中点用户消息、回答切换选中；底部换成「已选 N 条」。
            tap("第一个问题"); tap("第二个回答")
            assertEquals(setOf("u1"), state.selected)
            assertTrue(texts().any { it.contains("1") && it.contains(activity.resources.getQuantityString(R.plurals.message_selection_count, 1, 1).filterNot(Char::isDigit).trim()) })
            // 返回键退出多选。
            activity.onBackPressedDispatcher.onBackPressed(); panel.advance(20)
            assertNull(state.selected)
        } finally { runCatching { panel.close() }; runCatching { controller.pause().stop().destroy() } }
    }
}
