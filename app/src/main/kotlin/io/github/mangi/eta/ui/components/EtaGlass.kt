package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.app.LocalBlurEnabled
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.etaElevation
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

/*
 * 玻璃质感（参考 iOS 的半透明材质）：浮在内容上方的导航层——顶栏、输入胶囊、菜单、提示条、
 * 圆形按钮——用「背后内容模糊 + 半透明底色 + 上亮下暗的细高光边」呈现；内容本身保持实色。
 *
 * 有可模糊的背景层（LayerBackdrop）时做真实模糊；没有时（弹出窗口、设置里关掉模糊、
 * 设备不支持 RuntimeShader）退回较不透明的磨砂底色，看起来一致、只是不透出背后内容。
 */

/** 当前页面里可供玻璃元素模糊的背景层；由页面捕获内容后提供。 */
internal val LocalEtaGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 捕获页面内容用于玻璃模糊；关闭模糊或设备不支持时返回 null。[base] 是内容底下的页面底色。 */
@Composable
internal fun rememberEtaGlassBackdrop(base: Color = EtaTheme.colors.canvas): LayerBackdrop? {
    if (!LocalBlurEnabled.current || !isRuntimeShaderSupported()) return null
    return rememberLayerBackdrop {
        drawRect(base)
        drawContent()
    }
}

internal object EtaGlassDefaults {
    /** 有真实模糊时底色的不透明度：既能透出背后内容，文字又始终可读。 */
    const val BlurTintAlpha = 0.62f
    /** 没有模糊时的磨砂底色不透明度。 */
    const val SolidTintAlpha = 0.92f
    const val BlurRadius = 28f
}

/** 玻璃边缘：上沿更亮、下沿更暗的一圈细高光，模拟玻璃厚度的反光。 */
@Composable
internal fun etaGlassEdge(): Brush {
    val dark = EtaTheme.colors.canvas.luminance() < 0.5f
    return if (dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f)))
    else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.06f)))
}

/**
 * 玻璃面板。[tint] 是材质的底色（浅色模式偏白、深色模式偏灰），[elevation] 给一层很轻的投影让它「浮」起来。
 * [backdrop] 默认取页面提供的背景层；传 null 则用磨砂底色。
 */
internal fun Modifier.etaGlass(
    shape: Shape,
    tint: Color? = null,
    elevation: EtaElevation? = EtaElevation.E1,
    edgeWidth: Dp = 0.75.dp,
    backdrop: LayerBackdrop? = null,
    useLocalBackdrop: Boolean = true,
): Modifier = composed {
    val base = tint ?: EtaTheme.colors.surfaceRaised
    val layer = backdrop ?: if (useLocalBackdrop) LocalEtaGlassBackdrop.current else null
    val blurOn = LocalBlurEnabled.current && isRuntimeShaderSupported()
    val edge = etaGlassEdge()
    var m: Modifier = this
    if (elevation != null) m = m.etaElevation(elevation, shape)
    m = if (layer != null && blurOn) {
        m.textureBlur(
            backdrop = layer,
            shape = shape,
            blurRadius = EtaGlassDefaults.BlurRadius,
            colors = BlurColors(blendColors = listOf(BlendColorEntry(base.copy(alpha = EtaGlassDefaults.BlurTintAlpha)))),
        )
    } else {
        m.background(base.copy(alpha = EtaGlassDefaults.SolidTintAlpha), shape)
    }
    m.border(edgeWidth, edge, shape)
}
