package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/**
 * 玻璃窗口里的页面信息：在窗口里时页面不加状态栏内边距、底色透明（由窗口外框提供），
 * 栈深度为 1 时不显示返回箭头（右上角有关闭键），深度 > 1 时显示返回箭头。
 */
@Stable
internal class EtaWindowInfo(val depth: Int)

internal val LocalEtaWindow = compositionLocalOf<EtaWindowInfo?> { null }

/**
 * 玻璃窗口（§3.3）：从底部升起，盖在当前页面上；顶部距状态栏 24dp、上圆角 36；外框是玻璃
 * （底色 `surfaceSunken` 0.90、E3），里面的卡片仍是实色。背后的页面缩小、变暗（由宿主按 [EtaOverlayController.windowProgress] 绘制）。
 * 顶部抓手可下拉：超过 30% 或速度超过 1200dp/s 关闭，否则回弹。宽屏居中 560dp、最高 86%、四角都是 36。
 *
 * [open] 为真时显示；[closing] 为真时播放关闭动画，播完调用 [onClosed]。
 */
@Composable
internal fun EtaGlassWindow(
    open: Boolean,
    closing: Boolean,
    depth: Int,
    onRequestClose: () -> Unit,
    onClosed: () -> Unit,
    onBack: () -> Unit = onRequestClose,
    content: @Composable () -> Unit,
) {
    val overlay = LocalEtaOverlay.current
    val motion = EtaTheme.motion
    val progress = remember { Animatable(0f) }
    val shown = open && !closing
    LaunchedEffect(shown, open) {
        if (shown) {
            progress.animateTo(1f, if (motion.reduceMotion) tween(motion.short) else tween(motion.long, easing = motion.emphasizedDecelerate)) {
                overlay?.windowProgress = value
            }
        } else if (progress.value > 0f || open) {
            progress.animateTo(0f, tween(motion.exitDuration(motion.medium), easing = motion.accelerate)) {
                overlay?.windowProgress = value
            }
            overlay?.windowProgress = 0f
            if (open) onClosed()
        }
    }
    val active = open || progress.value > 0f
    val body: @Composable () -> Unit = {
        EtaGlassWindowFrame(progress, depth, closing, onRequestClose, onBack, content)
    }
    if (active && !EtaOverlayEntry(true, body)) {
        // 没有宿主（测试）时直接画在原地。
        body()
    }
}

@Composable
private fun EtaGlassWindowFrame(
    progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    depth: Int,
    closing: Boolean,
    onRequestClose: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = EtaTheme.motion
    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(0f) }
    val dragOffset = remember { Animatable(0f) }
    // 返回键：窗口里有下一级时退一级，否则关闭窗口（窗口内的页面切换由内层导航自己处理并优先）。
    EtaOverlayBackHandler(enabled = !closing, onBack = onBack)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val windowHeightPx = with(density) { maxHeight.toPx() }
        // 背后：点空白处关闭（不另加遮罩，变暗由宿主对页面层做）。
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onRequestClose))
        val shape = if (wide) EtaTheme.shapes.radiusWindow
            else RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
        Column(
            Modifier
                .align(if (wide) Alignment.Center else Alignment.BottomCenter)
                .then(if (wide) Modifier.width(560.dp).fillMaxHeight(0.86f) else Modifier.fillMaxWidth().padding(top = statusTop + 24.dp).fillMaxHeight())
                .graphicsLayer {
                    val p = progress.value
                    translationY = (1f - p) * (if (wide) size.height * 0.25f + 48.dp.toPx() else size.height) + dragOffset.value
                    if (wide) alpha = p.coerceIn(0f, 1f)
                }
                .etaGlass(shape, tint = EtaTheme.colors.surfaceSunken, elevation = EtaElevation.E3,
                    blurAlpha = EtaGlassDefaults.WindowAlpha, solidAlpha = 0.98f)
                .clip(shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .imePadding(),
        ) {
            // 顶部：抓手（可下拉关闭）+ 右上角关闭键。
            Box(
                Modifier.fillMaxWidth().height(36.dp)
                    .draggable(
                        rememberDraggableState { delta ->
                            drag = (drag + delta).coerceAtLeast(0f)
                            scope.launch { dragOffset.snapTo(drag) }
                        },
                        Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val fast = velocity > with(density) { 1200.dp.toPx() }
                            if (fast || drag > windowHeightPx * 0.3f) {
                                onRequestClose()
                            }
                            drag = 0f
                            dragOffset.animateTo(0f, motion.follow())
                        },
                    ),
            ) {
                Box(Modifier.align(Alignment.Center).size(40.dp, 5.dp)
                    .background(EtaTheme.colors.textQuiet.copy(alpha = 0.45f), EtaTheme.shapes.radiusFull))
                val closeLabel = stringResource(R.string.action_close)
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 6.dp)
                        .requiredSize(30.dp)
                        .clip(EtaTheme.shapes.radiusFull)
                        .background(EtaTheme.colors.textPrimary.copy(alpha = 0.06f))
                        .clickable(onClickLabel = closeLabel, onClick = onRequestClose)
                        .semantics { contentDescription = closeLabel; role = androidx.compose.ui.semantics.Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(16.dp), tint = EtaTheme.colors.textSecondary)
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalEtaWindow provides EtaWindowInfo(depth)) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}
