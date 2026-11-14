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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Check
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
 * 长按消息出现的菜单：一枚玻璃小窗（[EtaPopover]），从被按的消息旁边（优先下方，放不下就上方）
 * 展开，背后不变暗，背后的内容被模糊。[anchor] 是消息在窗口中的位置；[alignEnd] 为真时与消息右边对齐（用户消息）。
 */
@Composable
internal fun EtaContextMenu(
    show: Boolean,
    anchor: Rect,
    onDismiss: () -> Unit,
    alignEnd: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = rememberEtaHaptics()
    LaunchedEffect(show) { if (show) haptics.longPress() }
    EtaPopover(show, anchor, onDismiss, align = if (alignEnd) EtaPopoverAlign.End else EtaPopoverAlign.Start, content = content)
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
    selected: Boolean = false,
    summary: String? = null,
) {
    val color = when {
        !enabled -> EtaTheme.colors.textTertiary
        destructive -> EtaTheme.colors.dangerText
        else -> EtaTheme.colors.textPrimary
    }
    // 小窗圆角 24、内边距 8 → 条目圆角 16（同心圆角）。
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(EtaTheme.colors.textPrimary.copy(alpha = 0.06f)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = EtaTheme.type.rowTitle, color = color, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (summary != null) Text(summary, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        when {
            selected -> top.yukonga.miuix.kmp.basic.Icon(androidx.compose.material.icons.Icons.Outlined.Check, null,
                Modifier.padding(start = 12.dp).size(18.dp), tint = EtaTheme.colors.textPrimary)
            icon != null -> top.yukonga.miuix.kmp.basic.Icon(icon, null, Modifier.padding(start = 12.dp).size(18.dp), tint = color)
        }
    }
}

/** 小窗里的分隔：0.5dp，左右各内缩 12dp，与条目文字对齐。 */
@Composable
internal fun EtaMenuDivider() {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .height(0.5.dp).background(EtaTheme.colors.outline),
    )
}
