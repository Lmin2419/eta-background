package io.github.mangi.eta.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.R
import io.github.mangi.eta.assistant.NativeAssistantSettings
import io.github.mangi.eta.assistant.PowerKeyAdapter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import io.github.mangi.eta.ui.app.OverlayPermissionSettings
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.EtaWindowDialog

@Composable
internal fun NativeAssistantPreference(context: Context) {
    var show by remember { mutableStateOf(false) }
    val state by NativeAssistantSettings.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, show) {
        val observer = LifecycleEventObserver { _, event ->
            if (show && event == Lifecycle.Event.ON_RESUME) NativeAssistantSettings.refresh(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    EtaArrowPreference(
        title = stringResource(R.string.native_assistant_title),
        summary = stringResource(R.string.native_assistant_entry_summary),
        startAction = { EtaPreferenceIcon(Icons.Outlined.PowerSettingsNew, tint = EtaPreferenceColors.Yellow) },
        onClick = { show = true; NativeAssistantSettings.refresh(context) },
    )
    EtaWindowDialog(show = show, title = stringResource(R.string.native_assistant_title),
        onDismissRequest = { show = false }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            EtaPreference(summary = stringResource(R.string.native_assistant_explanation))
            if (state.foreignCleared) EtaPreference(summary = stringResource(R.string.native_assistant_foreign_cleared))
            PowerKeyAdapterPicker(state, onPick = { NativeAssistantSettings.setAdapter(context, it) })
            EtaSwitchPreference(
                title = stringResource(R.string.native_assistant_enable),
                checked = state.configured,
                enabled = !state.busy && (state.canEnable || state.configured),
                summary = nativeAssistantMessage(context, if (state.busy) "BUSY" else state.status),
                onCheckedChange = { if (it) NativeAssistantSettings.enable(context) else NativeAssistantSettings.restore(context) },
            )
            state.error?.let { code -> EtaPreference(summary = nativeAssistantMessage(context, code) + "\n" + code) }
            if (state.error == "OVERLAY_REQUIRED") {
                EtaArrowPreference(title = stringResource(R.string.native_assistant_overlay),
                    onClick = { OverlayPermissionSettings.open(context) })
            }
            if (state.canReapply) {
                EtaArrowPreference(title = stringResource(R.string.native_assistant_reapply),
                    summary = stringResource(R.string.native_assistant_reapply_summary), enabled = !state.busy,
                    onClick = { NativeAssistantSettings.reapply(context) })
            }
            if (state.configured) {
                EtaPreferenceDivider()
                EtaArrowPreference(title = stringResource(R.string.native_assistant_confirm),
                    summary = stringResource(R.string.native_assistant_test_instruction), enabled = !state.busy,
                    onClick = { NativeAssistantSettings.confirmWake(context) })
            }
            if (state.backupExists) {
                EtaArrowPreference(title = stringResource(R.string.native_assistant_restore),
                    summary = stringResource(R.string.native_assistant_restore_summary), enabled = !state.busy,
                    onClick = { NativeAssistantSettings.restore(context) })
            }
            EtaArrowPreference(title = stringResource(R.string.native_assistant_refresh), enabled = !state.busy,
                onClick = { NativeAssistantSettings.refresh(context) })
            // 「关闭」只留对话框底部按钮栏那一个（EtaWindowDialog 自带），内容里不再重复一个。
        }
    }
}

/** 适配方式：自动（按机型识别）或手动指定 ColorOS / HyperOS / 通用。配置过的要先恢复原配置才能换。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PowerKeyAdapterPicker(state: io.github.mangi.eta.assistant.NativeAssistantUiState, onPick: (PowerKeyAdapter) -> Unit) {
    val locked = state.backupExists || state.busy
    val summary = when {
        state.backupExists -> stringResource(R.string.native_assistant_adapter_locked)
        state.choice == PowerKeyAdapter.AUTO ->
            stringResource(R.string.native_assistant_adapter_auto_summary, adapterName(state.detected))
        else -> stringResource(R.string.native_assistant_adapter_manual_summary, adapterName(state.adapter), adapterName(state.detected))
    }
    EtaPreference(title = stringResource(R.string.native_assistant_adapter_title), summary = summary)
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            PowerKeyAdapter.AUTO to R.string.native_assistant_adapter_auto,
            PowerKeyAdapter.COLOROS to R.string.native_assistant_adapter_coloros,
            PowerKeyAdapter.HYPEROS to R.string.native_assistant_adapter_hyperos,
            PowerKeyAdapter.GENERIC to R.string.native_assistant_adapter_generic,
        ).forEach { (adapter, label) ->
            io.github.mangi.eta.ui.components.EtaChoiceChip(stringResource(label), state.choice == adapter,
                { if (state.choice != adapter) onPick(adapter) }, enabled = !locked)
        }
    }
}

@Composable
private fun adapterName(adapter: PowerKeyAdapter): String = stringResource(when (adapter) {
    PowerKeyAdapter.COLOROS -> R.string.native_assistant_adapter_name_coloros
    PowerKeyAdapter.HYPEROS -> R.string.native_assistant_adapter_name_hyperos
    else -> R.string.native_assistant_adapter_name_generic
})

private fun nativeAssistantMessage(context: Context, code: String): String = context.getString(when (code) {
    "HYPEROS_REVERTED" -> R.string.native_assistant_hyperos_reverted
    "OFF" -> R.string.native_assistant_off
    "BUSY" -> R.string.native_assistant_busy
    "AWAITING_KEY_TEST" -> R.string.native_assistant_pending
    "CONFIRMED_BY_USER" -> R.string.native_assistant_confirmed
    "ROOT_REQUIRED", "ROOT_UNAVAILABLE" -> R.string.native_assistant_root_required
    "FRAMEWORK_CONNECTED" -> R.string.native_assistant_framework
    "OVERLAY_REQUIRED" -> R.string.native_assistant_overlay_required
    "CONFIGURATION_CHANGED" -> R.string.native_assistant_changed
    "RESTORE_FIRST", "RESTORE_INCOMPLETE" -> R.string.native_assistant_restore_first
    "BACKUP_UNAVAILABLE" -> R.string.native_assistant_backup_failed
    "USER_CHANGED" -> R.string.native_assistant_user_changed
    "WAKE_NOT_SEEN" -> R.string.native_assistant_wake_not_seen
    "COMMAND_TIMEOUT", "COMMAND_RESULT_UNKNOWN" -> R.string.native_assistant_uncertain
    "COLOROS_ADAPTER_REQUIRED" -> R.string.native_assistant_coloros_required
    "COLOROS_FIRMWARE_UNSUPPORTED" -> R.string.native_assistant_coloros_unsupported
    "COLOROS_POLICY_OWNED" -> R.string.native_assistant_coloros_owned
    "COLOROS_POLICY_UNAVAILABLE" -> R.string.native_assistant_coloros_policy_unavailable
    "ASSISTANT_SERVICE_NOT_READY" -> R.string.native_assistant_service_not_ready
    else -> R.string.native_assistant_unavailable
})
