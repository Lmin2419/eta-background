package io.github.mangi.eta.ui.components

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.ui.theme.EtaMotion
import io.github.mangi.eta.ui.theme.EtaTheme
import java.time.Duration
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EtaComponentBehaviorTest {
    @Test fun rowAndSwitchThumbEachDispatchExactlyOnceAndDisabledRowDoesNotDispatch() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var calls = 0
        withPanel({
            EtaSwitchPreference("Voice", checked.value, { calls++; checked.value = it },
                enabled = enabled.value, modifier = Modifier.testTag("switch"))
        }) { panel ->
            panel.tap(panel.tag("switch"), fromRight = 42f)
            assertEquals(1, calls)
            assertTrue(checked.value)
            panel.tap(panel.tag("switch"), fromLeft = 24f)
            assertEquals(2, calls)
            assertFalse(checked.value)
            enabled.value = false
            panel.advance()
            panel.tap(panel.tag("switch"), fromRight = 42f)
            panel.tap(panel.tag("switch"), fromLeft = 24f)
            assertEquals(2, calls)
        }
    }

    @Test fun radioAndCheckboxRetainOneAccessibleSelectionAction() {
        var radioCalls = 0
        var checkCalls = 0
        withPanel({
            Column {
                EtaRadioButtonPreference("Model", true, { radioCalls++ }, modifier = Modifier.testTag("radio"))
                EtaCheckboxPreference("Tool", false, { checkCalls++ }, modifier = Modifier.testTag("check"))
            }
        }) { panel ->
            panel.tap(panel.tag("radio"), fromRight = 28f)
            panel.tap(panel.tag("check"), fromRight = 28f)
            assertEquals(1, radioCalls)
            assertEquals(1, checkCalls)
            assertEquals(2, panel.nodes().count { it.config.getOrNull(SemanticsActions.OnClick) != null })
        }
    }

    @Test fun loadingButtonKeepsItsWidthAndCannotDispatch() {
        val loading = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var calls = 0
        withPanel({
            EtaButton("Save changes", { calls++ }, Modifier.testTag("save"),
                enabled = enabled.value, loading = loading.value)
        }) { panel ->
            val width = panel.tag("save").boundsInRoot.width
            assertTrue(panel.tag("save").boundsInRoot.height >= 48f)
            panel.tap(panel.tag("save"))
            assertEquals(1, calls)
            loading.value = true
            panel.advance()
            assertEquals(width, panel.tag("save").boundsInRoot.width, 0.1f)
            val spinner = panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Running") == true }
            assertEquals(16f, spinner.boundsInRoot.width, 0.1f)
            assertEquals(16f, spinner.boundsInRoot.height, 0.1f)
            panel.tap(panel.tag("save"))
            assertEquals(1, calls)
            loading.value = false
            enabled.value = false
            panel.advance()
            panel.tap(panel.tag("save"))
            assertEquals(1, calls)
        }
    }

    @Test fun fullWidthButtonCentersItsLabel() {
        withPanel({
            EtaButton("Import selected", {}, Modifier.fillMaxWidth().testTag("wide-button"))
        }) { panel ->
            val button = panel.tag("wide-button").boundsInRoot
            val label = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Import selected" } == true }.boundsInRoot
            assertTrue(label.width < button.width)
            assertEquals(button.center.x, label.center.x, 0.5f)
            assertEquals(button.center.y, label.center.y, 0.5f)
        }
    }

    @Test fun searchEditingImeAndClearKeepCallerOwnedState() {
        val query = mutableStateOf("")
        val edits = mutableListOf<String>()
        val searches = mutableListOf<String>()
        withPanel({
            EtaSearchField(query.value, { query.value = it; edits += it }, "Search models",
                onSearch = { searches += it })
        }) { panel ->
            val input = panel.nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }
            assertTrue(input.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("eta")))
            panel.advance()
            assertEquals("eta", query.value)
            val ime = panel.nodes().first { it.config.getOrNull(SemanticsActions.OnImeAction) != null }
            ime.config[SemanticsActions.OnImeAction].action!!.invoke()
            assertEquals(listOf("eta"), searches)
            panel.clickDescription("Clear search")
            assertEquals("", query.value)
            assertEquals(listOf("eta", ""), edits)
        }
    }

    @Test fun segmentedControlChangesOnlyTheSelectedIndex() {
        val selected = mutableIntStateOf(0)
        val enabled = mutableStateOf(true)
        val calls = mutableListOf<Int>()
        withPanel({
            EtaSegmented(listOf("Configuration", "Models"), selected.intValue,
                { calls += it; selected.intValue = it }, enabled = enabled.value)
        }) { panel ->
            panel.clickText("Models")
            assertEquals(listOf(1), calls)
            assertEquals(1, selected.intValue)
            val tabs = panel.nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
            assertEquals(2, tabs.size)
            assertEquals(listOf(false, true), tabs.map { it.config[SemanticsProperties.Selected] })
            enabled.value = false
            panel.advance()
            panel.tap(tabs.first())
            assertEquals(listOf(1), calls)
        }
    }

    @Test fun collapsingAndCompactHeadersReuseScrollStateAndKeepBackAction() {
        lateinit var behavior: ScrollBehavior
        val compact = mutableStateOf(false)
        var backCalls = 0
        withPanel({
            behavior = MiuixScrollBehavior()
            AdaptiveTopAppBar("Settings", Modifier.testTag("bar"), scrollBehavior = behavior,
                compact = compact.value, navigationIcon = { MiuixBackButton({ backCalls++ }) })
        }) { panel ->
            val expandedHeight = panel.tag("bar").boundsInRoot.height
            val expansion = -behavior.state.heightOffsetLimit
            assertTrue(expansion > 0f)
            behavior.nestedScrollConnection.onPreScroll(Offset(0f, -1000f), NestedScrollSource.UserInput)
            behavior.nestedScrollConnection.onPostScroll(Offset(0f, -120f), Offset.Zero, NestedScrollSource.UserInput)
            panel.advance()
            val collapsedHeight = panel.tag("bar").boundsInRoot.height
            assertEquals(expandedHeight - expansion, collapsedHeight, 1f)
            assertEquals(1f, behavior.state.collapsedFraction, 0.001f)
            compact.value = true
            panel.advance()
            assertEquals(collapsedHeight, panel.tag("bar").boundsInRoot.height, 1f)
            assertEquals(0f, behavior.state.heightOffset, 0.1f)
            compact.value = false
            panel.advance()
            assertEquals(1f, behavior.state.collapsedFraction, 0.001f)
            panel.clickDescription("Back")
            assertEquals(1, backCalls)
            behavior.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 1000f), NestedScrollSource.UserInput)
            panel.advance()
            assertEquals(expandedHeight, panel.tag("bar").boundsInRoot.height, 1f)
        }
    }

    @Test fun dialogActionsWrapAtLargeTextAndKeepTheirCallbacks() {
        var cancel = 0
        var confirm = 0
        withPanel({
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.width(240.dp)) {
                    MiuixDialogActions("Remove selected entries", { cancel++ }, { confirm++ },
                        cancelText = "Keep entries", destructive = true)
                }
            }
        }) { panel ->
            val buttons = panel.nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }
            assertEquals(2, buttons.size)
            assertFalse(buttons[0].boundsInRoot.overlaps(buttons[1].boundsInRoot))
            buttons.forEach { assertTrue(it.boundsInRoot.width <= 240f); assertTrue(it.boundsInRoot.height >= 44f) }
            panel.clickText("Keep entries")
            panel.clickText("Remove selected entries")
            assertEquals(1, cancel)
            assertEquals(1, confirm)
        }
    }

    @Test fun overlayDialogRetainsSeparateRequestAndFinishedCallbacks() {
        val show = mutableStateOf(true)
        var requests = 0
        var finished = 0
        withPanel({
            Scaffold {
                Box(Modifier.fillMaxSize())
                EtaOverlayDialog(show.value, title = "Confirm", onDismissRequest = { requests++; show.value = false },
                    onDismissFinished = { finished++ }) { EtaButton("Accept", {}) }
            }
        }) { panel ->
            assertEquals(0, requests)
            assertEquals(0, finished)
            panel.clickText("Close")
            panel.advance(90)
            assertEquals(1, requests)
            assertEquals(1, finished)
        }
    }

    @Test fun sheetDismissibilityAndFinishedCallbackRemainOwnedByTheHost() {
        val show = mutableStateOf(true)
        val allow = mutableStateOf(false)
        var requests = 0
        var finished = 0
        withPanel({
            Scaffold {
                Box(Modifier.fillMaxSize())
                EtaSheet(show.value, title = "Models", allowDismiss = allow.value,
                    onDismissRequest = { requests++; show.value = false },
                    onDismissFinished = { finished++ }) { EtaButton("Import", {}) }
            }
        }) { panel ->
            // 不允许关闭时，「关闭」按钮不可用。
            assertTrue(panel.clickableFor("Close").config.contains(SemanticsProperties.Disabled))
            allow.value = true
            panel.advance()
            panel.clickText("Close")
            panel.advance(90)
            assertEquals(1, requests)
            assertEquals(1, finished)
        }
    }

    @Test fun toastActionDoesNotReplaceTheThreeSecondDismissal() {
        val message = mutableStateOf<String?>("Saved")
        var actions = 0
        var dismissed = 0
        withPanel({
            EtaToast(message.value, { dismissed++; message.value = null }, actionLabel = "Undo", onAction = { actions++ })
        }) { panel ->
            assertEquals(0, dismissed)
            panel.clickText("Undo")
            assertEquals(1, actions)
            assertEquals(0, dismissed)
            panel.advance(180)
            assertEquals(1, dismissed)
            assertNull(message.value)
            panel.advance(90)
            assertEquals(1, dismissed)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h896dp-xhdpi")
    fun narrowLargeTextDialogKeepsItsTitleAndScreenMargins() {
        withPanel({
            CompositionLocalProvider(LocalDensity provides Density(2f, 2f)) {
                Scaffold {
                    Box(Modifier.fillMaxSize())
                    EtaOverlayDialog(true, title = "Remove selected models?", modifier = Modifier.testTag("dialog"),
                        onDismissRequest = {}) {
                        MiuixDialogActions("Remove models", {}, {}, cancelText = "Keep models", destructive = true)
                    }
                }
            }
        }) { panel ->
            val dialog = panel.tag("dialog")
            assertEquals("Remove selected models?", dialog.config[SemanticsProperties.PaneTitle])
            // COUI 底部对话框：左右各留至少 8dp，不贴屏幕边。
            assertTrue(dialog.boundsInRoot.width <= (320f - 16f) * 2f + 1f)
            assertTrue(dialog.boundsInRoot.left >= 16f - 1f)
            assertTrue(panel.nodes().any {
                it.config.getOrNull(SemanticsProperties.Heading) != null &&
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Remove selected models?" } == true
            })
            val buttons = panel.nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }
            assertEquals(2, buttons.size)
            assertFalse(buttons[0].boundsInRoot.overlaps(buttons[1].boundsInRoot))
        }
    }

    private fun withPanel(content: @Composable () -> Unit, block: (Panel) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Panel(controller.get(), content)
        try { panel.advance(); block(panel) }
        finally { panel.close(); controller.pause().stop().destroy() }
    }

    private class Panel(activity: ComponentActivity, content: @Composable () -> Unit) {
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
        private val recomposer = Recomposer(scope.coroutineContext)
        private val view = ComposeView(activity)
        init {
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            view.setParentCompositionContext(recomposer)
            view.setContent {
                AgentAppTheme(AppearanceSettings(), false) {
                    EtaTheme(colors = EtaTheme.colors, motion = EtaMotion(true)) {
                        CompositionLocalProvider(LocalDensity provides Density(1f)) {
                            Column(Modifier.fillMaxSize()) { content() }
                        }
                    }
                }
            }
            activity.setContentView(view)
        }
        private fun root(): ViewRootForTest = findRoot(view) ?: error("No Compose root")
        private fun findRoot(view: View): ViewRootForTest? {
            if (view is ViewRootForTest) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findRoot(view.getChildAt(i))?.let { return it }
            return null
        }
        /** 主窗口加上对话框等子窗口（COUI 对话框是独立窗口）。 */
        private fun roots(): List<ViewRootForTest> {
            val main = root()
            val others = runCatching {
                val manager = org.robolectric.util.ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
                val views = org.robolectric.util.ReflectionHelpers.getField<List<View>>(manager, "mViews")
                views.mapNotNull { findRoot(it) }.filter { it !== main }
            }.getOrDefault(emptyList())
            return listOf(main) + others
        }
        fun nodes(): List<SemanticsNode> {
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            return roots().flatMap { root ->
                if (root !== root()) runCatching { root.measureAndLayoutForTest() }
                all(root.semanticsOwner.unmergedRootSemanticsNode)
            }
        }
        fun tag(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == value }
        fun clickText(text: String) = clickAncestor(nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true })
        fun clickDescription(text: String) = clickAncestor(nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true })
        fun clickableFor(text: String): SemanticsNode {
            var node: SemanticsNode? = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true }
            while (node != null && node.config.getOrNull(SemanticsActions.OnClick) == null) node = node.parent
            return requireNotNull(node)
        }
        private fun clickAncestor(node: SemanticsNode) {
            var clickable: SemanticsNode? = node
            while (clickable != null && clickable.config.getOrNull(SemanticsActions.OnClick) == null) clickable = clickable.parent
            requireNotNull(clickable).config[SemanticsActions.OnClick].action!!.invoke()
            advance()
        }
        fun tap(node: SemanticsNode, fromRight: Float? = null, fromLeft: Float? = null) {
            val bounds = node.boundsInRoot
            val x = fromRight?.let { bounds.right - it } ?: fromLeft?.let { bounds.left + it } ?: bounds.center.x
            val now = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { i, action ->
                val event = MotionEvent.obtain(now, now + i * 20L, action, x, bounds.center.y, 0)
                (root() as View).dispatchTouchEvent(event)
                event.recycle()
            }
            advance()
        }
        fun advance(frames: Int = 30) = repeat(frames) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            clock.sendFrame(SystemClock.uptimeMillis() * 1_000_000L)
            root().measureAndLayoutForTest()
        }
        fun close() { view.disposeComposition(); recomposer.cancel(); scope.cancel() }
    }
}
