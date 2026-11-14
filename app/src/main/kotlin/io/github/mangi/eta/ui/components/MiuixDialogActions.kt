package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R

/**
 * 对话框底部两个按钮并排、各占一半、48dp 高；危险操作红字。字很大放不下时文字换行，按钮不变窄。
 */
@Composable
fun MiuixDialogActions(
    confirmText: String, onCancel: () -> Unit, onConfirm: () -> Unit,
    modifier: Modifier = Modifier, cancelText: String? = null,
    cancelEnabled: Boolean = true, confirmEnabled: Boolean = true, destructive: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EtaButton(cancelText ?: stringResource(R.string.action_cancel), onCancel, modifier = Modifier.weight(1f),
            enabled = cancelEnabled, minHeight = 48.dp, kind = EtaButtonKind.Tonal)
        EtaButton(confirmText, onConfirm, modifier = Modifier.weight(1f), enabled = confirmEnabled, minHeight = 48.dp,
            kind = if (destructive) EtaButtonKind.Destructive else EtaButtonKind.Primary)
    }
}
