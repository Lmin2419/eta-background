package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle
import io.github.mangi.eta.ui.app.LocalBlurEnabled
import io.github.mangi.eta.ui.app.LocalTopBarBlurStyle
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

/**
 * 玻璃顶栏：页面内容滚到顶栏下面时透出并被模糊（类似 iOS 的半透明导航栏）。
 * 外观设置里关掉「模糊」或设备不支持时，退回与页面同色的实色顶栏。
 */
@Composable
internal fun rememberTopBarBackdrop(base: Color = EtaTheme.colors.canvas): LayerBackdrop? {
    if (!LocalBlurEnabled.current || !isRuntimeShaderSupported()) return null
    return rememberLayerBackdrop {
        drawRect(base)
        drawContent()
    }
}

@Composable
internal fun TopBarBackdrop(backdrop: LayerBackdrop?, color: Color = EtaTheme.colors.canvas, content: @Composable () -> Unit) {
    val modifier = when {
        backdrop == null -> Modifier.background(color)
        // 渐进模糊：越靠上越实，向下自然过渡到内容，没有硬边（iOS 的滚动边缘效果）。
        LocalTopBarBlurStyle.current == AppearanceTopBarBlurStyle.PROGRESSIVE -> Modifier.progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
            blurRadius = 14f,
            colors = BlurColors(blendColors = listOf(BlendColorEntry(color.copy(alpha = 0.55f)))),
        )
        else -> Modifier.textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 28f,
            colors = BlurColors(blendColors = listOf(BlendColorEntry(color.copy(alpha = 0.72f)))),
        )
    }
    // 分隔线由 AdaptiveTopAppBar 按滚动位置淡入淡出，这里只铺材质。
    Box(modifier) { content() }
}

internal fun Modifier.captureForTopBar(backdrop: LayerBackdrop?): Modifier =
    if (backdrop == null) this else layerBackdrop(backdrop)

@Composable
internal fun topBarContainerColor(backdrop: LayerBackdrop?): Color =
    if (backdrop == null) EtaTheme.colors.canvas else Color.Transparent

/** 对话页滚动边缘向下多出的渐隐高度（不计入列表的顶部留白）。 */
internal val ChatScrollEdgeTail = androidx.compose.ui.unit.Dp(24f)

/**
 * 对话页顶部的滚动边缘：没有整条顶栏，只有三个浮着的玻璃控件；控件下方一条从页面底色到透明的渐隐，
 * 叠一层很轻的渐进模糊（半径 12）。消息滑到控件下面时渐渐变淡，而不是从按钮之间硬穿过去。
 * 控件本身经 [LocalEtaGlassBackdrop] 拿到同一背景层做真模糊。
 */
@Composable
internal fun ChatScrollEdge(backdrop: LayerBackdrop?, content: @Composable () -> Unit) {
    val canvas = EtaTheme.colors.canvas
    val fade = androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to canvas.copy(alpha = 0.94f), 0.55f to canvas.copy(alpha = 0.72f), 1f to canvas.copy(alpha = 0f),
    )
    val blur = if (backdrop != null) Modifier.progressiveTextureBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        gradient = ProgressiveBlur.Top.copy(curve = 1.6f),
        blurRadius = 12f,
        colors = BlurColors(blendColors = listOf(BlendColorEntry(canvas.copy(alpha = 0.08f)))),
    ) else Modifier
    androidx.compose.foundation.layout.Column(Modifier.then(blur).background(fade)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalEtaGlassBackdrop provides backdrop) { content() }
        androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().height(ChatScrollEdgeTail))
    }
}
