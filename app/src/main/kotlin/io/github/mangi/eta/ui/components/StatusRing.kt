package io.github.mangi.eta.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TaskVisualStatus.color(): Color = when (this) {
    TaskVisualStatus.Running -> EtaTheme.colors.running
    TaskVisualStatus.Attention -> EtaTheme.colors.attention
    TaskVisualStatus.Paused -> EtaTheme.colors.paused
    TaskVisualStatus.Success -> EtaTheme.colors.success
    TaskVisualStatus.Failed -> EtaTheme.colors.danger
    TaskVisualStatus.Queued -> EtaTheme.colors.textTertiary
}

@Composable
fun TaskVisualStatus.textColor(): Color = when (this) {
    TaskVisualStatus.Attention -> EtaTheme.colors.attentionText
    TaskVisualStatus.Success -> EtaTheme.colors.successText
    TaskVisualStatus.Failed -> EtaTheme.colors.dangerText
    TaskVisualStatus.Running -> EtaTheme.colors.onAccentSoft
    else -> color()
}

@Composable
fun TaskVisualStatus.backgroundColor(): Color = when (this) {
    TaskVisualStatus.Running -> EtaTheme.colors.runningSoft
    TaskVisualStatus.Attention -> EtaTheme.colors.attentionSoft
    TaskVisualStatus.Paused, TaskVisualStatus.Queued -> EtaTheme.colors.surfaceSunken
    TaskVisualStatus.Success -> EtaTheme.colors.successSoft
    TaskVisualStatus.Failed -> EtaTheme.colors.dangerSoft
}

@Composable
fun TaskVisualStatus.label(): String = stringResource(when (this) {
    TaskVisualStatus.Running -> R.string.task_visual_running
    TaskVisualStatus.Attention -> R.string.task_visual_attention
    TaskVisualStatus.Paused -> R.string.task_visual_paused
    TaskVisualStatus.Success -> R.string.task_visual_success
    TaskVisualStatus.Failed -> R.string.task_visual_failed
    TaskVisualStatus.Queued -> R.string.task_visual_queued
})

/**
 * Pass visible=false for a retained, hidden surface. Clipped/offscreen and stopped owners
 * are also gated here. Frame values are read only during drawing, never composition.
 */
@Composable
fun StatusRing(
    status: TaskVisualStatus,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    visible: Boolean = true,
    contentDescription: String? = status.label(),
    monochrome: Color? = null,
) {
    val motion = EtaTheme.motion
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val view = LocalView.current
    var inViewport by remember { mutableStateOf(false) }
    val animate = status == TaskVisualStatus.Running && visible && inViewport &&
        lifecycleState.isAtLeast(Lifecycle.State.STARTED) && !motion.reduceMotion
    Box(modifier.size(size).onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        inViewport = bounds.width > 0f && bounds.height > 0f &&
            bounds.right > 0f && bounds.bottom > 0f && bounds.left < view.width && bounds.top < view.height
    }.semantics {
        contentDescription?.let { this.contentDescription = it }
    }) {
        // 状态切换：新图形从 0.6 放大淡入（运行圈 → 对勾像是「收拢」成结果），减少动画时只淡入淡出。
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                val enter = fadeIn(tween(motion.medium, easing = motion.emphasizedDecelerate))
                (if (motion.reduceMotion) enter else enter + scaleIn(tween(motion.medium, easing = motion.emphasizedDecelerate), initialScale = 0.6f)) togetherWith
                    fadeOut(tween(motion.exitDuration(motion.short), easing = motion.accelerate))
            },
            label = "eta_status",
        ) { drawnStatus ->
            StatusGlyph(drawnStatus, animate && drawnStatus == status, monochrome)
        }
    }
}

@Composable
private fun StatusGlyph(status: TaskVisualStatus, animate: Boolean, monochrome: Color?) {
    val colors = EtaTheme.colors
    val color = status.color()
    val motion = EtaTheme.motion
    val transition = if (animate) rememberInfiniteTransition(label = "eta_status_running") else null
    val rotation = transition?.animateFloat(0f, 360f,
        infiniteRepeatable(tween(motion.ringRotationMillis, easing = LinearEasing)), label = "eta_status_rotation")
    val aurora = transition?.animateFloat(0f, 360f,
        infiniteRepeatable(tween(motion.auroraMillis, easing = LinearEasing)), label = "eta_status_aurora")
    Box(Modifier.fillMaxSize().drawWithCache {
        val stroke = 2.dp.toPx().coerceAtMost(size.minDimension / 6f)
        val radius = (size.minDimension - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val origin = center - Offset(radius, radius)
        val arcSize = Size(radius * 2, radius * 2)
        val radians = Math.toRadians(colors.aurora.angleDegrees.toDouble())
        val direction = Offset(sin(radians).toFloat(), -cos(radians).toFloat()) * radius
        val brush = monochrome?.let { SolidColor(it) } ?: Brush.linearGradient(*colors.aurora.stops,
            start = center - direction, end = center + direction)
        onDrawBehind {
            when (status) {
                TaskVisualStatus.Running -> {
                    val phase = aurora?.value ?: 0f
                    rotate(phase) {
                        drawArc(brush, (rotation?.value ?: 0f) - phase - 90f, 270f, false,
                            origin, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                TaskVisualStatus.Queued -> repeat(8) { index ->
                    drawArc(color, index * 45f, 24f, false, origin, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                TaskVisualStatus.Paused -> {
                    drawCircle(color, radius, style = Stroke(stroke))
                    glyphLine(color, stroke, 0.40f, 0.35f, 0.40f, 0.65f)
                    glyphLine(color, stroke, 0.60f, 0.35f, 0.60f, 0.65f)
                }
                else -> {
                    drawCircle(color, radius + stroke / 2f)
                    when (status) {
                        TaskVisualStatus.Attention -> {
                            glyphLine(colors.statusGlyph, stroke, 0.5f, 0.27f, 0.5f, 0.53f)
                            drawCircle(colors.statusGlyph, stroke / 2f, Offset(size.width * 0.5f, size.height * 0.72f))
                        }
                        TaskVisualStatus.Success -> {
                            glyphLine(colors.statusGlyph, stroke, 0.27f, 0.50f, 0.44f, 0.67f)
                            glyphLine(colors.statusGlyph, stroke, 0.44f, 0.67f, 0.74f, 0.34f)
                        }
                        TaskVisualStatus.Failed -> {
                            glyphLine(colors.statusGlyph, stroke, 0.34f, 0.34f, 0.66f, 0.66f)
                            glyphLine(colors.statusGlyph, stroke, 0.66f, 0.34f, 0.34f, 0.66f)
                        }
                    }
                }
            }
        }
    })
}

private fun DrawScope.glyphLine(color: Color, width: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(color, Offset(size.width * x1, size.height * y1),
        Offset(size.width * x2, size.height * y2), width, StrokeCap.Round)
}
