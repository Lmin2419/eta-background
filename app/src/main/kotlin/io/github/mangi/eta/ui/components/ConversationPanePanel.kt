package io.github.mangi.eta.ui.components

import androidx.compose.foundation.border
import androidx.compose.animation.*
import io.github.mangi.eta.ui.components.EtaAnimatedContent as AnimatedContent
import io.github.mangi.eta.ui.components.EtaAnimatedVisibility as AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.*
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.foundation.layout.*
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.backdrop.couiBackdrop
import io.github.mangi.eta.ui.theme.EtaTypography
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import io.github.mangi.eta.ui.theme.etaElevation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.runtime.AgentExecutionService
import io.github.mangi.eta.agent.runtime.TaskNotificationState
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.theme.EtaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

internal data class PaneWorkspaceActions(
    val onNew: () -> Unit = {}, val onTasks: () -> Unit = {}, val onTerminal: () -> Unit = {},
    val onBrowser: () -> Unit = {}, val onKimi: (() -> Unit)? = null,
    val kimiLabel: String = "Kimi Code", val canStopKimi: Boolean = false,
    val onStopKimi: () -> Unit = {}, val onRefreshKimi: () -> Unit = {},
    val runningCount: Int = 0, val permissionsPending: Int = 0,
    /** 已配对电脑时显示「电脑」入口；[desktopRunning] 是电脑上正在运行的会话数。 */
    val onDesktop: (() -> Unit)? = null, val desktopRunning: Int = 0,
    /** 侧栏里直接列出的电脑会话（最近几条），点开直达电脑会话。 */
    val desktopSessions: List<PaneDesktopSession> = emptyList(),
    val desktopName: String? = null, val desktopOnline: Boolean = false,
    /** true：电脑会话按时间与本机对话混排；false：单独一个「电脑」分组。 */
    val desktopMixed: Boolean = true,
    val onOpenDesktopSession: (String) -> Unit = {},
)

/** 侧栏里的一条电脑会话。[status] 只在运行中 / 需要你时有值。 */
internal data class PaneDesktopSession(val id: String, val title: String, val updatedAt: Long, val status: TaskVisualStatus? = null)
internal data class PaneManagementActions(
    val delete: (Set<String>, (Int) -> Unit) -> Unit = { _, _ -> },
    val archive: (Set<String>) -> ConversationArchiveChange = { ConversationArchiveChange(emptySet(), 0, 0) },
    val unarchive: (Set<String>, Long?) -> Int = { _, _ -> 0 },
    val pin: (Set<String>, Boolean) -> Unit = { _, _ -> },
    val moveToGroup: (Set<String>, String?) -> Unit = { _, _ -> },
    val createGroup: (String, Set<String>) -> String? = { _, _ -> null },
    val renameGroup: (String, String) -> String? = { _, _ -> null },
    val toggleGroup: (String) -> Unit = {},
    val reorderGroup: (String, Int) -> Unit = { _, _ -> },
    val deleteGroup: (String) -> Unit = {},
)
internal val LocalPaneManagement = staticCompositionLocalOf { PaneManagementActions() }

internal val LocalPaneWorkspace = staticCompositionLocalOf { PaneWorkspaceActions() }

@Composable
internal fun conversationPaneContainerColor(): Color = EtaTheme.colors.surfaceSunken

@Composable
internal fun ConversationPanePanel(
    state: ConversationPaneUiState, width: Dp, listState: LazyListState,
    onSearchChange: (String) -> Unit, onConversationSelected: (String) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit, onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit, onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit, onOpenTools: () -> Unit, onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit, onOpenPermissions: () -> Unit, modifier: Modifier = Modifier, active: Boolean = true,
    floating: Boolean = false,
) {
    val workspace = LocalPaneWorkspace.current
    val management = LocalPaneManagement.current
    val resources = LocalResources.current
    var archived by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showMore by remember { mutableStateOf(false) }
    val groupDialogs = remember { ConversationGroupDialogState() }
    var deleteTarget by remember { mutableStateOf(emptySet<String>()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastVersion by remember { mutableIntStateOf(0) }
    var undo by remember { mutableStateOf<ConversationArchiveChange?>(null) }
    var desktopCollapsed by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<ConversationSummaryUi?>(null) }
    // 长按对话的菜单从按住的位置展开（上游 COUI 菜单）。
    val paneMenuAnchor = com.itos.coui.menu.rememberCouiMenuAnchor()
    // 底部玻璃栏采样的列表画面（上游侧栏的 COUI 背景采样）；关闭模糊或省电时不采样。
    // 侧栏底部玻璃栏暂用磨砂玻璃：侧栏关着时也在屏幕外组合，COUI 流体材质在真机上与首页空白有关，先不用。
    val paneBackdrop: com.itos.coui.backdrop.CouiBackdrop? = null
    val selecting = selectedIds.isNotEmpty()
    val motion = EtaTheme.motion
    val visibleConversations = remember(state.conversations, state.searchQuery, archived) {
        state.conversations.filter { if (state.searchQuery.isNotBlank()) true else (it.archivedAt != null) == archived }
    }
    val visibleIds = remember(visibleConversations) { visibleConversations.mapTo(linkedSetOf()) { it.id } }
    LaunchedEffect(visibleIds) { selectedIds = selectedIds.intersect(visibleIds) }
    LaunchedEffect(active) { if (!active) { selectedIds = emptySet(); showMore = false; deleteTarget = emptySet(); groupDialogs.dismiss() } }
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = active && (selecting || archived),
        onBackCompleted = { if (selecting) selectedIds = emptySet() else { archived = false; onSearchChange("") } })
    val snapshots by AgentExecutionService.taskSnapshots.collectAsState()
    val taskStatuses = remember(snapshots) { conversationTaskStatuses(snapshots) }
    val today = LocalDate.now()
    val sections = remember(state.conversations, state.groups, state.searchQuery, archived, today) { projectConversationSections(state, archived, today) }
    val groupNames = remember(state.groups) { state.groups.associate { it.id to it.name } }
    // 侧栏是浅灰底（与 Claude 一致），主页面纯白；选中行是更深一档的灰色胶囊。
    // 悬浮侧栏的底色由外层玻璃提供；宽屏常驻侧栏仍是浅灰实色。
    Column(modifier.width(width).fillMaxHeight().then(if (floating) Modifier else Modifier.background(EtaTheme.colors.surfaceSunken))
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
        AnimatedContent(selecting, transitionSpec = { fadeIn(tween(motion.short, easing = motion.standard)) togetherWith fadeOut(tween(motion.exitDuration(180), easing = motion.accelerate)) }, label = "selectionToolbar") { mode ->
            if (mode) ConversationSelectionHeader(selectedIds.size, visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds),
                { selectedIds = emptySet() }, { selectedIds = if (selectedIds.containsAll(visibleIds)) emptySet() else visibleIds })
            else if (archived) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                EtaIconButton({ archived = false; onSearchChange("") }) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.action_back)) }
                Text(stringResource(R.string.eta_archived), style = EtaTheme.type.headline)
            } else Column {
                // 与上游侧栏一致：左上大标题，右上「新建会话」；搜索、权限、设置在底部的玻璃栏里。
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), Modifier.weight(1f), style = EtaTheme.type.title1, color = EtaTheme.colors.textPrimary)
                    CircleIconButton(Icons.Outlined.Add, stringResource(R.string.eta_new_session), glass = false, onClick = workspace.onNew)
                }
            }
        }
        val showDock = !selecting
        Box(Modifier.weight(1f).fillMaxWidth()) {
        // 列表从底部玻璃栏下面穿过：玻璃栏采样这块列表做液态玻璃折射。
        Box(Modifier.fillMaxSize().then(if (paneBackdrop != null) Modifier.couiBackdrop(paneBackdrop)
            .background(EtaTheme.colors.surfaceSunken) else Modifier)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().couiMenuAnchor(paneMenuAnchor),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (showDock) PaneDockHeight + 8.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (visibleConversations.isEmpty() && (state.groups.isEmpty() || archived || state.searchQuery.isNotBlank())) item {
                ListEmptyState(stringResource(if (state.searchQuery.isBlank()) R.string.conversation_empty else R.string.conversation_no_results), icon = Icons.Outlined.Search)
            }
            // 功能入口是列表顶部的几行（图标 + 文字），随列表一起滚走；终端入口移到「设置」。
            if (!selecting && !archived && state.searchQuery.isBlank()) {
                // 功能入口：一排实色白色方块（任务 / 角色 / 浏览器 / 电脑 / Kimi），图标在上、文字在下。
                item(key = "nav-grid") {
                    val tiles = buildList {
                        add(PaneTile(Icons.Outlined.TaskAlt, stringResource(R.string.eta_tasks), workspace.onTasks, workspace.runningCount))
                        add(PaneTile(Icons.Outlined.PeopleAlt, stringResource(R.string.eta_characters), onOpenCharacters))
                        add(PaneTile(Icons.Outlined.Language, stringResource(R.string.eta_browser), workspace.onBrowser))
                        workspace.onDesktop?.let { add(PaneTile(Icons.Outlined.Computer, stringResource(R.string.desktop_entry), it, workspace.desktopRunning)) }
                        workspace.onKimi?.let { add(PaneTile(Icons.Outlined.Code, workspace.kimiLabel, it,
                            onLongClick = if (workspace.canStopKimi) workspace.onStopKimi else null)) }
                    }
                    PaneTileRow(tiles, Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
            }
            val showDesktop = !archived && !selecting && state.searchQuery.isBlank() && workspace.desktopSessions.isNotEmpty()
            // 分组样式：电脑会话单独一组，放在最上面，标题带电脑名和连接状态，右侧「全部」进完整电脑页。
            if (showDesktop && !workspace.desktopMixed) {
                item(key = "desktop-header") {
                    DesktopPaneHeader(workspace.desktopName, workspace.desktopOnline, desktopCollapsed,
                        onToggle = { desktopCollapsed = !desktopCollapsed }, onAll = workspace.onDesktop, modifier = etaAnimateItem())
                }
                if (!desktopCollapsed) items(workspace.desktopSessions.take(DesktopPaneLimit), key = { "desktop/${it.id}" }) { row ->
                    DesktopPaneRow(row, { workspace.onOpenDesktopSession(row.id) }, etaAnimateItem())
                }
            }
            // 混排样式：电脑会话按更新时间插进「今天 / 昨天 / 7 天内 / 更早」，只靠小电脑图标区分。
            val desktopByTime = if (showDesktop && workspace.desktopMixed)
                workspace.desktopSessions.take(DesktopPaneLimit).groupBy { timeSectionOf(it.updatedAt, today) } else emptyMap()
            val consumedTimes = HashSet<Int>()
            sections.forEach { section ->
                val group = section.group
                if (group != null) item(key = section.key) {
                    val status = section.conversations.firstNotNullOfOrNull { row ->
                        taskStatuses[row.id] ?: TaskVisualStatus.Running.takeIf { row.isActiveRun }
                    }
                    val expandedText = stringResource(if (group.collapsed) R.string.eta_group_collapsed else R.string.eta_group_expanded)
                    Row(etaAnimateItem().fillMaxWidth().heightIn(min = 36.dp).semantics { heading(); stateDescription = expandedText }
                        .combinedClickable(onClick = { management.toggleGroup(group.id) }, onLongClick = { groupDialogs.menuId = group.id })
                        .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.eta_group_count, group.name, section.conversations.size), Modifier.weight(1f),
                            style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        status?.let { StatusRing(it, size = 12.dp) }
                        Icon(if (group.collapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore, null,
                            Modifier.size(20.dp), tint = EtaTheme.colors.textSecondary)
                    }
                } else section.timeSection?.let { timeSection ->
                    item(key = section.key) {
                        Text(stringResource(when (timeSection) { 0 -> R.string.conversation_section_pinned; 1 -> R.string.conversation_section_today;
                            2 -> R.string.eta_yesterday; 3 -> R.string.eta_last_week; else -> R.string.eta_earlier }),
                            etaAnimateItem().padding(top = 12.dp, bottom = 6.dp), style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary)
                    }
                }
                val mixedDesktop = section.timeSection?.let { consumedTimes += it; desktopByTime[it] }.orEmpty()
                items(mixedDesktop, key = { "desktop/${it.id}" }) { row ->
                    DesktopPaneRow(row, { workspace.onOpenDesktopSession(row.id) }, etaAnimateItem())
                }
                items(section.visibleConversations, key = { it.id }) { conversation ->
                    ConversationTextRow(conversation, if (selecting) conversation.id in selectedIds else state.selectedConversationId == conversation.id, state.searchQuery,
                        taskStatuses[conversation.id] ?: TaskVisualStatus.Running.takeIf { conversation.isActiveRun }, selecting,
                        { if (selecting) selectedIds = if (conversation.id in selectedIds) selectedIds - conversation.id else selectedIds + conversation.id
                          else onConversationSelected(conversation.id) },
                        { _ -> if (selecting) selectedIds = selectedIds + conversation.id else paneMenuAnchor.open { menuTarget = conversation } },
                        groupName = groupNames[conversation.groupId], modifier = etaAnimateItem())
                }
            }
            desktopByTime.keys.filter { it !in consumedTimes }.sorted().forEach { time ->
                item(key = "desktop-time-$time") {
                    Text(stringResource(when (time) { 1 -> R.string.conversation_section_today; 2 -> R.string.eta_yesterday; 3 -> R.string.eta_last_week; else -> R.string.eta_earlier }),
                        etaAnimateItem().padding(top = 12.dp, bottom = 6.dp), style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary)
                }
                items(desktopByTime[time].orEmpty(), key = { "desktop/${it.id}" }) { row ->
                    DesktopPaneRow(row, { workspace.onOpenDesktopSession(row.id) }, etaAnimateItem())
                }
            }
            // 归档入口是列表最后一行，随列表滚动，不压在玻璃栏上。
            val archiveCount = state.conversations.count { it.archivedAt != null }
            if (!archived && !selecting && state.searchQuery.isBlank() && archiveCount > 0) item(key = "archive-entry") {
                Box(etaAnimateItem().padding(top = 8.dp).clip(EtaTheme.shapes.radiusLg)) {
                    EtaArrowPreference(stringResource(R.string.eta_archived_count, archiveCount),
                        onClick = { archived = true; onSearchChange("") })
                }
            }
        }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            key(toastVersion) {
                EtaToast(toast, { toast = null; undo = null }, Modifier.padding(horizontal = 12.dp),
                    actionLabel = if (undo != null) stringResource(R.string.eta_undo) else null,
                    onAction = undo?.let { change -> { management.unarchive(change.ids, change.archivedAt); undo = null; toast = null } })
            }
            if (showDock) PaneBottomDock(state.searchQuery, onSearchChange, workspace.permissionsPending > 0,
                onOpenPermissions, onOpenSettings, paneBackdrop)
        }
        }
        AnimatedContent(selecting, transitionSpec = { fadeIn(tween(motion.short, easing = motion.standard)) togetherWith fadeOut(tween(motion.exitDuration(180), easing = motion.accelerate)) }, label = "selectionActions") { mode ->
        if (mode) ConversationSelectionBar(archived,
            onArchive = {
                val ids = selectedIds
                selectedIds = emptySet(); toastVersion++
                if (archived) {
                    undo = null
                    toast = resources.getString(R.string.eta_unarchived_count, management.unarchive(ids, null))
                } else {
                    val change = management.archive(ids)
                    undo = change.takeIf { it.ids.isNotEmpty() }
                    toast = if (change.skipped > 0) resources.getString(R.string.eta_archived_skipped, change.ids.size, change.skipped)
                        else resources.getString(R.string.eta_archive_done, change.ids.size)
                }
            }, onDelete = { deleteTarget = selectedIds }, onMore = { showMore = true })
        else Spacer(Modifier.height(0.dp))
    }
    }
    EtaSheet(showMore, title = stringResource(R.string.eta_more), onDismissRequest = { showMore = false }) {
        val target = visibleConversations.singleOrNull { it.id in selectedIds }
        Column {
            val targets = visibleConversations.filter { it.id in selectedIds }
            val unpin = targets.isNotEmpty() && targets.all { it.isPinned }
            EtaArrowPreference(stringResource(if (unpin) R.string.eta_unpin else R.string.eta_pin),
                enabled = targets.any { it.archivedAt == null }, onClick = {
                    management.pin(selectedIds, !unpin); showMore = false; selectedIds = emptySet()
                })
            EtaArrowPreference(stringResource(R.string.eta_move_to_group), onClick = {
                groupDialogs.moveIds = selectedIds; showMore = false
            })
            if (target == null) Text(stringResource(R.string.eta_single_only), Modifier.padding(16.dp), style = EtaTheme.type.footnote, color = EtaTheme.colors.textSecondary)
            EtaArrowPreference(stringResource(R.string.action_rename), enabled = target != null, onClick = { target?.let(onConversationRename); showMore = false })
            EtaArrowPreference(stringResource(R.string.action_export), enabled = target != null, onClick = { target?.let(onConversationExport); showMore = false })
        }
    }
    // 长按对话：从这一行展开玻璃小窗（置顶 / 移到分组 / 改名 / 导出 / 多选 / 删除）。
    val menuConversation = menuTarget
    EtaContextMenu(menuConversation != null, onDismiss = { menuTarget = null }, couiAnchor = paneMenuAnchor,
        header = menuConversation?.title?.takeIf { it.isNotBlank() }) {
        val target = menuConversation ?: return@EtaContextMenu
        val ids = setOf(target.id)
        EtaMenuItem(stringResource(if (target.isPinned) R.string.eta_unpin else R.string.eta_pin), icon = Icons.Outlined.PushPin,
            enabled = target.archivedAt == null, onClick = { management.pin(ids, !target.isPinned); menuTarget = null })
        EtaMenuItem(stringResource(R.string.eta_move_to_group), icon = Icons.Outlined.Folder,
            onClick = { groupDialogs.moveIds = ids; menuTarget = null })
        EtaMenuItem(stringResource(R.string.action_rename), icon = Icons.Outlined.Edit,
            onClick = { onConversationRename(target); menuTarget = null })
        EtaMenuItem(stringResource(R.string.action_export), icon = Icons.Outlined.IosShare,
            onClick = { onConversationExport(target); menuTarget = null })
        EtaMenuItem(stringResource(R.string.conversation_select_multiple), icon = Icons.Outlined.Checklist,
            onClick = { selectedIds = ids; menuTarget = null })
        EtaMenuDivider()
        EtaMenuItem(stringResource(R.string.action_delete), icon = Icons.Outlined.Delete, destructive = true,
            onClick = { deleteTarget = ids; menuTarget = null })
    }
    ConversationGroupDialogs(state, management, groupDialogs) { selectedIds = emptySet() }
    if (deleteTarget.isNotEmpty()) {
        val running = state.conversations.count { it.id in deleteTarget && (it.isActiveRun || taskStatuses[it.id] != null) }
        EtaWindowDialog(true, title = stringResource(R.string.eta_delete_count_title, deleteTarget.size),
            summary = stringResource(R.string.eta_delete_count_body, deleteTarget.size) +
                if (running > 0) "\n" + stringResource(R.string.eta_delete_running_body, running) else "",
            onDismissRequest = { deleteTarget = emptySet() }) {
            MiuixDialogActions(confirmText = stringResource(if (running > 0) R.string.conversation_stop_and_delete else R.string.action_delete), destructive = true,
                onCancel = { deleteTarget = emptySet() }, onConfirm = {
                    val ids = deleteTarget; deleteTarget = emptySet(); selectedIds = emptySet(); undo = null
                    management.delete(ids) { count -> toastVersion++; toast = resources.getString(R.string.eta_deleted_count, count) }
                })
        }
    }
}

internal fun conversationSection(conversation: ConversationSummaryUi, today: LocalDate): Int {
    if (conversation.isPinned) return 0
    if (conversation.updatedAtMillis <= 0) return 1
    val date = Instant.ofEpochMilli(conversation.updatedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when { !date.isBefore(today) -> 1; date == today.minusDays(1) -> 2; !date.isBefore(today.minusDays(7)) -> 3; else -> 4 }
}

/** Same snapshot/status mapping as the task center; attention wins when a chat has two runs. */
internal fun conversationTaskStatuses(tasks: List<TaskNotificationState>): Map<String, TaskVisualStatus> =
    tasks.filter { !it.completed && it.conversationId != null }.groupBy { it.conversationId!! }.mapValues { (_, runs) ->
        val states = runs.map { it.toTaskVisualStatus() }
        states.firstOrNull { it == TaskVisualStatus.Attention }
            ?: states.firstOrNull { it == TaskVisualStatus.Running } ?: states.first()
    }

@Composable
private fun ConversationTextRow(conversation: ConversationSummaryUi, selected: Boolean, query: String, taskStatus: TaskVisualStatus?,
    selecting: Boolean, onClick: () -> Unit, onLongClick: (androidx.compose.ui.geometry.Rect) -> Unit, groupName: String? = null, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val colors = EtaTheme.colors
    val title = conversation.title.ifBlank { conversation.preview }
    val markedTitle = remember(title, query, colors.accent) { buildAnnotatedString {
        append(title)
        if (query.isNotBlank()) {
            var index = title.indexOf(query, ignoreCase = true)
            while (index >= 0) { addStyle(SpanStyle(color = colors.accent), index, index + query.length); index = title.indexOf(query, index + query.length, ignoreCase = true) }
        }
    } }
    val background by animateColorAsState(if (selected) colors.textPrimary.copy(alpha = 0.06f) else Color.Transparent, tween(EtaTheme.motion.short), label = "selectedConversation")
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(EtaTheme.shapes.radiusMd)
        .semantics { if (selecting) { this.selected = selected; role = Role.Checkbox } }
        .background(background)
        .onGloballyPositioned { bounds = it.boundsInWindow() }
        .etaTouchScale().combinedClickable(onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick(bounds) })
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selecting) ConversationSelectionMarker(selected)
        else Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            // 每行左侧一个类型图标（与 Claude 一致）；运行中 / 需要你时换成状态圈。
            if (taskStatus != null) StatusRing(taskStatus, size = 14.dp)
            else Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(18.dp), tint = colors.textSecondary)
        }
        if (conversation.isPinned && query.isNotBlank()) Icon(Icons.Outlined.PushPin, stringResource(R.string.eta_pin), Modifier.size(14.dp), tint = colors.textSecondary)
        Text(markedTitle, Modifier.weight(1f), style = EtaTheme.type.rowTitle.copy(fontSize = 15.sp), color = if (selected) colors.onAccentSoft else colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selecting && taskStatus != null) StatusRing(taskStatus, size = 12.dp)
        if (conversation.archivedAt != null && query.isNotBlank()) Text(stringResource(R.string.eta_archived),
            Modifier.background(colors.surfaceRaised, EtaTheme.shapes.radiusFull).padding(horizontal = 6.dp, vertical = 2.dp),
            style = EtaTheme.type.caption, color = colors.textSecondary)
        if (groupName != null && query.isNotBlank()) Text(groupName,
            Modifier.widthIn(max = 80.dp).background(colors.surfaceRaised, EtaTheme.shapes.radiusFull).padding(horizontal = 6.dp, vertical = 2.dp),
            style = EtaTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(conversation.timeLabel, style = EtaTheme.type.caption, color = colors.textTertiary, maxLines = 1)
    }
}

private const val DesktopPaneLimit = 8

/** 与 [conversationSection] 同一套时间分段：1 今天、2 昨天、3 七天内、4 更早。 */
internal fun timeSectionOf(millis: Long, today: LocalDate): Int {
    if (millis <= 0) return 1
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when { !date.isBefore(today) -> 1; date == today.minusDays(1) -> 2; !date.isBefore(today.minusDays(7)) -> 3; else -> 4 }
}

@Composable
private fun DesktopPaneHeader(name: String?, online: Boolean, collapsed: Boolean, onToggle: () -> Unit, onAll: (() -> Unit)?, modifier: Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(EtaTheme.shapes.radiusMd).clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Computer, null, Modifier.size(16.dp), tint = EtaTheme.colors.textSecondary)
            Text(listOfNotNull(stringResource(R.string.desktop_entry), name?.takeIf { it.isNotBlank() }).joinToString(" · "),
                Modifier.weight(1f, fill = false), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(6.dp).background(if (online) EtaTheme.colors.success else EtaTheme.colors.textTertiary, EtaTheme.shapes.radiusFull))
            Spacer(Modifier.weight(1f))
            Icon(if (collapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp), tint = EtaTheme.colors.textSecondary)
        }
        if (onAll != null) Text(stringResource(R.string.desktop_pane_all), Modifier.clip(EtaTheme.shapes.radiusMd).clickable(onClick = onAll)
            .padding(horizontal = 10.dp, vertical = 8.dp), style = EtaTheme.type.caption, color = EtaTheme.colors.accent)
    }
}

/** 侧栏里的电脑会话行：与本机对话同一行样式，左侧是淡灰的小电脑图标（运行中 / 需要你时换成状态圈）。 */
@Composable
private fun DesktopPaneRow(row: PaneDesktopSession, onClick: () -> Unit, modifier: Modifier) {
    val colors = EtaTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).etaTouchScale().clip(EtaTheme.shapes.radiusMd).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 与本机对话行同宽的图标位（20dp），标题左边缘对齐。
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (row.status != null) StatusRing(row.status, size = 14.dp)
            else Icon(Icons.Outlined.Computer, stringResource(R.string.desktop_entry), Modifier.size(18.dp), tint = colors.textSecondary)
        }
        Text(row.title.ifBlank { stringResource(R.string.desktop_untitled) }, Modifier.weight(1f), style = EtaTheme.type.rowTitle.copy(fontSize = 15.sp),
            color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val yesterday = stringResource(R.string.eta_yesterday)
        Text(remember(row.updatedAt, yesterday) { io.github.mangi.eta.ui.app.ConversationTimeLabels.label(row.updatedAt, yesterdayLabel = yesterday, recentLabel = "") },
            style = io.github.mangi.eta.ui.theme.EtaTypography.tabular(EtaTheme.type.caption), color = colors.textTertiary, maxLines = 1)
    }
}

/** 圆形描边图标按钮（44dp），用于侧栏的搜索与设置。 */
/** 底部玻璃栏的高度（含上下留白），列表底部按它留出空间。 */
private val PaneDockHeight = 72.dp

/**
 * 侧栏底部的玻璃栏（上游 ConversationPaneBottomDock）：左边搜索胶囊，右边权限和设置两枚圆钮。
 * 三者都是液态玻璃，浮在会话列表上面；有待处理的权限时权限钮带一个提示点。
 */
@Composable
private fun PaneBottomDock(
    query: String, onSearchChange: (String) -> Unit, permissionsAttention: Boolean,
    onOpenPermissions: () -> Unit, onOpenSettings: () -> Unit, backdrop: com.itos.coui.backdrop.CouiBackdrop?,
) {
    androidx.compose.runtime.CompositionLocalProvider(io.github.mangi.eta.ui.components.coui.LocalEtaCouiBackdrop provides backdrop) {
        Row(Modifier.fillMaxWidth().height(PaneDockHeight).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = EtaTheme.shapes.radiusFull,
                capsule = true,
                onClick = null,
            ) {
                EtaSearchField(query, onSearchChange, stringResource(R.string.conversation_search_hint),
                    Modifier.fillMaxWidth(), bare = true)
            }
            Box {
                CircleIconButton(Icons.Outlined.VerifiedUser, stringResource(R.string.route_permissions), onClick = onOpenPermissions)
                if (permissionsAttention) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp)
                    .background(EtaTheme.colors.attention, EtaTheme.shapes.radiusFull))
            }
            CircleIconButton(Icons.Outlined.Settings, stringResource(R.string.route_settings), onClick = onOpenSettings)
        }
    }
}

@Composable
internal fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, glass: Boolean = true) {
    if (glass) {
        // 对话页顶部：上游 COUI 的流体液态玻璃圆钮（边缘折光、内发光、按压回弹）。
        io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
            modifier = Modifier.requiredSize(44.dp),
            shape = EtaTheme.shapes.radiusFull,
            capsule = false,
            contentDescription = description,
            onClick = onClick,
        ) { Icon(icon, null, Modifier.size(22.dp), tint = EtaTheme.colors.textPrimary) }
        return
    }
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // 对话页顶部是玻璃（模糊背后的消息）；侧栏里本身已在玻璃上，按钮用实色白圆，不叠玻璃。
    Box(Modifier.requiredSize(44.dp).etaPressScale(press, pressedScale = 0.92f)
        .then(if (glass) Modifier.etaGlass(EtaTheme.shapes.radiusFull, elevation = io.github.mangi.eta.ui.theme.EtaElevation.E1, interactionSource = press)
            else Modifier.etaElevation(io.github.mangi.eta.ui.theme.EtaElevation.E1, EtaTheme.shapes.radiusFull).background(EtaTheme.colors.surfaceRaised, EtaTheme.shapes.radiusFull))
        .clip(EtaTheme.shapes.radiusFull).clickable(interactionSource = press, indication = null, onClickLabel = description, onClick = onClick)
        .semantics { contentDescription = description; role = Role.Button }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = EtaTheme.colors.textPrimary)
    }
}


private class PaneTile(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val count: Int = 0,
    val onLongClick: (() -> Unit)? = null,
)

/** 侧栏顶部的一排功能方块：实色白块（圆角 16、E1），22dp 线条图标；有运行中的任务时右上角一个计数。 */
@Composable
private fun PaneTileRow(tiles: List<PaneTile>, modifier: Modifier = Modifier) {
    val colors = EtaTheme.colors
    val haptic = LocalHapticFeedback.current
    var stopTarget by remember { mutableStateOf<PaneTile?>(null) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { tile ->
            Column(Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).etaTouchScale(0.94f)
                .combinedClickable(onClickLabel = tile.label, onClick = tile.onClick,
                    onLongClick = tile.onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); stopTarget = tile } }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                Box(Modifier.fillMaxWidth().aspectRatio(1f)
                    .etaElevation(io.github.mangi.eta.ui.theme.EtaElevation.E1, shape)
                    .background(colors.surfaceRaised, shape),
                    contentAlignment = Alignment.Center) {
                    Icon(tile.icon, null, Modifier.size(22.dp), tint = colors.textPrimary)
                    if (tile.count > 0) Text(tile.count.toString(),
                        style = EtaTypography.tabular(EtaTheme.type.micro), color = colors.onBrand,
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                            .background(colors.brand, EtaTheme.shapes.radiusFull).padding(horizontal = 5.dp, vertical = 1.dp))
                }
                Text(tile.label, style = EtaTheme.type.caption, color = colors.textSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
    val target = stopTarget
    if (target?.onLongClick != null) EtaSheet(true, title = target.label, onDismissRequest = { stopTarget = null }) {
        EtaButton(stringResource(R.string.action_stop), { stopTarget = null; target.onLongClick.invoke() },
            kind = EtaButtonKind.Destructive, modifier = Modifier.fillMaxWidth())
    }
}
