package io.github.mangi.eta.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.runtime.*
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Stage81PreviewRenderingTest {
    private data class Case(val name: String, val status: TaskVisualStatus, val frame: Boolean, val label: String,
        val hasConversation: Boolean = true, val stopped: Boolean = false)
    private val cases = listOf(
        Case("success-page", TaskVisualStatus.Success, true, "已完成"),
        Case("success-released", TaskVisualStatus.Success, false, "已完成"),
        Case("failure-page", TaskVisualStatus.Failed, true, "未完成"),
        Case("failure-released", TaskVisualStatus.Failed, false, "未完成"),
        Case("running-waiting", TaskVisualStatus.Running, false, "正在执行"))

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
    private fun layout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }
    private fun clickable(node: SemanticsNode): SemanticsNode =
        if (node.config.getOrNull(SemanticsActions.OnClick) != null) node else clickable(requireNotNull(node.parent))

    private fun render(case: Case, dark: Boolean, check: (Stage3TestPanel, Float, Int, Int, MutableList<PreviewAction>, PreviewPresentation) -> Unit) {
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val ui = PreviewPresentation().apply {
            appName = "美团"; appIcon = owner.get().getDrawable(R.drawable.ic_eta_mark)
            frameReady = case.frame; frameUnavailable = !case.frame
            taskStopped = case.stopped
            result = if (case.status == TaskVisualStatus.Success) "已下单：生椰拿铁一杯，预计 25 分钟送达。" else "订单未提交，请检查网络后重试。"
            failureReason = if (case.status == TaskVisualStatus.Failed) result else ""
            step = "正在点击提交订单"
            hint.text = if (case.frame) "任务已结束，下面可继续操作" else "暂时拿不到画面，请稍后再试"
            controls = PreviewWindowMapper.map(PreviewSnapshot(case.status, false, false, false, false, false, case.frame, !case.frame, case.hasConversation))
        }
        val frame = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(255, 248, 230))
            Canvas(this).apply {
                drawRect(20f, 24f, 300f, 90f, Paint().apply { color = android.graphics.Color.rgb(255, 209, 71) })
                drawText("订单页面 / Test fixture", 30f, 64f, Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 20f; isAntiAlias = true })
            }
        }
        val picture = TaskPreviewImageView(owner.get()).apply { if (case.frame) setImageBitmap(frame) }
        val actions = mutableListOf<PreviewAction>()
        var tonal = 0; var hintColor = 0
        val panel = Stage3TestPanel(owner.get(), dark) {
            Box(Modifier.fillMaxSize().background(EtaTheme.colors.canvas), contentAlignment = Alignment.TopCenter) {
                // Production preview is always dark, including over a light host.
                AgentAppTheme(AppearanceSettings(themeMode = AppearanceThemeMode.DARK), false) {
                    // 次按钮与上游一致：COUI 次按钮浅灰底（深色 #353535）。
                    tonal = (if (EtaTheme.colors.isDark) androidx.compose.ui.graphics.Color(0xFF353535) else androidx.compose.ui.graphics.Color(0xFFEBEBEB)).toArgb()
                    hintColor = (if (case.status == TaskVisualStatus.Failed) EtaTheme.colors.danger else EtaTheme.colors.textSecondary).toArgb()
                    Box(Modifier.width(244.dp).height(480.dp).padding(top = 8.dp)) {
                        EtaPreviewWindow(ui, picture, { actions += it }, {}, {})
                    }
                }
            }
        }
        try { panel.advance(35); check(panel, owner.get().resources.displayMetrics.density, tonal, hintColor, actions, ui) }
        finally { panel.close(); owner.pause().stop().destroy(); frame.recycle() }
    }

    @Test fun screenshotMatrix() {
        for (case in cases) for (dark in listOf(false, true)) render(case, dark) { panel, _, _, _, _, _ ->
            panel.capture("stage81-${case.name}-${if (dark) "dark" else "light"}")
        }
    }

    private fun assertCase(index: Int) {
        val case = cases[index]
        for (dark in listOf(false, true)) render(case, dark) { panel, density, tonal, hintColor, actions, _ ->
            val nodes = panel.nodes()
            val status = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_status" }
            assertEquals(case.label, text(status))
            val hint = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_hint" }
            assertEquals(hintColor, layout(hint).layoutInput.style.color.toArgb())
            if (case.status == TaskVisualStatus.Running) {
                assertEquals("正在等待画面…", text(hint))
                assertNotNull(clickable(nodes.single { text(it) == "接管" }).config.getOrNull(SemanticsProperties.Disabled))
            } else {
                assertTrue("no orange primary", nodes.none { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_primary" })
                val chat = nodes.single { text(it) == "打开会话" }
                val app = nodes.single { text(it) == "打开 App" }
                val buttons = listOf(chat, app).map(::clickable)
                assertEquals(buttons[0].boundsInRoot.width, buttons[1].boundsInRoot.width, 1f)
                assertEquals(buttons[0].boundsInRoot.center.y, buttons[1].boundsInRoot.center.y, 1f)
                val bitmap = panel.bitmap()
                try {
                    panel.draw(bitmap)
                    buttons.forEach { button ->
                        val bounds = button.boundsInRoot
                        assertEquals(40f, bounds.height / density, .5f)
                        assertEquals("Tonal background", tonal, bitmap.getPixel(bounds.center.x.toInt(), (bounds.top + 5 * density).toInt()))
                    }
                } finally { bitmap.recycle() }
                for (label in listOf(chat, app)) {
                    val l = layout(label)
                    assertEquals(1, l.lineCount); assertFalse(l.isLineEllipsized(0)); assertFalse(l.didOverflowHeight)
                    // CJK intrinsic advance can exceed its rounded pixel width by a fraction of a pixel.
                    assertTrue("${text(label)} must fit", l.getLineRight(0) - l.getLineLeft(0) <= l.size.width + 1f)
                }
                val descriptions = nodes.flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                assertEquals("only the header closes", 1, descriptions.count { it == "关闭" })
                assertEquals(case.frame, descriptions.contains("操作页面"))
                assertEquals(!case.frame, nodes.any { text(it) == "页面已释放" })
                val rowActions = nodes.filter { it.config.getOrNull(SemanticsActions.OnClick) != null &&
                    it.boundsInRoot.center.y >= buttons[0].boundsInRoot.top }
                assertEquals(if (case.frame) 3 else 2, rowActions.size)
                rowActions.forEach { assertEquals(buttons[0].boundsInRoot.center.y, it.boundsInRoot.center.y, 1f) }
                panel.click(chat); panel.click(app)
                assertEquals(listOf(PreviewAction.OpenConversation, PreviewAction.OpenApp), actions)
            }
        }
    }
    @Test fun successWithPage() = assertCase(0)
    @Test fun successWithoutPage() = assertCase(1)
    @Test fun failureWithPage() = assertCase(2)
    @Test fun failureWithoutPage() = assertCase(3)
    @Test fun runningWithoutFrame() = assertCase(4)

    @Test fun noConversationUsesOneFullWidthTonalButton() {
        for (dark in listOf(false, true)) render(cases[1].copy(hasConversation = false), dark) { panel, density, _, _, actions, _ ->
            val nodes = panel.nodes()
            assertTrue(nodes.none { text(it) == "打开会话" })
            val app = nodes.single { text(it) == "打开 App" }
            val row = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_actions" }
            assertEquals(row.boundsInRoot.width, clickable(app).boundsInRoot.width, 1f)
            assertEquals(40f, clickable(app).boundsInRoot.height / density, .5f)
            assertTrue(nodes.none { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_primary" })
            panel.click(app); assertEquals(listOf(PreviewAction.OpenApp), actions)
        }
    }

    @Test fun stoppedKeepsItsOwnLabelAndTerminalButtons() {
        for (dark in listOf(false, true)) render(cases[3].copy(stopped = true), dark) { panel, _, _, _, _, _ ->
            val nodes = panel.nodes()
            assertEquals("已停止", text(nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_status" }))
            assertTrue(nodes.none { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_primary" })
            assertTrue(nodes.any { text(it) == "打开会话" }); assertTrue(nodes.any { text(it) == "打开 App" })
        }
    }

    @Test fun taskEndingInTheOpenWindowOnlyChangesItsPresentation() {
        render(cases[4], false) { panel, _, _, _, actions, ui ->
            val original = panel.root()
            ui.controls = PreviewWindowMapper.map(PreviewSnapshot(TaskVisualStatus.Success, false, false, false, false, false, false, true, true))
            ui.result = "任务已完成。"
            panel.advance(3)
            assertSame(original, panel.root()); assertFalse(ui.expanded); assertTrue(actions.isEmpty())
            assertEquals("已完成", text(panel.nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_status" }))
        }
    }
}
