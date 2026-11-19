package io.github.mangi.eta.ui.screens.scheduled

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.scheduler.ScheduledTaskScheduleCalculator
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.model.ScheduledTask
import io.github.mangi.eta.data.model.ScheduledTaskCapabilities
import io.github.mangi.eta.data.model.ScheduledTaskDeliveryMode
import io.github.mangi.eta.data.model.ScheduledTaskMisfirePolicy
import io.github.mangi.eta.data.model.ScheduledTaskPresets
import io.github.mangi.eta.data.model.ScheduledTaskQuietHours
import io.github.mangi.eta.data.model.ScheduledTaskSchedule
import io.github.mangi.eta.data.model.ScheduledTaskSessionMode
import io.github.mangi.eta.data.model.ScheduledTaskTriggerKind
import io.github.mangi.eta.data.model.enabledModel
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.data.repository.ScheduledTaskRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaChoiceChip
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.components.EtaListGroup
import io.github.mangi.eta.ui.components.EtaOverlayDropdownPreference
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaSheet
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.EtaToastCenter
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.StatusError
import io.github.mangi.eta.ui.model.AgentModelPickerProjector
import io.github.mangi.eta.ui.screens.subagent.SubagentModelPickerContent
import io.github.mangi.eta.ui.theme.EtaTheme
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 新建 / 编辑定时任务（胖小胖样式）。上游 a4169ba0 的编辑页逻辑原样保留，界面换成我们的分组卡片；
 * 另加「界面操作走后台虚拟屏」：开着时界面操作放到后台虚拟屏，锁屏也能执行。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduledTaskEditorScreen(
    taskId: String? = null,
    presetId: String? = null,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val isEditing = !taskId.isNullOrBlank()
    val preset = remember(presetId) { ScheduledTaskPresets.find(presetId) }

    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val primaryProviderId by RuntimeConfigRepository.selectedProviderIdFlow().collectAsState(initial = null)
    val primaryModelId by RuntimeConfigRepository.selectedModelIdFlow().collectAsState(initial = null)

    var loadedExisting by remember(taskId) { mutableStateOf<ScheduledTask?>(null) }
    var initialized by remember(taskId, presetId) { mutableStateOf(false) }

    var name by remember { mutableStateOf(preset?.let { resources.getString(it.defaultNameRes) }.orEmpty()) }
    var prompt by remember { mutableStateOf(preset?.let { resources.getString(it.promptRes) }.orEmpty()) }
    var triggerKind by remember { mutableStateOf(preset?.schedule?.kind ?: ScheduledTaskTriggerKind.DAILY) }
    var hour by remember { mutableIntStateOf(preset?.schedule?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(preset?.schedule?.minute ?: 0) }
    var daysOfWeek by remember { mutableStateOf(preset?.schedule?.daysOfWeek ?: setOf(1, 2, 3, 4, 5)) }
    var intervalMinutes by remember { mutableIntStateOf(preset?.schedule?.intervalMinutes ?: 120) }
    var onceTriggerAtMs by remember { mutableLongStateOf(System.currentTimeMillis() + 60 * 60_000L) }
    var cronExpression by remember { mutableStateOf("0 9 * * 1-5") }
    var deliveryMode by remember { mutableStateOf(preset?.deliveryMode ?: ScheduledTaskDeliveryMode.ALWAYS) }
    var showRunningNotification by remember { mutableStateOf(false) }
    var sessionMode by remember { mutableStateOf(preset?.sessionMode ?: ScheduledTaskSessionMode.ISOLATED) }
    var misfirePolicy by remember { mutableStateOf(ScheduledTaskMisfirePolicy.RUN_ONCE_WITHIN_WINDOW) }
    var quietHoursEnabled by remember { mutableStateOf(false) }
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    var selectedReasoningEffort by remember { mutableStateOf<String?>(null) }
    var capabilities by remember { mutableStateOf(preset?.capabilities ?: ScheduledTaskCapabilities()) }
    var timeoutSeconds by remember { mutableIntStateOf(ScheduledTask.DEFAULT_TIMEOUT_SECONDS) }
    var showModelSheet by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { RuntimeConfigRepository.ensureDefaults(EtaApp.serviceInstance) }
    LaunchedEffect(taskId) {
        if (!taskId.isNullOrBlank()) {
            val existing = withContext(Dispatchers.IO) { ScheduledTaskRepository.getTask(taskId, context) }
            if (existing != null) {
                loadedExisting = existing
                name = existing.name
                prompt = existing.prompt
                triggerKind = existing.schedule.kind
                hour = existing.schedule.hour
                minute = existing.schedule.minute
                daysOfWeek = existing.schedule.daysOfWeek
                intervalMinutes = existing.schedule.intervalMinutes
                existing.schedule.triggerAtEpochMs?.let { onceTriggerAtMs = it }
                cronExpression = existing.schedule.cronExpression.ifBlank { "0 9 * * 1-5" }
                deliveryMode = existing.deliveryMode
                showRunningNotification = existing.showRunningNotification
                sessionMode = existing.sessionMode
                misfirePolicy = existing.misfirePolicy
                quietHoursEnabled = existing.quietHours.enabled
                selectedModelId = existing.modelId
                selectedReasoningEffort = existing.reasoningEffort
                capabilities = existing.capabilities
                timeoutSeconds = existing.timeoutSeconds
            }
        }
        initialized = true
    }

    val currentSchedule = remember(triggerKind, hour, minute, daysOfWeek, intervalMinutes, onceTriggerAtMs, cronExpression) {
        ScheduledTaskSchedule(
            kind = triggerKind,
            triggerAtEpochMs = if (triggerKind == ScheduledTaskTriggerKind.ONCE) onceTriggerAtMs else null,
            hour = hour,
            minute = minute,
            daysOfWeek = daysOfWeek,
            intervalMinutes = intervalMinutes,
            cronExpression = cronExpression.trim(),
        )
    }
    val previewNowMs = remember(currentSchedule, quietHoursEnabled) { System.currentTimeMillis() }
    val scheduleError = remember(currentSchedule, previewNowMs) {
        ScheduledTaskScheduleCalculator.validateSchedule(currentSchedule, previewNowMs)
    }
    val nextRunPreview = remember(currentSchedule, previewNowMs, quietHoursEnabled, scheduleError) {
        scheduleError ?: run {
            val next = ScheduledTaskScheduleCalculator.computeNextRunAt(
                schedule = currentSchedule,
                afterMs = previewNowMs,
                quietHours = ScheduledTaskQuietHours(enabled = quietHoursEnabled),
            )
            "下次运行：${ScheduledTaskScheduleCalculator.formatNextRunLabel(next, previewNowMs)}"
        }
    }

    val pickerState = remember(providers, primaryProviderId, primaryModelId) {
        AgentModelPickerProjector.project(providers, primaryProviderId, primaryModelId)
    }
    val resolvedCustom = remember(providers, selectedModelId) {
        selectedModelId?.takeIf { it.isNotBlank() }?.let { providers.enabledModel(it) }
    }
    val primaryModelName = pickerState.selectedModel?.displayName
    val modelSummary = when {
        resolvedCustom != null -> "${resolvedCustom.provider.name} / ${resolvedCustom.model.displayName}"
        !primaryModelName.isNullOrBlank() -> "跟随默认（$primaryModelName）"
        else -> "跟随全局默认模型"
    }
    val effectiveCapabilities = remember(providers, resolvedCustom, primaryModelId) {
        val target = resolvedCustom ?: providers.enabledModel(primaryModelId)
        target?.let { (provider, model) -> RuntimeConfigRepository.reasoningCapabilities(provider, model) }
    }
    val effortOptions = remember(effectiveCapabilities) {
        listOf<ReasoningEffort?>(null) + effectiveCapabilities?.selectableEfforts.orEmpty()
    }
    val effortLabels = remember(effortOptions) {
        effortOptions.map { effort ->
            when (effort) {
                null -> "跟随全局默认"
                ReasoningEffort.DEFAULT -> "模型默认"
                else -> effort.displayName
            }
        }
    }
    val selectedEffortIndex = remember(effortOptions, selectedReasoningEffort) {
        val current = selectedReasoningEffort?.let(ReasoningEffort::fromWireValue)
        effortOptions.indexOf(current).takeIf { it >= 0 } ?: 0
    }

    val openTimePicker: () -> Unit = {
        TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
    }
    val openOncePicker: () -> Unit = {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(onceTriggerAtMs).atZone(zone)
        DatePickerDialog(context, { _, y, mon, d ->
            TimePickerDialog(context, { _, h, m ->
                onceTriggerAtMs = current.withYear(y).withMonth(mon + 1).withDayOfMonth(d)
                    .withHour(h).withMinute(m).withSecond(0).withNano(0).toInstant().toEpochMilli()
            }, current.hour, current.minute, true).show()
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    val canSave = initialized && !saving && name.isNotBlank() && prompt.isNotBlank() && scheduleError == null
    val onSaveClick: () -> Unit = {
        if (canSave) {
            saving = true
            scope.launch {
                val nowMs = System.currentTimeMillis()
                val existing = loadedExisting
                val taskToSave = (existing ?: ScheduledTask(id = UUID.randomUUID().toString(), name = name.trim(), prompt = prompt.trim())).copy(
                    name = name.trim(),
                    prompt = prompt.trim(),
                    enabled = existing?.enabled ?: true,
                    schedule = currentSchedule,
                    misfirePolicy = misfirePolicy,
                    quietHours = ScheduledTaskQuietHours(enabled = quietHoursEnabled),
                    deliveryMode = deliveryMode,
                    showRunningNotification = showRunningNotification,
                    sessionMode = sessionMode,
                    modelId = selectedModelId,
                    reasoningEffort = selectedReasoningEffort,
                    capabilities = capabilities,
                    timeoutSeconds = timeoutSeconds,
                )
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        ScheduledTaskRepository.saveTask(task = taskToSave, nowMs = nowMs, rescheduleAlarm = true, context = context)
                    }
                }
                saving = false
                result.fold(
                    onSuccess = { saved -> onSaved(saved.id) },
                    onFailure = { failure ->
                        EtaToastCenter.show(context, failure.message ?: "保存定时任务失败", Toast.LENGTH_SHORT)
                    },
                )
            }
        }
    }

    val triggerTabs = remember {
        listOf(
            ScheduledTaskTriggerKind.DAILY to "每天",
            ScheduledTaskTriggerKind.WEEKDAYS to "工作日",
            ScheduledTaskTriggerKind.WEEKLY to "每周",
            ScheduledTaskTriggerKind.INTERVAL to "间隔",
            ScheduledTaskTriggerKind.ONCE to "一次性",
            ScheduledTaskTriggerKind.CRON to "Cron",
        )
    }
    val deliveryOptions = listOf(
        ScheduledTaskDeliveryMode.ALWAYS to "每次完成后通知",
        ScheduledTaskDeliveryMode.ON_CHANGE to "仅发现变化时通知",
        ScheduledTaskDeliveryMode.SILENT to "静默归档不通知",
    )
    val sessionOptions = listOf(
        ScheduledTaskSessionMode.ISOLATED to "每次独立归档（主聊天保持干净）",
        ScheduledTaskSessionMode.DEDICATED to "固定专属对话（按时间续写）",
    )
    val misfireOptions = listOf(
        ScheduledTaskMisfirePolicy.RUN_ONCE_WITHIN_WINDOW to "3 小时内最多补跑最近 1 次",
        ScheduledTaskMisfirePolicy.SKIP to "错过即跳过",
    )
    val timeoutChoices = listOf(120, 300, 600, 900, 1800)
    val timeoutLabels = listOf("2 分钟", "5 分钟（推荐）", "10 分钟", "15 分钟", "30 分钟")
    val intervalChoices = listOf(15, 30, 60, 120, 240, 360, 720)
    val weekDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    MiuixScaffoldPage(
        title = stringResource(if (isEditing) R.string.route_scheduled_task_edit else R.string.route_scheduled_task_new),
        onBack = onBack,
        actions = {
            EtaIconButton(onClick = onSaveClick, enabled = canSave) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.action_save),
                    tint = if (canSave) EtaTheme.colors.accent else EtaTheme.colors.textTertiary,
                )
            }
        },
        overlay = {
            EtaSheet(
                show = showModelSheet,
                title = "执行模型",
                onDismissRequest = { showModelSheet = false },
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                SubagentModelPickerContent(
                    pickerState = pickerState,
                    selectedSecondaryModelId = selectedModelId,
                    primaryModelName = primaryModelName,
                    inheritLabel = "跟随全局默认模型",
                    onSelectModel = { id ->
                        showModelSheet = false
                        selectedModelId = id
                    },
                )
            }
        },
    ) {
        item(key = "content") {
            EtaListGroup(title = "任务内容") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = "任务名称（例如：每日晚间简报）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(8000) },
                        label = "要胖小胖做什么：目标、数据来源、想要的输出",
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item(key = "schedule") {
            EtaListGroup(title = "运行时间") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    triggerTabs.forEach { (kind, label) ->
                        EtaChoiceChip(label, selected = triggerKind == kind, onClick = {
                            triggerKind = kind
                            if (kind == ScheduledTaskTriggerKind.ONCE && onceTriggerAtMs <= System.currentTimeMillis()) {
                                onceTriggerAtMs = System.currentTimeMillis() + 60 * 60_000L
                            }
                        })
                    }
                }
                EtaPreferenceDivider(hasLeading = false)
                when (triggerKind) {
                    ScheduledTaskTriggerKind.DAILY, ScheduledTaskTriggerKind.WEEKDAYS, ScheduledTaskTriggerKind.WEEKLY -> {
                        if (triggerKind == ScheduledTaskTriggerKind.WEEKLY) {
                            FlowRow(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                (1..7).forEach { day ->
                                    val selected = day in daysOfWeek
                                    EtaChoiceChip("周${weekDayLabels[day - 1]}", selected = selected, onClick = {
                                        val next = if (selected) daysOfWeek - day else daysOfWeek + day
                                        if (next.isNotEmpty()) daysOfWeek = next
                                    })
                                }
                            }
                            EtaPreferenceDivider(hasLeading = false)
                        }
                        EtaArrowPreference(
                            title = "触发时间",
                            startAction = { EtaPreferenceIcon(Icons.Outlined.AccessTime, tint = EtaPreferenceColors.Blue) },
                            endActions = {
                                Text(
                                    ScheduledTaskScheduleCalculator.formatHourMinute(hour, minute),
                                    style = EtaTheme.type.body,
                                    color = EtaTheme.colors.textSecondary,
                                )
                            },
                            onClick = openTimePicker,
                        )
                    }
                    ScheduledTaskTriggerKind.INTERVAL -> {
                        EtaOverlayDropdownPreference(
                            title = "间隔",
                            items = intervalChoices.map { ScheduledTaskScheduleCalculator.formatInterval(it) },
                            selectedIndex = intervalChoices.indexOf(intervalMinutes).takeIf { it >= 0 } ?: 3,
                            startAction = { EtaPreferenceIcon(Icons.Outlined.AccessTime, tint = EtaPreferenceColors.Blue) },
                            onSelectedIndexChange = { intervalMinutes = intervalChoices[it] },
                        )
                    }
                    ScheduledTaskTriggerKind.ONCE -> {
                        EtaArrowPreference(
                            title = "运行时间",
                            startAction = { EtaPreferenceIcon(Icons.Outlined.Event, tint = EtaPreferenceColors.Blue) },
                            endActions = {
                                Text(
                                    ScheduledTaskUiFormatting.formatTimestamp(onceTriggerAtMs),
                                    style = EtaTheme.type.body,
                                    color = EtaTheme.colors.textSecondary,
                                )
                            },
                            onClick = openOncePicker,
                        )
                    }
                    ScheduledTaskTriggerKind.CRON -> {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            TextField(
                                value = cronExpression,
                                onValueChange = { cronExpression = it.take(120) },
                                label = "Cron：分 时 日 月 周（例如 0 9 * * 1-5）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                EtaPreferenceDivider(hasLeading = false)
                EtaPreference(
                    summary = nextRunPreview,
                    summaryColor = if (scheduleError != null) {
                        top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor(color = StatusError)
                    } else {
                        top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor()
                    },
                )
            }
        }
        item(key = "delivery") {
            EtaListGroup(title = "结果怎么给你") {
                EtaOverlayDropdownPreference(
                    title = "通知",
                    items = deliveryOptions.map { it.second },
                    selectedIndex = deliveryOptions.indexOfFirst { it.first == deliveryMode }.coerceAtLeast(0),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Notifications, tint = EtaPreferenceColors.Orange) },
                    onSelectedIndexChange = { deliveryMode = deliveryOptions[it].first },
                )
                EtaPreferenceDivider()
                EtaOverlayDropdownPreference(
                    title = "对话",
                    items = sessionOptions.map { it.second },
                    selectedIndex = sessionOptions.indexOfFirst { it.first == sessionMode }.coerceAtLeast(0),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Event, tint = EtaPreferenceColors.Teal) },
                    onSelectedIndexChange = { sessionMode = sessionOptions[it].first },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = "运行时显示进度通知",
                    summary = "关掉后在后台静默执行，不出运行中的通知，只在完成时按上面的设置推结果",
                    checked = showRunningNotification,
                    onCheckedChange = { showRunningNotification = it },
                )
            }
        }
        item(key = "model") {
            EtaListGroup(title = "模型") {
                EtaArrowPreference(
                    title = "执行模型",
                    summary = modelSummary,
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Cloud, tint = EtaPreferenceColors.Blue) },
                    onClick = { showModelSheet = true },
                )
                if (effortOptions.size > 1) {
                    EtaPreferenceDivider()
                    EtaOverlayDropdownPreference(
                        title = "思考强度",
                        items = effortLabels,
                        selectedIndex = selectedEffortIndex,
                        startAction = { EtaPreferenceIcon(Icons.Outlined.Psychology, tint = EtaPreferenceColors.Violet) },
                        onSelectedIndexChange = { selectedReasoningEffort = effortOptions.getOrNull(it)?.wireValue },
                    )
                }
            }
        }
        item(key = "capabilities") {
            EtaListGroup(title = "能用哪些工具（再和全局开关取交集）") {
                EtaSwitchPreference(
                    title = "网页与浏览器",
                    checked = capabilities.browserTools,
                    onCheckedChange = { capabilities = capabilities.copy(browserTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = "读取设备信息",
                    summary = "通知、日历、使用时长等",
                    checked = capabilities.deviceReadTools,
                    onCheckedChange = { capabilities = capabilities.copy(deviceReadTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = "控制设备状态",
                    summary = "闹钟、亮度、开关等会改设置的操作",
                    checked = capabilities.deviceActionTools,
                    onCheckedChange = { capabilities = capabilities.copy(deviceActionTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = "终端与文件",
                    checked = capabilities.terminalTools,
                    onCheckedChange = { capabilities = capabilities.copy(terminalTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = "MCP 工具",
                    checked = capabilities.mcpTools,
                    onCheckedChange = { capabilities = capabilities.copy(mcpTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = "操作应用界面",
                    summary = "打开第三方应用、点按、输入",
                    checked = capabilities.guiTools,
                    onCheckedChange = { capabilities = capabilities.copy(guiTools = it) },
                )
                if (capabilities.guiTools) {
                    EtaPreferenceDivider(hasLeading = false)
                    EtaSwitchPreference(
                        title = "在后台虚拟屏操作",
                        summary = if (capabilities.backgroundScreen) {
                            "界面操作放到后台虚拟屏，不占用你的屏幕；锁屏、熄屏也能执行（需要 Root）"
                        } else {
                            "在前台屏幕上操作；锁屏时不执行，改发提醒，解锁后点通知再跑"
                        },
                        checked = capabilities.backgroundScreen,
                        onCheckedChange = { capabilities = capabilities.copy(backgroundScreen = it) },
                    )
                }
            }
        }
        item(key = "advanced") {
            EtaListGroup(title = "高级") {
                EtaOverlayDropdownPreference(
                    title = "错过时间时",
                    items = misfireOptions.map { it.second },
                    selectedIndex = misfireOptions.indexOfFirst { it.first == misfirePolicy }.coerceAtLeast(0),
                    onSelectedIndexChange = { misfirePolicy = misfireOptions[it].first },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = "夜间免打扰",
                    summary = "23:00–07:30 之间的周期触发自动跳过",
                    checked = quietHoursEnabled,
                    onCheckedChange = { quietHoursEnabled = it },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaOverlayDropdownPreference(
                    title = "单次最长运行",
                    items = timeoutLabels,
                    selectedIndex = timeoutChoices.indexOf(timeoutSeconds).takeIf { it >= 0 } ?: 1,
                    onSelectedIndexChange = { timeoutSeconds = timeoutChoices[it] },
                )
            }
        }
    }
}
