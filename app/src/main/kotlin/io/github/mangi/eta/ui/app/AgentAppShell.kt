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
    onLaunchKimiWeb: () -> Unit,
    kimiWebLabel: String,
    canStopKimiWeb: Boolean,
    onStopKimiWeb: () -> Unit,
    onRefreshKimiWeb: () -> Unit,
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
    showKimi: Boolean = false,
    paneManagement: io.github.mangi.eta.ui.components.PaneManagementActions = io.github.mangi.eta.ui.components.PaneManagementActions(),
    onOpenCharacter: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var toast by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = MiuixScrollBehavior()
    // Only the shell's Miuix bar measures this behaviour. Without that measurement its collapse
    // range is unbounded and it would swallow every upward drag of the page beneath it.
    val usesShellTopBar = currentRoute !is AppRoute.Settings && !(currentRoute is AppRoute.Home && homeTopBar != null)
    val backdrop = rememberTopBarBackdrop()
    val topBarColor = topBarContainerColor(backdrop)
    // 对话页正文的 COUI 背景源：顶部三个液态玻璃控件从这里采样（Android 14+）。
    val isHome = currentRoute is AppRoute.Home && homeTopBar != null
    val couiPageBackdrop = if (isHome) com.itos.coui.backdrop.rememberCouiBackdrop(diagnosticName = "chat-page") else null
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
                        io.github.mangi.eta.ui.components.coui.LocalEtaCouiBackdrop provides couiPageBackdrop,
                    ) { homeTopBar() }
                }
                else if (currentRoute !is AppRoute.Settings) {
                    TopBarBackdrop(backdrop) {
                        AgentTopBar(
                            route = currentRoute,
                            scrollBehavior = scrollBehavior,
                            color = topBarColor,
                            onBack = onBack,
                            onOpenConversationPane = onOpenConversationPane,
                            onNewConversation = onNewConversation,
                            onOpenCommonTasks = onOpenCommonTasks,
                            onOpenTerminal = onOpenTerminal,
                            onLaunchKimiWeb = onLaunchKimiWeb,
                            kimiWebLabel = kimiWebLabel,
                            canStopKimiWeb = canStopKimiWeb,
                            onStopKimiWeb = onStopKimiWeb,
                            onRefreshKimiWeb = onRefreshKimiWeb,
                            onOpenBrowser = onOpenBrowser,
                            homeCharacter = homeCharacter,
                            onOpenCharacter = onOpenCharacter,
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .captureForTopBar(backdrop)
                    .then(if (couiPageBackdrop != null) Modifier.couiBackdrop(couiPageBackdrop) else Modifier)
                    .then(if (usesShellTopBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
            ) {
                content(padding)
            }
        }
    }

    // 侧栏里直接列出电脑上最近的会话（与本机对话按时间混排，左侧小电脑图标区分）。
    val desktopList by io.github.mangi.eta.agent.remote.DesktopBridge.sessions.collectAsState()
    val desktopServer by io.github.mangi.eta.agent.remote.DesktopBridge.server.collectAsState()
    val desktopLink by io.github.mangi.eta.agent.remote.DesktopBridge.client.state.collectAsState()
    val desktopApprovals by io.github.mangi.eta.agent.remote.DesktopBridge.approvals.collectAsState()
    val desktopQuestions by io.github.mangi.eta.agent.remote.DesktopBridge.questions.collectAsState()
    val paneDesktop = remember(desktopList, desktopApprovals, desktopQuestions, onOpenDesktop != null) {
        if (onOpenDesktop == null) emptyList() else {
            val needsYou = desktopApprovals.mapTo(HashSet()) { it.sessionId } + desktopQuestions.map { it.sessionId }
            desktopList.rows.filter { with(io.github.mangi.eta.ui.screens.desktop.DesktopListing) { it.isListed() } }
                .sortedByDescending { it.updatedAt }.take(8).map { row ->
                    io.github.mangi.eta.ui.components.PaneDesktopSession(row.id, row.title.orEmpty(), row.updatedAt, when {
                        row.id in needsYou -> io.github.mangi.eta.ui.model.TaskVisualStatus.Attention
                        row.running -> io.github.mangi.eta.ui.model.TaskVisualStatus.Running
                        else -> null
                    })
                }
        }
    }
    CompositionLocalProvider(io.github.mangi.eta.ui.components.LocalPaneManagement provides paneManagement, LocalChatToast provides { toast = it }, LocalPaneWorkspace provides PaneWorkspaceActions(
        onNewConversation, onOpenCommonTasks, onOpenTerminal, onOpenBrowser,
        onLaunchKimiWeb.takeIf { showKimi }, kimiWebLabel, canStopKimiWeb, onStopKimiWeb, onRefreshKimiWeb,
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

@Composable
private fun AgentTopBar(
    route: AppRoute?,
    scrollBehavior: ScrollBehavior,
    color: Color,
    onBack: () -> Unit,
    onOpenConversationPane: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenCommonTasks: () -> Unit,
    onOpenTerminal: () -> Unit,
    onLaunchKimiWeb: () -> Unit,
    kimiWebLabel: String,
    canStopKimiWeb: Boolean,
    onStopKimiWeb: () -> Unit,
    onRefreshKimiWeb: () -> Unit,
    onOpenBrowser: () -> Unit,
    homeCharacter: RoleplayChatUi?,
    onOpenCharacter: () -> Unit,
) {
    val isHome = route is AppRoute.Home
    val navigationIcon: @Composable () -> Unit = {
        if (isHome) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenConversationPane) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ShortText,
                        modifier = Modifier.size(24.dp),
                        contentDescription = stringResource(R.string.action_conversation_history),
                    )
                }
                // 角色对话的身份标识放在顶栏左侧，点按进入角色详情；普通对话保持空标题。
                homeCharacter?.let { character ->
                    CharacterTopBarIdentity(character = character, onClick = onOpenCharacter)
                }
            }
        } else {
            MiuixBackButton(onClick = onBack)
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        if (isHome) {
            TopBarOverflowMenu(
                onNewConversation = onNewConversation,
                onOpenCommonTasks = onOpenCommonTasks,
                onOpenTerminal = onOpenTerminal,
                onLaunchKimiWeb = onLaunchKimiWeb,
                kimiWebLabel = kimiWebLabel,
                canStopKimiWeb = canStopKimiWeb,
                onStopKimiWeb = onStopKimiWeb,
                onRefreshKimiWeb = onRefreshKimiWeb,
                onOpenBrowser = onOpenBrowser,
            )
        }
    }

    // 首页聊天舞台与终端这类整屏工具页没有可驱动折叠的滚动列表，大标题会一直展开、
    // 白占一屏顶部；它们使用紧凑标题，其余二级内容页统一使用可折叠大标题。
    val compactTitle = isHome || route is AppRoute.Terminal
    if (compactTitle) {
        SmallTopAppBar(
            title = titleForRoute(route),
            color = color,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    } else {
        AdaptiveTopAppBar(
            title = titleForRoute(route),
            color = color,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}

private val TopBarMenuIconSize = 20.dp

/**
 * 首页顶栏溢出菜单。WindowListPopup 以父布局为锚点，因此与触发按钮包在同一个 Box 中，
 * 弹层从按钮下方右对齐展开。
 */
@Composable
private fun TopBarOverflowMenu(
    onNewConversation: () -> Unit,
    onOpenCommonTasks: () -> Unit,
    onOpenTerminal: () -> Unit,
    onLaunchKimiWeb: () -> Unit,
    kimiWebLabel: String,
    canStopKimiWeb: Boolean,
    onStopKimiWeb: () -> Unit,
    onRefreshKimiWeb: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val speechSettings by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val hasSpeechPlayback = speechSettings?.let { it.tts != TtsProvider.NONE } == true
    val autoReadEnabled = speechSettings?.chatAutoSpeak == true
    Box {
        IconButton(onClick = { onRefreshKimiWeb(); showMenu = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                modifier = Modifier.size(24.dp),
                contentDescription = stringResource(R.string.action_more),
            )
        }
        WindowListPopup(
            show = showMenu,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { showMenu = false },
        ) {
            val newConversationText = stringResource(R.string.action_new_conversation)
            val commonTasksText = stringResource(R.string.common_tasks_title)
            val openTerminalText = stringResource(R.string.action_open_terminal)
            val launchKimiWebText = kimiWebLabel
            val stopKimiWebText = stringResource(R.string.capability_kimi_stop)
            val openBrowserText = stringResource(R.string.action_open_browser)
            val autoReadText = stringResource(
                if (autoReadEnabled) R.string.speech_disable_auto_read else R.string.speech_enable_auto_read,
            )
            val menuItems = remember(
                newConversationText,
                commonTasksText,
                openTerminalText,
                launchKimiWebText,
                openBrowserText,
                stopKimiWebText,
                canStopKimiWeb,
                autoReadText,
                hasSpeechPlayback,
                autoReadEnabled,
            ) {
                listOf(
                    DropdownItem(
                        text = newConversationText,
                        icon = { modifier ->
                            Icon(
                                imageVector = Icons.Outlined.AddComment,
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                    DropdownItem(
                        text = openTerminalText,
                        icon = { modifier ->
                            Icon(
                                imageVector = Icons.Outlined.Terminal,
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                    DropdownItem(
                        text = launchKimiWebText,
                        icon = { modifier ->
                            Icon(
                                painter = painterResource(R.drawable.ic_kimi_code),
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                    DropdownItem(
                        text = openBrowserText,
                        icon = { modifier ->
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                    DropdownItem(
                        text = commonTasksText,
                        icon = { modifier ->
                            Icon(imageVector = Icons.Outlined.BookmarkBorder, contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize))
                        },
                    ),
                ) + (if (canStopKimiWeb) {
                    listOf(
                        DropdownItem(
                            text = stopKimiWebText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Outlined.Stop,
                                    contentDescription = null,
                                    modifier = modifier.size(TopBarMenuIconSize),
                                )
                            },
                        ),
                    )
                } else {
                    emptyList()
                }) + if (hasSpeechPlayback) {
                    listOf(
                        DropdownItem(
                            text = autoReadText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = if (autoReadEnabled) Icons.AutoMirrored.Outlined.VolumeUp
                                        else Icons.AutoMirrored.Outlined.VolumeOff,
                                    contentDescription = null,
                                    modifier = modifier.size(TopBarMenuIconSize),
                                )
                            },
                        ),
                    )
                } else emptyList()
            }
            ListPopupColumn {
                menuItems.forEachIndexed { index, item ->
                    DropdownImpl(
                        item = item,
                        optionSize = menuItems.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            showMenu = false
                            if (hasSpeechPlayback && index == menuItems.lastIndex) {
                                speechSettings?.let { current ->
                                    scope.launch {
                                        SpeechSettingsRepository.save(current.copy(chatAutoSpeak = !current.chatAutoSpeak))
                                    }
                                }
                            } else when (index) {
                                0 -> onNewConversation()
                                1 -> onOpenTerminal()
                                2 -> onLaunchKimiWeb()
                                3 -> onOpenBrowser()
                                4 -> onOpenCommonTasks()
                                5 -> onStopKimiWeb()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun titleForRoute(route: AppRoute?): String = when (route) {
    is AppRoute.Home, is AppRoute.Browser -> ""
    is AppRoute.Terminal -> stringResource(R.string.route_terminal)
    is AppRoute.Tools -> stringResource(R.string.route_tools)
    is AppRoute.WebToolsSettings -> stringResource(R.string.web_tools_settings_title)
    is AppRoute.Skills -> stringResource(R.string.route_skills)
    is AppRoute.CommonTasks, is AppRoute.Tasks -> stringResource(R.string.eta_tasks)
    is AppRoute.Subagents -> "Subagent"
    is AppRoute.ConversationTitleSettings -> stringResource(R.string.conversation_title_settings_title)
    is AppRoute.ContextCompactionSettings -> stringResource(R.string.compaction_settings_title)
    is AppRoute.AssistantOverlaySettings -> stringResource(R.string.assistant_overlay_settings_title)
    is AppRoute.Characters -> "角色"
    is AppRoute.CharacterDetail -> "角色详情"
    is AppRoute.CharacterEditor -> "编辑角色"
    is AppRoute.CharacterPersona -> "我的人设"
    is AppRoute.CharacterMemory -> "剧情记忆"
    is AppRoute.Permissions -> stringResource(R.string.route_permissions)
    is AppRoute.SystemEnhance -> stringResource(R.string.route_system_enhancements)
    is AppRoute.Settings -> stringResource(R.string.route_settings)
    is AppRoute.AssistantSettings -> stringResource(R.string.eta_more_assistant_settings)
    is AppRoute.SpeechSettings -> stringResource(R.string.speech_settings_title)
    is AppRoute.SpeechRecognition -> stringResource(R.string.speech_recognition_title)
    is AppRoute.SpeechSynthesis -> stringResource(R.string.speech_synthesis_title)
    is AppRoute.SpeechOss -> stringResource(R.string.speech_oss_title)
    is AppRoute.AppearanceSettings -> stringResource(R.string.appearance_title)
    is AppRoute.DataBackup -> stringResource(R.string.data_backup_title)
    is AppRoute.Memory -> stringResource(R.string.route_memory)
    is AppRoute.LinuxEnvironment -> stringResource(R.string.route_linux_environment)
    is AppRoute.Workspace -> stringResource(R.string.capability_workspace)
    is AppRoute.SharedFolders -> stringResource(R.string.route_shared_folders)
    is AppRoute.LinuxFiles -> stringResource(R.string.route_linux_files)
    is AppRoute.ModelProviders -> stringResource(R.string.route_model_providers)
    is AppRoute.CommunityCatalog -> "从目录添加"
    is AppRoute.CommunityCatalogProvider -> "选择模型"
    is AppRoute.McpServers -> stringResource(R.string.route_mcp_servers)
    is AppRoute.McpServerDetail -> stringResource(R.string.route_mcp_server_detail)
    is AppRoute.ModelProviderDetail -> stringResource(R.string.route_provider_details)
    is AppRoute.ModelProviderNew -> stringResource(R.string.route_new_provider)
    is AppRoute.DesktopSessions, is AppRoute.DesktopChat -> stringResource(R.string.desktop_title)
    is AppRoute.DesktopBridgeSettings -> stringResource(R.string.desktop_settings_title)
    null -> stringResource(R.string.app_name)
}

@Composable
private fun CharacterTopBarIdentity(character: RoleplayChatUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(
            name = character.characterName,
            avatarPath = character.avatarPath,
            size = 32.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = character.characterName,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}
