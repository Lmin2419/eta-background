package io.github.mangi.eta.ui.screens.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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

@Composable
fun PermissionHealthScreen(state: PermissionHealthUiState, onAction: (PermissionHealthAction) -> Unit, modifier: Modifier = Modifier) {
    var refreshEngine by remember { mutableIntStateOf(0) }
    val engineReady = rememberEngineReadiness(state, refreshEngine)
    val engine = PermissionHealthItemUi("engine", stringResource(R.string.eta_ready_engine),
        stringResource(if (engineReady == true) R.string.eta_engine_ready_summary else R.string.eta_engine_unavailable_summary),
        if (engineReady == true) PermissionStatusUi.Available else PermissionStatusUi.Missing,
        stringResource(R.string.eta_engine_check))
    // Stable priority sorting retains all original permission IDs and action routing.
    val rows = (state.items.filterNot { it.id == "engine" } + engine).sortedBy { if (it.status == PermissionStatusUi.Available) 1 else 0 }
    MiuixScaffoldPage(title = stringResource(R.string.ui_permission_health_3048bb),
        onBack = { onAction(PermissionHealthAction.NavigateBack) }, modifier = modifier,
        containerColor = EtaTheme.colors.canvas) {
        item(key = "readiness") { PermissionHealthCard(state, null, engineReady = engineReady) }
        items(rows, key = { it.id }) { item ->
            PermissionItemCard(item, checking = item.id == "engine" && engineReady == null, onActionClick = {
                if (item.id == "engine") refreshEngine++ else onAction(PermissionHealthAction.OpenItemAction(item.id))
            })
        }
        item(key = "permission_bottom_space") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PermissionItemCard(item: PermissionHealthItemUi, checking: Boolean, onActionClick: () -> Unit) {
    val icon = when (item.id) {
        "accessibility" -> Icons.Rounded.AccessibilityNew
        "overlay" -> Icons.Rounded.Layers
        "root" -> Icons.Rounded.Key
        "engine", "model" -> Icons.Rounded.Memory
        "terminal" -> Icons.Rounded.Terminal
        "notification", "notifications" -> Icons.Rounded.Notifications
        "background" -> Icons.Rounded.History
        "app_list" -> Icons.Rounded.Dashboard
        LOCAL_NETWORK_PERMISSION_ITEM_ID -> Icons.Rounded.Wifi
        "location" -> Icons.Rounded.LocationOn
        "notification_history" -> Icons.Rounded.NotificationsActive
        "usage_access" -> Icons.Rounded.QueryStats
        "calendar" -> Icons.Rounded.CalendarMonth
        "notification_policy" -> Icons.Rounded.DoNotDisturbOn
        else -> Icons.Rounded.Shield
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
