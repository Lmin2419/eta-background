package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.remote.DesktopSessionRow
import io.github.mangi.eta.agent.remote.DesktopSessionsState
import io.github.mangi.eta.agent.remote.DesktopTurnEvent
import io.github.mangi.eta.agent.runtime.TaskNotificationState
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 2026111601 反馈：任务胶囊点任务直达对话、电脑任务进首页、「下一轮」看得见。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeBackgroundItemsTest {
    private fun row(id: String, running: Boolean = true, subagent: Boolean = false, parent: String? = null, archived: Boolean = false) =
        DesktopSessionRow(id, "电脑会话 $id", 0L, running, blank = false, live = true, cwd = null, parentId = parent,
            subagent = subagent, workspaceId = null, pinned = false, archived = archived)

    private fun turn(session: String, reason: String) = DesktopTurnEvent(session, 3, reason, null, "", 0L)

    /** 2026111625：后台屏任务也先打开它的对话；只有找不到对话时才退回画面预览。 */
    @Test fun tasksOpenTheirConversationAndOnlyOrphanBackgroundAppTasksOpenThePreview() {
        assertFalse(TaskNotificationState("r1", "c1", "整理纪要", AgentOverlayStatus.Reasoning).opensPreview())
        assertFalse(TaskNotificationState("r2", "c2", "点外卖", AgentOverlayStatus.Reasoning, targetPackage = "com.sankuai.meituan").opensPreview())
        assertTrue(TaskNotificationState("r3", null, "点外卖", AgentOverlayStatus.Reasoning, targetPackage = "com.sankuai.meituan").opensPreview())
    }

    @Test fun runningDesktopSessionsAreShownOnlyWhileConnectedAndFresh() {
        val state = DesktopSessionsState(rows = listOf(row("a"), row("b", running = false), row("c", subagent = true),
            row("d", parent = "a"), row("e", archived = true)), loaded = true)
        assertEquals(listOf("a"), desktopRunningSessions(state, connected = true).map { it.id })
        assertTrue("offline: no stale running sessions", desktopRunningSessions(state, connected = false).isEmpty())
        assertTrue("cached list is not trusted", desktopRunningSessions(state.copy(cached = true), connected = true).isEmpty())
    }

    /** 2026111603 反馈：电脑会话页也要看到手机上在跑的任务；正在看的那个电脑会话不进胶囊。 */
    @Test fun theDesktopPageListsPhoneTasksAndOtherDesktopSessions() {
        val phone = TaskNotificationState("r1", "c1", "整理纪要", AgentOverlayStatus.Reasoning)
        val done = TaskNotificationState("r2", "c2", "已完成", AgentOverlayStatus.ResultReady, completed = true)
        val desktop = DesktopSessionsState(rows = listOf(row("a"), row("b")), loaded = true)
        val items = backgroundItems(listOf(phone, done), currentConversationId = null, desktop = desktop, connected = true,
            currentDesktopSessionId = "a")
        assertEquals(listOf("整理纪要", "电脑会话 b"), items.map { it.title })
        // 手机对话页：正在看的对话不列，电脑会话全列。
        assertEquals(listOf("电脑会话 a", "电脑会话 b"),
            backgroundItems(listOf(phone), "c1", desktop, connected = true, currentDesktopSessionId = null).map { it.title })
    }

    @Test fun desktopTurnsBecomeDoneOrFailedNotices() {
        val rows = listOf(row("a"), row("sub", subagent = true))
        val done = desktopDoneNotice(turn("a", "completed"), rows)!!
        assertTrue(done.desktop); assertFalse(done.failed); assertEquals("a", done.target); assertEquals("电脑会话 a", done.title)
        assertTrue(desktopDoneNotice(turn("a", "error"), rows)!!.failed)
        assertNull("stopped by the user", desktopDoneNotice(turn("a", "aborted"), rows))
        assertNull("subagents report inside their parent", desktopDoneNotice(turn("sub", "completed"), rows))
    }

    @Test fun tappingTheDoneNoticeOpensItsTarget() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val item = DoneNoticeItem("desktop:a:3", "电脑会话 a", failed = false, desktop = true, target = "a")
        var opened: DoneNoticeItem? = null
        val panel = Stage3TestPanel(controller.get()) { DoneNoticeCapsule(item) { opened = it } }
        try {
            panel.advance(20)
            val label = controller.get().getString(R.string.eta_task_done_notice, "电脑会话 a")
            panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true })
            assertEquals(item, opened)
        } finally { panel.close(); controller.pause().stop().destroy() }
    }

    @Test fun theTitleCapsuleShowsTheModelQueuedForTheNextTurn() {
        io.github.mangi.eta.data.datastore.SettingsDataStore.init(org.robolectric.RuntimeEnvironment.getApplication())
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val queued = Stage3Models.providerGroups.single().models.last()
        val panel = Stage3TestPanel(controller.get()) {
            HomeConversationTopBar(Stage3Models.copy(queuedModel = queued), true, false, AgentContextUsageUi(1, 128000), true, true, null,
                {}, {}, {}, {}, {}, {}, {}, {}, conversationTitle = "整理纪要")
        }
        try {
            panel.advance(20)
            val nextTurn = controller.get().getString(R.string.eta_model_next_turn)
            assertTrue(panel.nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(nextTurn) && it.text.contains("Small") } == true })
        } finally { panel.close(); controller.pause().stop().destroy() }
    }
}
