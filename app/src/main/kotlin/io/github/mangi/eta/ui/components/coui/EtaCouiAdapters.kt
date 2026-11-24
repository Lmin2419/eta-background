package io.github.mangi.eta.ui.components.coui

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.thirdparty.coui.backdrop.couiBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.mangi.eta.thirdparty.coui.backdrop.rememberCouiBackdrop
import io.github.mangi.eta.thirdparty.coui.effect.CouiFloatingRenderer

/*
 * 14 号方案 14.4a：COUI 组件库迁移的适配层。
 *
 * 胖小胖的界面外壳建在旧库 io.github.itoseo:coui 上。迁移到 io.github.suqi8.coui.kmp 2.1.0 时，
 * 先让 coui 包以外的页面只通过这里的名字使用「菜单锚点」「采样源」等能力，之后逐段把这里的实现换成新库，
 * 页面代码不用再动。
 */

/* 菜单锚点、菜单、对话框、半窗在 EtaCouiOverlays.kt（14.4b 起用 COUI 2.1.0）。 */

/**
 * 浮动控件的采样源：被它包住的内容会被上层的控件模糊。14.4c 起按外观设置里的「控件材质」记录：
 * 液态玻璃用旧库的采样源（[legacy]），COUI 17 磨砂用新库 coui-blur 的图层记录（[coui]），两者只记一份。
 */
@androidx.compose.runtime.Stable
internal class EtaBackdrop internal constructor(
    internal val legacy: io.github.mangi.eta.thirdparty.coui.backdrop.CouiBackdrop?,
    internal val coui: io.github.suqi8.coui.kmp.blur.LayerBackdrop?,
)

@Composable
internal fun rememberEtaBackdrop(diagnosticName: String): EtaBackdrop {
    val frost = io.github.mangi.eta.ui.app.LocalControlMaterial.current ==
        io.github.mangi.eta.data.model.AppearanceControlMaterial.COUI_FROST
    return if (frost) {
        // 记录时先铺一层页面底色：内容里透明的地方模糊出来不会发灰。
        val base = io.github.mangi.eta.ui.theme.EtaTheme.colors.canvas
        val layer = io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop {
            drawRect(base)
            drawContent()
        }
        androidx.compose.runtime.remember(layer) { EtaBackdrop(null, layer) }
    } else {
        val legacy = rememberCouiBackdrop(diagnosticName = diagnosticName)
        androidx.compose.runtime.remember(legacy) { EtaBackdrop(legacy, null) }
    }
}

internal fun Modifier.etaBackdrop(backdrop: EtaBackdrop): Modifier = when {
    backdrop.legacy != null -> couiBackdrop(backdrop.legacy)
    backdrop.coui != null -> layerBackdrop(backdrop.coui)
    else -> this
}

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

/** COUI 页面配色里的强调色、标题色、说明色（上游生图卡片的 COUI 分支用）。 */
internal data class EtaCouiPageColors(
    val accent: androidx.compose.ui.graphics.Color,
    val title: androidx.compose.ui.graphics.Color,
    val summary: androidx.compose.ui.graphics.Color,
)

@Composable
internal fun etaCouiPageColors(dark: Boolean): EtaCouiPageColors {
    // 14.4d：旧库去掉后用新库 COUI 2.1.0 的配色（强调色是胖小胖的珊瑚色）。
    val colors = etaCouiColors(dark)
    return EtaCouiPageColors(accent = colors.primary, title = colors.labelPrimary, summary = colors.labelSecondary)
}
