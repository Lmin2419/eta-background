package io.github.mangi.eta.ui.components

import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon

internal enum class ComposerAction { Microphone, Send, Stop, Queue, Disabled }

internal fun resolveComposerAction(streaming: Boolean, compacting: Boolean, hasContent: Boolean): ComposerAction = when {
    compacting -> if (streaming) ComposerAction.Stop else ComposerAction.Disabled
    streaming -> if (hasContent) ComposerAction.Queue else ComposerAction.Stop
    hasContent -> ComposerAction.Send
    else -> ComposerAction.Microphone
}

/** Long press arms an action; only an uncancelled release inside the target commits it. */
@Composable
internal fun ComposerActionButton(
    action: ComposerAction,
    label: String,
    longPressLabel: String,
    onClick: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: (Boolean) -> Unit,
    onAccessibleLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    plain: Boolean = false,
) {
    val click by rememberUpdatedState(onClick)
    val holdStart by rememberUpdatedState(onHoldStart)
    val holdEnd by rememberUpdatedState(onHoldEnd)
    val accessibleLongClick by rememberUpdatedState(onAccessibleLongClick)
    val enabled = action != ComposerAction.Disabled
    val supportsHold = action == ComposerAction.Queue || action == ComposerAction.Microphone
    val colors = EtaTheme.colors
    val motion = EtaTheme.motion
    // 发送键是一屏里唯一的珊瑚橘（brand）；停止是黑底白方块；排队中性灰；没有内容时是灰底淡箭头。
    val background by animateColorAsState(when {
        plain -> androidx.compose.ui.graphics.Color.Transparent
        action == ComposerAction.Send -> colors.brand
        action == ComposerAction.Queue -> colors.accentSoft
        action == ComposerAction.Stop -> colors.textPrimary
        else -> colors.surfaceSunken
    }, tween(motion.short), label = "composerActionColor")
    val foreground = when {
        plain -> colors.textSecondary
        action == ComposerAction.Send -> colors.onBrand
        action == ComposerAction.Queue -> colors.onAccentSoft
        action == ComposerAction.Stop -> colors.canvas
        else -> colors.textQuiet
    }
    val flash = remember { Animatable(0f) }
    var sent by remember { mutableIntStateOf(0) }
    LaunchedEffect(sent) {
        if (sent > 0) { flash.snapTo(1f); flash.animateTo(0f, tween(motion.short)) }
    }
    val haptics = rememberEtaHaptics()
    val trigger = { if (action == ComposerAction.Send) sent++; haptics.tick(); click() }
    val latestTrigger by rememberUpdatedState(trigger)
    val aurora = colors.aurora
    // 固定尺寸：空间不够时让文字省略，按钮不变形（§2.3）。
    Box(modifier.requiredSize(if (plain) 44.dp else 48.dp, 48.dp)
        .semantics {
            role = Role.Button
            contentDescription = label
            if (!enabled) disabled()
            onClick { if (enabled) latestTrigger(); enabled }
            if (enabled && supportsHold) onLongClick(longPressLabel) { accessibleLongClick(); true }
        }
        .onKeyEvent { event ->
            if (enabled && event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.Spacebar)) {
                latestTrigger(); true
            } else false
        }.focusable(enabled)
        .pointerInput(action, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                if (!supportsHold) {
                    waitForUpOrCancellation()?.let { it.consume(); latestTrigger() }
                } else {
                    var settled = false
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation().also { settled = true }
                    }
                    if (settled) {
                        up?.let { it.consume(); latestTrigger() }
                    } else {
                        var released = false
                        try {
                            holdStart()
                            waitForUpOrCancellation()?.let { it.consume(); released = true }
                        } finally { holdEnd(released) }
                    }
                }
            }
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(if (plain) 40.dp else 36.dp).background(background, SendButtonShape).drawWithContent {
            drawContent()
            if (flash.value > 0f) drawRoundRect(Brush.sweepGradient(*aurora.stops), alpha = flash.value, style = Stroke(2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension / 2))
        }, contentAlignment = Alignment.Center) {
            // 图标变形：旧图标缩小淡出，新图标带一点回弹地放大出现（麦克风 → 箭头 → 停止）。
            androidx.compose.animation.AnimatedContent(
                targetState = action,
                transitionSpec = {
                    if (motion.reduceMotion) androidx.compose.animation.fadeIn(tween(motion.micro)) togetherWith androidx.compose.animation.fadeOut(tween(motion.micro))
                    else (androidx.compose.animation.fadeIn(tween(160)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 600f), initialScale = 0.5f)) togetherWith
                        (androidx.compose.animation.fadeOut(tween(100)) + androidx.compose.animation.scaleOut(tween(120), targetScale = 0.5f))
                },
                label = "composerActionIcon",
            ) { shown ->
                Icon(when (shown) {
                    ComposerAction.Send, ComposerAction.Disabled -> Icons.Outlined.ArrowUpward
                    ComposerAction.Queue -> Icons.Outlined.Schedule
                    ComposerAction.Stop -> Icons.Outlined.Stop
                    else -> Icons.Outlined.Mic
                }, null, Modifier.size(when {
                    plain -> 22.dp
                    shown == ComposerAction.Stop -> 16.dp
                    else -> 20.dp
                }), tint = foreground)
            }
        }
    }
}

private val SendButtonShape = CircleShape
