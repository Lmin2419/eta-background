package io.github.mangi.eta.ui.components

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.datastore.SettingsDataStore
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.ui.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "en-rUS-w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConversationBatchGestureTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private val ctx: android.content.Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        SettingsDataStore.init(RuntimeEnvironment.getApplication())
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }
    @After fun tearDown() { controller.pause().stop().destroy() }

    private fun conv(id: String, title: String, archivedAt: Long? = null) =
        ConversationSummaryUi(id, title, "", "", mode = ConversationModeUi.Chat, archivedAt = archivedAt)

    private fun Stage3TestPanel.longPress(n: SemanticsNode) {
        val c = n.boundsInRoot.center; val t = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, c.x, c.y, t); advance(45)
        touch(MotionEvent.ACTION_UP, c.x, c.y, t); advance(30)
    }
    private fun Stage3TestPanel.tapNode(n: SemanticsNode) {
        val c = n.boundsInRoot.center; tapFinger(c.x, c.y); advance(5)
    }
    private fun Stage3TestPanel.find(text: String): SemanticsNode? = nodes().firstOrNull {
        it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true ||
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true
    }
    private fun Stage3TestPanel.require(text: String) = find(text) ?: error("missing: $text")
    private fun SemanticsNode.setInput(s: String) {
        config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(s))
    }

    @Composable
    private fun Body(
        rows: List<ConversationSummaryUi>, query: String = "", openId: String? = null,
        onSearch: (String) -> Unit = {}, onOpen: (String) -> Unit = {},
        onArchive: (Set<String>) -> ConversationArchiveChange = { ConversationArchiveChange(it, 0, 42L) },
        onUnarchive: (Set<String>, Long?) -> Int = { ids, _ -> ids.size },
    ) {
        EtaAppOverlayHost {
            CompositionLocalProvider(
                LocalPaneManagement provides PaneManagementActions(
                    delete = { _, c -> c(0) }, archive = onArchive, unarchive = onUnarchive,
                )
            ) {
                ConversationPanePanel(
                    state = ConversationPaneUiState(rows, openId, query),
                    width = 360.dp, listState = rememberLazyListState(),
                    onSearchChange = onSearch, onConversationSelected = onOpen,
                    onConversationRename = {}, onConversationExport = {}, onConversationDelete = {},
                    onOpenSettings = {}, onOpenModelProviders = {}, onOpenTools = {},
                    onOpenSkills = {}, onOpenCharacters = {}, onOpenPermissions = {},
                )
            }
        }
    }

    @Test fun longPress_selectsOne_noOpen() {
        var opened: String? = null
        val panel = Stage3TestPanel(controller.get()) {
            Body(listOf(conv("a", "Alpha"), conv("b", "Beta")), onOpen = { opened = it })
        }
        try {
            panel.advance(25)
            panel.longPress(panel.require("Alpha"))
            assertNotNull(panel.find(ctx.getString(R.string.eta_selection_count, 1)))
            assertNull(opened)
        } finally { panel.close() }
    }

    @Test fun tapToggles_untilZero_exits() {
        val panel = Stage3TestPanel(controller.get()) {
            Body(listOf(conv("a", "Alpha"), conv("b", "Beta")))
        }
        val cancel = ctx.getString(R.string.action_cancel)
        try {
            panel.advance(25)
            panel.longPress(panel.require("Alpha"))
            panel.tapNode(panel.require("Beta"))
            assertNotNull(panel.find(ctx.getString(R.string.eta_selection_count, 2)))
            panel.tapNode(panel.require("Alpha"))
            assertNotNull(panel.find(ctx.getString(R.string.eta_selection_count, 1)))
            panel.tapNode(panel.require("Beta")); panel.advance(5)
            assertNull(panel.find(cancel))
        } finally { panel.close() }
    }

    @Test fun cancelAndBack_exitMultiSelect() {
        val panel = Stage3TestPanel(controller.get()) {
            Body(listOf(conv("a", "Alpha"), conv("b", "Beta")))
        }
        val cancel = ctx.getString(R.string.action_cancel)
        try {
            panel.advance(25)
            panel.longPress(panel.require("Alpha"))
            panel.tapNode(panel.require(cancel)); panel.advance(5)
            assertNull(panel.find(cancel))
            panel.longPress(panel.require("Alpha"))
            assertNotNull(panel.find(cancel))
            controller.get().onBackPressedDispatcher.onBackPressed(); panel.advance(30)
            assertNull(panel.find(cancel))
        } finally { panel.close() }
    }

    @Test fun searchThenSelectAll_limits() {
        val all = listOf(conv("a", "Match Alpha"), conv("b", "Beta"), conv("c", "Match Gamma"))
        var q by mutableStateOf("")
        val panel = Stage3TestPanel(controller.get()) {
            Body(all.filter { it.title.contains(q) }, q, onSearch = { q = it })
        }
        try {
            panel.advance(25)
            // 阶段 12：侧栏搜索框点圆形搜索按钮才出现。
            panel.nodes().firstOrNull { it.config.getOrNull(SemanticsActions.SetText) != null }
                ?: run { panel.nodes().first { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty().contains(controller.get().getString(io.github.mangi.eta.R.string.conversation_search_hint)) && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    .config[SemanticsActions.OnClick].action?.invoke(); panel.advance(20) }
            panel.nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }.setInput("Match")
            panel.advance(5)
            panel.longPress(panel.require("Match Alpha"))
            panel.tapNode(panel.require(ctx.getString(R.string.eta_select_all))); panel.advance(5)
            assertNotNull(panel.find(ctx.getString(R.string.eta_selection_count, 2)))
        } finally { panel.close() }
    }

    @Test fun moreDisabledInMultiSelect() {
        var openId by mutableStateOf<String?>("a")
        val panel = Stage3TestPanel(controller.get()) {
            Body(listOf(conv("a", "Alpha"), conv("b", "Beta")), openId = openId, onOpen = { openId = it })
        }
        val moreLabel = ctx.getString(R.string.eta_more)
        try {
            panel.advance(25)
            panel.longPress(panel.require("Alpha"))
            panel.tapNode(panel.require("Beta"))
            panel.tapNode(panel.require(moreLabel)); panel.advance(30)
            assertNotNull(panel.find(ctx.getString(R.string.eta_single_only)))
            for (label in listOf(R.string.action_rename, R.string.action_export)) {
                var node: SemanticsNode? = panel.require(ctx.getString(label))
                while (node != null && node.config.getOrNull(SemanticsProperties.Disabled) == null) node = node.parent
                assertNotNull("Single-only action must be disabled", node)
            }
        } finally { panel.close() }
    }

    @Test fun archive_showsCount_andUndo() {
        var rows by mutableStateOf(listOf(conv("a", "Alpha"), conv("b", "Beta")))
        var archivedIds: Set<String> = emptySet(); var unarchivedIds: Set<String> = emptySet()
        val panel = Stage3TestPanel(controller.get()) {
            Body(rows,
                onArchive = { ids ->
                    archivedIds = ids
                    rows = rows.map { if (it.id in ids) it.copy(archivedAt = 42L) else it }
                    ConversationArchiveChange(ids, 0, 42L)
                },
                onUnarchive = { ids, _ ->
                    unarchivedIds = ids
                    rows = rows.map { if (it.id in ids) it.copy(archivedAt = null) else it }
                    ids.size
                })
        }
        try {
            panel.advance(25)
            panel.longPress(panel.require("Alpha"))
            panel.tapNode(panel.require("Beta"))
            panel.tapNode(panel.require(ctx.getString(R.string.eta_archive))); panel.advance(5)
            assertEquals(setOf("a", "b"), archivedIds)
            assertNotNull(panel.find(ctx.getString(R.string.eta_archived_count, 2)))
            panel.tapNode(panel.require(ctx.getString(R.string.eta_undo))); panel.advance(5)
            assertEquals(setOf("a", "b"), unarchivedIds)
        } finally { panel.close() }
    }

    @Test fun searchIncludesArchived() {
        val all = listOf(
            conv("a", "Alpha"), conv("b", "Beta", archivedAt = 42L),
            conv("c", "Gamma", archivedAt = 42L)
        )
        var q by mutableStateOf("")
        val panel = Stage3TestPanel(controller.get()) {
            Body(all.filter { it.title.contains(q) }, q, onSearch = { q = it })
        }
        try {
            panel.advance(25)
            assertNotNull(panel.find("Alpha"))
            assertNull(panel.find("Beta"))
            // 阶段 12：侧栏搜索框点圆形搜索按钮才出现。
            panel.nodes().firstOrNull { it.config.getOrNull(SemanticsActions.SetText) != null }
                ?: run { panel.nodes().first { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty().contains(controller.get().getString(io.github.mangi.eta.R.string.conversation_search_hint)) && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    .config[SemanticsActions.OnClick].action?.invoke(); panel.advance(20) }
            panel.nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }.setInput("Beta")
            panel.advance(5)
            assertNotNull(panel.find("Beta"))
        } finally { panel.close() }
    }

    @Test fun archivedSubpageSupportsUnarchiveAndBackAndUndoExpires() {
        var rows by mutableStateOf(listOf(conv("a", "Alpha"), conv("b", "Beta", 42L)))
        var restored = emptySet<String>()
        val panel = Stage3TestPanel(controller.get()) {
            Body(rows, onArchive = { ids ->
                rows = rows.map { if (it.id in ids) it.copy(archivedAt = 84L) else it }
                ConversationArchiveChange(ids, 0, 84L)
            }, onUnarchive = { ids, _ ->
                restored = ids; rows = rows.map { if (it.id in ids) it.copy(archivedAt = null) else it }; ids.size
            })
        }
        try {
            panel.advance(25)
            panel.tapNode(panel.require(ctx.getString(R.string.eta_archived_count, 1)))
            assertNotNull(panel.find("Beta")); assertNull(panel.find("Alpha"))
            panel.longPress(panel.require("Beta"))
            panel.tapNode(panel.require(ctx.getString(R.string.eta_unarchive)))
            assertEquals(setOf("b"), restored)
            controller.get().onBackPressedDispatcher.onBackPressed(); panel.advance(30)
            assertNotNull(panel.find("Beta")); assertNotNull(panel.find("Alpha"))
            panel.longPress(panel.require("Alpha"))
            panel.tapNode(panel.require(ctx.getString(R.string.eta_archive)))
            assertNotNull(panel.find(ctx.getString(R.string.eta_undo)))
            panel.advance(220)
            assertNull(panel.find(ctx.getString(R.string.eta_undo)))
        } finally { panel.close() }
    }

    @Test fun screenshots_lightDark200() {
        val all = listOf(conv("a", "Alpha"), conv("b", "Beta"))
        data class Shot(val dark: Boolean, val fs: Float, val name: String)
        listOf(Shot(false, 1f, "light"), Shot(true, 1f, "dark"), Shot(false, 2f, "font200"))
            .forEach { s ->
                val p = Stage3TestPanel(controller.get(), dark = s.dark, fontScale = s.fs) { Body(all) }
                try { p.advance(25); p.longPress(p.require("Alpha")); p.capture("select_${s.name}") } finally { p.close() }
            }
    }
}
