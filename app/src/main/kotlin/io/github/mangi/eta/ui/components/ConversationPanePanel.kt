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
internal fun conversationPaneContainerColor(): Color = EtaTheme.colors.surface

@Composable
internal fun ConversationPanePanel(
    state: ConversationPaneUiState, width: Dp, listState: LazyListState,
    onSearchChange: (String) -> Unit, onConversationSelected: (String) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit, onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit, onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit, onOpenTools: () -> Unit, onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit, onOpenPermissions: () -> Unit, modifier: Modifier = Modifier, active: Boolean = true,
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
    var searching by remember { mutableStateOf(false) }
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
    Column(modifier.width(width).fillMaxHeight().background(EtaTheme.colors.surface)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
        AnimatedContent(selecting, transitionSpec = { fadeIn(tween(motion.short, easing = motion.standard)) togetherWith fadeOut(tween(motion.exitDuration(180), easing = motion.accelerate)) }, label = "selectionToolbar") { mode ->
            if (mode) ConversationSelectionHeader(selectedIds.size, visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds),
                { selectedIds = emptySet() }, { selectedIds = if (selectedIds.containsAll(visibleIds)) emptySet() else visibleIds })
            else if (archived) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                EtaIconButton({ archived = false; onSearchChange("") }) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.action_back)) }
                Text(stringResource(R.string.eta_archived), style = EtaTheme.type.headline)
            } else Column {
                // 与 Claude 侧栏同一套结构：左上衬线大标题，右上圆形搜索；点搜索才展开搜索框。
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), Modifier.weight(1f), style = EtaTheme.type.title1, color = EtaTheme.colors.textPrimary)
                    CircleIconButton(Icons.Outlined.Search, stringResource(R.string.conversation_search_hint)) {
                        searching = !searching; if (!searching) onSearchChange("")
                    }
                }
                AnimatedVisibility(searching || state.searchQuery.isNotBlank()) {
                    EtaSearchField(state.searchQuery, onSearchChange, stringResource(R.string.conversation_search_hint),
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (visibleConversations.isEmpty() && (state.groups.isEmpty() || archived || state.searchQuery.isNotBlank())) item {
                ListEmptyState(stringResource(if (state.searchQuery.isBlank()) R.string.conversation_empty else R.string.conversation_no_results), icon = Icons.Outlined.Search)
            }
            // 功能入口是列表顶部的几行（图标 + 文字），随列表一起滚走；终端入口移到「设置」。
            if (!selecting && !archived && state.searchQuery.isBlank()) {
                item(key = "nav-tasks") { PaneNavRow(Icons.Outlined.TaskAlt, stringResource(R.string.eta_tasks), workspace.onTasks, count = workspace.runningCount) }
                item(key = "nav-characters") { PaneNavRow(Icons.Outlined.PeopleAlt, stringResource(R.string.eta_characters), onOpenCharacters) }
                item(key = "nav-browser") { PaneNavRow(Icons.Outlined.Language, stringResource(R.string.eta_browser), workspace.onBrowser) }
                workspace.onDesktop?.let { open -> item(key = "nav-desktop") { PaneNavRow(Icons.Outlined.Computer, stringResource(R.string.desktop_entry), open, count = workspace.desktopRunning) } }
                workspace.onKimi?.let { launch -> item(key = "nav-kimi") { PaneNavRow(Icons.Outlined.Code, workspace.kimiLabel, launch,
                    onLongClick = if (workspace.canStopKimi) workspace.onStopKimi else null) } }
                item(key = "nav-gap") { Spacer(Modifier.height(8.dp)) }
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
                        { selectedIds = selectedIds + conversation.id }, groupName = groupNames[conversation.groupId], modifier = etaAnimateItem())
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
        }
        val archiveCount = state.conversations.count { it.archivedAt != null }
        if (!archived && !selecting && state.searchQuery.isBlank() && archiveCount > 0) {
            EtaArrowPreference(stringResource(R.string.eta_archived_count, archiveCount),
                onClick = { archived = true; onSearchChange("") })
        }
        key(toastVersion) {
            EtaToast(toast, { toast = null; undo = null }, Modifier.padding(horizontal = 12.dp),
                actionLabel = if (undo != null) stringResource(R.string.eta_undo) else null,
                onAction = undo?.let { change -> { management.unarchive(change.ids, change.archivedAt); undo = null; toast = null } })
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
        else {
        // 底部：左边圆形设置按钮（有待处理权限时带提示点），右边黑色「新建会话」胶囊。
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                CircleIconButton(Icons.Outlined.Settings, stringResource(R.string.route_settings), onClick = onOpenSettings)
                if (workspace.permissionsPending > 0) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp).background(EtaTheme.colors.attention, EtaTheme.shapes.radiusFull))
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.clip(EtaTheme.shapes.radiusFull).background(EtaTheme.colors.textPrimary)
                .clickable(onClick = workspace.onNew).heightIn(min = 48.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = EtaTheme.colors.canvas)
                Text(stringResource(R.string.eta_new_session), style = EtaTheme.type.rowTitle, color = EtaTheme.colors.canvas, maxLines = 1)
            }
        }
        }
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
    selecting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, groupName: String? = null, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val colors = EtaTheme.colors
    val title = conversation.title.ifBlank { conversation.preview }
    val markedTitle = remember(title, query, colors.accent) { buildAnnotatedString {
        append(title)
        if (query.isNotBlank()) {
            var index = title.indexOf(query, ignoreCase = true)
            while (index >= 0) { addStyle(SpanStyle(color = colors.accent), index, index + query.length); index = title.indexOf(query, index + query.length, ignoreCase = true) }
        }
    } }
    val background by animateColorAsState(if (selected) colors.accentSoft else Color.Transparent, tween(EtaTheme.motion.short), label = "selectedConversation")
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(EtaTheme.shapes.radiusMd)
        .semantics { if (selecting) { this.selected = selected; role = Role.Checkbox } }
        .background(background)
        .combinedClickable(onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() })
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
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(EtaTheme.shapes.radiusMd).clickable(onClick = onClick)
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
@Composable
internal fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(EtaTheme.shapes.radiusFull).background(EtaTheme.colors.surfaceRaised)
        .border(1.dp, EtaTheme.colors.outline, EtaTheme.shapes.radiusFull).clickable(onClickLabel = description, onClick = onClick)
        .semantics { contentDescription = description; role = Role.Button }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = EtaTheme.colors.textPrimary)
    }
}

/** 侧栏顶部的功能行：线条图标 + 文字，计数显示在右侧。 */
@Composable
private fun PaneNavRow(icon: ImageVector, label: String, onClick: () -> Unit, count: Int = 0, onLongClick: (() -> Unit)? = null) {
    val haptic = LocalHapticFeedback.current
    var showStop by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(EtaTheme.shapes.radiusMd)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showStop = true } })
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = EtaTheme.colors.textPrimary)
        Text(label, Modifier.weight(1f), style = EtaTheme.type.rowTitle.copy(fontSize = 16.sp), color = EtaTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusRing(TaskVisualStatus.Running, size = 12.dp, contentDescription = null)
            Text(count.toString(), style = EtaTheme.type.caption, color = EtaTheme.colors.textSecondary)
        }
    }
    if (onLongClick != null) EtaSheet(showStop, title = label, onDismissRequest = { showStop = false }) {
        EtaButton(stringResource(R.string.action_stop), { showStop = false; onLongClick() }, kind = EtaButtonKind.Destructive, modifier = Modifier.fillMaxWidth())
    }
}
