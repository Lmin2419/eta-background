package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.ui.model.ConversationGroupUi
import io.github.mangi.eta.ui.model.ConversationModeUi
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.model.TaskVisualStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 阶段 12 样张：侧栏里的电脑会话，「分组」与「混排」两种放法。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SidebarDesktopSampleTest {
    private val now = System.currentTimeMillis()
    private fun row(id: String, title: String, ago: Long, group: String? = null) =
        ConversationSummaryUi(id, title, "", "", mode = ConversationModeUi.Chat, groupId = group, updatedAtMillis = now - ago)
    private val state = ConversationPaneUiState(listOf(
        row("a", "帮我订一杯咖啡", 20 * 60_000), row("b", "整理今天的未读通知", 3 * 3600_000),
        row("c", "比较两款手机的续航", 26 * 3600_000), row("d", "周末出行计划", 4 * 86400_000),
        row("w1", "季度复盘提纲", 2 * 3600_000, "work"), row("w2", "会议纪要", 30 * 3600_000, "work"),
    ), null, "", listOf(ConversationGroupUi("work", "工作", 0, conversationCount = 2)))
    private val desktop = listOf(
        PaneDesktopSession("d1", "修复登录页的空指针", now - 5 * 60_000, TaskVisualStatus.Running),
        PaneDesktopSession("d2", "生成周报", now - 50 * 60_000, TaskVisualStatus.Attention),
        PaneDesktopSession("d3", "设计新首页", now - 5 * 3600_000),
        PaneDesktopSession("d4", "整理 CI 缓存", now - 28 * 3600_000),
    )

    private fun render(name: String, mixed: Boolean, dark: Boolean = false) {
        SettingsDataStore.init(RuntimeEnvironment.getApplication())
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val actions = PaneWorkspaceActions(onDesktop = {}, desktopSessions = desktop, desktopName = "my-mac", desktopOnline = true, desktopMixed = mixed)
        val panel = Stage3TestPanel(owner.get(), dark) {
            EtaAppOverlayHost { CompositionLocalProvider(LocalPaneWorkspace provides actions) {
                ConversationPanePanel(state, 360.dp, rememberLazyListState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            } }
        }
        try { panel.advance(40); panel.capture(name) } finally { panel.close(); owner.pause().stop().destroy() }
    }

    @Test fun grouped() = render("sidebar-desktop-grouped-light", mixed = false)
    @Test fun mixed() = render("sidebar-desktop-mixed-light", mixed = true)
}
