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
import androidx.compose.material.icons.rounded.ChatBubbleOutline
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

/** 电脑端会话列表：按 DSH 工作区分组，状态与标题实时更新；空白「新对话」与归档会话不进主列表。 */
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
    val prefs by linkPrefs.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var menuFor by remember { mutableStateOf<DesktopSessionRow?>(null) }
    var renaming by remember { mutableStateOf<DesktopSessionRow?>(null) }
    val connected = connection is DesktopBridgeClient.State.Connected

    // 回到列表时刷新一次，平时靠推送保持最新。
    LaunchedEffect(connected) {
        if (connected) DesktopBridge.refreshSessions()
    }
    // 标题在本地过滤，聊天内容交给电脑搜索（输入停顿后再查）。
    LaunchedEffect(query, connected) {
        val trimmed = query.trim()
        if (trimmed.length < 2 || !connected) { hits = emptyMap(); return@LaunchedEffect }
        delay(400)
        hits = runCatching { DesktopBridge.search(trimmed).toMap() }.getOrDefault(emptyMap())
    }

    fun failed(error: DesktopBridgeException) =
        Toast.makeText(context, resources.getString(R.string.desktop_failed, error.message), Toast.LENGTH_SHORT).show()

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
        creating = creating,
        query = query,
        contentHits = hits,
        onQueryChange = { query = it },
        onBack = onBack,
        onOpenSession = onOpenSession,
        onOpenSettings = onOpenSettings,
        onCreate = ::create,
        onRetry = DesktopBridge::retry,
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
    query: String = "",
    contentHits: Map<String, String> = emptyMap(),
    onQueryChange: (String) -> Unit = {},
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        val needsYou = buildSet {
            approvals.forEach { add(it.sessionId) }
            questions.forEach { add(it.sessionId) }
        }
        val listed = sessions.rows.filter { it.listed }
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            val matches = listed.filter { row ->
                row.title.orEmpty().contains(trimmed, ignoreCase = true) || row.id in contentHits
            } + sessions.rows.filter { it.archived && !it.blank && (it.title.orEmpty().contains(trimmed, ignoreCase = true) || it.id in contentHits) }
            item(key = "results") {
                if (matches.isEmpty()) {
                    ListEmptyState(title = stringResource(R.string.desktop_search_empty), icon = Icons.Rounded.Search)
                } else {
                    EtaPreferenceGroup {
                        matches.forEachIndexed { index, row ->
                            if (index > 0) EtaPreferenceDivider(hasLeading = false)
                            DesktopSessionRowItem(row, row.id in needsYou, row.id in unread, contentHits[row.id], { onOpenSession(row.id) }, { onMore(row) })
                        }
                    }
                }
            }
            return@MiuixScaffoldPage
        }
        val groups = groupByWorkspace(listed, sessions.workspaces)
        if (sessions.loaded && listed.isEmpty()) {
            item(key = "empty") {
                ListEmptyState(
                    title = stringResource(R.string.desktop_no_sessions),
                    icon = Icons.Rounded.Computer,
                    action = { EtaButton(text = stringResource(R.string.desktop_new_session), onClick = { onCreate(sessions.workspaces.firstOrNull()) }, enabled = connected && !creating) },
                )
            }
        }
        for ((workspace, rows) in groups) {
            item(key = "ws-${workspace?.id ?: "other"}") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    EtaPreferenceGroupTitle(workspace?.title ?: stringResource(R.string.desktop_other_sessions), modifier = Modifier.weight(1f))
                    if (workspace != null) {
                        EtaIconButton(onClick = { onCreate(workspace) }, enabled = connected && !creating) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.desktop_new_session))
                        }
                    }
                }
                EtaPreferenceGroup {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) EtaPreferenceDivider(hasLeading = false)
                        DesktopSessionRowItem(row, row.id in needsYou, row.id in unread, null, { onOpenSession(row.id) }, { onMore(row) })
                    }
                }
            }
        }
        val archived = sessions.rows.filter { it.archived && !it.blank && !it.subagent && it.parentId == null }
            .sortedByDescending { it.updatedAt }
        if (archived.isNotEmpty()) {
            item(key = "archived") {
                EtaPreferenceGroup(modifier = Modifier.padding(horizontal = EtaPreferenceDefaults.SidePadding).padding(top = 12.dp, bottom = EtaPreferenceDefaults.GroupSpacing)) {
                    EtaArrowPreference(
                        title = stringResource(R.string.desktop_archived, archived.size),
                        startAction = { EtaPreferenceIcon(Icons.Rounded.Inventory2, tint = EtaPreferenceColors.Slate) },
                        endActions = { Text(stringResource(if (showArchived) R.string.desktop_collapse else R.string.desktop_expand), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) },
                        onClick = { showArchived = !showArchived },
                    )
                    if (showArchived) {
                        archived.forEach { row ->
                            EtaPreferenceDivider(hasLeading = false)
                            DesktopSessionRowItem(row, row.id in needsYou, false, null, { onOpenSession(row.id) }, { onMore(row) })
                        }
                    }
                }
            }
        }
    }
}

/** 主列表显示的会话：不含子代理、桌面预建的空白「新对话」和已归档会话（与桌面侧栏一致）。 */
internal val DesktopSessionRow.listed: Boolean get() = !subagent && parentId == null && !blank && !archived

@Composable
private fun DesktopSessionRowItem(
    row: DesktopSessionRow,
    needsYou: Boolean,
    unread: Boolean,
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
    val summary = listOfNotNull(status, pinned, updated.takeIf { it.isNotEmpty() }, snippet?.takeIf { it.isNotBlank() }?.take(80)).joinToString(" · ")
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

/** 置顶在前，其余按最近活动排序；不属于任何工作区的会话归入“其他”。 */
internal fun groupByWorkspace(
    rows: List<DesktopSessionRow>,
    workspaces: List<DesktopWorkspace>,
): List<Pair<DesktopWorkspace?, List<DesktopSessionRow>>> {
    val ordered = rows.sortedWith(compareByDescending<DesktopSessionRow> { it.pinned }.thenByDescending { it.updatedAt })
    val byWorkspace = ordered.groupBy { it.workspaceId }
    val known = workspaces.associateBy { it.id }
    val result = ArrayList<Pair<DesktopWorkspace?, List<DesktopSessionRow>>>()
    for (workspace in workspaces) byWorkspace[workspace.id]?.let { result += workspace to it }
    for ((id, list) in byWorkspace) if (id == null || id !in known) result += null to list
    return result
        .groupBy({ it.first }, { it.second })
        .map { (workspace, lists) -> workspace to lists.flatten() }
        .sortedByDescending { (_, list) -> list.maxOf { it.updatedAt } }
}
