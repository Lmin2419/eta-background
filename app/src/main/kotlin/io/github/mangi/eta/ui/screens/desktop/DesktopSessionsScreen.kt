package io.github.mangi.eta.ui.screens.desktop

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.mangi.eta.ui.components.EtaPreferenceGroupItem
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.mangi.eta.agent.remote.DesktopLinkController
import io.github.mangi.eta.agent.remote.DesktopLinkSettings
import io.github.mangi.eta.agent.remote.DesktopQuestion
import io.github.mangi.eta.ui.components.EtaSheet
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.StatusRing
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.remote.DesktopApproval
import io.github.mangi.eta.agent.remote.DesktopBridge
import io.github.mangi.eta.agent.remote.DesktopBridgeClient
import io.github.mangi.eta.agent.remote.DesktopBridgeException
import io.github.mangi.eta.agent.remote.DesktopSessionRow
import io.github.mangi.eta.agent.remote.DesktopSessionsState
import io.github.mangi.eta.agent.remote.DesktopWorkspace
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDefaults
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/** 电脑端会话列表：最近任务在前，其后按 DSH 工作区分组（可收起）；空白「新对话」与归档会话不进主列表。 */
@Composable
internal fun DesktopSessionsScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    DisposableEffect(Unit) {
        DesktopBridge.acquire(context)
        onDispose { DesktopBridge.release() }
    }
    val linkPrefs = remember { DesktopLinkSettings.init(context) }
    val config by DesktopBridge.config.collectAsState()
    val connection by DesktopBridge.client.state.collectAsState()
    val sessions by DesktopBridge.sessions.collectAsState()
    val approvals by DesktopBridge.approvals.collectAsState()
    val questions by DesktopBridge.questions.collectAsState()
    val unread by DesktopBridge.unread.collectAsState()
    val touched by DesktopBridge.touched.collectAsState()
    val prefs by linkPrefs.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var menuFor by remember { mutableStateOf<DesktopSessionRow?>(null) }
    var renaming by remember { mutableStateOf<DesktopSessionRow?>(null) }
    val connected = connection is DesktopBridgeClient.State.Connected

    // 每次回到列表（包括从会话返回、从后台回来）都和电脑核对一次；平时靠推送保持最新。
    LifecycleResumeEffect(connected) {
        if (connected) scope.launch { DesktopBridge.refreshSessions() }
        onPauseOrDispose { }
    }
    // 标题在本地过滤，聊天内容交给电脑搜索（输入停顿后再查）。
    LaunchedEffect(query, connected) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || !connected) { hits = emptyMap(); return@LaunchedEffect }
        delay(400)
        hits = runCatching { DesktopBridge.search(trimmed).toMap() }.getOrDefault(emptyMap())
    }

    fun failed(error: DesktopBridgeException) =
        Toast.makeText(context, error.display(resources), Toast.LENGTH_SHORT).show()

    fun create(workspace: DesktopWorkspace?) {
        if (creating) return
        creating = true
        scope.launch {
            try {
                onOpenSession(DesktopBridge.createSession(workspace?.id, workspace?.path))
            } catch (error: DesktopBridgeException) {
                failed(error)
            } finally {
                creating = false
            }
        }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try { block() } catch (error: DesktopBridgeException) { failed(error) }
        }
    }

    DesktopSessionsContent(
        configured = config?.isComplete == true,
        enabled = prefs.enabled,
        connection = connection,
        sessions = sessions,
        approvals = approvals,
        questions = questions,
        unread = unread,
        touched = touched,
        collapsed = prefs.collapsedGroups,
        creating = creating,
        query = query,
        contentHits = hits,
        onQueryChange = { query = it },
        onBack = onBack,
        onOpenSession = onOpenSession,
        onOpenSettings = onOpenSettings,
        onCreate = ::create,
        onRetry = DesktopBridge::retry,
        onRefresh = { scope.launch { DesktopBridge.refreshSessions() } },
        onToggleGroup = DesktopLinkSettings::toggleGroup,
        onEnable = { DesktopLinkController.setEnabled(context, true) },
        onMore = { menuFor = it },
        overlay = {
            DesktopApprovalHost(approvals)
            DesktopSessionMenu(
                row = menuFor,
                remindOn = menuFor?.let { prefs.reminds(it.id) } == true,
                onDismiss = { menuFor = null },
                onPin = { row -> act { DesktopBridge.setPinned(row.id, !row.pinned) } },
                onArchive = { row -> act { DesktopBridge.setArchived(row.id, !row.archived) } },
                onRename = { row -> renaming = row },
                onRemind = { row, on -> DesktopLinkSettings.setSessionReminders(row.id, on) },
            )
            DesktopRenameSheet(
                row = renaming,
                onDismiss = { renaming = null },
                onRename = { row, title -> act { DesktopBridge.rename(row.id, title) } },
            )
        },
    )
}

@Composable
internal fun DesktopSessionsContent(
    configured: Boolean,
    connection: DesktopBridgeClient.State,
    sessions: DesktopSessionsState,
    approvals: List<DesktopApproval>,
    creating: Boolean,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCreate: (DesktopWorkspace?) -> Unit,
    onRetry: () -> Unit,
    enabled: Boolean = true,
    questions: List<DesktopQuestion> = emptyList(),
    unread: Set<String> = emptySet(),
    touched: Map<String, Long> = emptyMap(),
    collapsed: Set<String> = emptySet(),
    query: String = "",
    contentHits: Map<String, String> = emptyMap(),
    onQueryChange: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onToggleGroup: (String) -> Unit = {},
    onEnable: () -> Unit = {},
    onMore: (DesktopSessionRow) -> Unit = {},
    overlay: @Composable () -> Unit = {},
) {
    val connected = connection is DesktopBridgeClient.State.Connected
    var showArchived by rememberSaveable { mutableStateOf(false) }
    MiuixScaffoldPage(
        title = stringResource(R.string.desktop_title),
        onBack = onBack,
        actions = {
            if (configured && enabled) {
                EtaIconButton(onClick = onRefresh, enabled = connected && !sessions.loading) {
                    if (sessions.loading) StatusRing(TaskVisualStatus.Running, size = 18.dp)
                    else Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.desktop_refresh))
                }
            }
            EtaIconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.desktop_settings_title))
            }
        },
        overlay = overlay,
    ) {
        if (configured && !enabled) {
            item(key = "disabled") {
                ListEmptyState(
                    title = stringResource(R.string.desktop_link_disabled),
                    summary = stringResource(R.string.desktop_link_disabled_summary),
                    icon = Icons.Rounded.Computer,
                    action = { EtaButton(text = stringResource(R.string.desktop_link_enable), onClick = onEnable) },
                )
            }
            return@MiuixScaffoldPage
        }
        item(key = "status") {
            DesktopConnectionBanner(connection, configured = configured, onRetry = onRetry)
        }
        if (!configured) {
            item(key = "setup") {
                ListEmptyState(
                    title = stringResource(R.string.desktop_not_configured),
                    summary = stringResource(R.string.desktop_settings_entry_summary),
                    icon = Icons.Rounded.Computer,
                    action = { EtaButton(text = stringResource(R.string.desktop_configure), onClick = onOpenSettings) },
                )
            }
            return@MiuixScaffoldPage
        }
        val waiting = approvals.size + questions.size
        if (waiting > 0) {
            item(key = "approvals") {
                EtaPreferenceGroup {
                    EtaArrowPreference(
                        title = stringResource(R.string.desktop_waiting_banner, waiting),
                        startAction = { EtaPreferenceIcon(Icons.Rounded.WarningAmber, tint = EtaPreferenceColors.Orange) },
                        onClick = { onOpenSession((approvals.firstOrNull()?.sessionId ?: questions.first().sessionId)) },
                    )
                }
            }
        }
        item(key = "search") {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.desktop_search_hint),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 12.dp),
            )
        }
        val needsYou = buildSet {
            approvals.forEach { add(it.sessionId) }
            questions.forEach { add(it.sessionId) }
        }
        val listed = sessions.rows.filter { it.listed }
        val workspaceTitles = sessions.workspaces.associate { it.id to it.title }

        /** 一组会话，逐行进入懒加载列表；首尾两行负责卡片的圆角。 */
        fun sessionRows(group: String, rows: List<DesktopSessionRow>, showWorkspace: Boolean = false, snippets: Map<String, String> = emptyMap()) {
            rows.forEachIndexed { index, row ->
                item(key = "$group/${row.id}", contentType = "session") {
                    EtaPreferenceGroupItem(isFirst = index == 0, isLast = index == rows.lastIndex, hasLeading = true, modifier = Modifier.animateItem()) {
                        DesktopSessionRowItem(
                            row = row,
                            needsYou = row.id in needsYou,
                            unread = row.id in unread,
                            context = if (showWorkspace) row.workspaceId?.let(workspaceTitles::get) else null,
                            snippet = snippets[row.id],
                            onClick = { onOpenSession(row.id) },
                            onMore = { onMore(row) },
                        )
                    }
                }
            }
            item(key = "$group/end", contentType = "gap") { Spacer(Modifier.height(EtaPreferenceDefaults.GroupSpacing - 8.dp)) }
        }

        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            val matches = (listed + sessions.rows.filter { it.archived && !it.blank && !it.subagent && it.parentId == null })
                .filter { row -> row.title.orEmpty().contains(trimmed, ignoreCase = true) || row.id in contentHits }
                .sortedByDescending { it.updatedAt }
            if (matches.isEmpty()) {
                item(key = "results-empty") { ListEmptyState(title = stringResource(R.string.desktop_search_empty), icon = Icons.Rounded.Search) }
            } else {
                sessionRows("results", matches, showWorkspace = true, snippets = contentHits)
            }
            return@MiuixScaffoldPage
        }

        val groups = groupByWorkspace(listed, sessions.workspaces)
        if (sessions.loaded && listed.isEmpty() && groups.none { it.workspace != null }) {
            item(key = "empty") {
                ListEmptyState(
                    title = stringResource(R.string.desktop_no_sessions),
                    icon = Icons.Rounded.Computer,
                    action = { EtaButton(text = stringResource(R.string.desktop_new_session), onClick = { onCreate(sessions.workspaces.firstOrNull()) }, enabled = connected && !creating) },
                )
            }
        }
        // 最近任务：跨工作区最近动过的几个，等你处理和运行中的排在最前。只有一小组会话时不重复显示。
        if (listed.size > RECENT_LIMIT || groups.count { it.rows.isNotEmpty() } > 1) {
            val recent = recentSessions(listed, needsYou, touched)
            val open = RECENT_GROUP !in collapsed
            item(key = "h-$RECENT_GROUP", contentType = "header") {
                DesktopGroupHeader(
                    title = stringResource(R.string.desktop_recent),
                    count = null,
                    running = 0,
                    expanded = open,
                    onToggle = { onToggleGroup(RECENT_GROUP) },
                )
            }
            if (open) sessionRows(RECENT_GROUP, recent, showWorkspace = true)
        }
        for (group in groups) {
            val open = group.key !in collapsed
            item(key = "h-${group.key}", contentType = "header") {
                DesktopGroupHeader(
                    title = group.workspace?.title ?: stringResource(R.string.desktop_other_sessions),
                    count = group.rows.size,
                    running = group.rows.count { it.running },
                    attention = group.rows.count { it.id in needsYou },
                    expanded = open,
                    onToggle = { onToggleGroup(group.key) },
                    onCreate = group.workspace?.let { workspace -> { onCreate(workspace) } },
                    createEnabled = connected && !creating,
                )
            }
            if (!open) continue
            if (group.rows.isEmpty()) {
                item(key = "${group.key}/empty", contentType = "hint") {
                    Text(
                        text = stringResource(R.string.desktop_group_empty),
                        style = EtaTheme.type.caption,
                        color = EtaTheme.colors.textTertiary,
                        modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            } else {
                sessionRows(group.key, group.rows)
            }
        }
        val archived = sessions.rows.filter { it.archived && !it.blank && !it.subagent && it.parentId == null }
            .sortedByDescending { it.updatedAt }
        if (archived.isNotEmpty()) {
            item(key = "h-archived", contentType = "header") {
                DesktopGroupHeader(
                    title = stringResource(R.string.desktop_archived_title),
                    count = archived.size,
                    running = 0,
                    expanded = showArchived,
                    onToggle = { showArchived = !showArchived },
                )
            }
            if (showArchived) sessionRows("archived", archived, showWorkspace = true)
        }
    }
}

/** 主列表显示的会话：不含子代理、桌面预建的空白「新对话」和已归档会话（与桌面侧栏一致）。 */
internal val DesktopSessionRow.listed: Boolean get() = !subagent && parentId == null && !blank && !archived

internal const val RECENT_GROUP = "__recent"
internal const val OTHER_GROUP = "__other"
internal const val RECENT_LIMIT = 5

/** 一个可收起的分组；[workspace] 为空表示不属于任何工作区的「其他」。 */
internal data class DesktopSessionGroup(val key: String, val workspace: DesktopWorkspace?, val rows: List<DesktopSessionRow>)

/**
 * 分组标题：点按收起或展开。收起时仍能看到会话数，以及里面有几个在运行、几个等你处理，
 * 不用展开就知道要不要点进去。
 */
@Composable
private fun DesktopGroupHeader(
    title: String,
    count: Int?,
    running: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    attention: Int = 0,
    onCreate: (() -> Unit)? = null,
    createEnabled: Boolean = true,
) {
    val state = stringResource(if (expanded) R.string.desktop_group_expanded else R.string.desktop_group_collapsed)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(EtaTheme.shapes.radiusMd)
                .clickable(onClickLabel = state, role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "group_chevron")
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = EtaTheme.colors.textTertiary,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
            )
            Text(
                text = title,
                style = EtaTheme.type.footnote,
                color = EtaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count != null) Text(count.toString(), style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary)
            // 收起后把组里的动静提到标题上。
            if (!expanded && attention > 0) {
                StatusRing(TaskVisualStatus.Attention, size = 14.dp)
                Text(stringResource(R.string.desktop_group_attention, attention), style = EtaTheme.type.caption, color = EtaTheme.colors.textSecondary)
            } else if (!expanded && running > 0) {
                StatusRing(TaskVisualStatus.Running, size = 14.dp)
                Text(stringResource(R.string.desktop_group_running, running), style = EtaTheme.type.caption, color = EtaTheme.colors.textSecondary)
            }
        }
        if (onCreate != null) {
            EtaIconButton(onClick = onCreate, enabled = createEnabled) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.desktop_new_session_in, title))
            }
        }
    }
}

@Composable
private fun DesktopSessionRowItem(
    row: DesktopSessionRow,
    needsYou: Boolean,
    unread: Boolean,
    context: String?,
    snippet: String?,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val updated = remember(row.updatedAt) {
        if (row.updatedAt <= 0) "" else DateUtils.getRelativeTimeSpanString(row.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    val status = when {
        needsYou -> stringResource(R.string.desktop_needs_you)
        row.running -> stringResource(R.string.desktop_running)
        unread -> stringResource(R.string.desktop_done_unread)
        else -> null
    }
    val pinned = if (row.pinned) stringResource(R.string.desktop_pinned) else null
    val summary = listOfNotNull(
        status,
        pinned,
        context?.takeIf { it.isNotBlank() },
        updated.takeIf { it.isNotEmpty() },
        snippet?.takeIf { it.isNotBlank() }?.take(80),
    ).joinToString(" · ")
    EtaArrowPreference(
        title = row.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.desktop_untitled),
        summary = summary,
        // 每行都占住状态位，标题对齐：等你处理 > 运行中 > 已完成未读 > 置顶 > 普通。
        startAction = {
            when {
                needsYou -> StatusRing(TaskVisualStatus.Attention, size = 16.dp)
                row.running -> StatusRing(TaskVisualStatus.Running, size = 16.dp)
                unread -> StatusRing(TaskVisualStatus.Success, size = 16.dp)
                row.pinned -> Icon(Icons.Rounded.PushPin, contentDescription = null, tint = EtaTheme.colors.accent, modifier = Modifier.size(16.dp))
                else -> Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = EtaTheme.colors.textTertiary, modifier = Modifier.size(16.dp))
            }
        },
        endActions = {
            EtaIconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.desktop_session_actions), modifier = Modifier.size(18.dp))
            }
        },
        onClick = onClick,
    )
}

/** 会话的操作：置顶、重命名、归档，以及是否提醒这个会话（与桌面侧栏右键菜单对应）。 */
@Composable
private fun DesktopSessionMenu(
    row: DesktopSessionRow?,
    remindOn: Boolean,
    onDismiss: () -> Unit,
    onPin: (DesktopSessionRow) -> Unit,
    onArchive: (DesktopSessionRow) -> Unit,
    onRename: (DesktopSessionRow) -> Unit,
    onRemind: (DesktopSessionRow, Boolean) -> Unit,
) {
    var last by remember { mutableStateOf(row) }
    if (row != null) last = row
    val target = last ?: return
    EtaSheet(show = row != null, title = target.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.desktop_untitled), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            EtaPreferenceGroup(modifier = Modifier) {
                if (!target.archived) {
                    EtaArrowPreference(
                        title = stringResource(if (target.pinned) R.string.desktop_unpin else R.string.desktop_pin),
                        startAction = { EtaPreferenceIcon(Icons.Rounded.PushPin, tint = EtaPreferenceColors.Blue) },
                        onClick = { onPin(target); onDismiss() },
                    )
                    EtaPreferenceDivider(hasLeading = false)
                }
                EtaArrowPreference(
                    title = stringResource(R.string.desktop_rename),
                    startAction = { EtaPreferenceIcon(Icons.Rounded.Edit, tint = EtaPreferenceColors.Green) },
                    onClick = { onRename(target); onDismiss() },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaArrowPreference(
                    title = stringResource(if (target.archived) R.string.desktop_unarchive else R.string.desktop_archive),
                    startAction = { EtaPreferenceIcon(Icons.Rounded.Inventory2, tint = EtaPreferenceColors.Orange) },
                    onClick = { onArchive(target); onDismiss() },
                )
            }
            EtaPreferenceGroup(modifier = Modifier.padding(top = 12.dp)) {
                EtaSwitchPreference(
                    title = stringResource(R.string.desktop_session_remind),
                    summary = stringResource(R.string.desktop_session_remind_summary),
                    checked = remindOn,
                    onCheckedChange = { on -> onRemind(target, on) },
                )
            }
        }
    }
}

@Composable
private fun DesktopRenameSheet(row: DesktopSessionRow?, onDismiss: () -> Unit, onRename: (DesktopSessionRow, String) -> Unit) {
    var last by remember { mutableStateOf(row) }
    if (row != null) last = row
    val target = last ?: return
    var title by remember(target.id) { mutableStateOf(target.title.orEmpty()) }
    EtaSheet(show = row != null, title = stringResource(R.string.desktop_rename), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            EtaButton(
                text = stringResource(R.string.desktop_rename_save),
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && title.trim() != target.title,
                onClick = { onRename(target, title.trim()); onDismiss() },
            )
        }
    }
}

/**
 * 按电脑上的工作区顺序分组（与桌面侧栏一致），没有会话的工作区也保留，方便直接在里面新建。
 * 组内置顶在前，其余按最近活动排序；不属于任何已知工作区的会话归入「其他」。
 */
internal fun groupByWorkspace(
    rows: List<DesktopSessionRow>,
    workspaces: List<DesktopWorkspace>,
): List<DesktopSessionGroup> {
    val known = workspaces.mapTo(HashSet()) { it.id }
    val byWorkspace = rows
        .sortedWith(compareByDescending<DesktopSessionRow> { it.pinned }.thenByDescending { it.updatedAt })
        .groupBy { row -> row.workspaceId?.takeIf { it in known } }
    val groups = workspaces.map { DesktopSessionGroup(it.id, it, byWorkspace[it.id].orEmpty()) }
    val other = byWorkspace[null].orEmpty()
    return if (other.isEmpty()) groups else groups + DesktopSessionGroup(OTHER_GROUP, null, other)
}

/**
 * 「最近任务」：等你处理的在最前，其次是正在运行的，再按最近一次活动
 * （你发消息的时间，或本机看到它开始、结束运行的时间）排序。
 */
internal fun recentSessions(
    rows: List<DesktopSessionRow>,
    needsYou: Set<String>,
    touched: Map<String, Long>,
    limit: Int = RECENT_LIMIT,
): List<DesktopSessionRow> = rows
    .sortedWith(
        compareByDescending<DesktopSessionRow> { it.id in needsYou }
            .thenByDescending { it.running }
            .thenByDescending { maxOf(it.updatedAt, touched[it.id] ?: 0L) },
    )
    .take(limit)
