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

/** 菜单项收集器：在 [EtaContextMenu] 里，[EtaMenuItem] 不直接绘制，而是交给 COUI 菜单。 */
internal class EtaMenuCollector {
    class Entry(
        val text: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector?,
        val enabled: Boolean,
        val destructive: Boolean,
        val selected: Boolean,
        val dividerBefore: Boolean,
        val onClick: () -> Unit,
    )
    val entries = mutableListOf<Entry>()
    var dividerPending = false
}

internal val LocalEtaMenuCollector = androidx.compose.runtime.staticCompositionLocalOf<EtaMenuCollector?> { null }

/**
 * COUI 菜单与对话框依赖平台着色器（Android 14+ 的 Mesh 阴影、AGSL）。本机测试环境（Robolectric）
 * 实现不了这些着色器，组合会直接报错；这时退回旧的小窗与面板，保证测试仍能检查菜单内容。
 */
internal object EtaCouiSupport {
    @Volatile
    var popups: Boolean = android.os.Build.FINGERPRINT != "robolectric"
}

/**
 * 弹出菜单（小窗）：与上游 3.4.x 一致，使用 COUI 菜单——从按住的位置展开，背后的页面被模糊，
 * 手指不松开滑到某一项松手即可选中。[couiAnchor] 挂在被长按的元素上（`couiMenuAnchor`），
 * 打开时用 `couiAnchor.open { ... }` 记录触点。没有锚点时跟随父布局定位。
 */
@Composable
internal fun EtaContextMenu(
    show: Boolean,
    anchor: Rect = Rect.Zero,
    onDismiss: () -> Unit,
    alignEnd: Boolean = false,
    couiAnchor: com.itos.coui.menu.CouiMenuAnchor? = null,
    header: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!EtaCouiSupport.popups) {
        EtaPopover(show, anchor, onDismiss, align = if (alignEnd) EtaPopoverAlign.End else EtaPopoverAlign.Start, title = header, content = content)
        return
    }
    val collector = EtaMenuCollector()
    androidx.compose.runtime.CompositionLocalProvider(LocalEtaMenuCollector provides collector) {
        Column(Modifier.size(0.dp)) { content() }
    }
    val entries = collector.entries.toList()
    val dark = com.itos.coui.theme.CouiTheme.isDark
    val destructive = com.itos.coui.dialog.CouiDialogDefaults.themeColors(dark).destructive
    val items = entries.mapIndexed { index, entry ->
        com.itos.coui.menu.CouiMenuItem(
            value = index,
            text = entry.text,
            icon = entry.icon,
            selected = entry.selected,
            enabled = entry.enabled,
            dividerBefore = entry.dividerBefore,
            iconTint = if (entry.destructive) destructive else null,
        )
    }
    com.itos.coui.menu.Menu(
        expanded = show && items.isNotEmpty(),
        onDismissRequest = onDismiss,
        items = items,
        onItemClick = { item -> entries.getOrNull(item.value)?.onClick?.invoke() },
        anchor = couiAnchor,
        headerText = header,
    )
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
    LocalEtaMenuCollector.current?.let { collector ->
        collector.entries += EtaMenuCollector.Entry(text, icon, enabled, destructive, selected, collector.dividerPending, onClick)
        collector.dividerPending = false
        return
    }
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
    LocalEtaMenuCollector.current?.let { it.dividerPending = true; return }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .height(0.5.dp).background(EtaTheme.colors.outline),
    )
}
