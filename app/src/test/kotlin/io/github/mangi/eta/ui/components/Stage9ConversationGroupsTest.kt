package io.github.mangi.eta.ui.components

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.ui.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Stage9ConversationGroupsTest {
    private fun row(id: String, title: String, group: String? = null, pin: Long? = null, time: Long = 1) =
        ConversationSummaryUi(id, title, "", "", mode = ConversationModeUi.Chat, groupId = group, pinnedAt = pin,
            isPinned = pin != null, updatedAtMillis = time)
    private fun fixture() = ConversationPaneUiState(listOf(
        row("p1", "置顶旧", "work", 10), row("p2", "置顶新", pin = 20),
        row("w1", "工作一", "work", time = 3).copy(isActiveRun = true), row("w2", "工作二", "work", time = 2),
        row("l1", "折叠会话", "learn"), row("plain", "普通会话")), null, "",
        listOf(ConversationGroupUi("work", "工作", 0, conversationCount = 3),
            ConversationGroupUi("learn", "学习", 1, collapsed = true, conversationCount = 1)))
    @Composable private fun Body(state: ConversationPaneUiState, actions: PaneManagementActions) {
        EtaAppOverlayHost { CompositionLocalProvider(LocalPaneManagement provides actions) {
            ConversationPanePanel(state, 360.dp, rememberLazyListState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
    }
    private fun Stage3TestPanel.windowNodes(): List<Pair<androidx.compose.ui.platform.ViewRootForTest, SemanticsNode>> {
        val manager = org.robolectric.util.ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
        val views = org.robolectric.util.ReflectionHelpers.getField<List<android.view.View>>(manager, "mViews")
        fun roots(view: android.view.View): List<androidx.compose.ui.platform.ViewRootForTest> = when (view) {
            is androidx.compose.ui.platform.ViewRootForTest -> listOf(view)
            is android.view.ViewGroup -> (0 until view.childCount).flatMap { roots(view.getChildAt(it)) }
            else -> emptyList()
        }
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        return views.flatMap(::roots).flatMap { root ->
            root.measureAndLayoutForTest()
            nodes(root.semanticsOwner.unmergedRootSemanticsNode).map { root to it }
        }
    }
    private fun SemanticsNode.matches(value: String) = config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == value } ||
        config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(value)
    private fun Stage3TestPanel.find(value: String) = windowNodes().lastOrNull { it.second.matches(value) }?.second
    private fun Stage3TestPanel.require(value: String) = find(value) ?: error("Missing $value")
    private fun Stage3TestPanel.press(value: String, long: Boolean = false) {
        val (root, node) = windowNodes().lastOrNull { it.second.matches(value) } ?: error("Missing $value")
        val center = node.boundsInRoot.center
        val time = SystemClock.uptimeMillis()
        fun touchWindow(action: Int) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, center.x, center.y, 0)
            (root as android.view.View).dispatchTouchEvent(event); event.recycle()
        }
        touchWindow(MotionEvent.ACTION_DOWN); advance(if (long) 45 else 2)
        touchWindow(MotionEvent.ACTION_UP); advance(30)
        if (long && windowNodes().any { it.second.matches("多选") }) press("多选")
    }
    private fun inThemes(block: (Stage3TestPanel, Boolean) -> Unit, actions: PaneManagementActions = PaneManagementActions()) {
        SettingsDataStore.init(RuntimeEnvironment.getApplication())
        for (dark in listOf(false, true)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val panel = Stage3TestPanel(owner.get(), dark) { Body(fixture(), actions) }
            try { panel.advance(40); block(panel, dark) }
            finally { panel.close(); owner.pause().stop().destroy() }
        }
    }
    @Test fun sidebarShowsPinsThenGroupsWithCollapsedMembersHidden() = inThemes({ panel, dark ->
        panel.capture("stage9c-sidebar-${if (dark) "dark" else "light"}")
        assertNotNull(panel.find("工作 · 2")); assertNotNull(panel.find("学习 · 1"))
        assertNull(panel.find("折叠会话"))
        assertTrue(panel.require("置顶新").boundsInRoot.top < panel.require("置顶旧").boundsInRoot.top)
        assertTrue(panel.require("置顶旧").boundsInRoot.top < panel.require("工作 · 2").boundsInRoot.top)
        assertEquals(1, panel.nodes().count { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text == "置顶旧" } })
    })
    @Test fun fingerLongPressAndMoveSheetKeepTheSelectedConversationIds() {
        val moved = mutableListOf<Pair<Set<String>, String?>>()
        inThemes({ panel, dark ->
            panel.press("工作一", long = true); panel.press("更多")
            assertNotNull(panel.find("置顶")); panel.press("移到分组")
            panel.capture("stage9c-move-${if (dark) "dark" else "light"}")
            assertNotNull(panel.find("新建分组")); assertNotNull(panel.find("移出分组"))
            panel.press("学习 · 1")
            assertEquals(setOf("w1") to "learn", moved.last())
        }, PaneManagementActions(moveToGroup = { ids, group -> moved += ids to group }))
    }
    @Test fun fingerLongPressGroupOffersReorderRenameAndSafeDeleteConfirmation() {
        val deleted = mutableListOf<String>()
        inThemes({ panel, dark ->
            panel.press("工作 · 2", long = true)
            panel.capture("stage9c-group-actions-${if (dark) "dark" else "light"}")
            assertNotNull(panel.find("重命名分组")); assertNotNull(panel.find("上移")); assertNotNull(panel.find("下移"))
            panel.press("删除分组")
            assertNotNull(panel.find("删除分组后，里面的 3 个会话会回到列表，不会被删除"))
            panel.press("删除")
            assertEquals("work", deleted.last())
        }, PaneManagementActions(deleteGroup = { deleted += it }))
    }
    @Test fun multiSelectPinsAndAllPinnedSelectionUnpins() {
        val changes = mutableListOf<Pair<Set<String>, Boolean>>()
        inThemes({ panel, _ ->
            panel.press("工作一", long = true); panel.press("工作二"); panel.press("更多")
            panel.press("置顶")
            assertEquals(setOf("w1", "w2") to true, changes.last())
            panel.press("置顶旧", long = true); panel.press("置顶新"); panel.press("更多")
            assertNotNull(panel.find("取消置顶")); panel.press("取消置顶")
            assertEquals(setOf("p1", "p2") to false, changes.last())
        }, PaneManagementActions(pin = { ids, value -> changes += ids to value }))
    }
    @Test fun newGroupKeepsSelectionAndDisplaysInlineValidationWithoutClosing() {
        val created = mutableListOf<Pair<String, Set<String>>>()
        inThemes({ panel, _ ->
            panel.press("工作一", long = true); panel.press("更多"); panel.press("移到分组"); panel.press("新建分组")
            panel.press("保存"); assertNotNull(panel.find("请输入分组名"))
            val field = panel.windowNodes().last { it.second.config.getOrNull(SemanticsActions.SetText) != null }.second
            field.config[SemanticsActions.SetText].action!!(androidx.compose.ui.text.AnnotatedString(" 新工作 "))
            panel.advance(10); panel.press("保存")
            assertEquals("新工作" to setOf("w1"), created.last())
        }, PaneManagementActions(createGroup = { name, ids ->
            if (name.isBlank()) "请输入分组名" else { created += name.trim() to ids; null }
        }))
    }
    @Test fun groupRenameAndReorderCallbacksKeepStableGroupIds() {
        val renamed = mutableListOf<Pair<String, String>>()
        val reordered = mutableListOf<Pair<String, Int>>()
        inThemes({ panel, _ ->
            panel.press("学习 · 1", long = true); panel.press("上移")
            assertEquals("learn" to -1, reordered.last())
            panel.press("工作 · 2", long = true); panel.press("重命名分组")
            val field = panel.windowNodes().last { it.second.config.getOrNull(SemanticsActions.SetText) != null }.second
            field.config[SemanticsActions.SetText].action!!(androidx.compose.ui.text.AnnotatedString("新名称"))
            panel.advance(10); panel.press("保存")
            assertEquals("work" to "新名称", renamed.last())
        }, PaneManagementActions(renameGroup = { id, name -> renamed += id to name; null },
            reorderGroup = { id, direction -> reordered += id to direction }))
    }

}
