package io.github.mangi.eta.ui.components

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.*
import io.github.mangi.eta.agent.voice.EtaAssistantEdgeGlow
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.navigation.*
import io.github.mangi.eta.ui.screens.tasks.ActiveTaskCard
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.*
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Stage7PolishTest {
    @Test fun previewPrimaryFillsRowAndSecondaryIsRoundAtLargeText() {
        for (scale in listOf(1f, 2f)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val ui = PreviewPresentation().apply {
                appName = "应用名称非常长但状态仍然只能显示一行"
                appIcon = owner.get().getDrawable(R.drawable.ic_eta_mark)
                manual = true; frameReady = true; step = "正在点击提交订单"
                controls = PreviewWindowMapper.map(PreviewSnapshot(TaskVisualStatus.Paused, true, false, false, false, false, true, false, true))
            }
            val picture = TaskPreviewImageView(owner.get())
            val actions = mutableListOf<PreviewAction>()
            val panel = Stage3TestPanel(owner.get(), dark = true, fontScale = scale) {
                Box(Modifier.width(255.dp).height(450.dp)) { EtaPreviewWindow(ui, picture, { actions += it }, {}, {}) }
            }
            try {
                panel.advance(25)
                fun tag(tag: String) = panel.nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                val primary = tag("preview_primary").boundsInRoot
                val secondary = tag("preview_secondary").boundsInRoot
                val density = owner.get().resources.displayMetrics.density
                assertEquals(48f, secondary.width / density, 1f)
                assertEquals(48f, secondary.height / density, 1f)
                assertEquals(175f, primary.width / density, 1f)
                assertTrue(primary.height >= 48 * density)
                assertTrue(primary.right < secondary.left)
                assertTrue(panel.nodes().any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { s -> s.contains(ui.appName) && s.contains(ui.step) } == true })
                panel.tapFinger(primary.center.x, primary.center.y)
                panel.tapFinger(secondary.center.x, secondary.center.y)
                assertEquals(listOf(PreviewAction.ReturnToAi, PreviewAction.Back), actions)
                panel.capture("stage7_preview_${if (scale == 2f) "font200" else "normal"}")
            } finally { panel.close(); owner.pause().stop().destroy() }
        }
    }

    @Test fun reducedVisibilityHasNoSlideAndGlowIsStatic() {
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var shown by mutableStateOf(false)
        var arrivals = 0
        val panel = Stage3TestPanel(owner.get()) {
            EtaAssistantEdgeGlow(true, { arrivals++ })
            EtaAnimatedVisibility(shown, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                Box(Modifier.size(100.dp).testTag("fade_target"))
            }
        }
        try {
            panel.advance(15); shown = true; panel.advance(2)
            fun bounds() = panel.nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == "fade_target" }.boundsInRoot
            val initial = bounds(); panel.advance(8); assertEquals(initial, bounds())
            assertEquals(1, arrivals)
            val first = panel.bitmap(); panel.draw(first)
            panel.advance(20)
            val later = panel.bitmap(); panel.draw(later)
            try { assertTrue("Reduced glow must not draw moving frames", first.sameAs(later)) }
            finally { first.recycle(); later.recycle() }
            shown = false; panel.advance(10)
            assertFalse(panel.nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "fade_target" })
        } finally { panel.close(); owner.pause().stop().destroy() }
    }

    @Test fun homeBrandAndLargeTextSuggestionsRemainScrollableAndFillOnly() {
        for ((dark, scale) in listOf(false to 1f, true to 1f, false to 2f)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            var filled = ""
            val panel = Stage3TestPanel(owner.get(), dark, scale) {
                HomeEmptyState(true, { filled = it }, Modifier.fillMaxSize().background(io.github.mangi.eta.ui.theme.EtaTheme.colors.canvas))
            }
            try {
                panel.advance(25)
                assertTrue(panel.nodes().any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(owner.get().getString(R.string.app_name)) == true })
                assertFalse(panel.nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "η" } == true })
                assertEquals("", filled)
                panel.capture("stage7_empty_${if (dark) "dark" else if (scale == 2f) "font200" else "light"}")
                val view = panel.root() as View
                panel.dragFinger(view.width * .5f, view.height * .85f, view.width * .5f, view.height * .25f)
                val target = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == owner.get().getString(R.string.eta_example_phones) } == true }
                panel.tapFinger(target.boundsInRoot.center.x, target.boundsInRoot.center.y)
                assertEquals(owner.get().getString(R.string.eta_prompt_phones), filled)
            } finally { panel.close(); owner.pause().stop().destroy() }
        }
    }

    @Test fun taskTalkBackKeepsFullStateAndSeparateActionButtons() {
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val title = "美团，非常长的任务标题，视觉省略后仍须完整播报"
        val task = TaskNotificationState("a11y", "conversation", title, AgentOverlayStatus.RunningTool("提交订单"))
        val panel = Stage3TestPanel(owner.get(), dark = true, fontScale = 2f) {
            EtaAppOverlayHost { ActiveTaskCard(task, false, {}) }
        }
        try {
            panel.advance(30)
            val summaries = panel.nodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertTrue(summaries.any { it.contains(title) && it.contains(owner.get().getString(R.string.eta_accessibility_background_running)) })
            val summary = panel.nodes().single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.contains(title) } == true }
            // Unmerged test trees retain children; the accessibility boundary clears them.
            assertTrue(summary.config.isClearingSemantics)
            for (id in listOf(R.string.eta_task_view, R.string.overlay_pause, R.string.overlay_stop_task)) {
                val label = owner.get().getString(id)
                val node = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true }
                var parent: SemanticsNode? = node
                while (parent != null && parent.config.getOrNull(SemanticsActions.OnClick) == null) parent = parent.parent
                assertNotNull("Action remains separately reachable: $label", parent)
                assertNotEquals(summary.id, parent!!.id)
                assertTrue(node.boundsInRoot.bottom <= (panel.root() as View).height)
            }
            panel.capture("stage7_task_dark_font200")
        } finally { panel.close(); owner.pause().stop().destroy() }
    }

    @Test fun sidebarRealFingerDragStillOpensAndClosesWithBothMotionPolicies() {
        for (reduced in listOf(false, true)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            var open by mutableStateOf(false)
            val panel = Stage3TestPanel(owner.get(), reduceMotion = reduced) {
                EtaAppOverlayHost {
                    ConversationSidePaneScaffold(ConversationPaneUiState(emptyList(), null, ""), open, true,
                        { open = true }, { open = false }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) {
                        Box(Modifier.fillMaxSize()) { Text("Conversation") }
                    }
                }
            }
            try {
                panel.advance(20)
                val view = panel.root() as View
                panel.dragFinger(view.width * .08f, view.height * .5f, view.width * .92f, view.height * .5f, 50)
                assertTrue("Finger must open the pane, reduced=$reduced", open)
                panel.dragFinger(view.width * .8f, view.height * .5f, view.width * .08f, view.height * .5f, 50)
                assertFalse("Finger must close the pane, reduced=$reduced", open)
            } finally { panel.close(); owner.pause().stop().destroy() }
        }
    }

    @Test fun reducedNavigationKeepsTheCoveredPageOpaqueThroughoutTheFade() {
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        lateinit var stack: NavBackStack
        val panel = Stage3TestPanel(owner.get()) {
            stack = rememberNavBackStack<AppRoute>(AppRoute.Home)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Green)) {
                NavDisplay(stack, onBack = { stack.removeLastOrNull() }, transition = rememberEtaNavigationTransition()) {
                    entry<AppRoute.Home> { Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Red)) }
                    entry<AppRoute.Settings> { Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Blue)) }
                }
            }
        }
        try {
            panel.advance(25)
            for (push in listOf(true, false)) {
                if (push) stack.add(AppRoute.Settings) else stack.removeLastOrNull()
                var blendedFrames = 0
                repeat(12) {
                    panel.advance()
                    val bitmap = panel.bitmap()
                    try {
                        panel.draw(bitmap)
                        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                        assertTrue("Navigation must not expose the green host", android.graphics.Color.green(pixel) <= 1)
                        if (android.graphics.Color.red(pixel) > 1 && android.graphics.Color.blue(pixel) > 1) blendedFrames++
                    } finally { bitmap.recycle() }
                }
                assertTrue("Must sample the fade, not only its end", blendedFrames > 0)
            }
        } finally { panel.close(); owner.pause().stop().destroy() }
    }

    @Test fun navigationRealSwipeCancelsThenCommitsWithBothMotionPolicies() {
        for (reduced in listOf(false, true)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            lateinit var stack: NavBackStack
            val panel = Stage3TestPanel(owner.get(), reduceMotion = reduced) {
                stack = rememberNavBackStack<AppRoute>(AppRoute.Home, AppRoute.Settings)
                NavDisplay(stack, onBack = { stack.removeLastOrNull() }, transition = rememberEtaNavigationTransition()) {
                    entry<AppRoute.Home> { Box(Modifier.fillMaxSize()) { Text("Home") } }
                    entry<AppRoute.Settings>(swipeDismiss = NavSwipeDirection.LeftToRight) { Box(Modifier.fillMaxSize()) { Text("Settings") } }
                }
            }
            try {
                panel.advance(30)
                val view = panel.root() as View
                panel.dragFinger(view.width * .05f, view.height * .5f, view.width * .12f, view.height * .5f, 50)
                assertEquals(2, stack.size)
                panel.dragFinger(view.width * .05f, view.height * .5f, view.width * .95f, view.height * .5f, 50)
                assertEquals(1, stack.size)
            } finally { panel.close(); owner.pause().stop().destroy() }
        }
    }
}
