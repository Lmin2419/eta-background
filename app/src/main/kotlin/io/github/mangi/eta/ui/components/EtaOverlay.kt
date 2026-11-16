package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/*
 * 浮层宿主（etax/design/05 §3.1）：小窗、玻璃窗口画在 App 同一棵 Compose 树的最上层，
 * 这样才能模糊背后的内容（Popup / Dialog 是独立窗口，拿不到背后的像素）。
 *
 * 调用方在自己的位置声明浮层；登记时抓住调用处的 CompositionLocal，在宿主里原样提供，
 * 浮层里读到的 Local（角色、页面状态等）与调用处一致。没有宿主（单元测试）时退回 Popup。
 */

@Stable
internal class EtaOverlayController {
    internal class Entry(
        val id: Long,
        val context: State<CompositionLocalContext>,
        val content: State<@Composable () -> Unit>,
    )

    internal val entries = mutableStateListOf<Entry>()
    private var next = 0L

    /** 宿主在窗口中的左上角，用来把 boundsInWindow 换算成浮层坐标。 */
    internal var origin by mutableStateOf(Offset.Zero)

    /** 玻璃窗口的展开程度（0..1），驱动背后页面缩小、变暗。 */
    internal var windowProgress by mutableFloatStateOf(0f)

    internal fun add(context: State<CompositionLocalContext>, content: State<@Composable () -> Unit>): Long {
        val id = ++next
        entries += Entry(id, context, content)
        return id
    }

    internal fun remove(id: Long) {
        entries.removeAll { it.id == id }
    }
}

internal val LocalEtaOverlay = staticCompositionLocalOf<EtaOverlayController?> { null }

/** 整个 App 内容的背景层（宿主捕获），浮层与对话框用它做真模糊。 */
internal val LocalEtaAppBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 在浮层宿主里显示 [content]，[active] 为真期间保持登记。返回是否已交给宿主（false 时调用方自己退回 Popup）。
 */
@Composable
internal fun EtaOverlayEntry(active: Boolean, content: @Composable () -> Unit): Boolean {
    val controller = LocalEtaOverlay.current ?: return false
    val context = rememberUpdatedState(currentCompositionLocalContext)
    val latest = rememberUpdatedState(content)
    DisposableEffect(controller, active) {
        val id = if (active) controller.add(context, latest) else null
        onDispose { id?.let(controller::remove) }
    }
    return true
}

/** 宿主里的浮层层：按登记顺序叠放，后登记的在上。 */
@Composable
internal fun EtaOverlayLayer(controller: EtaOverlayController, appBackdrop: LayerBackdrop?) {
    controller.entries.forEach { entry ->
        key(entry.id) {
            CompositionLocalProvider(entry.context.value) {
                CompositionLocalProvider(LocalEtaGlassBackdrop provides appBackdrop) {
                    entry.content.value()
                }
            }
        }
    }
}

/** 浮层的返回键：最上面的浮层先消费。 */
@Composable
internal fun EtaOverlayBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(state = state, isBackEnabled = enabled, onBackCompleted = onBack)
}

internal enum class EtaPopoverAlign { Start, Center, End }

/** 小窗出现/收回的进度：出现用跟手弹簧，收回原路返回；动画进行中可以反向。 */
@Composable
internal fun rememberEtaOverlayProgress(show: Boolean): Pair<Boolean, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>> {
    var visible by remember { mutableStateOf(show) }
    val progress = remember { Animatable(0f) }
    val motion = EtaTheme.motion
    LaunchedEffect(show) {
        if (show) {
            visible = true
            progress.animateTo(1f, if (motion.reduceMotion) tween(motion.short) else motion.follow())
        } else if (visible) {
            progress.animateTo(0f, tween(motion.exitDuration(motion.short), easing = motion.accelerate))
            visible = false
        }
    }
    return visible to progress
}

/**
 * 小窗（§3.2）：从锚点展开（0.9 → 1 + 淡入，变换原点在锚点一侧），关闭原路返回。
 * 宽 232–300dp，圆角 24，内边距 8；最高占屏幕 60%，超出可滚动；优先在锚点下方，放不下翻到上方，
 * 离屏幕边缘至少 8dp。背后不变暗，点外面或返回键关闭。[anchor] 是锚点在窗口中的位置。
 */
@Composable
internal fun EtaPopover(
    show: Boolean,
    anchor: Rect,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    align: EtaPopoverAlign = EtaPopoverAlign.Start,
    title: String? = null,
    minWidth: androidx.compose.ui.unit.Dp = 232.dp,
    maxWidth: androidx.compose.ui.unit.Dp = 300.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (visible, progress) = rememberEtaOverlayProgress(show)
    if (!visible) return
    val body: @Composable () -> Unit = {
        val origin = LocalEtaOverlay.current?.origin ?: Offset.Zero
        EtaPopoverSurface(anchor.translate(-origin), progress, onDismiss, show, modifier, align, title, minWidth, maxWidth, content)
    }
    if (!EtaOverlayEntry(true, body)) {
        Popup(popupPositionProvider = FullWindowPosition, onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true)) { body() }
    }
}

@Composable
private fun EtaPopoverSurface(
    anchor: Rect,
    progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    onDismiss: () -> Unit,
    active: Boolean,
    modifier: Modifier,
    align: EtaPopoverAlign,
    title: String?,
    minWidth: androidx.compose.ui.unit.Dp,
    maxWidth: androidx.compose.ui.unit.Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = EtaTheme.motion
    EtaOverlayBackHandler(active, onDismiss)
    val shape = RoundedCornerShape(24.dp)
    // 收回动画期间不再拦截触摸：点按直接落到背后的页面。
    Box(
        Modifier
            .fillMaxSize()
            .then(if (active) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                else Modifier),
    ) {
        val density = LocalDensity.current
        val margin = with(density) { 8.dp.roundToPx() }
        val gap = with(density) { 6.dp.roundToPx() }
        var below by remember { mutableStateOf(true) }
        Layout(
            content = {
                Column(
                    modifier
                        .widthIn(min = minWidth, max = maxWidth)
                        .graphicsLayer {
                            val p = progress.value
                            alpha = p.coerceIn(0f, 1f)
                            val scale = if (motion.reduceMotion) 1f else 0.9f + 0.1f * p
                            scaleX = scale; scaleY = scale
                            val x = when (align) { EtaPopoverAlign.Start -> 0f; EtaPopoverAlign.Center -> 0.5f; EtaPopoverAlign.End -> 1f }
                            transformOrigin = TransformOrigin(x, if (below) 0f else 1f)
                        }
                        .semantics { title?.let { paneTitle = it } }
                        .etaGlass(shape, elevation = EtaElevation.E2, blurAlpha = EtaGlassDefaults.PopoverAlpha, solidAlpha = 0.97f)
                        .clip(shape)
                        .then(if (active) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                            else Modifier)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    content = content,
                )
            },
        ) { measurables, constraints ->
            val maxHeight = (constraints.maxHeight * 0.6f).toInt()
            val card = measurables.single().measure(
                Constraints(maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0), maxHeight = maxHeight),
            )
            val spaceBelow = constraints.maxHeight - anchor.bottom - gap - margin
            val spaceAbove = anchor.top - gap - margin
            below = card.height <= spaceBelow || spaceBelow >= spaceAbove
            val y = if (below) (anchor.bottom + gap).toInt() else (anchor.top - gap - card.height).toInt()
            val x = when (align) {
                EtaPopoverAlign.Start -> anchor.left.toInt()
                EtaPopoverAlign.End -> (anchor.right - card.width).toInt()
                EtaPopoverAlign.Center -> (anchor.center.x - card.width / 2f).toInt()
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                card.place(
                    x.coerceIn(margin, (constraints.maxWidth - card.width - margin).coerceAtLeast(margin)),
                    y.coerceIn(margin, (constraints.maxHeight - card.height - margin).coerceAtLeast(margin)),
                )
            }
        }
    }
}

internal object FullWindowPosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}
