package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButtonColors

internal enum class EtaButtonKind { Primary, Tonal, Secondary, Ghost, Destructive }
internal enum class EtaButtonSize { Regular, Small }

/** Existing explicit colors, dimensions, indication and interaction sources remain supported. */
@Composable
internal fun EtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = EtaControlDefaults.ButtonCornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = EtaControlDefaults.ButtonMinHeight,
    colors: TextButtonColors? = null,
    insideMargin: PaddingValues = EtaControlDefaults.ButtonPadding,
    textStyle: TextStyle? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
    kind: EtaButtonKind = EtaButtonKind.Tonal,
    size: EtaButtonSize = EtaButtonSize.Regular,
    loading: Boolean = false,
) {
    val palette = EtaTheme.colors
    val (background, foreground) = when (kind) {
        // 主按钮是黑底白字（深色模式反过来），与 Claude 一致；珊瑚色只用于发送键和品牌元素。
        EtaButtonKind.Primary -> palette.textPrimary to palette.canvas
        EtaButtonKind.Tonal -> palette.accentSoft to palette.onAccentSoft
        EtaButtonKind.Secondary -> Color.Transparent to palette.textPrimary
        EtaButtonKind.Ghost -> Color.Transparent to palette.accent
        EtaButtonKind.Destructive -> palette.dangerSoft to palette.dangerText
    }
    val container = colors?.let { if (enabled) it.color else it.disabledColor }
        ?: background.copy(alpha = background.alpha * if (enabled) 1f else 0.38f)
    val contentColor = colors?.let { if (enabled) it.textColor else it.disabledTextColor }
        ?: foreground.copy(alpha = if (enabled) 1f else 0.38f)
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = EtaTheme.motion
    val scale = animateFloatAsState(if (pressed && enabled && !motion.reduceMotion) 0.97f else 1f,
        tween(motion.micro, easing = motion.standard), label = "buttonPress")
    val shape = RoundedCornerShape(cornerRadius)
    val small = size == EtaButtonSize.Small
    val height = if (small && minHeight == EtaControlDefaults.ButtonMinHeight) 32.dp else minHeight
    val padding = if (small && insideMargin == EtaControlDefaults.ButtonPadding) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else insideMargin
    val pressedColor = palette.pressed
    Box(
        modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .heightIn(min = height).widthIn(min = minWidth)
            .clip(shape).background(container)
            .then(if (kind == EtaButtonKind.Secondary && colors == null) Modifier.border(1.dp, palette.outlineStrong, shape) else Modifier)
            .clickable(enabled = enabled && !loading, role = Role.Button,
                interactionSource = source, indication = indication, onClick = onClick)
            .drawWithContent { drawContent(); if (pressed && enabled) drawRect(pressedColor) }
            .padding(padding),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = false,
    ) {
        // Retain the label's measurement and semantics while loading.
        Text(text, color = contentColor, style = textStyle ?: if (small) EtaTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold) else EtaTheme.type.rowTitle,
            modifier = Modifier.graphicsLayer { alpha = if (loading) 0f else 1f })
        if (loading) StatusRing(TaskVisualStatus.Running, size = 16.dp, monochrome = contentColor)
    }
}
