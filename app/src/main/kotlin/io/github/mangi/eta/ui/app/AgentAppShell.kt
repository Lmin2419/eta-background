package io.github.mangi.eta.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.CompositionLocalProvider
import io.github.mangi.eta.ui.components.LocalChatToast
import io.github.mangi.eta.ui.components.LocalPaneWorkspace
import io.github.mangi.eta.ui.components.PaneWorkspaceActions
import io.github.mangi.eta.ui.components.EtaToast
import io.github.mangi.eta.ui.components.HomeTaskPaneLayout
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.itos.coui.backdrop.couiBackdrop
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.components.AdaptiveTopAppBar
import io.github.mangi.eta.ui.components.CharacterAvatar
import io.github.mangi.eta.ui.components.ConversationSidePaneScaffold
import io.github.mangi.eta.ui.components.MiuixBackButton
import io.github.mangi.eta.ui.components.RoleplayChatUi
import io.github.mangi.eta.ui.components.TopBarBackdrop
import io.github.mangi.eta.ui.components.captureForTopBar
import io.github.mangi.eta.ui.components.rememberTopBarBackdrop
import io.github.mangi.eta.ui.components.topBarContainerColor
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.navigation.AppRoute
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * Agent App 统一壳层。
 *
 * - 负责全局 Scaffold、状态栏/横向安全边距、顶层工具栏。
 * - 首页工具栏只保留历史入口与溢出菜单，保持聊天舞台干净。
 * - 非首页子路由统一提供返回按钮与标题，避免每个页面各自像独立设置页。
 * - Settings 由标准二级页骨架自己提供 TopAppBar，壳层在此路由不重复绘制。
 */
@Composable
internal fun AgentAppShell(
    currentRoute: AppRoute?,
    isCurrentRoute: Boolean,
    conversationPaneState: ConversationPaneUiState?,
    isConversationPaneOpen: Boolean,
    onBack: () -> Unit,
    onOpenConversationPane: () -> Unit,
    onDismissConversationPane: () -> Unit,
    onSearchConversations: (String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenCommonTasks: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    modifier: Modifier = Modifier,
    homeCharacter: RoleplayChatUi? = null,
    homeTopBar: (@Composable () -> Unit)? = null,
    homeSidePanel: (@Composable () -> Unit)? = null,
    onDismissHomeSidePanel: () -> Unit = {},
    runningTaskCount: Int = 0,
    permissionsPending: Int = 0,
    onOpenDesktop: (() -> Unit)? = null,
    desktopRunning: Int = 0,
    onOpenDesktopSession: (String) -> Unit = {},
    paneManagement: io.github.mangi.eta.ui.components.PaneManagementActions = io.github.mangi.eta.ui.components.PaneManagementActions(),
    onOpenCharacter: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var toast by remember { mutableStateOf<String?>(null) }
    // 外壳只承载首页（对话页的玻璃控件）和终端页（GlassPageTopBar）；两者都不用 Miuix 顶栏和它的折叠滚动行为。
    val backdrop = io.github.mangi.eta.ui.components.trackRenderSource("顶栏(Miuix)", rememberTopBarBackdrop())
    // 对话页唯一的 COUI 背景源：挂在消息区上（AgentChatBody），顶部液态玻璃控件与输入框都从这里采样。
    val chatCouiSource = if (currentRoute is AppRoute.Home && homeTopBar != null && LocalBlurEnabled.current)
        com.itos.coui.backdrop.rememberCouiBackdrop(diagnosticName = "chat-page") else null
    io.github.mangi.eta.ui.components.trackRenderSource("对话页(COUI)", chatCouiSource)
    val pageContent: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (currentRoute is AppRoute.Home) EtaTheme.colors.canvas else MiuixTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
            topBar = {
                if (currentRoute is AppRoute.Home && homeTopBar != null) io.github.mangi.eta.ui.components.ChatScrollEdge(backdrop) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        io.github.mangi.eta.ui.components.coui.LocalEtaCouiBackdrop provides chatCouiSource,
                    ) { homeTopBar() }
                }
                // 终端页：与对话页、电脑会话页同一套液态玻璃顶部（返回圆钮 + 标题胶囊）。
                else if (currentRoute is AppRoute.Terminal) io.github.mangi.eta.ui.components.GlassPageTopBar(
                    title = stringResource(R.string.route_terminal), subtitle = null, onBack = onBack, onTitle = null,
                    sourceName = "终端顶部(COUI)", balanceTrailing = true,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .captureForTopBar(backdrop),
            ) {
                // 背景源挂在对话页的消息区上（AgentChatBody），这里只往下传。
                CompositionLocalProvider(io.github.mangi.eta.ui.components.coui.LocalChatCouiSource provides chatCouiSource) {
                    content(padding)
                }
            }
        }
    }

    // 侧栏里直接列出电脑上最近的会话（与本机对话按时间混排，左侧小电脑图标区分）。
    val desktopList by io.github.mangi.eta.agent.remote.DesktopBridge.sessions.collectAsState()
    val desktopServer by io.github.mangi.eta.agent.remote.DesktopBridge.server.collectAsState()
    val desktopLink by io.github.mangi.eta.agent.remote.DesktopBridge.client.state.collectAsState()
    val desktopApprovals by io.github.mangi.eta.agent.remote.DesktopBridge.approvals.collectAsState()
    val desktopQuestions by io.github.mangi.eta.agent.remote.DesktopBridge.questions.collectAsState()
    val desktopUnread by io.github.mangi.eta.agent.remote.DesktopBridge.unread.collectAsState()
    val paneDesktop = remember(desktopList, desktopApprovals, desktopQuestions, desktopUnread, onOpenDesktop != null) {
        if (onOpenDesktop == null) emptyList() else {
            val needsYou = desktopApprovals.mapTo(HashSet()) { it.sessionId } + desktopQuestions.map { it.sessionId }
            desktopList.rows.filter { with(io.github.mangi.eta.ui.screens.desktop.DesktopListing) { it.isListed() } }
                .sortedByDescending { it.updatedAt }.take(8).map { row ->
                    io.github.mangi.eta.ui.components.PaneDesktopSession(row.id, row.title.orEmpty(), row.updatedAt, unread = row.id in desktopUnread, status = when {
                        row.id in needsYou -> io.github.mangi.eta.ui.model.TaskVisualStatus.Attention
                        row.running -> io.github.mangi.eta.ui.model.TaskVisualStatus.Running
                        else -> null
                    })
                }
        }
    }
    CompositionLocalProvider(io.github.mangi.eta.ui.components.LocalPaneManagement provides paneManagement, LocalChatToast provides { toast = it }, LocalPaneWorkspace provides PaneWorkspaceActions(
        onNewConversation, onOpenCommonTasks, onOpenTerminal, onOpenBrowser,
        runningTaskCount, permissionsPending, onOpenDesktop, desktopRunning,
        desktopSessions = paneDesktop,
        desktopName = desktopServer?.host,
        desktopOnline = desktopLink is io.github.mangi.eta.agent.remote.DesktopBridgeClient.State.Connected,
        onOpenDesktopSession = onOpenDesktopSession,
    )) {
    Box(modifier = modifier.fillMaxSize()) {
        if (conversationPaneState != null && currentRoute is AppRoute.Home) {
            ConversationSidePaneScaffold(
                state = conversationPaneState,
                visible = isConversationPaneOpen,
                backHandlerEnabled = isCurrentRoute,
                onOpen = onOpenConversationPane,
                onDismiss = onDismissConversationPane,
                onSearchChange = onSearchConversations,
                onConversationSelected = onSelectConversation,
                onConversationRename = onConversationRename,
                onConversationExport = onConversationExport,
                onConversationDelete = onConversationDelete,
                onOpenSettings = onOpenSettings,
                onOpenModelProviders = onOpenModelProviders,
                onOpenTools = onOpenTools,
                onOpenSkills = onOpenSkills,
                onOpenCharacters = onOpenCharacters,
                onOpenPermissions = onOpenPermissions,
            ) {
                HomeTaskPaneLayout(homeSidePanel, onDismissHomeSidePanel, isCurrentRoute, pageContent)
            }
        } else {
            pageContent()
        }
        EtaToast(toast, { toast = null }, Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding().imePadding().padding(bottom = 112.dp))
    }
    }
}
