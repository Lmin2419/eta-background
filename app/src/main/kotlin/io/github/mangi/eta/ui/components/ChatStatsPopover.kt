package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.TokenUsageUi
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 每条回复正文下方的一行统计小字：`52 tok/s · 首字 0.6s · 4.8s`（生成速度、平均首字时间、总用时，
 * 没有数据的段省略）。11sp、等宽数字、最淡的文字色；点一下从这里展开本轮统计卡片。
 */
@Composable
internal fun TurnStatsLine(
    usage: TokenUsageUi,
    modifier: Modifier = Modifier,
) {
    if (!usage.hasPerformanceStats) return
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val steps = (usage.stepCount ?: 1).coerceAtLeast(1)
    val llmMs = usage.llmMs ?: 0L
    val toolMs = usage.toolMs ?: 0L
    val ttftSteps = usage.ttftSteps ?: if (usage.ttftMs != null) 1 else 0
    val avgTtftMs = if (ttftSteps > 0 && usage.ttftMs != null) usage.ttftMs / ttftSteps else null
    val decodeTokens = usage.decodeTokens ?: usage.outputTokens
    val tps = if (usage.decodeMs != null && usage.decodeMs > 0L && decodeTokens != null && decodeTokens > 0) {
        decodeTokens.toDouble() / (usage.decodeMs.toDouble() / 1_000.0)
    } else {
        null
    }
    val speedText = tps?.let { "${formatTokensPerSecond(it)} tok/s" }
    val ttftLabel = avgTtftMs?.takeIf { it > 0L }?.let { stringResource(R.string.turn_stats_ttft, formatStatShort(it)) }
    val total = (llmMs + toolMs).takeIf { it > 0L }?.let(::formatStatShort)
    val line = listOfNotNull(speedText, ttftLabel, total).joinToString(" · ")
    if (line.isEmpty()) return
    val colors = io.github.mangi.eta.ui.theme.EtaTheme.colors
    val description = stringResource(R.string.turn_stats_open)
    Text(
        text = line,
        style = io.github.mangi.eta.ui.theme.EtaTypography.tabular(io.github.mangi.eta.ui.theme.EtaTheme.type.micro),
        color = colors.textQuiet,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(top = 8.dp)
            .onGloballyPositioned { anchor = it.boundsInWindow() }
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClickLabel = description, role = Role.Button) { open = true }
            .padding(vertical = 4.dp),
    )
    ChatStatsPopoverPopup(show = open, anchor = anchor, onDismissRequest = { open = false }) {
        StatsSectionHeader(
            icon = ChatActionIcons.Gauge,
            title = stringResource(R.string.turn_stats_title),
            value = stringResource(R.string.turn_stats_steps, steps),
        )
        StatsSectionDivider()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (llmMs > 0L) {
                StatsDetailRow(label = stringResource(R.string.turn_stats_model_time), value = formatStatDuration(llmMs))
            }
            if (toolMs > 0L) {
                StatsDetailRow(label = stringResource(R.string.turn_stats_tool_time), value = formatStatDuration(toolMs))
            }
            if (avgTtftMs != null && avgTtftMs > 0L) {
                StatsDetailRow(label = stringResource(R.string.turn_stats_ttft_label), value = formatStatDuration(avgTtftMs))
            }
            if (speedText != null) {
                StatsDetailRow(label = stringResource(R.string.turn_stats_speed_label), value = speedText)
            }
        }
    }
}

/**
 * 输入框右下角上下文圆环弹出的统计卡片：
 * 整合当前上下文窗口占用、会话累计 Token 明细（含缓存命中率）以及整场会话性能统计。
 */
@Composable
internal fun ContextUsageStatsPopoverPopup(
    usage: AgentContextUsageUi,
    summaryText: String,
    detailText: String,
    canCompact: Boolean,
    onCompact: () -> Unit,
    onDismissRequest: () -> Unit,
    show: Boolean = true,
    anchor: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero,
    backgroundCompacting: Boolean = false,
) {
    val stats = usage.sessionStats
    val cacheHit = if (stats.promptTokens > 0 && stats.cachedTokens > 0) {
        formatCacheHitPercent(stats.cachedTokens, stats.promptTokens)
    } else {
        null
    }
    val avgTtftMs = if (stats.ttftSteps > 0) stats.ttftMs / stats.ttftSteps else null
    val sessionSpeed = if (stats.decodeMs > 0L && stats.decodeTokens > 0) {
        "${formatTokensPerSecond(stats.decodeTokens.toDouble() / (stats.decodeMs.toDouble() / 1_000.0))} tok/s"
    } else {
        null
    }

    ChatStatsPopoverPopup(show = show, anchor = anchor, align = EtaPopoverAlign.End, onDismissRequest = onDismissRequest) {
        if (backgroundCompacting) {
            Text(
                text = stringResource(R.string.context_background_compacting),
                style = io.github.mangi.eta.ui.theme.EtaTheme.type.footnote,
                color = io.github.mangi.eta.ui.theme.EtaTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        StatsSectionHeader(
            icon = ChatActionIcons.Database,
            title = if (stats.hasTokenUsage) "Token 用量" else stringResource(R.string.ui_contextual_usage_d12810),
            value = if (stats.hasTokenUsage) "${formatExactTokenCount(stats.totalTokens)} tok" else null,
        )
        StatsSectionDivider()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (usage.contextTokens != null && usage.contextWindow != null && usage.contextWindow > 0) {
                StatsDetailRow(
                    label = stringResource(R.string.ui_contextual_usage_d12810),
                    value = summaryText.replace(" tokens · ", " · "),
                )
            } else {
                Text(
                    text = detailText,
                    style = MiuixTheme.textStyles.footnote1.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (stats.hasTokenUsage) {
                if (stats.promptTokens > 0 || stats.outputTokens > 0) {
                    if (cacheHit != null) {
                        StatsDetailRow(label = "缓存命中", value = "$cacheHit%")
                    }
                    StatsDetailRow(
                        label = "未命中",
                        value = "${formatExactTokenCount(stats.uncachedInputTokens)} tok",
                    )
                    if (stats.hasCacheWriteTokens || stats.cacheWriteTokens > 0) {
                        StatsDetailRow(
                            label = "缓存写入",
                            value = "${formatExactTokenCount(stats.cacheWriteTokens)} tok",
                        )
                    }
                    if (stats.cachedTokens > 0) {
                        StatsDetailRow(
                            label = "缓存读取",
                            value = "${formatExactTokenCount(stats.cachedTokens)} tok",
                        )
                    }
                    StatsOutputRow(
                        outputTokens = stats.outputTokens,
                        reasoningTokens = stats.reasoningTokens,
                    )
                }
                if (stats.compactionCount > 0) {
                    val lastIn = stats.lastCompactionPromptTokens ?: stats.compactionPromptTokens
                    val lastCached = stats.lastCompactionCachedTokens ?: stats.compactionCachedTokens
                    val lastHit = formatCacheHitPercent(lastCached, lastIn)
                    val label = when {
                        stats.compactionCount == 1 && stats.lastCompactionArchiveId.isNotBlank() ->
                            "最近压缩（${stats.lastCompactionArchiveId}）"
                        stats.compactionCount == 1 -> "最近压缩"
                        else -> "压缩请求（${stats.compactionCount} 次）"
                    }
                    val detailValue = buildString {
                        append("${formatExactTokenCount(lastIn)} tok")
                        lastHit?.let { append(" · 命中 $it%") }
                    }
                    StatsDetailRow(label = label, value = detailValue)
                    if (stats.compactionCount > 1) {
                        StatsDetailRow(
                            label = "压缩累计",
                            value = "${formatExactTokenCount(stats.compactionPromptTokens + stats.compactionOutputTokens)} tok",
                        )
                    }
                    stats.lastCompactionPaybackTurns?.let { paybackTurns ->
                        val paybackValue = if (stats.lastCompactionPaidBack) {
                            "已回本（已 ${stats.lastCompactionTurnsAfter} 轮）"
                        } else {
                            "需 $paybackTurns 轮（已 ${stats.lastCompactionTurnsAfter} 轮）"
                        }
                        StatsDetailRow(label = "压缩回本", value = paybackValue)
                    }
                }
                if (stats.subagentCount > 0) {
                    val subLabel = if (stats.subagentCount == 1) "子智能体" else "子智能体（${stats.subagentCount} 次）"
                    val subHit = formatCacheHitPercent(stats.subagentCachedTokens, stats.subagentPromptTokens)
                    val subValue = buildString {
                        append("${formatExactTokenCount(stats.subagentPromptTokens)} tok")
                        subHit?.let { append(" · 命中 $it%") }
                    }
                    StatsDetailRow(label = subLabel, value = subValue)
                }
                if (stats.ttlOrPrefixMissTokens > 5_000) {
                    StatsDetailRow(
                        label = "非增量未命中",
                        value = "${formatExactTokenCount(stats.ttlOrPrefixMissTokens)} tok",
                    )
                }
            }
        }

        if (stats.hasPerformanceStats) {
            Spacer(Modifier.height(14.dp))
            StatsSectionHeader(
                icon = ChatActionIcons.Gauge,
                title = "会话统计",
                value = "${stats.turns} 轮 ${stats.steps} 步",
            )
            StatsSectionDivider()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stats.llmMs > 0L) {
                    StatsDetailRow(label = "模型用时", value = formatStatDuration(stats.llmMs))
                }
                if (stats.toolMs > 0L) {
                    StatsDetailRow(label = "工具调用用时", value = formatStatDuration(stats.toolMs))
                }
                if (avgTtftMs != null && avgTtftMs > 0L) {
                    StatsDetailRow(label = "首 token 平均（TTFT）", value = formatStatDuration(avgTtftMs))
                }
                if (sessionSpeed != null) {
                    StatsDetailRow(label = "输出速度（TPS）", value = sessionSpeed)
                }
            }
        }

        if (canCompact) {
            Spacer(Modifier.height(12.dp))
            val compactLabel = stringResource(R.string.context_compact_action)
            EtaButton(
                compactLabel,
                onClick = {
                    onDismissRequest()
                    onCompact()
                },
                kind = EtaButtonKind.Tonal,
                minHeight = 44.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun ChatStatsPopoverPopup(
    onDismissRequest: () -> Unit,
    show: Boolean = true,
    anchor: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero,
    align: EtaPopoverAlign = EtaPopoverAlign.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 统计卡片是一枚玻璃小窗：从点击处展开、原路收回，背后内容被模糊。
    EtaPopover(show, anchor, onDismissRequest, align = align, minWidth = 256.dp, maxWidth = 320.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), content = content)
    }
}

@Composable
private fun StatsSectionHeader(
    icon: ImageVector,
    title: String,
    value: String?,
) {
    val primaryColor = MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = primaryColor,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote1.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = primaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!value.isNullOrBlank()) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = value,
                style = MiuixTheme.textStyles.footnote1.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = primaryColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatsSectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

@Composable
private fun StatsDetailRow(
    label: String,
    value: String,
) {
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val valueColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFeatureSettings = "tnum",
            ),
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatsOutputRow(
    outputTokens: Int,
    reasoningTokens: Int,
) {
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val valueColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "输出",
            style = MiuixTheme.textStyles.footnote1.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            color = labelColor,
        )
        Spacer(Modifier.width(16.dp))
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = valueColor)) {
                append("${formatExactTokenCount(outputTokens)} tok")
            }
            if (reasoningTokens > 0) {
                withStyle(SpanStyle(color = labelColor)) {
                    append("（其中推理 ${formatExactTokenCount(reasoningTokens)} tok）")
                }
            }
        }
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFeatureSettings = "tnum",
            ),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/** 统计小字用的短格式：`0.6s`、`12s`、`1m 12s`。 */
internal fun formatStatShort(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    if (clamped < 10_000L) {
        val tenths = (clamped / 100.0).roundToLong()
        return "${tenths / 10L}.${tenths % 10L}s"
    }
    val seconds = (clamped / 1_000.0).roundToLong()
    return if (seconds < 60L) "${seconds}s" else "${seconds / 60L}m ${seconds % 60L}s"
}

internal fun formatStatDuration(ms: Long): String {
    val clampedMs = ms.coerceAtLeast(0L)
    val seconds = clampedMs / 1_000.0
    if (seconds < 60.0) {
        val tenths = (seconds * 10.0).roundToLong()
        val whole = tenths / 10L
        val decimal = tenths % 10L
        return if (decimal == 0L) "${whole}秒" else "$whole.${decimal}秒"
    }
    val totalSeconds = seconds.roundToLong()
    val minutes = totalSeconds / 60L
    val remSeconds = totalSeconds % 60L
    return "${minutes}分${remSeconds}秒"
}

internal fun formatTokensPerSecond(tps: Double): String {
    val clamped = tps.coerceAtLeast(0.0)
    if (clamped >= 10.0) {
        return clamped.roundToInt().toString()
    }
    val tenths = (clamped * 10.0).roundToInt()
    val whole = tenths / 10
    val decimal = tenths % 10
    return if (decimal == 0) whole.toString() else "$whole.$decimal"
}

internal fun formatExactTokenCount(tokens: Int, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getIntegerInstance(locale).format(tokens.coerceAtLeast(0))

internal fun formatCacheHitPercent(cachedTokens: Int, promptTokens: Int): String? {
    if (promptTokens <= 0 || cachedTokens < 0) return null
    if (cachedTokens >= promptTokens) return "100"
    val ratio = cachedTokens.toDouble() / promptTokens.toDouble() * 100.0
    val rounded = ratio.roundToInt()
    if (rounded < 100) return rounded.toString()
    val oneDecimal = (ratio * 10.0).toInt() / 10.0
    return if (oneDecimal < 100.0) "%.1f".format(Locale.US, oneDecimal) else "99.9"
}
