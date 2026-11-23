package io.github.mangi.eta.ui.app

import android.app.Application
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.EtaContextMenu
import io.github.mangi.eta.ui.components.EtaMenuDivider
import io.github.mangi.eta.ui.components.EtaMenuItem
import io.github.mangi.eta.ui.components.EtaSheet
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.components.coui.EtaCouiInputDialog
import io.github.mangi.eta.ui.components.coui.EtaMenuAnchor
import io.github.mangi.eta.ui.components.coui.etaMenuAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 14.4b：菜单、对话框、半窗、输入对话框换成 COUI 2.1.0 后的硬件渲染截图（浅色、深色、200% 字体），
 * 存到 build/reports/device-mode/overlay-*.png。每张都检查弹层真的画出来了（文字在、不是整页空白）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CouiOverlaysDeviceModeTest {
    @Before fun setUp() = DeviceModeScreens.initEnvironment()

    private fun Stage3TestPanel.texts(): List<String> = nodes().flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    private fun shoot(name: String, dark: Boolean, fontScale: Float, expect: String, act: (Stage3TestPanel) -> Unit = {},
        minInk: Int = 300, content: @androidx.compose.runtime.Composable () -> Unit) {
        var seen: List<String> = emptyList()
        // 新库的圆角遮罩（squircle）着色器本机渲染器编译不了（真机可以），和设置页一样关掉着色器截图。
        val shot = DeviceModeScreens.capture(name, dark = dark, fontScale = fontScale, deviceMode = false, act = { panel ->
            act(panel)
            panel.advance(30)
            seen = panel.texts()
        }, content = content)
        assertEquals("$name: PixelCopy", android.view.PixelCopy.SUCCESS, shot.result)
        assertTrue("$name must not be blank (ink=${shot.ink})", shot.ink > minInk)
        assertTrue("$name 缺少「$expect」：$seen", seen.any { expect in it })
    }

    private fun variants(base: String, expect: String, act: (Stage3TestPanel) -> Unit = {}, minInk: Int = 300,
        content: @androidx.compose.runtime.Composable () -> Unit) {
        shoot("$base-light", dark = false, fontScale = 1f, expect = expect, act = act, minInk = minInk, content = content)
        shoot("$base-dark", dark = true, fontScale = 1f, expect = expect, act = act, minInk = minInk, content = content)
        shoot("$base-font200", dark = false, fontScale = 2f, expect = expect, act = act, minInk = minInk, content = content)
    }

    @androidx.compose.runtime.Composable
    private fun Page() = DeviceModeScreens.Home(DeviceModeScreens.sampleMessages, title = "整理本周工作进展")

    @Test fun menuOpensAtTheFinger() {
        val anchor = EtaMenuAnchor()
        val show = mutableStateOf(false)
        variants("overlay-menu", expect = "重新回答", act = { panel ->
            show.value = false
            panel.advance(10)
            val target = panel.nodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("menu-anchor") == true
            }.boundsInRoot
            val down = android.os.SystemClock.uptimeMillis()
            panel.touch(MotionEvent.ACTION_DOWN, target.center.x, target.center.y, down)
            panel.advance(2)
            panel.touch(MotionEvent.ACTION_UP, target.center.x, target.center.y, down)
            anchor.open { show.value = true }
            panel.advance(40)
        }) {
            Box(Modifier.fillMaxSize()) {
                Page()
                Box(Modifier.align(Alignment.Center).padding(top = 120.dp).size(160.dp, 72.dp)
                    .semantics { contentDescription = "menu-anchor" }.etaMenuAnchor(anchor))
                EtaContextMenu(show.value, onDismiss = { show.value = false }, couiAnchor = anchor, header = "这条回答") {
                    EtaMenuItem("复制", onClick = {}, icon = Icons.Outlined.ContentCopy)
                    EtaMenuItem("重新回答", onClick = {}, icon = Icons.Outlined.Refresh)
                    EtaMenuDivider()
                    EtaMenuItem("删除", onClick = {}, destructive = true, icon = Icons.Outlined.Delete)
                }
            }
        }
    }

    @Test fun dialogWithActions() = variants("overlay-dialog", expect = "删除这个对话？") {
        Box(Modifier.fillMaxSize()) {
            Page()
            EtaWindowDialog(show = true, title = "删除这个对话？", summary = "删除后无法恢复，相关的定时任务也会停用。",
                onDismissRequest = {}) {
                MiuixDialogActions(confirmText = "删除", onCancel = {}, onConfirm = {}, destructive = true)
            }
        }
    }

    @Test fun noticeDialogHasCloseButton() = variants("overlay-notice",
        // 深色页盖上遮罩、只有一个灰色按钮时亮像素少，阈值放低（文字检查仍在）。
        expect = org.robolectric.RuntimeEnvironment.getApplication().getString(io.github.mangi.eta.R.string.action_close), minInk = 100) {
        Box(Modifier.fillMaxSize()) {
            Page()
            EtaWindowDialog(show = true, title = "角色库", summary = "已导入 3 个角色。", onDismissRequest = {}) {}
        }
    }

    @Test fun sheet() = variants("overlay-sheet", expect = "选择模型") {
        Box(Modifier.fillMaxSize()) {
            Page()
            EtaSheet(show = true, title = "选择模型", onDismissRequest = {}) {
                listOf("日常（快）", "深度（慢，适合复杂问题）", "看图").forEach { label ->
                    io.github.mangi.eta.ui.components.EtaPreference(title = label, onClick = {})
                }
            }
        }
    }

    @Test fun inputDialog() = variants("overlay-input", expect = "重命名对话") {
        Box(Modifier.fillMaxSize()) {
            Page()
            EtaCouiInputDialog(visible = true, title = "重命名对话", value = "整理本周工作进展", onValueChange = {},
                confirmText = "保存", onConfirm = {}, onDismissRequest = {}, placeholder = "对话名称")
        }
    }
}
