package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
    EtaCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), onClick = onOpenPermissions,
        insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.eta_background_ready), Modifier.weight(1f), style = EtaTheme.type.headline)
                Text(stringResource(if (checking) R.string.eta_ready_checking else if (pending == 0) R.string.eta_permissions_ready else R.string.eta_ready_pending, pending),
                    style = EtaTheme.type.footnote, color = if (pending == 0) EtaTheme.colors.successText else EtaTheme.colors.attentionText)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    val title = stringResource(item.title)
                    val spokenTitle = stringResource(when (item.id) {
                        "accessibility" -> R.string.eta_ready_accessibility_full
                        "notifications" -> R.string.eta_ready_notifications_full
                        else -> item.title
                    })
                    val status = if (item.checking) TaskVisualStatus.Queued else if (item.status == PermissionStatusUi.Available) TaskVisualStatus.Success else TaskVisualStatus.Attention
                    val description = when {
                        item.checking -> stringResource(R.string.eta_ready_checking)
                        item.id == "engine" && item.status != PermissionStatusUi.Available -> stringResource(R.string.eta_engine_not_ready)
                        else -> item.status.label()
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusRing(status, size = 16.dp, contentDescription = "$spokenTitle: $description")
                        Text(title, Modifier.fillMaxWidth(), maxLines = 1, softWrap = false, style = EtaTheme.type.caption, color = EtaTheme.colors.textSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
