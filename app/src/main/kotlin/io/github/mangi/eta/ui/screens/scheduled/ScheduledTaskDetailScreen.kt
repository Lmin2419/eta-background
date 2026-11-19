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

    val modelDisplay = remember(providers, currentTask?.modelId) {
        val id = currentTask?.modelId?.takeIf { it.isNotBlank() }
        if (id == null) {
            "跟随全局默认模型"
        } else {
            providers.enabledModel(id)?.let { "${it.provider.name} / ${it.model.displayName}" }
                ?: "模型已失效（将回退默认）"
        }
    }

    val onRunOrStop: () -> Unit = {
        if (isRunning) {
            ScheduledTaskRunner.cancelRunningTask(context, taskId)
            EtaToastCenter.show(context, "正在停止当前运行…", Toast.LENGTH_SHORT)
        } else {
            scope.launch {
                val res = withContext(Dispatchers.IO) { ScheduledTaskRunner.triggerManualRun(context, taskId) }
                val text = when (res) {
                    ScheduledTaskRunner.ManualTriggerResult.Started -> "已开始后台试跑"
                    ScheduledTaskRunner.ManualTriggerResult.AlreadyRunning -> "这个任务正在运行"
                    ScheduledTaskRunner.ManualTriggerResult.NotFound -> "任务不存在"
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
                title = "删除定时任务",
                summary = "确定删除「${currentTask?.name.orEmpty()}」？后续排期会取消、运行时间线会清除，已导入的对话保留。",
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
                title = "清空任务小记事本",
                summary = "清空后，下一次运行不再参考上次记下的状态或游标。",
                onDismissRequest = { showClearMemoConfirm = false },
            ) {
                MiuixDialogActions(
                    confirmText = "清空",
                    onCancel = { showClearMemoConfirm = false },
                    onConfirm = {
                        showClearMemoConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) { ScheduledTaskRepository.updateMemo(taskId, "", context = context) }
                            EtaToastCenter.show(context, "已清空任务小记事本", Toast.LENGTH_SHORT)
                        }
                    },
                )
            }
        },
    ) {
        if (currentTask == null) {
            item(key = "missing") {
                Text(
                    "这个定时任务已不存在或已被删除。",
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
                isRunning -> "正在后台运行…"
                currentTask.enabled -> "下次运行：${ScheduledTaskScheduleCalculator.formatNextRunLabel(currentTask.nextRunAt, nowMs)}"
                else -> "已暂停"
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
                    EtaPreference(title = "已自动暂停", summary = reason, titleColor = errorTitleColor())
                }
                EtaPreferenceDivider()
                EtaArrowPreference(
                    title = if (isRunning) stringResource(R.string.scheduled_action_stop) else stringResource(R.string.scheduled_action_run_now),
                    summary = if (isRunning) "停止这次运行" else "不改排期、不受免打扰限制，马上在后台跑一次",
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
            EtaListGroup(title = "任务指令与配置") {
                EtaPreference(title = "指令", summary = currentTask.prompt)
                EtaPreferenceDivider()
                EtaPreference(
                    title = "执行模型",
                    summary = modelDisplay,
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Cloud, tint = EtaPreferenceColors.Blue) },
                )
                EtaPreferenceDivider()
                EtaPreference(
                    title = "通知与会话",
                    summary = ScheduledTaskUiFormatting.deliveryBadge(currentTask.deliveryMode) + " · " +
                        (if (currentTask.showRunningNotification) "显示运行进度" else "静默运行") + " · " +
                        ScheduledTaskUiFormatting.sessionModeLabel(currentTask.sessionMode),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Notifications, tint = EtaPreferenceColors.Orange) },
                )
                EtaPreferenceDivider()
                EtaPreference(
                    title = "能力范围",
                    summary = buildCapabilitiesSummary(currentTask),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Tune, tint = EtaPreferenceColors.Violet) },
                )
            }
        }
        if (currentTask.memo.isNotBlank()) {
            item(key = "memo") {
                EtaListGroup(title = "任务小记事本（供下次运行对比）") {
                    EtaPreference(summary = currentTask.memo.take(1200))
                    EtaPreferenceDivider()
                    EtaArrowPreference(title = "清空记事本", onClick = { showClearMemoConfirm = true })
                }
            }
        }
        item(key = "timeline") {
            EtaListGroup(title = "运行时间线") {
                if (runs.isEmpty()) {
                    EtaPreference(summary = "还没有运行记录，点上面的「立即试跑」看看实际效果。")
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
                    title = "删除定时任务",
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
                isLive -> "点按实时查看运行过程"
                canOpen -> "点按查看完整对话并继续追问"
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

private fun buildCapabilitiesSummary(task: ScheduledTask): String {
    val caps = task.capabilities
    val list = buildList {
        if (caps.browserTools) add("网页与浏览器")
        if (caps.deviceReadTools) add("设备信息读取")
        if (caps.deviceActionTools) add("设备状态控制")
        if (caps.terminalTools) add("终端与文件")
        if (caps.mcpTools) add("MCP")
        if (caps.guiTools) add(if (caps.backgroundScreen) "应用界面操作（后台虚拟屏）" else "应用界面操作（前台）")
    }
    return if (list.isEmpty()) "只用对话推理" else list.joinToString(" · ")
}
