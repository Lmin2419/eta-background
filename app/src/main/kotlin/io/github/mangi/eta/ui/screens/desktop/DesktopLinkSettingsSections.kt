package io.github.mangi.eta.ui.screens.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.remote.DesktopBridge
import io.github.mangi.eta.agent.remote.DesktopLinkController
import io.github.mangi.eta.agent.remote.DesktopLinkPrefs
import io.github.mangi.eta.agent.remote.DesktopLinkSettings
import io.github.mangi.eta.agent.remote.DesktopReminderScope
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 「设置 → 电脑」里的同步开关、常驻通知与提醒设置，以及电脑端诊断信息。 */
internal fun LazyListScope.desktopLinkSections(prefs: DesktopLinkPrefs) {
    item(key = "link-switch") {
        val context = LocalContext.current
        EtaPreferenceGroup {
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_switch),
                summary = stringResource(R.string.desktop_link_switch_summary),
                checked = prefs.enabled,
                onCheckedChange = { on -> DesktopLinkController.setEnabled(context, on) },
            )
        }
        DesktopDiagnostics()
    }
    item(key = "link-notification") {
        EtaPreferenceGroupTitle(stringResource(R.string.desktop_link_notification_title))
        EtaPreferenceGroup {
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_low_priority),
                summary = stringResource(R.string.desktop_link_low_priority_summary),
                checked = prefs.ongoingLowPriority,
                enabled = prefs.enabled,
                onCheckedChange = { on -> DesktopLinkSettings.update { it.copy(ongoingLowPriority = on) } },
            )
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_fluid_cloud),
                summary = stringResource(R.string.desktop_link_fluid_cloud_summary),
                checked = prefs.fluidCloud,
                enabled = prefs.enabled,
                onCheckedChange = { on -> DesktopLinkSettings.update { it.copy(fluidCloud = on) } },
            )
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_glow),
                summary = stringResource(R.string.desktop_link_glow_summary),
                checked = prefs.glow,
                enabled = prefs.enabled,
                onCheckedChange = { on -> DesktopLinkSettings.update { it.copy(glow = on) } },
            )
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_sound),
                summary = stringResource(R.string.desktop_link_sound_summary),
                checked = prefs.reminderSound,
                enabled = prefs.enabled,
                onCheckedChange = { on -> DesktopLinkSettings.update { it.copy(reminderSound = on) } },
            )
        }
    }
    item(key = "link-reminders") {
        EtaPreferenceGroupTitle(stringResource(R.string.desktop_link_reminders_title))
        EtaPreferenceGroup {
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_remind_completed),
                checked = prefs.remindCompleted,
                enabled = prefs.enabled,
                onCheckedChange = { on -> DesktopLinkSettings.update { it.copy(remindCompleted = on) } },
            )
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_remind_approval),
                checked = prefs.remindApproval,
                enabled = prefs.enabled,
                onCheckedChange = { on -> DesktopLinkSettings.update { it.copy(remindApproval = on) } },
            )
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_remind_question),
                checked = prefs.remindQuestion,
                enabled = prefs.enabled,
                onCheckedChange = { on -> DesktopLinkSettings.update { it.copy(remindQuestion = on) } },
            )
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_remind_error),
                checked = prefs.remindError,
                enabled = prefs.enabled,
                onCheckedChange = { on -> DesktopLinkSettings.update { it.copy(remindError = on) } },
            )
            EtaSwitchPreference(
                title = stringResource(R.string.desktop_link_remind_all),
                summary = stringResource(R.string.desktop_link_remind_all_summary),
                checked = prefs.reminderScope == DesktopReminderScope.All,
                enabled = prefs.enabled,
                onCheckedChange = { on ->
                    DesktopLinkSettings.update { it.copy(reminderScope = if (on) DesktopReminderScope.All else DesktopReminderScope.Watched) }
                },
            )
        }
    }
}

/** 电脑端插件的版本、运行位置与会话数，方便核对「列表不全」这类问题。 */
@Composable
private fun DesktopDiagnostics() {
    val server by DesktopBridge.server.collectAsState()
    val sessions by DesktopBridge.sessions.collectAsState()
    val info = server ?: return
    val rows = sessions.rows
    val visible = rows.count { !it.blank && !it.archived && !it.subagent && it.parentId == null }
    val runtime = when (info.runtime) {
        "desktop" -> stringResource(R.string.desktop_runtime_desktop)
        "node" -> stringResource(R.string.desktop_runtime_node)
        else -> stringResource(R.string.desktop_runtime_unknown)
    }
    val lines = buildList {
        add(stringResource(R.string.desktop_diag_plugin, info.version ?: "?", runtime))
        // 插件上报了接口清单才知道 DSH 的情况；0.2.2 之前的 DSH 没有 events 接口。
        info.dshApi?.let { api ->
            add(info.dshVersion?.let { stringResource(R.string.desktop_diag_dsh, it) }
                ?: if ("events" in api) stringResource(R.string.desktop_diag_dsh, "0.2.2+") else stringResource(R.string.desktop_diag_dsh_legacy))
        }
        if (info.dshApi == null) add(stringResource(R.string.desktop_diag_plugin_outdated))
        if (sessions.loaded) add(stringResource(R.string.desktop_diag_sessions, visible, rows.count { it.archived }, rows.count { it.blank }))
        if (info.runtime == "node") add(stringResource(R.string.desktop_diag_node_warning))
    }
    Text(
        text = lines.joinToString("\n"),
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}
