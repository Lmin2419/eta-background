package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.*
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.ui.app.CommonTasksStore
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.screens.tasks.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Scaffold

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskAndSidebarPresentationTest {
    @org.junit.Before @org.junit.After fun clearRetainedCardsBetweenTests() {
        dismissRetainedTaskCardsForTest(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Before fun init() { SettingsDataStore.init(RuntimeEnvironment.getApplication()) }

    @Test fun completionRowsDeduplicateAndUseFinishOrderAndKnownDurations() {
        val cards = listOf(TaskNotificationState("a", "chat-a", "A", AgentOverlayStatus.ResultReady, completed = true,
            firstPublishedAtElapsedMs = 10, completedAtElapsedMs = 50, completedAtMillis = 500),
            TaskNotificationState("b", "chat-b", "B", AgentOverlayStatus.RunFailed, completed = true, completedAtMillis = 300))
        val archives = listOf(CompletedTaskUi("a", null, "old", TaskVisualStatus.Success, 500, 35, archiveSource = "assistant", archiveConversationKey = "external-a")) +
            List(25) { CompletedTaskUi("archive-$it", null, "Archived", TaskVisualStatus.Success, it.toLong()) }
        val rows = completedTaskRows(cards, archives)
        assertEquals(20, rows.size); assertEquals(listOf("a", "b"), rows.take(2).map { it.runId })
        assertEquals(35L, rows.first().durationMs); assertEquals("chat-a", rows.first().conversationId)
        assertEquals("external-a", rows.first().archiveConversationKey)
        assertNull(rows[1].durationMs); assertEquals(TaskVisualStatus.Failed, rows[1].status)
    }

    @Test fun attentionRunsAreFirstAndCompletedRunsStayOutOfActiveTab() {
        val a = TaskNotificationState("a", "chat", "A", AgentOverlayStatus.Reasoning)
        val b = a.copy(runId = "b", status = AgentOverlayStatus.ContinuationUnavailable)
        assertEquals(listOf("b", "a"), orderedActiveTasks(listOf(a, b, a.copy(runId = "c", completed = true))).map { it.runId })
    }

    @Test fun sidebarGroupsUseDatesAndExistingPinnedFlag() {
        val today = LocalDate.of(2026, 11, 6)
        fun summary(days: Long, pinned: Boolean = false) = ConversationSummaryUi("id", "Title", "", "", mode = ConversationModeUi.Chat,
            updatedAtMillis = today.minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), isPinned = pinned)
        assertEquals(listOf(1, 2, 3, 4), listOf(0L, 1L, 6L, 8L).map { conversationSection(summary(it), today) })
        assertEquals(0, conversationSection(summary(8, true), today))
    }

    @Test fun sidebarEntriesAndSelectionMoreMenuStayReachable() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val actions = mutableListOf<String>()
        val row = ConversationSummaryUi("chat", "Conversation", "", "Today", mode = ConversationModeUi.Chat)
        val panel = Stage3TestPanel(controller.get()) {
            EtaAppOverlayHost {
            CompositionLocalProvider(LocalPaneWorkspace provides PaneWorkspaceActions(onNew = { actions += "new" }, onTasks = { actions += "tasks" },
                onTerminal = { actions += "terminal" }, onBrowser = { actions += "browser" })) {
                ConversationPanePanel(ConversationPaneUiState(listOf(row), "chat", ""), 360.dp, rememberLazyListState(), {}, {},
                    { actions += "rename" }, {}, {}, {}, {}, {}, {}, { actions += "characters" }, {})
            }
            }
        }
        try {
            panel.advance(25)
            val title = controller.get().getString(R.string.eta_tasks)
            panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == title } == true })
            assertEquals(listOf("tasks"), actions)
            panel.capture("sidebar-light")
            val target = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == row.title } == true }.boundsInRoot.center
            val down = android.os.SystemClock.uptimeMillis()
            panel.touch(android.view.MotionEvent.ACTION_DOWN, target.x, target.y, down); panel.advance(45)
            panel.touch(android.view.MotionEvent.ACTION_UP, target.x, target.y, down); panel.advance(30)
            val more = panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(controller.get().getString(R.string.eta_more)) == true }.boundsInRoot.center
            panel.tapFinger(more.x, more.y); panel.advance(30)
            panel.capture("sidebar-menu-light")
            assertTrue("Sidebar sheets must have a visible host", panel.nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == controller.get().getString(R.string.action_rename) } == true })
        } finally { panel.close(); controller.pause().stop().destroy() }
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun wideTaskPanelPreservesReadableChatAndBackDismisses() {
        captureWidePanel(1280, false)
    }

    @Test @Config(qualifiers = "w840dp-h800dp-xhdpi")
    fun intermediateWidthOverlaysInsteadOfShrinkingChat() {
        captureWidePanel(840, true)
    }

    @Test @Config(qualifiers = "w900dp-h411dp-xhdpi")
    fun landscapeDarkTaskPanelStaysInsideTheConversationArea() {
        captureWidePanel(900, true, dark = true)
    }

    private fun captureWidePanel(widthDp: Int, overlay: Boolean, dark: Boolean = false) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val store = CommonTasksStore(controller.get(), scope)
        var shown by mutableStateOf(true)
        var chatWidth = 0
        val panel = Stage3TestPanel(controller.get(), dark = dark) {
            EtaAppOverlayHost {
                ConversationSidePaneScaffold(ConversationPaneUiState(emptyList(), null, ""), false, true,
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) {
                    HomeTaskPaneLayout(if (shown) ({ TasksScreen(store, { shown = false }, {}, {}, embedded = true) }) else null, { shown = false }) {
                        Box(Modifier.fillMaxSize().onSizeChanged { chatWidth = it.width }) { Stage3ChatFixture() }
                    }
                }
            }
        }
        try {
            panel.advance(40)
            val density = controller.get().resources.displayMetrics.density
            val expected = widthDp - 320 - if (overlay) 0 else 360
            assertEquals(expected.toFloat(), chatWidth / density, 1f)
            panel.capture("wide-$widthDp")
            controller.get().onBackPressedDispatcher.onBackPressed(); panel.advance(20)
            assertFalse(shown)
            assertEquals((widthDp - 320).toFloat(), chatWidth / density, 1f)
        } finally { panel.close(); scope.cancel(); controller.pause().stop().destroy() }
    }

    @Test fun liveStripFiltersConversationAndKeepsFailureUntilDismissed() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val context = controller.get()
        val failed = TaskNotificationState("failed-strip", "here", "Failure remains", AgentOverlayStatus.RunFailed, completed = true, targetPackage = "example.app")
        AgentExecutionService.completeTask(context, failed)
        AgentExecutionService.updateTasks(context, listOf(TaskNotificationState("other-strip", "other", "Other conversation", AgentOverlayStatus.Preparing, targetPackage = "example.app")))
        val panel = Stage3TestPanel(context) { EtaAppOverlayHost { ConversationLiveTasks("here", {}) } }
        fun has(text: String) = panel.nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true }
        try {
            panel.advance(230)
            assertTrue(has(failed.title)); assertFalse(has("Other conversation"))
            panel.capture("live-task-failed")
            panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(context.getString(R.string.action_close)) == true })
            panel.advance(10); assertFalse(has(failed.title))
            assertTrue(AgentExecutionService.taskSnapshots.value.any { it.runId == failed.runId })
        } finally { panel.close(); AgentExecutionService.updateTasks(context, emptyList()); controller.pause().stop().destroy() }
    }

    @Test fun liveStripSuccessDisappearsAfterThreeSecondsWithoutDismissingNotification() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val context = controller.get()
        val task = TaskNotificationState("success-strip", "here", "Successful task", AgentOverlayStatus.ResultReady, completed = true, targetPackage = "example.app")
        AgentExecutionService.completeTask(context, task)
        val panel = Stage3TestPanel(context) { EtaAppOverlayHost { ConversationLiveTasks("here", {}) } }
        fun has() = panel.nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == task.title } == true }
        try {
            panel.advance(20); assertTrue(has())
            panel.advance(200); assertFalse(has())
            assertTrue(AgentExecutionService.taskSnapshots.value.any { it.runId == task.runId })
        } finally { panel.close(); controller.pause().stop().destroy() }
    }

    @Test fun captureTaskCenterAndSidebar() {
        for (dark in listOf(false, true)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            val store = CommonTasksStore(controller.get(), scope)
            AgentExecutionService.updateTasks(controller.get(), listOf(TaskNotificationState("gallery-a", "chat", "整理今天的会议记录", AgentOverlayStatus.RunningTool("read_file")),
                TaskNotificationState("gallery-b", "chat", "查询订单状态", AgentOverlayStatus.Paused, paused = true)))
            val panel = Stage3TestPanel(controller.get(), dark) { Scaffold { TasksScreen(store, {}, {}, {}, initialTab = 0) } }
            try { panel.advance(40); panel.capture("tasks-${if (dark) "dark" else "light"}") }
            finally { panel.close(); scope.cancel(); AgentExecutionService.updateTasks(controller.get(), emptyList()); controller.pause().stop().destroy() }
        }
    }
}
