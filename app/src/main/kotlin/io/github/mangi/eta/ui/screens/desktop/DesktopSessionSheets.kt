package io.github.mangi.eta.ui.screens.desktop

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.remote.DesktopSessionState
import io.github.mangi.eta.agent.remote.DesktopSubagent
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.ui.components.EtaChoiceChip
import io.github.mangi.eta.ui.components.EtaListGroup
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaSheet
import io.github.mangi.eta.ui.components.ModelPickerRow
import io.github.mangi.eta.ui.components.StatusIdle
import io.github.mangi.eta.ui.components.StatusRunning
import io.github.mangi.eta.ui.components.StatusSuccess
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 用资源字符串构造投影文案，随语言切换。 */
@Composable
internal fun rememberDesktopLabels(): DesktopChatProjector.Labels {
    val res = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(res, locale) {
        val duration: (Long) -> String = { ms -> formatDuration(res, ms) }
        DesktopChatProjector.Labels(
            retryPrompt = res.getString(R.string.desktop_retry_prompt),
            attachment = res.getString(R.string.desktop_attachment),
            image = res.getString(R.string.desktop_image),
            turnTitle = { d, tokens, hit ->
                listOfNotNull(res.getString(R.string.desktop_turn), d, tokens, hit?.let { res.getString(R.string.desktop_cache_hit, it) })
                    .joinToString(" · ")
            },
            modelLine = { res.getString(R.string.desktop_model_line, it) },
            usageLine = { input, read, write, output, reasoning ->
                buildString {
                    append(res.getString(R.string.desktop_usage_input, input))
                    append(" · ").append(res.getString(R.string.desktop_usage_cache_read, read))
                    if (write != null) append(" · ").append(res.getString(R.string.desktop_usage_cache_write, write))
                    append(" · ").append(res.getString(R.string.desktop_usage_output, output))
                    if (reasoning != null) append(res.getString(R.string.desktop_usage_reasoning, reasoning))
                }
            },
            speedLine = { ttft, tps, tools ->
                listOfNotNull(
                    ttft?.let { res.getString(R.string.desktop_speed_ttft, it) },
                    tps?.let { "$it tok/s" },
                    tools?.let { res.getString(R.string.desktop_speed_tools, it) },
                ).joinToString(" · ")
            },
            timeLine = { start, steps -> res.getString(R.string.desktop_time_line, start, steps) },
            trigger = { kind, summary ->
                val base = res.getString(when (kind) {
                    "subagent-settled" -> R.string.desktop_trigger_subagent_settled
                    "agent-message" -> R.string.desktop_trigger_agent_message
                    "schedule" -> R.string.desktop_trigger_schedule
                    "goal" -> R.string.desktop_trigger_goal
                    "team" -> R.string.desktop_trigger_team
                    "webhook" -> R.string.desktop_trigger_webhook
                    else -> R.string.desktop_trigger_other
                })
                if (summary.isNullOrBlank()) base else "$base · $summary"
            },
            duration = duration,
            clock = { epoch ->
                val zone = ZoneId.systemDefault()
                val time = Instant.ofEpochMilli(epoch).atZone(zone)
                val skeleton = if (time.toLocalDate() == LocalDate.now(zone)) "HHmm" else "MMMdHHmm"
                time.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
            },
        )
    }
}

internal fun formatDuration(res: Resources, ms: Long): String = when {
    ms < 1_000 -> res.getString(R.string.desktop_duration_ms, ms.toInt())
    ms < 60_000 -> res.getString(R.string.desktop_duration_s, DesktopChatProjector.formatRate(ms / 1000.0))
    ms < 3_600_000 -> res.getString(R.string.desktop_duration_m, (ms / 60_000).toInt(), ((ms / 1000) % 60).toInt())
    else -> res.getString(R.string.desktop_duration_h, (ms / 3_600_000).toInt(), ((ms / 60_000) % 60).toInt())
}

/** 页头下方的一行统计：轮/步、缓存命中、总 token、输出速度，运行中附带已用时长。 */
@Composable
internal fun DesktopStatsLine(state: DesktopSessionState, runningForMs: Long?, onClick: () -> Unit) {
    val res = LocalResources.current
    val parts = buildList {
        state.stats?.takeIf { it.turns > 0 || it.steps > 0 }?.let { add(res.getString(R.string.desktop_stats_counts, it.turns, it.steps)) }
        state.usage?.let { usage ->
            usage.cacheHitPercent?.let { add(res.getString(R.string.desktop_cache_hit, it)) }
            if (usage.total > 0) add("${DesktopChatProjector.compactTokens(usage.total)} tok")
        }
        state.stats?.tokensPerSecond?.let { add("${DesktopChatProjector.formatRate(it)} tok/s") }
        runningForMs?.let { add(res.getString(R.string.desktop_running_for, formatDuration(res, it))) }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = EtaTheme.type.caption,
        color = EtaTheme.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

/** 会话设置：选模型、推理强度和完整的会话统计（对应桌面输入区的统计胶囊与弹窗）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopSessionSheet(
    show: Boolean,
    state: DesktopSessionState,
    picker: AgentModelPickerUiState,
    efforts: List<ReasoningEffort>,
    effort: ReasoningEffort,
    allowModelChange: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onModel: (String) -> Unit,
    onEffort: (ReasoningEffort) -> Unit,
) {
    val res = LocalResources.current
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.8f }
    EtaSheet(show, title = stringResource(R.string.desktop_session_settings), onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (allowModelChange && picker.providerGroups.isNotEmpty()) {
                item(key = "models-title") { EtaPreferenceGroupTitle(stringResource(R.string.desktop_models)) }
                picker.providerGroups.forEach { group ->
                    item(key = "provider:${group.providerId}") {
                        Text(group.providerName, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp))
                    }
                    items(group.models, key = { "model:${it.id}" }) { model ->
                        ModelPickerRow(model, model.id == picker.selectedModel?.id, { onModel(model.id) }, enabled = !busy)
                    }
                }
            }
            if (allowModelChange && efforts.any { it != ReasoningEffort.OFF } ) {
                item(key = "effort") {
                    EtaPreferenceGroupTitle(stringResource(R.string.desktop_reasoning_effort), Modifier.padding(top = 12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        efforts.forEach { option ->
                            EtaChoiceChip(option.displayName, option == effort, { onEffort(option) }, enabled = !busy)
                        }
                    }
                }
            }
            item(key = "stats") {
                EtaPreferenceGroupTitle(stringResource(R.string.desktop_stats_title), Modifier.padding(top = 16.dp))
                val rows = buildList {
                    state.stats?.let { add(res.getString(R.string.desktop_stats_counts, it.turns, it.steps) to "") }
                    state.usage?.let { usage ->
                        add(res.getString(R.string.desktop_stats_total) to DesktopChatProjector.exactTokens(usage.total))
                        usage.cacheHitPercent?.let { add(res.getString(R.string.desktop_stats_cache_hit_label) to "$it%") }
                        add(res.getString(R.string.desktop_stats_input) to DesktopChatProjector.exactTokens(usage.uncachedInput))
                        add(res.getString(R.string.desktop_stats_cache_read) to DesktopChatProjector.exactTokens(usage.cacheRead))
                        if (usage.cacheWrite > 0) add(res.getString(R.string.desktop_stats_cache_write) to DesktopChatProjector.exactTokens(usage.cacheWrite))
                        add(res.getString(R.string.desktop_stats_output) to DesktopChatProjector.exactTokens(usage.output))
                    }
                    state.pressure?.let { p ->
                        val used = p.pressureTokens
                        val window = p.contextWindow
                        if (used != null) add(res.getString(R.string.desktop_stats_context) to buildString {
                            append(DesktopChatProjector.compactTokens(used))
                            if (window != null && window > 0) append(" / ${DesktopChatProjector.compactTokens(window)}（${(used * 100 / window)}%）")
                        })
                    }
                    state.stats?.let { s ->
                        if (s.llmMs > 0) add(res.getString(R.string.desktop_stats_llm) to formatDuration(res, s.llmMs))
                        if (s.toolMs > 0) add(res.getString(R.string.desktop_stats_tool) to formatDuration(res, s.toolMs))
                        s.averageTtftMs?.let { add(res.getString(R.string.desktop_stats_ttft) to formatDuration(res, it)) }
                        s.tokensPerSecond?.let { add(res.getString(R.string.desktop_stats_tps) to "${DesktopChatProjector.formatRate(it)} tok/s") }
                    }
                }
                EtaListGroup(modifier = Modifier.fillMaxWidth()) {
                    rows.forEachIndexed { index, (label, value) ->
                        if (index > 0) EtaPreferenceDivider(false)
                        EtaPreference(title = label, endActions = { Text(value, style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary) })
                    }
                }
            }
        }
    }
}

/** 子代理目录：对应桌面主会话页头的子代理菜单，点开进入子代理对话。 */
@Composable
internal fun DesktopSubagentSheet(
    show: Boolean,
    children: List<DesktopSubagent>?,
    onDismiss: () -> Unit,
    onOpen: (DesktopSubagent) -> Unit,
) {
    val res = LocalResources.current
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.75f }
    EtaSheet(show, title = stringResource(R.string.desktop_subagents), onDismissRequest = onDismiss) {
        when {
            children == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() }
            children.isEmpty() -> Text(
                stringResource(R.string.desktop_subagents_empty),
                style = EtaTheme.type.subhead,
                color = EtaTheme.colors.textSecondary,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(children, key = { it.id }) { child ->
                    val status = when {
                        child.mode == "external" -> res.getString(R.string.desktop_subagent_external)
                        child.running -> res.getString(R.string.desktop_subagent_running)
                        child.timing?.lastTurnCompleted == true -> res.getString(R.string.desktop_subagent_done)
                        else -> res.getString(R.string.desktop_subagent_idle)
                    }
                    val color = when {
                        child.running -> StatusRunning
                        child.timing?.lastTurnCompleted == true -> StatusSuccess
                        else -> StatusIdle
                    }
                    val facts = listOfNotNull(
                        status,
                        child.name?.takeIf { it != child.displayTitle },
                        child.usage?.total?.takeIf { it > 0 }?.let { "${DesktopChatProjector.compactTokens(it)} tok" },
                        child.timing?.settledMs?.takeIf { it > 0 }?.let { formatDuration(res, it) },
                    ).joinToString(" · ")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = child.openable) { onOpen(child) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(color, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(child.displayTitle, style = EtaTheme.type.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(facts, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
