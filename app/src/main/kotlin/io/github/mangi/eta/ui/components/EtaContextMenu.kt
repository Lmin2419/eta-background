package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.etaElevation
import top.yukonga.miuix.kmp.basic.Text

/**
 * 长按消息出现的气泡菜单：背景变暗，菜单从被按的消息旁边（优先下方，放不下就上方）弹出，
 * 带一点回弹地从 0.9 倍放大。取代底部面板，手指不用移到屏幕底部。
 *
 * [anchor] 是消息在窗口中的位置；[alignEnd] 为真时菜单与消息右边对齐（用户消息）。
 */
@Composable
internal fun EtaContextMenu(
    show: Boolean,
    anchor: Rect,
    onDismiss: () -> Unit,
    alignEnd: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val motion = EtaTheme.motion
    val haptics = rememberEtaHaptics()
    LaunchedEffect(show) {
        if (show) {
            if (!visible) haptics.longPress()
            visible = true
            progress.animateTo(1f, if (motion.reduceMotion) tween(motion.short) else spring(dampingRatio = 0.72f, stiffness = 520f))
        } else if (visible) {
            progress.animateTo(0f, tween(motion.exitDuration(motion.short), easing = motion.accelerate))
            visible = false
        }
    }
    if (!visible) return
    Popup(
        popupPositionProvider = FullWindow,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.roundToPx() }
            var below by remember { mutableStateOf(true) }
            Layout(
                content = {
                    Column(
                        Modifier
                            .width(232.dp)
                            .graphicsLayer {
                                val p = progress.value
                                val scale = if (motion.reduceMotion) 1f else 0.9f + 0.1f * p
                                scaleX = scale; scaleY = scale
                                transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, if (below) 0f else 1f)
                            }
                            .etaGlass(EtaTheme.shapes.radiusLg, elevation = EtaElevation.E3, useLocalBackdrop = false)
                            .clip(EtaTheme.shapes.radiusLg)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                            .padding(vertical = 6.dp),
                        content = content,
                    )
                },
            ) { measurables, constraints ->
                val card = measurables.single().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
                val fitsBelow = anchor.bottom + margin + card.height <= constraints.maxHeight - margin
                below = fitsBelow || anchor.top - margin - card.height < margin
                val y = if (below) (anchor.bottom + margin).toInt().coerceAtMost(constraints.maxHeight - card.height - margin)
                    else (anchor.top - margin - card.height).toInt()
                val x = if (alignEnd) (anchor.right - card.width).toInt() else (anchor.left).toInt()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    card.place(x.coerceIn(margin, (constraints.maxWidth - card.width - margin).coerceAtLeast(margin)), y.coerceAtLeast(margin))
                }
            }
        }
    }
}

/**
 * 菜单里的一项：46dp 高、左对齐文字、右侧线条图标（可选），危险操作用红色。
 * 按下时出现一块内缩的圆角高光，像在玻璃上点亮一格。
 */
@Composable
internal fun EtaMenuItem(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val color = when {
        !enabled -> EtaTheme.colors.textTertiary
        destructive -> EtaTheme.colors.dangerText
        else -> EtaTheme.colors.textPrimary
    }
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .heightIn(min = 46.dp)
            .clip(EtaTheme.shapes.radiusMd)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = EtaTheme.type.rowTitle, color = color, modifier = Modifier.weight(1f))
        if (icon != null) top.yukonga.miuix.kmp.basic.Icon(icon, null, Modifier.padding(start = 12.dp).size(18.dp), tint = color)
    }
}

private object FullWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}
