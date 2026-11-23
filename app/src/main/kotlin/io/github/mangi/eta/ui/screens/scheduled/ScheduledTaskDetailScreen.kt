package io.github.mangi.eta.ui.screens.scheduled

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.scheduler.ScheduledTaskRunner
import io.github.mangi.eta.agent.scheduler.ScheduledTaskScheduleCalculator
import io.github.mangi.eta.data.model.ScheduledTask
import io.github.mangi.eta.data.model.ScheduledTaskRun
import io.github.mangi.eta.data.model.ScheduledTaskRunStatus
import io.github.mangi.eta.data.model.enabledModel
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.ScheduledTaskRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.components.EtaListGroup
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.EtaToastCenter
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.StatusError
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/**
 * 定时任务详情（胖小胖样式）：排期与开关、立即试跑 / 停止、指令与配置、任务小记事本、运行时间线、删除。
 * 时间线点开会把这次运行导入对话并切过去；正在跑的会实时挂到主聊天。
 */
@Composable
internal fun ScheduledTaskDetailScreen(
    taskId: String,
    onNavigate: (AppRoute) -> Unit,
    onOpenRun: (ScheduledTaskRun, ScheduledTask, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val task by remember(context, taskId) { ScheduledTaskRepository.observeTask(taskId, context) }
        .collectAsState(initial = null)
    val runs by remember(context, taskId) { ScheduledTaskRepository.observeRuns(taskId, limit = 50, context = context) }
        .collectAsState(initial = emptyList())
    val activeTaskIds by ScheduledTaskRunner.activeTaskIds.collectAsState()
    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearMemoConfirm by remember { mutableStateOf(false) }

    val isRunning = taskId in activeTaskIds
    val currentTask = task
    val nowMs = System.currentTimeMillis()

    val followDefaultLabel = stringResource(R.string.scheduled_model_follow_default)
    val unavailableLabel = stringResource(R.string.scheduled_model_unavailable_fallback)
    val modelDisplay = remember(providers, currentTask?.modelId, followDefaultLabel, unavailableLabel) {
        val id = currentTask?.modelId?.takeIf { it.isNotBlank() }
        if (id == null) {
            followDefaultLabel
        } else {
            providers.enabledModel(id)?.let { "${it.provider.name} / ${it.model.displayName}" }
                ?: unavailableLabel
        }
    }

    val onRunOrStop: () -> Unit = {
        if (isRunning) {
            ScheduledTaskRunner.cancelRunningTask(context, taskId)
            EtaToastCenter.show(context, R.string.scheduled_toast_stopping, Toast.LENGTH_SHORT)
        } else {
            scope.launch {
                val res = withContext(Dispatchers.IO) { ScheduledTaskRunner.triggerManualRun(context, taskId) }
                val text = when (res) {
                    ScheduledTaskRunner.ManualTriggerResult.Started -> R.string.scheduled_toast_manual_started
                    ScheduledTaskRunner.ManualTriggerResult.AlreadyRunning -> R.string.scheduled_toast_already_running
                    ScheduledTaskRunner.ManualTriggerResult.NotFound -> R.string.scheduled_toast_task_not_found
                }
                EtaToastCenter.show(context, text, Toast.LENGTH_SHORT)
            }
        }
    }
    val onToggleEnabled: (Boolean) -> Unit = { enabled ->
        scope.launch {
            withContext(Dispatchers.IO) { ScheduledTaskRepository.setEnabled(taskId, enabled, context = context) }
        }
    }

    val title = currentTask?.name?.ifBlank { null } ?: stringResource(R.string.route_scheduled_task_detail)
    MiuixScaffoldPage(
        title = title,
        onBack = onBack,
        actions = {
            if (currentTask != null) {
                EtaIconButton(onClick = { onNavigate(AppRoute.ScheduledTaskEditor(taskId = taskId)) }) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.route_scheduled_task_edit),
                        tint = EtaTheme.colors.textPrimary,
                    )
                }
            }
        },
        overlay = {
            EtaWindowDialog(
                show = showDeleteConfirm,
                title = stringResource(R.string.scheduled_action_delete_task),
                summary = stringResource(R.string.scheduled_delete_dialog_message, currentTask?.name.orEmpty()),
                onDismissRequest = { showDeleteConfirm = false },
            ) {
                MiuixDialogActions(
                    confirmText = stringResource(R.string.action_delete),
                    onCancel = { showDeleteConfirm = false },
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            ScheduledTaskRunner.cancelRunningTask(context, taskId)
                            withContext(Dispatchers.IO) {
                                ScheduledTaskRepository.deleteTask(taskId, rescheduleAlarm = true, context = context)
                            }
                            onBack()
                        }
                    },
                )
            }
            EtaWindowDialog(
                show = showClearMemoConfirm,
                title = stringResource(R.string.scheduled_clear_memo_dialog_title),
                summary = stringResource(R.string.scheduled_clear_memo_dialog_message),
                onDismissRequest = { showClearMemoConfirm = false },
            ) {
                MiuixDialogActions(
                    confirmText = stringResource(R.string.scheduled_action_clear),
                    onCancel = { showClearMemoConfirm = false },
                    onConfirm = {
                        showClearMemoConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) { ScheduledTaskRepository.updateMemo(taskId, "", context = context) }
                            EtaToastCenter.show(context, R.string.scheduled_toast_memo_cleared, Toast.LENGTH_SHORT)
                        }
                    },
                )
            }
        },
    ) {
        if (currentTask == null) {
            item(key = "missing") {
                Text(
                    stringResource(R.string.scheduled_detail_missing),
                    style = EtaTheme.type.subhead,
                    color = EtaTheme.colors.textSecondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@MiuixScaffoldPage
        }
        item(key = "control") {
            val scheduleText = ScheduledTaskScheduleCalculator.describeSchedule(currentTask.schedule)
            val nextText = when {
                isRunning -> stringResource(R.string.scheduled_status_running_background)
                currentTask.enabled -> stringResource(
                    R.string.scheduled_next_run_prefix,
                    ScheduledTaskScheduleCalculator.formatNextRunLabel(currentTask.nextRunAt, nowMs),
                )
                else -> stringResource(R.string.scheduled_status_paused)
            }
            EtaListGroup {
                EtaSwitchPreference(
                    title = scheduleText,
                    summary = nextText,
                    checked = currentTask.enabled,
                    onCheckedChange = onToggleEnabled,
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Tune, tint = EtaPreferenceColors.Blue) },
                )
                currentTask.autoDisabledReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    EtaPreferenceDivider()
                    EtaPreference(title = stringResource(R.string.scheduled_auto_paused_title), summary = reason, titleColor = errorTitleColor())
                }
                EtaPreferenceDivider()
                EtaArrowPreference(
                    title = if (isRunning) stringResource(R.string.scheduled_action_stop) else stringResource(R.string.scheduled_action_run_now),
                    summary = stringResource(
                        if (isRunning) R.string.scheduled_action_stop_summary else R.string.scheduled_action_run_now_summary,
                    ),
                    startAction = {
                        EtaPreferenceIcon(
                            if (isRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            tint = if (isRunning) EtaPreferenceColors.Orange else EtaPreferenceColors.Green,
                        )
                    },
                    onClick = onRunOrStop,
                )
            }
        }
        item(key = "config") {
            EtaListGroup(title = stringResource(R.string.scheduled_detail_section_config)) {
                EtaPreference(title = stringResource(R.string.scheduled_field_prompt), summary = currentTask.prompt)
                EtaPreferenceDivider()
                EtaPreference(
                    title = stringResource(R.string.scheduled_field_model),
                    summary = modelDisplay,
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Cloud, tint = EtaPreferenceColors.Blue) },
                )
                EtaPreferenceDivider()
                EtaPreference(
                    title = stringResource(R.string.scheduled_detail_field_notify_session),
                    summary = ScheduledTaskUiFormatting.deliveryBadge(currentTask.deliveryMode) + " · " +
                        stringResource(
                            if (currentTask.showRunningNotification) R.string.scheduled_progress_badge_show_full
                            else R.string.scheduled_progress_badge_silent,
                        ) + " · " +
                        ScheduledTaskUiFormatting.sessionModeLabel(currentTask.sessionMode),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Notifications, tint = EtaPreferenceColors.Orange) },
                )
                EtaPreferenceDivider()
                EtaPreference(
                    title = stringResource(R.string.scheduled_detail_field_capabilities),
                    summary = buildCapabilitiesSummary(currentTask),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Tune, tint = EtaPreferenceColors.Violet) },
                )
            }
        }
        if (currentTask.memo.isNotBlank()) {
            item(key = "memo") {
                EtaListGroup(title = stringResource(R.string.scheduled_detail_memo_title_with_hint)) {
                    EtaPreference(summary = currentTask.memo.take(1200))
                    EtaPreferenceDivider()
                    EtaArrowPreference(title = stringResource(R.string.scheduled_action_clear_memo), onClick = { showClearMemoConfirm = true })
                }
            }
        }
        item(key = "timeline") {
            EtaListGroup(title = stringResource(R.string.scheduled_detail_section_timeline)) {
                if (runs.isEmpty()) {
                    EtaPreference(summary = stringResource(R.string.scheduled_detail_timeline_empty))
                } else {
                    runs.forEachIndexed { index, run ->
                        if (index > 0) EtaPreferenceDivider()
                        val live = isRunning && run.status == ScheduledTaskRunStatus.RUNNING
                        RunTimelineRow(run, live, nowMs) { onOpenRun(run, currentTask, live) }
                    }
                }
            }
        }
        item(key = "delete") {
            EtaListGroup {
                EtaArrowPreference(
                    title = stringResource(R.string.scheduled_action_delete_task),
                    titleColor = errorTitleColor(),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Delete, tint = EtaPreferenceColors.Pink) },
                    onClick = { showDeleteConfirm = true },
                )
            }
        }
    }
}

@Composable
private fun errorTitleColor() = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(color = StatusError)

@Composable
private fun RunTimelineRow(run: ScheduledTaskRun, isLive: Boolean, nowMs: Long, onOpen: () -> Unit) {
    val canOpen = !run.archiveConversationKey.isNullOrBlank() &&
        (isLive || (run.status != ScheduledTaskRunStatus.RUNNING && run.status != ScheduledTaskRunStatus.SKIPPED))
    val dotColor = ScheduledTaskUiFormatting.statusDotColor(run.status, isLive, autoDisabled = false)
    val headline = buildString {
        append(ScheduledTaskUiFormatting.statusLabel(run.status, runningNow = isLive))
        append(" · ")
        append(ScheduledTaskUiFormatting.triggerSourceLabel(run.triggerSource))
        val dur = ScheduledTaskUiFormatting.formatDuration(run.durationMs)
        if (dur.isNotEmpty()) append(" · ").append(dur)
    }
    EtaPreference(
        title = headline,
        summary = run.summary.ifBlank {
            when {
                isLive -> stringResource(R.string.scheduled_timeline_tap_live)
                canOpen -> stringResource(R.string.scheduled_timeline_tap_conversation)
                else -> null
            }
        },
        startAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(12.dp))
            }
        },
        endActions = {
            Text(
                ScheduledTaskUiFormatting.formatTimestamp(run.startedAt, nowMs),
                style = EtaTheme.type.caption,
                color = EtaTheme.colors.textTertiary,
            )
        },
        onClick = if (canOpen) onOpen else null,
    )
}

@Composable
private fun buildCapabilitiesSummary(task: ScheduledTask): String {
    val caps = task.capabilities
    val list = buildList {
        if (caps.browserTools) add(stringResource(R.string.scheduled_cap_short_browser))
        if (caps.deviceReadTools) add(stringResource(R.string.scheduled_cap_device_read_title))
        if (caps.deviceActionTools) add(stringResource(R.string.scheduled_cap_device_action_title))
        if (caps.terminalTools) add(stringResource(R.string.scheduled_cap_short_terminal))
        if (caps.mcpTools) add("MCP")
        if (caps.guiTools) {
            add(stringResource(if (caps.backgroundScreen) R.string.scheduled_cap_short_gui_background else R.string.scheduled_cap_short_gui_foreground))
        }
    }
    return if (list.isEmpty()) stringResource(R.string.scheduled_cap_none) else list.joinToString(" · ")
}
