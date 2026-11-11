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
import io.github.mangi.eta.ui.components.EtaChoiceChip
import io.github.mangi.eta.ui.components.EtaSegmented
import io.github.mangi.eta.ui.theme.EtaTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 常驻通知的三档预设：安静（默认，低优先级）/ 标准 / 显眼（流体云 + 流光）。 */
internal enum class DesktopNoticePreset { Quiet, Standard, Prominent }

internal val DesktopLinkPrefs.noticePreset: DesktopNoticePreset get() = when {
    fluidCloud || glow -> DesktopNoticePreset.Prominent
    !ongoingLowPriority -> DesktopNoticePreset.Standard
    else -> DesktopNoticePreset.Quiet
}

internal fun DesktopLinkPrefs.withNoticePreset(preset: DesktopNoticePreset): DesktopLinkPrefs = when (preset) {
    DesktopNoticePreset.Quiet -> copy(ongoingLowPriority = true, fluidCloud = false, glow = false)
    DesktopNoticePreset.Standard -> copy(ongoingLowPriority = false, fluidCloud = false, glow = false)
    DesktopNoticePreset.Prominent -> copy(ongoingLowPriority = false, fluidCloud = true, glow = true)
}

/** 「设置 → 电脑」里的同步开关、常驻通知样式与提醒。诊断信息在「高级」里。 */
@OptIn(ExperimentalLayoutApi::class)
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
    }
    item(key = "link-notification") {
        EtaPreferenceGroupTitle(stringResource(R.string.desktop_link_notification_title))
        EtaPreferenceGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val preset = prefs.noticePreset
                EtaSegmented(
                    items = listOf(
                        stringResource(R.string.desktop_notice_quiet),
                        stringResource(R.string.desktop_notice_standard),
                        stringResource(R.string.desktop_notice_prominent),
                    ),
                    selectedIndex = preset.ordinal,
                    onSelectedIndexChange = { index -> DesktopLinkSettings.update { it.withNoticePreset(DesktopNoticePreset.entries[index]) } },
                    enabled = prefs.enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(when (preset) {
                        DesktopNoticePreset.Quiet -> R.string.desktop_notice_quiet_summary
                        DesktopNoticePreset.Standard -> R.string.desktop_notice_standard_summary
                        DesktopNoticePreset.Prominent -> R.string.desktop_notice_prominent_summary
                    }),
                    style = EtaTheme.type.caption,
                    color = EtaTheme.colors.textTertiary,
                )
            }
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.desktop_remind_what), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EtaChoiceChip(stringResource(R.string.desktop_link_remind_completed), prefs.remindCompleted,
                        { DesktopLinkSettings.update { it.copy(remindCompleted = !it.remindCompleted) } }, enabled = prefs.enabled)
                    EtaChoiceChip(stringResource(R.string.desktop_link_remind_approval), prefs.remindApproval,
                        { DesktopLinkSettings.update { it.copy(remindApproval = !it.remindApproval) } }, enabled = prefs.enabled)
                    EtaChoiceChip(stringResource(R.string.desktop_link_remind_question), prefs.remindQuestion,
                        { DesktopLinkSettings.update { it.copy(remindQuestion = !it.remindQuestion) } }, enabled = prefs.enabled)
                    EtaChoiceChip(stringResource(R.string.desktop_link_remind_error), prefs.remindError,
                        { DesktopLinkSettings.update { it.copy(remindError = !it.remindError) } }, enabled = prefs.enabled)
                }
                Text(stringResource(R.string.desktop_remind_which), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
                EtaSegmented(
                    items = listOf(stringResource(R.string.desktop_remind_scope_watched), stringResource(R.string.desktop_remind_scope_all)),
                    selectedIndex = if (prefs.reminderScope == DesktopReminderScope.All) 1 else 0,
                    onSelectedIndexChange = { index ->
                        DesktopLinkSettings.update { it.copy(reminderScope = if (index == 1) DesktopReminderScope.All else DesktopReminderScope.Watched) }
                    },
                    enabled = prefs.enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.desktop_remind_scope_summary), style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary)
            }
        }
    }
}

/** 电脑端插件的版本、运行位置与会话数，方便核对「列表不全」这类问题。 */
@Composable
internal fun DesktopDiagnostics() {
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
        // 插件 0.5.0 起由插件管理 frpc：断线时能看出是通道的问题还是电脑没开。
        info.tunnel?.let { tunnel ->
            val state = when (tunnel.state) {
                "connected" -> stringResource(R.string.desktop_tunnel_connected)
                "starting" -> stringResource(R.string.desktop_tunnel_starting)
                "absent" -> stringResource(R.string.desktop_tunnel_absent)
                else -> stringResource(R.string.desktop_tunnel_error, tunnel.message ?: tunnel.state)
            }
            add(stringResource(R.string.desktop_diag_tunnel, state))
        }
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
