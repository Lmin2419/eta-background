package io.github.mangi.eta.ui.screens.scheduled

import android.app.DatePickerDialog
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
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.StatusError
import io.github.mangi.eta.ui.model.AgentModelPickerProjector
import io.github.mangi.eta.ui.screens.subagent.SubagentModelPickerContent
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.suqi8.coui.kmp.basic.TimePicker as CouiTimePicker
import io.github.suqi8.coui.kmp.basic.TimeValue as CouiTimeValue
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
    val nextRunLabel = remember(currentSchedule, previewNowMs, quietHoursEnabled, scheduleError) {
        if (scheduleError != null) {
            null
        } else {
            val next = ScheduledTaskScheduleCalculator.computeNextRunAt(
                schedule = currentSchedule,
                afterMs = previewNowMs,
                quietHours = ScheduledTaskQuietHours(enabled = quietHoursEnabled),
            )
            ScheduledTaskScheduleCalculator.formatNextRunLabel(next, previewNowMs)
        }
    }
    val nextRunPreview = scheduleError ?: stringResource(R.string.scheduled_next_run_prefix, nextRunLabel.orEmpty())

    val pickerState = remember(providers, primaryProviderId, primaryModelId) {
        AgentModelPickerProjector.project(providers, primaryProviderId, primaryModelId)
    }
    val resolvedCustom = remember(providers, selectedModelId) {
        selectedModelId?.takeIf { it.isNotBlank() }?.let { providers.enabledModel(it) }
    }
    val primaryModelName = pickerState.selectedModel?.displayName
    val modelSummary = when {
        resolvedCustom != null -> "${resolvedCustom.provider.name} / ${resolvedCustom.model.displayName}"
        !primaryModelName.isNullOrBlank() -> stringResource(R.string.scheduled_model_follow_default_named, primaryModelName)
        else -> stringResource(R.string.scheduled_model_follow_default)
    }
    val effectiveCapabilities = remember(providers, resolvedCustom, primaryModelId) {
        val target = resolvedCustom ?: providers.enabledModel(primaryModelId)
        target?.let { (provider, model) -> RuntimeConfigRepository.reasoningCapabilities(provider, model) }
    }
    val effortOptions = remember(effectiveCapabilities) {
        listOf<ReasoningEffort?>(null) + effectiveCapabilities?.selectableEfforts.orEmpty()
    }
    val effortFollowLabel = stringResource(R.string.scheduled_effort_follow_default)
    val effortModelDefaultLabel = stringResource(R.string.scheduled_effort_model_default)
    val effortLabels = remember(effortOptions, effortFollowLabel, effortModelDefaultLabel) {
        effortOptions.map { effort ->
            when (effort) {
                null -> effortFollowLabel
                ReasoningEffort.DEFAULT -> effortModelDefaultLabel
                else -> effort.displayName
            }
        }
    }
    val selectedEffortIndex = remember(effortOptions, selectedReasoningEffort) {
        val current = selectedReasoningEffort?.let(ReasoningEffort::fromWireValue)
        effortOptions.indexOf(current).takeIf { it >= 0 } ?: 0
    }

    // 时间用 COUI 2.1.0 的原生时间滚轮（上游 05086d3a）；一次性任务先用系统日期选择，再选时间。
    // pickerTarget：null 不显示；"daily" 改每天/每周的触发时间；其余是一次性任务选好的日期（epoch ms，时间待定）。
    var pickerTarget by remember { mutableStateOf<String?>(null) }
    var pickerTime by remember { mutableStateOf(CouiTimeValue(hour = hour, minute = minute)) }
    val openTimePicker: () -> Unit = {
        pickerTime = CouiTimeValue(hour = hour, minute = minute)
        pickerTarget = "daily"
    }
    val openOncePicker: () -> Unit = {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(onceTriggerAtMs).atZone(zone)
        DatePickerDialog(context, { _, y, mon, d ->
            val day = current.withYear(y).withMonth(mon + 1).withDayOfMonth(d)
            pickerTime = CouiTimeValue(hour = current.hour, minute = current.minute)
            pickerTarget = day.toInstant().toEpochMilli().toString()
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    val saveFailedText = stringResource(R.string.scheduled_toast_save_failed)
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
                        EtaToastCenter.show(
                            context,
                            failure.message ?: saveFailedText,
                            Toast.LENGTH_SHORT,
                        )
                    },
                )
            }
        }
    }

    val triggerTabs = listOf(
        ScheduledTaskTriggerKind.DAILY to stringResource(R.string.scheduled_trigger_daily),
        ScheduledTaskTriggerKind.WEEKDAYS to stringResource(R.string.scheduled_trigger_weekdays),
        ScheduledTaskTriggerKind.WEEKLY to stringResource(R.string.scheduled_trigger_weekly),
        ScheduledTaskTriggerKind.INTERVAL to stringResource(R.string.scheduled_trigger_interval),
        ScheduledTaskTriggerKind.ONCE to stringResource(R.string.scheduled_trigger_once),
        ScheduledTaskTriggerKind.CRON to stringResource(R.string.scheduled_trigger_cron),
    )
    val deliveryOptions = listOf(
        ScheduledTaskDeliveryMode.ALWAYS to stringResource(R.string.scheduled_delivery_option_always),
        ScheduledTaskDeliveryMode.ON_CHANGE to stringResource(R.string.scheduled_delivery_option_on_change),
        ScheduledTaskDeliveryMode.SILENT to stringResource(R.string.scheduled_delivery_option_silent),
    )
    val sessionOptions = listOf(
        ScheduledTaskSessionMode.ISOLATED to stringResource(R.string.scheduled_session_option_isolated),
        ScheduledTaskSessionMode.DEDICATED to stringResource(R.string.scheduled_ed_session_dedicated),
    )
    val misfireOptions = listOf(
        ScheduledTaskMisfirePolicy.RUN_ONCE_WITHIN_WINDOW to stringResource(R.string.scheduled_misfire_option_catchup),
        ScheduledTaskMisfirePolicy.SKIP to stringResource(R.string.scheduled_misfire_option_skip),
    )
    val timeoutChoices = listOf(120, 300, 600, 900, 1800)
    val timeoutLabels = listOf(
        stringResource(R.string.scheduled_timeout_2m),
        stringResource(R.string.scheduled_timeout_5m_recommended),
        stringResource(R.string.scheduled_timeout_10m),
        stringResource(R.string.scheduled_timeout_15m),
        stringResource(R.string.scheduled_ed_timeout_30m),
    )
    val intervalChoices = listOf(15, 30, 60, 120, 240, 360, 720)
    val weekDayLabels = listOf(
        stringResource(R.string.scheduled_weekday_short_mon),
        stringResource(R.string.scheduled_weekday_short_tue),
        stringResource(R.string.scheduled_weekday_short_wed),
        stringResource(R.string.scheduled_weekday_short_thu),
        stringResource(R.string.scheduled_weekday_short_fri),
        stringResource(R.string.scheduled_weekday_short_sat),
        stringResource(R.string.scheduled_weekday_short_sun),
    )

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
            EtaWindowDialog(
                show = pickerTarget != null,
                title = stringResource(R.string.scheduled_editor_trigger_time),
                onDismissRequest = { pickerTarget = null },
            ) {
                // 新库的颜色默认是浅色，跟着胖小胖当前的深浅色切换，深色下数字才看得清。
                val dark = EtaTheme.colors.isDark
                io.github.suqi8.coui.kmp.theme.COUITheme(
                    colors = if (dark) io.github.suqi8.coui.kmp.theme.darkColorScheme() else io.github.suqi8.coui.kmp.theme.lightColorScheme(),
                ) {
                    CouiTimePicker(
                        value = pickerTime,
                        onValueChange = { pickerTime = it },
                        is24Hour = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MiuixDialogActions(
                    confirmText = stringResource(R.string.action_confirm),
                    onCancel = { pickerTarget = null },
                    onConfirm = {
                        val target = pickerTarget
                        if (target == "daily") {
                            hour = pickerTime.hour
                            minute = pickerTime.minute
                        } else if (target != null) {
                            val day = Instant.ofEpochMilli(target.toLong()).atZone(ZoneId.systemDefault())
                            onceTriggerAtMs = day.withHour(pickerTime.hour).withMinute(pickerTime.minute)
                                .withSecond(0).withNano(0).toInstant().toEpochMilli()
                        }
                        pickerTarget = null
                    },
                )
            }
            EtaSheet(
                show = showModelSheet,
                title = stringResource(R.string.scheduled_field_model),
                onDismissRequest = { showModelSheet = false },
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                SubagentModelPickerContent(
                    pickerState = pickerState,
                    selectedSecondaryModelId = selectedModelId,
                    primaryModelName = primaryModelName,
                    inheritLabel = stringResource(R.string.scheduled_model_follow_default),
                    onSelectModel = { id ->
                        showModelSheet = false
                        selectedModelId = id
                    },
                )
            }
        },
    ) {
        item(key = "content") {
            EtaListGroup(title = stringResource(R.string.scheduled_editor_section_content)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = stringResource(R.string.scheduled_editor_name_label_with_example),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(8000) },
                        label = stringResource(R.string.scheduled_ed_prompt_label),
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item(key = "schedule") {
            EtaListGroup(title = stringResource(R.string.scheduled_editor_section_schedule)) {
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
                                    EtaChoiceChip(weekDayLabels[day - 1], selected = selected, onClick = {
                                        val next = if (selected) daysOfWeek - day else daysOfWeek + day
                                        if (next.isNotEmpty()) daysOfWeek = next
                                    })
                                }
                            }
                            EtaPreferenceDivider(hasLeading = false)
                        }
                        EtaArrowPreference(
                            title = stringResource(R.string.scheduled_editor_trigger_time),
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
                            title = stringResource(R.string.scheduled_trigger_interval),
                            items = intervalChoices.map { ScheduledTaskScheduleCalculator.formatInterval(it) },
                            selectedIndex = intervalChoices.indexOf(intervalMinutes).takeIf { it >= 0 } ?: 3,
                            startAction = { EtaPreferenceIcon(Icons.Outlined.AccessTime, tint = EtaPreferenceColors.Blue) },
                            onSelectedIndexChange = { intervalMinutes = intervalChoices[it] },
                        )
                    }
                    ScheduledTaskTriggerKind.ONCE -> {
                        EtaArrowPreference(
                            title = stringResource(R.string.scheduled_editor_section_schedule),
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
                                label = stringResource(R.string.scheduled_ed_cron_label),
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
            EtaListGroup(title = stringResource(R.string.scheduled_ed_section_delivery)) {
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.scheduled_ed_field_notify),
                    items = deliveryOptions.map { it.second },
                    selectedIndex = deliveryOptions.indexOfFirst { it.first == deliveryMode }.coerceAtLeast(0),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Notifications, tint = EtaPreferenceColors.Orange) },
                    onSelectedIndexChange = { deliveryMode = deliveryOptions[it].first },
                )
                EtaPreferenceDivider()
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.scheduled_ed_field_conversation),
                    items = sessionOptions.map { it.second },
                    selectedIndex = sessionOptions.indexOfFirst { it.first == sessionMode }.coerceAtLeast(0),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Event, tint = EtaPreferenceColors.Teal) },
                    onSelectedIndexChange = { sessionMode = sessionOptions[it].first },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.scheduled_editor_show_progress_title),
                    summary = stringResource(R.string.scheduled_ed_show_progress_summary),
                    checked = showRunningNotification,
                    onCheckedChange = { showRunningNotification = it },
                )
            }
        }
        item(key = "model") {
            EtaListGroup(title = stringResource(R.string.scheduled_ed_section_model)) {
                EtaArrowPreference(
                    title = stringResource(R.string.scheduled_field_model),
                    summary = modelSummary,
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Cloud, tint = EtaPreferenceColors.Blue) },
                    onClick = { showModelSheet = true },
                )
                if (effortOptions.size > 1) {
                    EtaPreferenceDivider()
                    EtaOverlayDropdownPreference(
                        title = stringResource(R.string.scheduled_ed_reasoning_effort),
                        items = effortLabels,
                        selectedIndex = selectedEffortIndex,
                        startAction = { EtaPreferenceIcon(Icons.Outlined.Psychology, tint = EtaPreferenceColors.Violet) },
                        onSelectedIndexChange = { selectedReasoningEffort = effortOptions.getOrNull(it)?.wireValue },
                    )
                }
            }
        }
        item(key = "capabilities") {
            EtaListGroup(title = stringResource(R.string.scheduled_ed_section_tools)) {
                EtaSwitchPreference(
                    title = stringResource(R.string.scheduled_cap_short_browser),
                    checked = capabilities.browserTools,
                    onCheckedChange = { capabilities = capabilities.copy(browserTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.scheduled_ed_cap_device_read),
                    summary = stringResource(R.string.scheduled_ed_cap_device_read_summary),
                    checked = capabilities.deviceReadTools,
                    onCheckedChange = { capabilities = capabilities.copy(deviceReadTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.scheduled_ed_cap_device_action),
                    summary = stringResource(R.string.scheduled_ed_cap_device_action_summary),
                    checked = capabilities.deviceActionTools,
                    onCheckedChange = { capabilities = capabilities.copy(deviceActionTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.scheduled_cap_short_terminal),
                    checked = capabilities.terminalTools,
                    onCheckedChange = { capabilities = capabilities.copy(terminalTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.scheduled_ed_cap_mcp),
                    checked = capabilities.mcpTools,
                    onCheckedChange = { capabilities = capabilities.copy(mcpTools = it) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.scheduled_ed_cap_gui),
                    summary = stringResource(R.string.scheduled_ed_cap_gui_summary),
                    checked = capabilities.guiTools,
                    onCheckedChange = { capabilities = capabilities.copy(guiTools = it) },
                )
                if (capabilities.guiTools) {
                    EtaPreferenceDivider(hasLeading = false)
                    EtaSwitchPreference(
                        title = stringResource(R.string.scheduled_ed_background_screen),
                        summary = stringResource(
                            if (capabilities.backgroundScreen) R.string.scheduled_ed_background_screen_on_summary
                            else R.string.scheduled_ed_background_screen_off_summary,
                        ),
                        checked = capabilities.backgroundScreen,
                        onCheckedChange = { capabilities = capabilities.copy(backgroundScreen = it) },
                    )
                }
            }
        }
        item(key = "advanced") {
            EtaListGroup(title = stringResource(R.string.scheduled_ed_section_advanced)) {
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.scheduled_ed_misfire),
                    items = misfireOptions.map { it.second },
                    selectedIndex = misfireOptions.indexOfFirst { it.first == misfirePolicy }.coerceAtLeast(0),
                    onSelectedIndexChange = { misfirePolicy = misfireOptions[it].first },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.scheduled_ed_quiet_hours),
                    summary = stringResource(R.string.scheduled_ed_quiet_hours_summary),
                    checked = quietHoursEnabled,
                    onCheckedChange = { quietHoursEnabled = it },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.scheduled_ed_timeout),
                    items = timeoutLabels,
                    selectedIndex = timeoutChoices.indexOf(timeoutSeconds).takeIf { it >= 0 } ?: 1,
                    onSelectedIndexChange = { timeoutSeconds = timeoutChoices[it] },
                )
            }
        }
    }
}
