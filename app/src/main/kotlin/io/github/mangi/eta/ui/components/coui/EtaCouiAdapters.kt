package io.github.mangi.eta.ui.components.coui

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor

/*
 * 14 号方案 14.4a：COUI 组件库迁移的适配层。
 *
 * 胖小胖的界面外壳建在旧库 io.github.itoseo:coui 上。迁移到 io.github.suqi8.coui.kmp 2.1.0 时，
 * 先让 coui 包以外的页面只通过这里的名字使用「菜单锚点」「采样源」等能力，之后逐段把这里的实现换成新库，
 * 页面代码不用再动。这一段只是改名转发，界面和行为完全不变。
 */

/** 弹出菜单的锚点：菜单从这个位置弹出。 */
internal typealias EtaMenuAnchor = com.itos.coui.menu.CouiMenuAnchor

@Composable
internal fun rememberEtaMenuAnchor(): EtaMenuAnchor = rememberCouiMenuAnchor()

internal fun Modifier.etaMenuAnchor(anchor: EtaMenuAnchor): Modifier = couiMenuAnchor(anchor)

/** 液态玻璃的采样源：被它包住的内容会被上层的玻璃控件模糊、折射。 */
internal typealias EtaBackdrop = com.itos.coui.backdrop.CouiBackdrop

@Composable
internal fun rememberEtaBackdrop(diagnosticName: String): EtaBackdrop = rememberCouiBackdrop(diagnosticName = diagnosticName)

internal fun Modifier.etaBackdrop(backdrop: EtaBackdrop): Modifier = couiBackdrop(backdrop)

/** 当前设备能不能画浮动液态玻璃（Android 14 起、着色器可用）。 */
internal fun etaFloatingGlassAvailable(): Boolean =
    android.os.Build.VERSION.SDK_INT >= 34 && runCatching { CouiFloatingRenderer.pair() }.isSuccess

/**
 * COUI 开关（14.4b 起用 COUI 2.1.0 的原生开关：拖动、回弹、触感和滑块阴影由组件处理）。
 * 配色沿用旧库的数值，尺寸两边一样（44×24，滑块 18，边距 3），所以换库前后看起来一致。
 * 新库的主题只包住开关本身，不改全局的按压效果和滚动回弹。
 */
@Composable
internal fun EtaCouiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
    dark: Boolean,
    trackOn: androidx.compose.ui.graphics.Color,
    thumb: androidx.compose.ui.graphics.Color,
) {
    val colorScheme = if (dark) io.github.suqi8.coui.kmp.theme.darkColorScheme() else io.github.suqi8.coui.kmp.theme.lightColorScheme()
    // 圆角（squircle）遮罩要用 RuntimeShader；软件绘制（单测截图、少数离屏位图）不支持，没有着色器时退回普通圆角。
    io.github.suqi8.coui.kmp.theme.COUITheme(colors = colorScheme) {
        androidx.compose.runtime.CompositionLocalProvider(
            io.github.suqi8.coui.kmp.squircle.LocalSquircleEnabled provides io.github.mangi.eta.ui.components.EtaCouiSupport.shaders,
        ) {
        // 和旧库一样：开关自己处理点击时留出 48×48 的触控区域（整行可点时不留），行高因此不变。
        val touchTarget = if (onCheckedChange != null) {
            Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        } else {
            Modifier
        }
        androidx.compose.foundation.layout.Box(modifier.then(touchTarget), contentAlignment = androidx.compose.ui.Alignment.Center) {
        io.github.suqi8.coui.kmp.basic.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = io.github.suqi8.coui.kmp.basic.SwitchDefaults.switchColors(
                checkedThumbColor = thumb,
                uncheckedThumbColor = thumb,
                disabledCheckedThumbColor = androidx.compose.ui.graphics.Color(if (dark) 0x29FFFFFF else 0x8AFFFFFF),
                disabledUncheckedThumbColor = androidx.compose.ui.graphics.Color(if (dark) 0x29FFFFFF else 0x8AFFFFFF),
                checkedTrackColor = trackOn,
                uncheckedTrackColor = androidx.compose.ui.graphics.Color(if (dark) 0x40FFFFFF else 0x29000000),
                disabledCheckedTrackColor = trackOn.copy(alpha = 0x4D / 255f),
                disabledUncheckedTrackColor = androidx.compose.ui.graphics.Color(if (dark) 0x26FFFFFF else 0x14000000),
            ),
            enabled = enabled,
        )
        }
        }
    }
}

/** COUI 弹出菜单的一项（与 [io.github.mangi.eta.ui.components.EtaMenuCollector.Entry] 一一对应）。 */
internal class EtaCouiMenuEntry(
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val enabled: Boolean,
    val destructive: Boolean,
    val selected: Boolean,
    val dividerBefore: Boolean,
    val onClick: () -> Unit,
)

/** COUI 弹出菜单：从 [anchor] 处弹出，危险项用对话框的警示色。 */
@Composable
internal fun EtaCouiMenu(
    show: Boolean,
    entries: List<EtaCouiMenuEntry>,
    onDismiss: () -> Unit,
    anchor: EtaMenuAnchor?,
    header: String?,
    dark: Boolean,
) {
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
        anchor = anchor,
        headerText = header,
    )
}

/** COUI 页面配色里的强调色、标题色、说明色（上游生图卡片的 COUI 分支用）。 */
internal data class EtaCouiPageColors(
    val accent: androidx.compose.ui.graphics.Color,
    val title: androidx.compose.ui.graphics.Color,
    val summary: androidx.compose.ui.graphics.Color,
)

@Composable
internal fun etaCouiPageColors(dark: Boolean): EtaCouiPageColors {
    val colors = com.itos.coui.layout.CouiPageDefaults.themeColors(dark)
    return EtaCouiPageColors(accent = colors.accent, title = colors.title, summary = colors.summary)
}
