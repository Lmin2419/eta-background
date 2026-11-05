package io.github.mangi.eta.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
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
        startAction = { EtaPreferenceIcon(Icons.Rounded.PowerSettingsNew, tint = EtaPreferenceColors.Yellow) },
        onClick = { show = true; NativeAssistantSettings.refresh(context) },
    )
    EtaWindowDialog(show = show, title = stringResource(R.string.native_assistant_title),
        onDismissRequest = { show = false }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            EtaPreference(summary = stringResource(R.string.native_assistant_explanation))
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
            EtaButton(text = stringResource(R.string.action_close), onClick = { show = false })
        }
    }
}

private fun nativeAssistantMessage(context: Context, code: String): String = context.getString(when (code) {
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
