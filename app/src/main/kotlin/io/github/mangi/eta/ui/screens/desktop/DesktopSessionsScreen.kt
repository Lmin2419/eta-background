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
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/** 电脑端会话列表：按 DSH 工作区分组，状态与标题实时更新。 */
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
    val config by DesktopBridge.config.collectAsState()
    val connection by DesktopBridge.client.state.collectAsState()
    val sessions by DesktopBridge.sessions.collectAsState()
    val approvals by DesktopBridge.approvals.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    val connected = connection is DesktopBridgeClient.State.Connected

    // 回到列表时刷新一次，平时靠推送保持最新。
    LaunchedEffect(connected) {
        if (connected) DesktopBridge.refreshSessions()
    }

    fun create(workspace: DesktopWorkspace?) {
        if (creating) return
        creating = true
        scope.launch {
            try {
                onOpenSession(DesktopBridge.createSession(workspace?.id, workspace?.path))
            } catch (error: DesktopBridgeException) {
                Toast.makeText(context, resources.getString(R.string.desktop_failed, error.message), Toast.LENGTH_SHORT).show()
            } finally {
                creating = false
            }
        }
    }

    DesktopSessionsContent(
        configured = config?.isComplete == true,
        connection = connection,
        sessions = sessions,
        approvals = approvals,
        creating = creating,
        onBack = onBack,
        onOpenSession = onOpenSession,
        onOpenSettings = onOpenSettings,
        onCreate = ::create,
        onRetry = DesktopBridge::retry,
        overlay = { DesktopApprovalHost(approvals) },
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
    overlay: @Composable () -> Unit = {},
) {
    val connected = connection is DesktopBridgeClient.State.Connected
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
        if (approvals.isNotEmpty()) {
            item(key = "approvals") {
                EtaPreferenceGroup {
                    EtaArrowPreference(
                        title = stringResource(R.string.desktop_approval_banner, approvals.size),
                        startAction = { EtaPreferenceIcon(Icons.Rounded.WarningAmber, tint = EtaPreferenceColors.Orange) },
                        onClick = { onOpenSession(approvals.first().sessionId) },
                    )
                }
            }
        }
        val visible = sessions.rows.filterNot { it.subagent || it.parentId != null }
        val groups = groupByWorkspace(visible, sessions.workspaces)
        if (sessions.loaded && visible.isEmpty()) {
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
                        DesktopSessionRowItem(row, onClick = { onOpenSession(row.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSessionRowItem(row: DesktopSessionRow, onClick: () -> Unit) {
    val updated = remember(row.updatedAt) {
        if (row.updatedAt <= 0) "" else DateUtils.getRelativeTimeSpanString(row.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    val running = stringResource(R.string.desktop_running)
    EtaArrowPreference(
        title = row.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.desktop_untitled),
        summary = if (row.running) "$running · $updated" else updated,
        endActions = { DesktopRunningDot(row.running) },
        onClick = onClick,
    )
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
