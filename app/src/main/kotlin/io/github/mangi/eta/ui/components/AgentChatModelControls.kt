package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.formatContextUsage
import io.github.mangi.eta.ui.theme.EtaTheme

/** 用量达到这个比例才在输入胶囊里显示圆环；更低时不打扰。 */
internal const val ContextRingThreshold = 0.30f

/**
 * 输入胶囊里的上下文圆环：16dp，`brand` 弧 + `outline` 轨道，用量 ≥ 30% 才显示。
 * 后台正在整理较早的对话时，旁边一个呼吸的小点。点一下从这里展开用量小窗（含「压缩」）。
 */
@Composable
internal fun AgentContextUsageButton(
    usage: AgentContextUsageUi,
    onCompact: () -> Unit = {},
    canCompact: Boolean = false,
    modifier: Modifier = Modifier,
    backgroundCompacting: Boolean = false,
) {
    val progress = usage.progress ?: 0f
    val colors = EtaTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val summary = formatContextUsage(
        usage = usage,
        noUsageText = stringResource(R.string.context_no_previous_usage),
        noLimitText = stringResource(R.string.context_no_model_limit),
        locale = locale,
    )
    val detail = when {
        usage.contextWindow == null -> summary
        usage.contextTokens == null -> stringResource(R.string.context_usage_after_response)
        else -> stringResource(R.string.context_usage_previous_response, summary)
    }
    val usageDescription = stringResource(R.string.context_usage_ring, (progress * 100).toInt().coerceIn(0, 100))
    var showPopover by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(EtaTheme.motion.medium), label = "contextRing")
    val motion = EtaTheme.motion
    val breath = if (backgroundCompacting && !motion.reduceMotion) {
        rememberInfiniteTransition(label = "compactDot").animateFloat(0.35f, 1f,
            infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
    } else null
    // 用量接近满时弧线转为提醒色；其余时候只用品牌色这一处。
    val arc = if (progress >= 0.95f) colors.danger else colors.brand
    val track = colors.outline
    val dot = colors.brand
    Box(
        modifier
            .requiredSize(40.dp, ChatInputActionSize)
            .onGloballyPositioned { anchor = it.boundsInWindow() }
            .clip(CircleShape)
            .clickable(onClickLabel = usageDescription) { showPopover = true }
            .semantics { contentDescription = usageDescription; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.requiredSize(16.dp)) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(arc, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (backgroundCompacting) {
            Canvas(Modifier.align(Alignment.Center).offset(x = 11.dp, y = (-8).dp).size(5.dp)) {
                drawCircle(dot, alpha = breath?.value ?: 1f)
            }
        }
    }
    ContextUsageStatsPopoverPopup(
        usage = usage,
        summaryText = summary,
        detailText = detail,
        canCompact = canCompact,
        onCompact = onCompact,
        onDismissRequest = { showPopover = false },
        show = showPopover,
        anchor = anchor,
        backgroundCompacting = backgroundCompacting,
    )
}
