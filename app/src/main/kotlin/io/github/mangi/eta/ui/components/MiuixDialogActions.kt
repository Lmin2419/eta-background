package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R

/** Right-aligned actions wrap when large text no longer fits side by side. */
@Composable
fun MiuixDialogActions(
    confirmText: String, onCancel: () -> Unit, onConfirm: () -> Unit,
    modifier: Modifier = Modifier, cancelText: String? = null,
    cancelEnabled: Boolean = true, confirmEnabled: Boolean = true, destructive: Boolean = false,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EtaButton(cancelText ?: stringResource(R.string.action_cancel), onCancel,
            enabled = cancelEnabled, minHeight = 44.dp, kind = EtaButtonKind.Ghost)
        EtaButton(confirmText, onConfirm, enabled = confirmEnabled, minHeight = 44.dp,
            kind = if (destructive) EtaButtonKind.Destructive else EtaButtonKind.Primary)
    }
}
