package io.github.mangi.eta.ui.components.coui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.itos.coui.backdrop.ProvideCouiBackdrop
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogMaterial
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
import com.itos.coui.menu.rememberCouiWindowBackdrop
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
internal val LocalChatCouiSource = androidx.compose.runtime.staticCompositionLocalOf<CouiBackdrop?> { null }

internal val LocalEtaCouiBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }

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
    backdrop: CouiBackdrop? = LocalEtaCouiBackdrop.current,
    wrapContent: Boolean = false,
    /** 圆角半径；不传时是胶囊（短边的一半）。输入框这类圆角矩形必须传。 */
    cornerRadius: Dp? = null,
    /** 玻璃上再铺一层表面色的不透明度（0 = 纯玻璃）。输入框要压住背后的文字时用。 */
    fillAlpha: Float = 0f,
    content: @Composable () -> Unit,
) {
    val dark = CouiTheme.isDark
    val fill = io.github.mangi.eta.ui.theme.EtaTheme.colors.surfaceRaised.copy(alpha = fillAlpha)
    val filledContent: @Composable () -> Unit = if (fillAlpha > 0f) ({
        Box(Modifier.then(if (wrapContent) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).background(fill, shape),
            contentAlignment = Alignment.Center) { content() }
    }) else content
    val view = LocalView.current
    val renderer = remember {
        if (Build.VERSION.SDK_INT >= 34 && io.github.mangi.eta.ui.components.EtaCouiSupport.shaders) runCatching { CouiFloatingRenderer.standalone() }.getOrNull() else null
    }
    val accent = io.github.mangi.eta.ui.theme.EtaTheme.colors.accent
    if (Build.VERSION.SDK_INT >= 34 && backdrop != null && renderer != null && view.display != null) {
        ProvideCouiBackdrop(backdrop) {
            EtaApi34FloatingSurface(modifier, shape, capsule, backdrop, renderer, dark, focused, accent,
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

/** 半窗、对话框的 COUI 材质：有窗口背景源且设备支持时启用（背后的页面被模糊），否则为实色。 */
@Composable
internal fun rememberEtaDialogMaterial(dark: Boolean = CouiTheme.isDark): CouiDialogMaterial? {
    val backdrop = rememberCouiWindowBackdrop()
    return if (CouiDialogDefaults.isMaterialSupported() && backdrop != null) CouiDialogDefaults.material(dark) else null
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

/** 主操作（填充强调色；危险操作红色）。 */
@Composable
internal fun etaPrimaryDialogAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    dark: Boolean = CouiTheme.isDark,
): com.itos.coui.dialog.CouiDialogAction {
    val accent = if (destructive) CouiDialogDefaults.themeColors(dark).destructive else CouiTheme.colorSchemeFor(dark).accent
    return com.itos.coui.dialog.CouiDialogAction(
        text = text, onClick = onClick, enabled = enabled,
        style = com.itos.coui.dialog.CouiDialogActionStyle.Primary,
        colors = CouiDialogDefaults.primaryActionColors(accent),
    )
}

/**
 * 对话框里 [io.github.mangi.eta.ui.components.MiuixDialogActions] 声明的按钮，交给 COUI 对话框底部的按钮栏绘制
 * （与上游一致的并排 / 竖排规则）。只把可比较的文案与开关放进状态，点击回调走普通字段，避免反复重组。
 */
internal class EtaDialogActionSink {
    data class Spec(
        val confirmText: String,
        val confirmEnabled: Boolean,
        val destructive: Boolean,
        val cancelText: String,
        val cancelEnabled: Boolean,
    )
    var spec by mutableStateOf<Spec?>(null)
    var onConfirm: () -> Unit = {}
    var onCancel: () -> Unit = {}
}

internal val LocalEtaDialogActionSink = staticCompositionLocalOf<EtaDialogActionSink?> { null }

/**
 * 上游样式的弹窗（半窗）：从底部升起的 COUI 对话框，材质模糊背后的页面；标题、说明、内容，底部按钮栏。
 * 内容里的 MiuixDialogActions 会变成底部按钮；没有声明按钮时底部是「关闭」。
 */
@Composable
internal fun EtaCouiDialog(
    show: Boolean,
    title: String?,
    message: String?,
    onDismissRequest: () -> Unit,
    closeText: String,
    modifier: Modifier = Modifier,
    placement: com.itos.coui.dialog.CouiDialogPlacement = com.itos.coui.dialog.CouiDialogPlacement.Bottom,
    contentPadding: Dp = CouiDialogDefaults.contentPadding,
    maxContentHeight: Dp = Dp.Unspecified,
    dismissible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = CouiTheme.isDark
    val sink = remember { EtaDialogActionSink() }
    val spec = sink.spec
    val confirm = if (spec != null) etaPrimaryDialogAction(spec.confirmText, { sink.onConfirm() }, spec.confirmEnabled, spec.destructive, dark)
        else com.itos.coui.dialog.CouiDialogAction(closeText, onDismissRequest, enabled = dismissible)
    val dismiss = spec?.let { com.itos.coui.dialog.CouiDialogAction(it.cancelText, { sink.onCancel() }, it.cancelEnabled) }
    com.itos.coui.dialog.Dialog(
        modifier = modifier.semantics { title?.let { paneTitle = it } },
        visible = show,
        onDismissRequest = { if (dismissible) onDismissRequest() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
        dark = dark,
        title = title,
        message = message,
        confirmAction = confirm,
        dismissAction = dismiss,
        placement = placement,
        material = rememberEtaDialogMaterial(dark),
        backdrop = rememberCouiWindowBackdrop(),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalEtaDialogActionSink provides sink) {
            // 内容按列排布（与原来的对话框一致）；限制最大高度，里面的列表可以自己滚动。
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth()
                    .then(if (maxContentHeight != Dp.Unspecified) Modifier.heightIn(max = maxContentHeight) else Modifier)
                    .padding(horizontal = contentPadding)
                    .padding(top = 4.dp, bottom = 8.dp),
            ) { content() }
        }
    }
}

/** 上游的系统顶栏按钮样式：流体材质、阴影与轮廓光（Android 14+）。 */
@Composable
internal fun rememberEtaToolbarStyle(dark: Boolean = CouiTheme.isDark): com.itos.coui.topappbar.CouiToolbarStyle {
    val density = LocalDensity.current.density
    val contour = LocalCouiContourGlowEnabled.current
    val textStyle = com.itos.coui.text.rememberCouiTextStyle(CouiTextKind.TopBar)
    val colors = io.github.mangi.eta.ui.theme.EtaTheme.colors
    return CouiTopAppBarButtonDefaults.style(dark).copy(
        contentColor = colors.textPrimary,
        textStyle = textStyle,
        cursorColor = colors.accent,
        material = { CouiTopAppBarButtonDefaults.systemMaterial(dark, it) },
        shadow = { CouiTopAppBarButtonDefaults.shadow(dark, it, density, contour, alphaScale = 1f) },
    )
}

/**
 * COUI 顶栏图标按钮：Android 14+ 是浮动的液态玻璃圆钮（与上游一致），更低版本是带按压反馈的圆形按钮。
 * 必须放在 COUI 顶栏（TopAppBar / LargeTopAppBar）里。
 */
@Composable
internal fun EtaCouiTopBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = CouiTheme.isDark
    val renderer = remember { if (Build.VERSION.SDK_INT >= 34 && io.github.mangi.eta.ui.components.EtaCouiSupport.shaders) runCatching { CouiFloatingRenderer.standalone() }.getOrNull() else null }
    val tint = io.github.mangi.eta.ui.theme.EtaTheme.colors.textPrimary
    if (Build.VERSION.SDK_INT >= 34 && renderer != null) {
        com.itos.coui.topappbar.TopAppBarButton(
            modifier = modifier.then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
            onClick = onClick, enabled = enabled, renderer = renderer, style = rememberEtaToolbarStyle(dark),
        ) {
            top.yukonga.miuix.kmp.basic.Icon(icon, null, Modifier.size(22.dp), tint = tint)
        }
    } else {
        val source = remember { MutableInteractionSource() }
        Box(
            modifier.size(48.dp).clip(com.itos.coui.shape.CouiShape(24.dp, 2f))
                .couiButtonFeedback(dark, source, enabled)
                .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
            contentAlignment = Alignment.Center,
        ) { top.yukonga.miuix.kmp.basic.Icon(icon, null, Modifier.size(22.dp), tint = tint) }
    }
}

/** 二级页的返回键（浮动液态玻璃圆钮）。 */
@Composable
internal fun EtaCouiBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    EtaCouiTopBarIconButton(
        icon = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBackIos,
        contentDescription = androidx.compose.ui.res.stringResource(io.github.mangi.eta.R.string.action_back),
        onClick = onClick,
        modifier = modifier,
    )
}
