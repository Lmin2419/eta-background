package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * etax/design/05 §8：固定尺寸控件不变形。320dp 宽、字体 200% 下渲染对话页顶部控件和输入胶囊的按钮行，
 * 断言发送键、圆形按钮的宽等于高（长文字只会被省略，不会把按钮挤扁）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w320dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FixedSizeControlsTest {
    @Test fun sendKeyAndCircleButtonsStaySquareAtLargeFontOnNarrowScreens() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val longName = "anthropic/claude-extremely-long-model-identifier-for-layout-2026-11-14"
        val model = AgentModelOptionUi("m", "p", "Provider", "openai", longName, longName, 200_000)
        val panel = Stage3TestPanel(controller.get(), fontScale = 2f) {
            Column(Modifier.width(320.dp)) {
                Row(Modifier.width(320.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleIconButton(Icons.Outlined.Menu, "history", {})
                    Row(Modifier.weight(1f)) {
                        ModelPill(AgentModelPickerUiState(selectedModel = model), locked = false) {}
                    }
                    CircleIconButton(Icons.Outlined.Add, "new", {})
                }
                Row(Modifier.width(320.dp), verticalAlignment = Alignment.CenterVertically) {
                    top.yukonga.miuix.kmp.basic.Text("思考 · 标准思考 · 标准思考 · 标准", Modifier.weight(1f), maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    ComposerActionButton(ComposerAction.Microphone, "mic", "hold", {}, {}, {}, {}, plain = true)
                    ComposerActionButton(ComposerAction.Send, "send", "hold", {}, {}, {}, {})
                }
            }
        }
        try {
            panel.advance(10)
            val nodes = panel.nodes()
            for (label in listOf("history", "new", "send")) {
                val node = nodes.first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
                val size = node.size
                assertTrue("$label has no size", size.width > 0)
                assertEquals("$label must stay square: $size", size.width, size.height)
            }
        } finally {
            panel.close(); controller.pause().stop().destroy()
        }
    }
}
