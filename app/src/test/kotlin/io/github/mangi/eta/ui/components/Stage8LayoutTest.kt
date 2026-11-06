package io.github.mangi.eta.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.*
import io.github.mangi.eta.agent.runtime.*
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.theme.EtaTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Stage8LayoutTest {
    private fun text(panel: Stage3TestPanel, value: String) = panel.nodes().first {
        it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == value } == true
    }
    private fun layout(node: SemanticsNode): TextLayoutResult {
        val result = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result))
        return result.single()
    }
    private fun preview(width: Int, scale: Float, dark: Boolean, check: (Stage3TestPanel, PreviewPresentation, Float) -> Unit) {
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val ui = PreviewPresentation().apply {
            appName = "中国移动云盘"; step = "正在点击提交订单，等待页面加载完成"; manual = true; frameReady = true
            appIcon = owner.get().getDrawable(R.drawable.ic_eta_mark)
            controls = PreviewWindowMapper.map(PreviewSnapshot(TaskVisualStatus.Paused, true, false, false, false, false, true, false, true))
        }
        val picture = TaskPreviewImageView(owner.get())
        val panel = Stage3TestPanel(owner.get(), dark, scale) {
            Box(Modifier.fillMaxSize().background(EtaTheme.colors.canvas), contentAlignment = Alignment.TopCenter) {
                AgentAppTheme(AppearanceSettings(themeMode = AppearanceThemeMode.DARK), false) {
                    Box(Modifier.width(width.dp).height(480.dp).padding(top = 8.dp)) {
                        EtaPreviewWindow(ui, picture, {}, {}, {})
                    }
                }
            }
        }
        try { panel.advance(35); check(panel, ui, owner.get().resources.displayMetrics.density) }
        finally { panel.close(); owner.pause().stop().destroy() }
    }

    @Test fun s10_1_previewNamesKeepUsefulCharactersAndButtonsKeep48dp() {
        for (width in listOf(223, 244)) for (scale in listOf(1f, 2f)) {
            preview(width, scale, false) { panel, _, density ->
                val result = layout(text(panel, "中国移动云盘"))
                if (width == 244 && scale == 1f) assertFalse("244dp must show all six characters", result.isLineEllipsized(0))
                val required = if (scale == 2f) 2 else if (width == 223) 4 else 6
                assertTrue("$width/$scale visible end=${result.getLineEnd(0, true)}", result.getLineEnd(0, true) >= required)
                for (tag in listOf("preview_zoom", "preview_close")) {
                    val bounds = panel.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }.boundsInRoot
                    assertEquals(48f, bounds.width / density, .5f); assertEquals(48f, bounds.height / density, .5f)
                }
            }
        }
    }

    @Test fun s10_1_fiveStatusLabelsAndLatinNameRemainReadable() {
        preview(244, 1f, false) { panel, ui, _ ->
            ui.appName = "Google Chrome"; panel.advance(3)
            assertTrue(layout(text(panel, ui.appName)).getLineEnd(0, true) >= ui.appName.length - 2)
            for (status in listOf(TaskVisualStatus.Running, TaskVisualStatus.Attention, TaskVisualStatus.Paused, TaskVisualStatus.Success, TaskVisualStatus.Failed)) {
                ui.controls = PreviewWindowMapper.map(PreviewSnapshot(status, false, false, false, false, false, true, false, true))
                panel.advance(3)
                val node = panel.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_status" }
                val result = layout(node)
                assertEquals(1, result.lineCount); assertFalse("$status", result.isLineEllipsized(0))
            }
        }
    }

    @Test fun s10_9_visibleManualAndPausedHintsUseTheLocalizedHintCatalog() {
        preview(244, 1f, false) { panel, ui, _ ->
            val context = org.robolectric.RuntimeEnvironment.getApplication()
            assertTrue(panel.nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == context.getString(PreviewHintType.ManualReady.resourceId) } == true })
            ui.manual = false
            ui.controls = PreviewWindowMapper.map(PreviewSnapshot(TaskVisualStatus.Paused, false, false, false, false, false, true, false, true))
            panel.advance(3)
            assertTrue(panel.nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == context.getString(PreviewHintType.ReadOnlyPaused.resourceId) } == true })
        }
    }

    @Test fun s10_1_hintReservesTwoLinesWithoutMovingThePicture() {
        preview(244, 1f, true) { panel, ui, _ ->
            ui.inputFailed = true; ui.hint.text = "短提示"; panel.advance(3)
            fun frame() = panel.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "preview_frame" }.boundsInRoot
            val before = frame()
            ui.hint.text = "正在点击提交订单，等待页面加载完成"; panel.advance(3)
            val long = layout(text(panel, ui.hint.text))
            assertEquals("hint must permit two lines", 2, long.lineCount)
            assertEquals(before, frame())
        }
    }

    @Test fun previewMatrixBeforeAndAfterEvidence() {
        for (dark in listOf(false, true)) for (width in listOf(223, 244)) for (scale in listOf(1f, 2f)) {
            preview(width, scale, dark) { panel, _, _ ->
                panel.capture("stage8-preview-${if (dark) "dark" else "light"}-$width-${(scale * 100).toInt()}")
            }
        }
    }

    @Test fun s10_3_allFourSuggestionSummariesAreCompleteAndRowsAreEqualHeight() {
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(owner.get()) { HomeEmptyState(true, {}) }
        try {
            panel.advance(30)
            val nodes = listOf(R.string.eta_prompt_coffee, R.string.eta_prompt_notifications,
                R.string.eta_prompt_battery, R.string.eta_prompt_phones).map { text(panel, owner.get().getString(it)) }
            nodes.forEach { n -> assertFalse("full preset summary: ${n.config[SemanticsProperties.Text]}", layout(n).hasVisualOverflow) }
            fun card(node: SemanticsNode): SemanticsNode = if (node.config.getOrNull(SemanticsActions.OnClick) != null) node else card(requireNotNull(node.parent))
            nodes.chunked(2).forEach { assertEquals(card(it[0]).boundsInRoot.height, card(it[1]).boundsInRoot.height, 1f) }
        } finally { panel.close(); owner.pause().stop().destroy() }
    }

    @Test @Config(qualifiers = "en-rUS-w393dp-h851dp-xhdpi")
    fun s10_5_englishReadinessHasShortSingleLineLabelsAndFullSpokenNames() {
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(owner.get()) { PermissionHealthCard(PermissionHealthUiState(emptyList()), {}, engineReady = true) }
        try {
            panel.advance(20)
            for (label in listOf("Root", "A11y", "Overlay", "Alerts", "Engine")) {
                val matches = panel.nodes().filter { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true }
                assertEquals("short readiness label $label", 1, matches.size)
                val result = layout(matches.single()); assertEquals(1, result.lineCount); assertFalse("$label: ${result.size}, overflow=${result.hasVisualOverflow}", result.hasVisualOverflow)
            }
            val descriptions = panel.nodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertTrue(descriptions.any { it.startsWith("Accessibility:") }); assertTrue(descriptions.any { it.startsWith("Notifications:") })
        } finally { panel.close(); owner.pause().stop().destroy() }
    }

    @Test @Config(qualifiers = "en-rUS-w393dp-h851dp-xhdpi")
    fun englishReadinessEvidence() {
        for (dark in listOf(false, true)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val panel = Stage3TestPanel(owner.get(), dark) {
                Box(Modifier.fillMaxSize().background(EtaTheme.colors.canvas)) {
                    PermissionHealthCard(PermissionHealthUiState(emptyList()), {}, engineReady = true)
                }
            }
            try { panel.advance(20); panel.capture("stage8-readiness-en-${if (dark) "dark" else "light"}") }
            finally { panel.close(); owner.pause().stop().destroy() }
        }
    }

    @Test fun s10_6_expandedCapsuleLinksAndTaskRowsHave48dpTargets() {
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(owner.get()) { capsule() }
        try {
            panel.advance(60)
            val density = owner.get().resources.displayMetrics.density
            for (value in listOf("● 任务一", "○ 任务二", owner.get().getString(R.string.overlay_open_conversation), owner.get().getString(R.string.overlay_supplement))) {
                val n = text(panel, value)
                assertNotNull(n.config.getOrNull(SemanticsActions.OnClick))
                assertTrue("$value target ${n.boundsInRoot.height / density}dp", n.boundsInRoot.height / density >= 47.9f)
            }
        } finally { panel.close(); owner.pause().stop().destroy() }
    }

    @Composable private fun capsule() = AgentOverlayCapsule(AgentOverlayState.Initial, true, false,
        {}, {}, {}, {}, {}, {}, {}, tasks = listOf(OverlayTaskItem("one", "任务一"), OverlayTaskItem("two", "任务二")), selectedTaskId = "one")

    @Test fun componentBeforeAndAfterEvidence() {
        for (dark in listOf(false, true)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            for (scene in listOf("home", "readiness", "capsule")) {
                val panel = Stage3TestPanel(owner.get(), dark) {
                    Column(Modifier.fillMaxSize().background(EtaTheme.colors.canvas)) {
                        when (scene) {
                            "home" -> HomeEmptyState(true, {})
                            "readiness" -> PermissionHealthCard(PermissionHealthUiState(emptyList()), {}, engineReady = true)
                            else -> capsule()
                        }
                    }
                }
                try { panel.advance(60); panel.capture("stage8-$scene-${if (dark) "dark" else "light"}") }
                finally { panel.close() }
            }
            owner.pause().stop().destroy()
        }
    }

    @Test fun s10_2_badgeCoversAtMostFourByFourOnSquareAndNoneOnCircle() {
        for (circle in listOf(false, true)) for (dark in listOf(false, true)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val green = android.graphics.Color.rgb(0, 204, 0)
            if (circle) Canvas(bitmap).drawCircle(32f, 32f, 32f, Paint().apply { color = green }) else bitmap.eraseColor(green)
            shadowOf(owner.get().packageManager).setApplicationIcon("stage8.icon", BitmapDrawable(owner.get().resources, bitmap))
            val task = TaskNotificationState("stage8-icon", "stage8", "测试应用", AgentOverlayStatus.Reasoning, targetPackage = "stage8.icon")
            AgentExecutionService.updateTasks(owner.get(), listOf(task))
            val panel = Stage3TestPanel(owner.get(), dark) {
                Box(Modifier.fillMaxSize().background(EtaTheme.colors.canvas).padding(top = 16.dp)) { ConversationLiveTasks("stage8", {}) }
            }
            try {
                panel.advance(60); Thread.sleep(30); panel.advance(10)
                panel.capture("stage8-badge-${if (circle) "circle" else "square"}-${if (dark) "dark" else "light"}")
                val capture = panel.bitmap(); panel.draw(capture)
                val points = mutableListOf<Pair<Int, Int>>()
                for (y in 0 until capture.height) for (x in 0 until capture.width) if (capture.getPixel(x, y) == green) points += x to y
                assertTrue("fixture app icon must load", points.isNotEmpty())
                val left = points.minOf { it.first }; val top = points.minOf { it.second }
                val density = owner.get().resources.displayMetrics.density; val side = (28 * density).toInt()
                var covered = 0
                for (y in 0 until side) for (x in 0 until side) {
                    val inside = !circle || (x + .5f - side / 2f) * (x + .5f - side / 2f) + (y + .5f - side / 2f) * (y + .5f - side / 2f) < (side / 2f - 2 * density) * (side / 2f - 2 * density)
                    if (inside && capture.getPixel(left + x, top + y) != green) covered++
                }
                capture.recycle()
                assertTrue("circle=$circle covered=${covered / (density * density)} dp²", covered <= if (circle) 0 else (16 * density * density).toInt())
            } finally { panel.close(); AgentExecutionService.updateTasks(owner.get(), emptyList()); owner.pause().stop().destroy(); bitmap.recycle() }
        }
    }
}
