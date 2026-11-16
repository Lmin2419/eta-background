package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.PermissionHealthUiState
import io.github.mangi.eta.ui.model.PermissionStatusUi
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun PermissionHealthCard(
    state: PermissionHealthUiState,
    onOpenPermissions: (() -> Unit)?,
    modifier: Modifier = Modifier,
    engineReady: Boolean? = rememberEngineReadiness(state),
) {
    val items = readinessItems(state, engineReady)
    val pending = items.count { it.status != PermissionStatusUi.Available }
    val checking = items.any { it.checking }
    val allReady = pending == 0 && !checking
    // 全部就绪时收成一行；检查中显示运行圈；有缺项时只列出缺的那几项。
    EtaCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), onClick = onOpenPermissions,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = if (allReady || checking) 14.dp else 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRing(
                    when { checking -> TaskVisualStatus.Running; allReady -> TaskVisualStatus.Success; else -> TaskVisualStatus.Attention },
                    size = 18.dp, contentDescription = null,
                )
                Text(
                    stringResource(when { checking -> R.string.eta_ready_checking_line; allReady -> R.string.eta_ready_all_line; else -> R.string.eta_ready_pending_line }, pending),
                    Modifier.weight(1f), style = EtaTheme.type.rowTitle,
                    color = if (!checking && !allReady) EtaTheme.colors.attentionText else EtaTheme.colors.textPrimary,
                )
                if (onOpenPermissions != null) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = EtaTheme.colors.textTertiary)
            }
            if (!checking && !allReady) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.filter { it.status != PermissionStatusUi.Available }.forEach { item ->
                        val spokenTitle = stringResource(when (item.id) {
                            "accessibility" -> R.string.eta_ready_accessibility_full
                            "notifications" -> R.string.eta_ready_notifications_full
                            else -> item.title
                        })
                        val description = if (item.id == "engine") stringResource(R.string.eta_engine_not_ready) else item.status.label()
                        Row(Modifier.background(EtaTheme.colors.attentionSoft, EtaTheme.shapes.radiusFull)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusRing(TaskVisualStatus.Attention, size = 12.dp, contentDescription = "$spokenTitle: $description")
                            Text(stringResource(item.title), maxLines = 1, softWrap = false, style = EtaTheme.type.caption, color = EtaTheme.colors.attentionText)
                        }
                    }
                }
            }
        }
    }
}
