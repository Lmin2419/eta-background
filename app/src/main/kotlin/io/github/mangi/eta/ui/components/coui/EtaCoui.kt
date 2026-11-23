package io.github.mangi.eta.ui.components.coui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import io.github.suqi8.coui.kmp.blur.couiViewBlur
import androidx.compose.ui.draw.dropShadow
import com.itos.coui.backdrop.ProvideCouiBackdrop
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.couiToolbarFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import io.github.mangi.eta.ui.components.etaGlass
import io.github.mangi.eta.ui.theme.EtaElevation

/*
 * 上游 3.4.x 的 COUI 组件接入（etax/design 06）：
 * - 浮动液态玻璃表面：与上游侧栏底部的流体材质同一实现（COUI floating renderer：边缘折光、内发光、
 *   聚光与按压回弹），Android 14+ 且有背景源时启用；其余情况退回胖小胖自己的玻璃。
 * - COUI 胶囊按钮、半窗材质。
 */

/** 当前页面供浮动液态玻璃采样的 COUI 背景源（页面正文节点挂 couiBackdrop 后提供）。 */
/**
 * 对话页唯一的 COUI 背景源（与上游 COUI Scaffold 同一结构）：源挂在消息区上并带不透明底色，
 * 顶部控件和输入框作为它的兄弟节点采样；不再在整页外面再套一层源。
 */
internal val LocalChatCouiSource = androidx.compose.runtime.staticCompositionLocalOf<EtaBackdrop?> { null }

internal val LocalEtaCouiBackdrop = staticCompositionLocalOf<EtaBackdrop?> { null }

/**
 * 浮在内容上的液态玻璃控件（圆钮或胶囊）。[capsule] 为真时是胶囊形（宽大于高）。
 */
@Composable
internal fun EtaFloatingSurface(
    modifier: Modifier,
    shape: Shape,
    capsule: Boolean,
    contentDescription: String? = null,
    onClick: (() -> Unit)?,
    focused: Boolean = false,
    backdrop: EtaBackdrop? = LocalEtaCouiBackdrop.current,
    wrapContent: Boolean = false,
    /** 圆角半径；不传时是胶囊（短边的一半）。输入框这类圆角矩形必须传。 */
    cornerRadius: Dp? = null,
    /** 玻璃上再铺一层表面色的不透明度（0 = 纯玻璃）。输入框要压住背后的文字时用。 */
    fillAlpha: Float = 0f,
    content: @Composable () -> Unit,
) {
    val dark = CouiTheme.isDark
    val fill = io.github.mangi.eta.ui.theme.EtaTheme.colors.surfaceRaised.copy(alpha = fillAlpha)
    // 14.4c：外观设置选了 COUI 17 磨砂时，采样源是新库的图层记录，画磨砂材质（上游同款）。
    val frostSource = backdrop?.coui
    if (frostSource != null) {
        EtaFrostSurface(modifier, shape, capsule, frostSource, dark, focused,
            io.github.mangi.eta.ui.theme.EtaTheme.colors.accent, contentDescription, onClick,
            wrapContent, cornerRadius, fillAlpha, fill, content)
        return
    }
    val legacySource = backdrop?.legacy
    val filledContent: @Composable () -> Unit = if (fillAlpha > 0f) ({
        Box(Modifier.then(if (wrapContent) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).background(fill, shape),
            contentAlignment = Alignment.Center) { content() }
    }) else content
    val view = LocalView.current
    // 玻璃能不能画要看三件事：有背景源、着色器渲染器建好了、视图已经挂到屏幕上。
    // 以前后两件只在第一次组合时看一眼：那一刻没就绪（冷启动、页面刚切换），控件就一直是退回的实色样式，
    // 直到整页重建。现在都做成可观察的状态，就绪后自动换回液态玻璃。
    val hasDisplay = rememberViewHasDisplay(view)
    val renderer = rememberFloatingRenderer()
    val accent = io.github.mangi.eta.ui.theme.EtaTheme.colors.accent
    if (Build.VERSION.SDK_INT >= 34 && legacySource != null && renderer != null && hasDisplay) {
        ProvideCouiBackdrop(legacySource) {
            EtaApi34FloatingSurface(modifier, shape, capsule, legacySource, renderer, dark, focused, accent,
                contentDescription, onClick, filledContent, wrapContent, cornerRadius)
        }
    } else {
        val source = remember { MutableInteractionSource() }
        Box(
            modifier
                .etaGlass(shape, elevation = EtaElevation.E1, interactionSource = source)
                .clip(shape)
                .then(if (onClick != null) Modifier.couiButtonFeedback(dark, source, enabled = true)
                    .clickable(source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
                .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
            contentAlignment = Alignment.Center,
        ) { filledContent() }
    }
}

/**
 * COUI 17 磨砂材质的浮动控件（14.4c 对比试用）：背后用新库的视图模糊（圆钮、胶囊是次要按钮的磨砂，
 * 输入框这类圆角矩形是底栏磨砂，和上游一样），加一圈细亮边、浅阴影，按下轻微缩小。
 */
@Composable
private fun EtaFrostSurface(
    modifier: Modifier,
    shape: Shape,
    capsule: Boolean,
    backdrop: io.github.suqi8.coui.kmp.blur.LayerBackdrop,
    dark: Boolean,
    focused: Boolean,
    accent: Color,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    wrapContent: Boolean,
    cornerRadius: Dp?,
    fillAlpha: Float,
    fill: Color,
    content: @Composable () -> Unit,
) {
    val panel = cornerRadius != null
    val style = if (panel) io.github.suqi8.coui.kmp.blur.ViewBlurDefaults.frameworkBottomBar(dark)
        else io.github.suqi8.coui.kmp.blur.ViewBlurDefaults.contentSecondaryButton(dark)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && onClick != null) (if (capsule || panel) 0.97f else 0.92f) else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "frostPress",
    )
    val edge = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.75f)
    val pressTint = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .dropShadow(shape, androidx.compose.ui.graphics.shadow.Shadow(
                radius = if (panel) 16.dp else 10.dp,
                color = Color.Black,
                alpha = if (dark) 0.22f else if (panel) 0.045f else 0.07f,
            ))
            .clip(shape)
            .then(frostBlur(backdrop, style, shape))
            // 输入框（圆角矩形）按上游只用底栏磨砂，不再铺一层底色；胶囊类保留调用方要的底色。
            .then(if (fillAlpha > 0f && !panel) Modifier.background(fill, shape) else Modifier)
            .border(if (focused) 1.dp else 0.6.dp, if (focused) accent.copy(alpha = 0.55f) else edge, shape)
            .drawWithContent {
                drawContent()
                if (pressed && onClick != null) drawRect(pressTint)
            }
            .then(if (onClick != null) Modifier.clickable(source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.then(if (wrapContent) Modifier.fillMaxWidth() else Modifier.fillMaxSize()),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** 新库的视图模糊；没有着色器（单测、软件绘制）时不画，控件只剩细边和底色。 */
internal fun frostBlur(
    backdrop: io.github.suqi8.coui.kmp.blur.LayerBackdrop,
    style: io.github.suqi8.coui.kmp.blur.ViewBlurStyle,
    shape: Shape,
): Modifier = if (io.github.mangi.eta.ui.components.EtaCouiSupport.shaders) {
    Modifier.couiViewBlur(backdrop = backdrop, style = style, shape = shape)
} else {
    Modifier
}

/** 视图是否已挂到某个屏幕上；挂上之后会触发重组。 */
@Composable
private fun rememberViewHasDisplay(view: android.view.View): Boolean {
    val state = remember(view) { mutableStateOf(view.display != null) }
    androidx.compose.runtime.DisposableEffect(view) {
        val listener = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { state.value = v.display != null }
            override fun onViewDetachedFromWindow(v: android.view.View) = Unit
        }
        view.addOnAttachStateChangeListener(listener)
        // 组合提交时再看一次：第一次组合和挂上屏幕之间的空档里，监听器可能已经错过了。
        state.value = view.display != null
        onDispose { view.removeOnAttachStateChangeListener(listener) }
    }
    return state.value
}

private fun newFloatingRenderer(): CouiFloatingRenderer? =
    if (Build.VERSION.SDK_INT >= 34 && io.github.mangi.eta.ui.components.EtaCouiSupport.shaders) {
        runCatching { CouiFloatingRenderer.standalone() }
            .onFailure { error ->
                io.github.mangi.eta.core.AndroidAgentLogger.warnThrottled("coui_floating_renderer_failed") {
                    "COUI floating renderer unavailable: ${error.javaClass.simpleName}"
                }
            }
            .getOrNull()
    } else null

/** 着色器渲染器；偶尔建失败（如 GPU 正忙）时，接下来几帧再试，不让控件永久退回实色。 */
@Composable
private fun rememberFloatingRenderer(): CouiFloatingRenderer? {
    val state = remember { mutableStateOf(newFloatingRenderer()) }
    if (state.value == null && Build.VERSION.SDK_INT >= 34 && io.github.mangi.eta.ui.components.EtaCouiSupport.shaders) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            repeat(RENDERER_RETRY_FRAMES) {
                androidx.compose.runtime.withFrameNanos { }
                newFloatingRenderer()?.let { state.value = it; return@LaunchedEffect }
            }
        }
    }
    return state.value
}

private const val RENDERER_RETRY_FRAMES = 3

@RequiresApi(34)
@Composable
internal fun EtaApi34FloatingSurface(
    modifier: Modifier,
    shape: Shape,
    capsule: Boolean,
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    dark: Boolean,
    focused: Boolean,
    accentColor: Color,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
    wrapContent: Boolean = false,
    cornerRadius: Dp? = null,
) {
    val density = LocalDensity.current.density
    val contour = LocalCouiContourGlowEnabled.current
    val material = remember(dark, capsule) {
        CouiTopAppBarButtonDefaults.systemMaterial(dark = dark, capsule = capsule)
    }
    val shadow = remember(dark, capsule, density, contour) {
        CouiTopAppBarButtonDefaults.shadow(
            dark = dark,
            capsule = capsule,
            density = density,
            contour = contour,
            search = false,
            alphaScale = 1f,
        )
    }
    var boundsSize by remember { mutableStateOf(IntSize.Zero) }
    val contentLayer = rememberGraphicsLayer()
    val interactive = onClick != null
    val motion = rememberCouiTouchMotion(interactive)
    val press = rememberCouiPressMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = interactive,
        preset = if (capsule) SpotlightPreset.OpaqueMedium2 else SpotlightPreset.OpaqueSmall2,
        dark = dark,
    )

    Box(
        modifier = modifier
            .onSizeChanged { boundsSize = it }
            .drawWithCache {
                val lightClip = Path().apply {
                    addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                }
                onDrawWithContent {
                    drawContent()
                    if (drawLight != null) {
                        withTransform({
                            translate(motion.translationX.value, motion.translationY.value)
                            scale(
                                motion.scaleX.value + press.scale.value - 1f,
                                motion.scaleY.value + press.scale.value - 1f,
                                center,
                            )
                        }) {
                            if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) clipPath(lightClip) { drawLight.invoke(this) }
                        }
                    }
                }
            }
            .couiFloatingSurface(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = shape,
                contentLayer = contentLayer,
                contourGlowEnabled = contour,
                foregroundComposition = true,
            ) {
                CouiFloatingSurface(
                    bounds = Rect(
                        Offset(motion.translationX.value, motion.translationY.value),
                        Size(boundsSize.width.toFloat(), boundsSize.height.toFloat()),
                    ),
                    scaleX = motion.scaleX.value + press.scale.value - 1f,
                    scaleY = motion.scaleY.value + press.scale.value - 1f,
                    cornerWeight = if (capsule) 1f else 2f,
                    cornerRadius = cornerRadius?.let { it.value * density },
                )
            }
            .then(
                if (focused) {
                    Modifier.border(1.dp, accentColor.copy(alpha = 0.55f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .then(if (wrapContent) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .graphicsLayer {
                    translationX = motion.translationX.value
                    translationY = motion.translationY.value
                    scaleX = motion.scaleX.value + press.scale.value - 1f
                    scaleY = motion.scaleY.value + press.scale.value - 1f
                }
                .then(
                    if (interactive) {
                        Modifier
                            .couiTouchMotion(motion, true)
                            .couiPressMotion(press, enabled = true)
                            .then(lightInput)
                    } else {
                        Modifier
                    },
                )
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                }
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier
                            .couiToolbarFeedback(dark, interactionSource, enabled = true)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = onClick,
                            )
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

internal enum class EtaCouiButtonRole { Primary, Secondary, Destructive, DestructiveFilled }

/**
 * 上游的 COUI 胶囊按钮：胶囊形、按下回弹、触点聚光、轮廓光（Android 13+）。主按钮用强调色填充，
 * 次按钮浅灰底，危险按钮红字。
 */
@Composable
internal fun EtaCouiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: EtaCouiButtonRole = EtaCouiButtonRole.Secondary,
    minHeight: Dp = CouiDialogDefaults.buttonHeight,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val dark = CouiTheme.isDark
    val scheme = CouiTheme.colorSchemeFor(dark)
    val dialogColors = CouiDialogDefaults.themeColors(dark)
    val isPrimaryLike = role == EtaCouiButtonRole.Primary || role == EtaCouiButtonRole.DestructiveFilled
    val fillColor = when (role) {
        EtaCouiButtonRole.Primary -> if (enabled) scheme.accent else scheme.accent.copy(alpha = 0.3f)
        EtaCouiButtonRole.DestructiveFilled -> if (enabled) dialogColors.destructive else dialogColors.destructive.copy(alpha = 0.3f)
        EtaCouiButtonRole.Secondary, EtaCouiButtonRole.Destructive -> if (enabled) {
            if (dark) Color(0xFF353535) else Color(0xFFEBEBEB)
        } else {
            if (dark) Color(0x1AFFFFFF) else Color(0x14000000)
        }
    }
    val textColor = when (role) {
        EtaCouiButtonRole.Primary, EtaCouiButtonRole.DestructiveFilled -> if (enabled) Color.White else Color(0x4DFFFFFF)
        EtaCouiButtonRole.Destructive -> if (enabled) dialogColors.destructive else dialogColors.disabledAction
        EtaCouiButtonRole.Secondary -> if (enabled) scheme.onSurface else dialogColors.disabledAction
    }
    val source = remember { MutableInteractionSource() }
    val motion = rememberCouiTouchMotion(enabled)
    val press = rememberCouiPressMotion()
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = enabled,
        dark = dark,
        preset = if (isPrimaryLike) SpotlightPreset.OpaqueMedium1 else SpotlightPreset.OpaqueMedium2,
        cancelOnConsumedMove = true,
    )
    val glow = LocalCouiContourGlowEnabled.current
    val contour = remember(glow) {
        if (glow && Build.VERSION.SDK_INT >= 33) CouiContour(style = CouiContourStyle.Button) else null
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .graphicsLayer {
                scaleX = motion.scaleX.value + press.scale.value - 1f
                scaleY = motion.scaleY.value + press.scale.value - 1f
                translationX = motion.translationX.value
                translationY = motion.translationY.value
            }
            .couiTouchMotion(motion, enabled)
            .couiPressMotion(press, enabled, cancelOnConsumedMove = true)
            .then(lightInput)
            .clip(CouiCapsuleShape)
            .drawWithContent {
                drawContent()
                // 光效用 RuntimeShader，只能画在硬件加速画布上（截图、导出图片时走软件绘制）。
                if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) drawLight?.invoke(this)
            }
            .drawWithContent {
                drawRect(fillColor)
                if (Build.VERSION.SDK_INT >= 33 && contour != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                    val alpha = if (isPrimaryLike) {
                        if (dark) { if (enabled) 0.25f else 0.05f } else 0.3f
                    } else if (dark) {
                        if (enabled) 0.1f else 0.03f
                    } else {
                        if (enabled) 0.8f else 0.24f
                    }
                    val inner = if (isPrimaryLike) {
                        if (enabled) 0.1f else if (dark) 0.06f else 0.04f
                    } else if (dark) 0.01f else 0.05f
                    paint.shader = contour.shader(
                        size = size,
                        density = density,
                        radiusPx = size.minDimension / 2f,
                        cornerWeight = if (size.width == size.height) 2f else 1f,
                        surfaceColor = Color.Transparent,
                        innerGlowAlpha = inner,
                        lineAlpha = alpha,
                        effectAlpha = 1f,
                        lineWidthPx = (if (isPrimaryLike) 2.5f else 3f) * density,
                    )
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                }
                drawContent()
            }
            .couiButtonFeedback(dark, source, enabled)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, kind = CouiTextKind.ItemTitle, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
