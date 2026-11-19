package io.github.mangi.eta.ui.screens.scheduled

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.scheduler.ScheduledTaskHealthChecker
import io.github.mangi.eta.agent.scheduler.ScheduledTaskHealthSnapshot
import io.github.mangi.eta.agent.scheduler.ScheduledTaskRunner
import io.github.mangi.eta.agent.scheduler.ScheduledTaskScheduleCalculator
import io.github.mangi.eta.data.model.ScheduledTask
import io.github.mangi.eta.data.model.ScheduledTaskPresets
import io.github.mangi.eta.data.repository.ScheduledTaskRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.components.EtaListGroup
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaSwitch
import io.github.mangi.eta.ui.components.EtaToastCenter
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/**
 * 定时任务列表（胖小胖样式）：上游 a4169ba0 的双风格页面换成我们的 Miuix 骨架 + Eta 分组卡片。
 * 顶部一条系统权限提醒（电池无限制、精确闹钟、通知），中间是全部任务，底部是快速开始模板。
 */
@Composable
internal fun ScheduledTasksScreen(
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by remember(context) { ScheduledTaskRepository.observeTasks(context) }
        .collectAsState(initial = emptyList())
    val activeTaskIds by ScheduledTaskRunner.activeTaskIds.collectAsState()
    var health by remember(context) { mutableStateOf(ScheduledTaskHealthChecker.inspect(context)) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                health = ScheduledTaskHealthChecker.inspect(context)
                nowMs = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onToggleEnabled: (ScheduledTask, Boolean) -> Unit = { task, enabled ->
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ScheduledTaskRepository.setEnabled(task.id, enabled, context = context) }
            }.onFailure {
                EtaToastCenter.show(context, R.string.scheduled_toggle_failed, Toast.LENGTH_SHORT)
            }
        }
    }

    MiuixScaffoldPage(
        title = stringResource(R.string.route_scheduled_tasks),
        onBack = onBack,
        actions = {
            EtaIconButton(onClick = { onNavigate(AppRoute.ScheduledTaskEditor()) }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.route_scheduled_task_new),
                    tint = EtaTheme.colors.textPrimary,
                )
            }
        },
    ) {
        if (!health.allHealthy) {
            item(key = "health") {
                val guidance = healthGuidance(health)
                EtaListGroup {
                    EtaArrowPreference(
                        title = guidance.first,
                        summary = guidance.second,
                        startAction = { EtaPreferenceIcon(Icons.Outlined.WarningAmber, tint = EtaPreferenceColors.Orange) },
                        endActions = {
                            Text(guidance.third, style = EtaTheme.type.subhead, color = EtaTheme.colors.accent)
                        },
                        onClick = { openPrimaryHealthFix(context, health) },
                    )
                }
            }
        }
        if (tasks.isEmpty()) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.scheduled_intro),
                    style = EtaTheme.type.subhead,
                    color = EtaTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
        } else {
            item(key = "tasks") {
                val enabledCount = tasks.count { it.enabled }
                EtaListGroup(title = stringResource(R.string.scheduled_all_tasks, enabledCount, tasks.size)) {
                    tasks.forEachIndexed { index, task ->
                        if (index > 0) EtaPreferenceDivider()
                        val running = task.id in activeTaskIds
                        ScheduledTaskRow(
                            task = task,
                            running = running,
                            nowMs = nowMs,
                            onClick = { onNavigate(AppRoute.ScheduledTaskDetail(task.id)) },
                            onToggle = { onToggleEnabled(task, it) },
                        )
                    }
                }
            }
        }
        item(key = "presets") {
            EtaListGroup(title = stringResource(R.string.scheduled_quick_start)) {
                ScheduledTaskPresets.all.forEachIndexed { index, preset ->
                    if (index > 0) EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(preset.titleRes),
                        summary = "${ScheduledTaskScheduleCalculator.describeSchedule(preset.schedule)} · ${stringResource(preset.subtitleRes)}",
                        startAction = { EtaPreferenceIcon(Icons.Outlined.AutoAwesome, tint = EtaPreferenceColors.Violet) },
                        onClick = { onNavigate(AppRoute.ScheduledTaskEditor(presetId = preset.id)) },
                    )
                }
            }
        }
        if (tasks.isNotEmpty()) {
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.scheduled_intro),
                    style = EtaTheme.type.caption,
                    color = EtaTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ScheduledTaskRow(
    task: ScheduledTask,
    running: Boolean,
    nowMs: Long,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val dot = ScheduledTaskUiFormatting.statusDotColor(task.lastStatus, running, !task.autoDisabledReason.isNullOrBlank())
    EtaPreference(
        title = task.name.ifBlank { stringResource(R.string.scheduled_untitled) },
        summary = buildScheduleMetaLine(task, nowMs) + "\n" + buildStatusMetaLine(task, running, nowMs),
        startAction = {
            Box(Modifier.padding(end = 12.dp)) {
                EtaPreferenceIcon(Icons.Outlined.Schedule, tint = if (task.enabled) EtaPreferenceColors.Blue else EtaPreferenceColors.Slate)
                Box(
                    Modifier.align(androidx.compose.ui.Alignment.TopEnd).size(9.dp).clip(CircleShape).background(dot),
                )
            }
        },
        endActions = { EtaSwitch(checked = task.enabled, onCheckedChange = onToggle) },
        onClick = onClick,
    )
}

internal fun buildScheduleMetaLine(task: ScheduledTask, nowMs: Long): String {
    val scheduleText = ScheduledTaskScheduleCalculator.describeSchedule(task.schedule)
    val deliveryText = ScheduledTaskUiFormatting.deliveryBadge(task.deliveryMode)
    val nextText = if (task.enabled && task.nextRunAt != null) {
        " · 下次 ${ScheduledTaskScheduleCalculator.formatNextRunLabel(task.nextRunAt, nowMs)}"
    } else ""
    return "$scheduleText · $deliveryText$nextText"
}

internal fun buildStatusMetaLine(task: ScheduledTask, isRunning: Boolean, nowMs: Long): String {
    if (isRunning) return "正在后台运行…"
    if (!task.autoDisabledReason.isNullOrBlank()) return task.autoDisabledReason
    if (task.lastStatus == null) return if (task.enabled) "等待首次触发" else "已暂停"
    val statusText = ScheduledTaskUiFormatting.statusLabel(task.lastStatus, runningNow = false)
    val timeText = ScheduledTaskUiFormatting.formatTimestamp(task.lastRunAt, nowMs)
    val summarySnippet = task.lastSummary.trim().take(36)
    return buildString {
        if (timeText.isNotEmpty()) append(timeText).append(" · ")
        append(statusText)
        if (summarySnippet.isNotEmpty()) append(" · ").append(summarySnippet)
    }
}

private fun healthGuidance(health: ScheduledTaskHealthSnapshot): Triple<String, String, String> = when {
    !health.ignoringBatteryOptimizations -> Triple(
        "建议允许后台无限制耗电",
        "深度休眠时受限应用约 10 秒后会被切断后台网络，开启「电池无限制」定时任务才能稳定跑完。",
        "前往开启",
    )
    !health.canScheduleExactAlarms -> Triple(
        "精确闹钟权限未开启",
        "没开时定时任务可能晚几分钟触发；开启后可以准点唤醒执行。",
        "去授权",
    )
    else -> Triple(
        "系统通知未开启",
        "定时任务照常在后台执行，但收不到结果摘要和失败提醒。",
        "开启通知",
    )
}

private fun openPrimaryHealthFix(context: Context, health: ScheduledTaskHealthSnapshot) {
    val intent = when {
        !health.ignoringBatteryOptimizations -> ScheduledTaskHealthChecker.batteryOptimizationIntent(context)
        !health.canScheduleExactAlarms -> ScheduledTaskHealthChecker.exactAlarmSettingsIntent(context)
        else -> ScheduledTaskHealthChecker.notificationSettingsIntent(context)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            val fallback = if (!health.ignoringBatteryOptimizations) {
                ScheduledTaskHealthChecker.batterySettingsFallbackIntent(context)
            } else {
                ScheduledTaskHealthChecker.appDetailsIntent(context)
            }
            context.startActivity(fallback)
        }
    }
}
