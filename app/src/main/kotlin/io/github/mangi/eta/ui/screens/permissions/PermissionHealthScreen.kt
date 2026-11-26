package io.github.mangi.eta.ui.screens.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.*
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text
import kotlinx.coroutines.launch

@Composable
fun PermissionHealthScreen(state: PermissionHealthUiState, onAction: (PermissionHealthAction) -> Unit, modifier: Modifier = Modifier) {
    var refreshEngine by remember { mutableIntStateOf(0) }
    val engineReady = rememberEngineReadiness(state, refreshEngine)
    val rootReady = state.items.firstOrNull { it.id == "root" }?.status == PermissionStatusUi.Available
    // 引擎没就绪时按钮要真的做事：没有 Root 先去开 Root；有 Root 就直接用 Root 启动引擎，并告诉你结果。
    var startingEngine by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val starter = LocalEngineStarter.current
    val engine = PermissionHealthItemUi("engine", stringResource(R.string.eta_ready_engine),
        stringResource(when {
            engineReady == true -> R.string.eta_engine_ready_summary
            !rootReady -> R.string.eta_engine_need_root_summary
            else -> R.string.eta_engine_stopped_summary
        }),
        if (engineReady == true) PermissionStatusUi.Available else PermissionStatusUi.Missing,
        stringResource(if (!rootReady) R.string.eta_engine_open_root else R.string.eta_engine_start))
    fun onEngine() {
        when {
            engineReady == true -> refreshEngine++
            !rootReady -> onAction(PermissionHealthAction.OpenItemAction("root"))
            startingEngine -> Unit
            else -> {
                startingEngine = true
                scope.launch {
                    val (result, hint) = try { starter.start(context) }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (error: Exception) { EngineStartResult.Failed to error.message }
                    startingEngine = false
                    refreshEngine++
                    val message = when (result) {
                        EngineStartResult.Ready -> resources.getString(R.string.eta_engine_started_toast)
                        EngineStartResult.Starting -> resources.getString(R.string.eta_engine_still_starting_toast)
                        EngineStartResult.Failed -> resources.getString(R.string.eta_engine_start_failed_toast, hint.orEmpty().take(200))
                    }
                    EtaToastCenter.show(context, message, android.widget.Toast.LENGTH_LONG)
                }
            }
        }
    }
    // Stable priority sorting retains all original permission IDs and action routing.
    val rows = (state.items.filterNot { it.id == "engine" } + engine).sortedBy { if (it.status == PermissionStatusUi.Available) 1 else 0 }
    MiuixScaffoldPage(title = stringResource(R.string.ui_permission_health_3048bb),
        onBack = { onAction(PermissionHealthAction.NavigateBack) }, modifier = modifier,
        containerColor = EtaTheme.colors.canvas) {
        item(key = "readiness") { PermissionHealthCard(state, null, engineReady = engineReady) }
        items(rows, key = { it.id }) { item ->
            val engineRow = item.id == "engine"
            PermissionItemCard(item, checking = engineRow && (engineReady == null || startingEngine),
                busyLabel = if (engineRow && startingEngine) stringResource(R.string.eta_engine_starting) else null,
                onActionClick = { if (engineRow) onEngine() else onAction(PermissionHealthAction.OpenItemAction(item.id)) })
        }
        item(key = "permission_bottom_space") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PermissionItemCard(item: PermissionHealthItemUi, checking: Boolean, busyLabel: String? = null, onActionClick: () -> Unit) {
    val icon = when (item.id) {
        "accessibility" -> Icons.Outlined.AccessibilityNew
        "overlay" -> Icons.Outlined.Layers
        "root" -> Icons.Outlined.Key
        "engine", "model" -> Icons.Outlined.Memory
        "terminal" -> Icons.Outlined.Terminal
        "notification", "notifications" -> Icons.Outlined.Notifications
        "background" -> Icons.Outlined.History
        "app_list" -> Icons.Outlined.Dashboard
        LOCAL_NETWORK_PERMISSION_ITEM_ID -> Icons.Outlined.Wifi
        "location" -> Icons.Outlined.LocationOn
        "notification_history" -> Icons.Outlined.NotificationsActive
        "usage_access" -> Icons.Outlined.QueryStats
        "calendar" -> Icons.Outlined.CalendarMonth
        "notification_policy" -> Icons.Outlined.DoNotDisturbOn
        else -> Icons.Outlined.Shield
    }
    val title = when (item.id) {
        "calendar" -> stringResource(R.string.eta_permission_calendar)
        "notification_policy" -> stringResource(R.string.eta_permission_dnd)
        else -> item.title
    }
    val summary = when (item.id) {
        "calendar" -> stringResource(R.string.eta_permission_calendar_summary)
        "notification_policy" -> stringResource(R.string.eta_permission_dnd_summary)
        else -> item.summary.ifBlank { stringResource(when (item.id) {
            "background" -> R.string.eta_permission_background_summary
            "overlay" -> R.string.eta_permission_overlay_summary
            "accessibility" -> R.string.eta_permission_accessibility_summary
            "app_list" -> R.string.eta_permission_apps_summary
            else -> R.string.eta_permission_open_summary
        }) }
    }
    val available = item.status == PermissionStatusUi.Available
    val statusLabel = when {
        busyLabel != null -> busyLabel
        checking -> stringResource(R.string.eta_ready_checking)
        available -> stringResource(R.string.status_enabled)
        item.id == "engine" -> stringResource(R.string.eta_engine_not_ready)
        else -> item.status.label()
    }
    EtaCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp), onClick = onActionClick.takeUnless { checking }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EtaIconTile(icon, tint = EtaTheme.colors.tileGreen)
                Text(title, Modifier.weight(1f), style = EtaTheme.type.headline)
                Text(statusLabel,
                    Modifier.background(if (available) EtaTheme.colors.successSoft else EtaTheme.colors.attentionSoft, EtaTheme.shapes.radiusFull)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = EtaTheme.type.caption, color = if (available) EtaTheme.colors.successText else EtaTheme.colors.attentionText)
            }
            Text(summary, style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
            // Available rows retain the old tap-to-system-settings action, without a redundant enable button.
            if (!available) {
                val actionLabel = when (item.id) {
                    "calendar" -> stringResource(R.string.state_ui_to_authorize_762ec4)
                    "notification_policy" -> stringResource(R.string.state_ui_go_to_settings_1f2998)
                    else -> item.primaryActionLabel ?: stringResource(R.string.state_ui_to_open_13ec17)
                }
                EtaButton(actionLabel, onActionClick, enabled = !checking,
                    kind = EtaButtonKind.Tonal, size = EtaButtonSize.Small)
            }
        }
    }
}
