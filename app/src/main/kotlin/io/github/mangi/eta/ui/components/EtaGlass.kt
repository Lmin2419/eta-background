package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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

/** 页面内容延伸到玻璃顶栏下方时，列表需要额外留出的顶部距离（顶栏高度）。 */
internal val LocalTopBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

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
    /** 有真实模糊时底色的不透明度（标准档）：既能透出背后内容，文字又始终可读。 */
    const val BlurTintAlpha = 0.64f
    /** 深色模式的标准档。 */
    const val BlurTintAlphaDark = 0.55f
    /** 没有模糊时的磨砂底色不透明度。 */
    const val SolidTintAlpha = 0.92f
    /** 小窗、窗口上面有大量文字，底色更实，背后的字不能透上来干扰阅读。 */
    const val PopoverAlpha = 0.92f
    const val WindowAlpha = 0.90f
    const val BlurRadius = 28f
}

/** 当前外观设置下的玻璃底色不透明度（通透 / 标准 / 磨砂）；深色模式略降，避免发灰。 */
@Composable
internal fun etaGlassTintAlpha(): Float {
    val strength = io.github.mangi.eta.ui.app.LocalAppearanceSettings.current.glassStrength
    val dark = EtaTheme.colors.isDark
    return when (strength) {
        io.github.mangi.eta.data.model.AppearanceGlassStrength.STANDARD -> if (dark) EtaGlassDefaults.BlurTintAlphaDark else EtaGlassDefaults.BlurTintAlpha
        else -> if (dark) strength.tintAlpha - 0.09f else strength.tintAlpha
    }
}

/** 真实模糊是否可用：设置里打开、设备支持、且不在省电模式。 */
@Composable
internal fun etaGlassBlurOn(): Boolean = LocalBlurEnabled.current && isRuntimeShaderSupported()

/**
 * 玻璃边缘：上沿一道高光，向下很快消失——像光从上方照进玻璃。外圈另有一条极淡的深色细线，
 * 在浅色底上把玻璃和背景分开（见 [etaGlass]）。
 */
@Composable
internal fun etaGlassEdge(): Brush {
    val dark = EtaTheme.colors.isDark
    return if (dark) Brush.verticalGradient(0f to Color.White.copy(alpha = 0.18f), 0.4f to Color.White.copy(alpha = 0.03f), 1f to Color.Transparent)
    else Brush.verticalGradient(0f to Color.White.copy(alpha = 0.95f), 0.4f to Color.White.copy(alpha = 0.25f), 1f to Color.Transparent)
}

/** 外圈细线：浅色 rgba(0,0,0,0.07)，深色用很淡的白。 */
@Composable
internal fun etaGlassHairline(): Color =
    if (EtaTheme.colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)

/**
 * 玻璃面板。[tint] 是材质的底色（默认 `surface`），[elevation] 给一层很轻的投影让它「浮」起来。
 * [backdrop] 默认取页面提供的背景层；传 null 则用磨砂底色。[blurAlpha] 为 null 时跟随外观设置的玻璃档位；
 * 小窗、窗口传更高的值，并且不会低于当前档位。
 */
internal fun Modifier.etaGlass(
    shape: Shape,
    tint: Color? = null,
    elevation: EtaElevation? = EtaElevation.E1,
    edgeWidth: Dp = 0.75.dp,
    backdrop: LayerBackdrop? = null,
    useLocalBackdrop: Boolean = true,
    solidAlpha: Float = EtaGlassDefaults.SolidTintAlpha,
    blurAlpha: Float? = null,
    interactionSource: androidx.compose.foundation.interaction.InteractionSource? = null,
): Modifier = composed {
    val base = tint ?: EtaTheme.colors.surface
    val layer = backdrop ?: if (useLocalBackdrop) LocalEtaGlassBackdrop.current else null
    val blurOn = etaGlassBlurOn()
    val strengthAlpha = etaGlassTintAlpha()
    val alpha = blurAlpha?.coerceAtLeast(strengthAlpha) ?: strengthAlpha
    val edge = etaGlassEdge()
    val hairline = etaGlassHairline()
    val sheen = etaGlassSheen()
    var m: Modifier = this
    if (elevation != null) m = m.etaElevation(elevation, shape)
    m = if (layer != null && blurOn) {
        m.textureBlur(
            backdrop = layer,
            shape = shape,
            blurRadius = EtaGlassDefaults.BlurRadius,
            colors = BlurColors(blendColors = listOf(BlendColorEntry(base.copy(alpha = alpha)))),
        )
    } else {
        m.background(base.copy(alpha = solidAlpha), shape)
    }
    // 按下时整块玻璃微微提亮（液态玻璃被「按亮」的感觉），配合调用方的按压回弹。
    val pressed = interactionSource?.let { src ->
        val down by src.collectIsPressedAsState()
        androidx.compose.animation.core.animateFloatAsState(if (down) 1f else 0f,
            androidx.compose.animation.core.tween(if (down) 90 else 260), label = "glassPress")
    }
    val dark = EtaTheme.colors.isDark
    m.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            drawOutline(outline, sheen)
            val p = pressed?.value ?: 0f
            if (p > 0f) drawOutline(outline, Color.White.copy(alpha = (if (dark) 0.10f else 0.35f) * p))
        }
    }.border(0.5.dp, hairline, shape).border(edgeWidth, edge, shape)
}

/** 上沿反光：顶部一抹很淡的白，向下 45% 处消失（浅色 0.20，深色 0.05）。 */
@Composable
internal fun etaGlassSheen(): Brush {
    val top = if (EtaTheme.colors.isDark) 0.05f else 0.20f
    return Brush.verticalGradient(0f to Color.White.copy(alpha = top), 0.45f to Color.Transparent)
}
