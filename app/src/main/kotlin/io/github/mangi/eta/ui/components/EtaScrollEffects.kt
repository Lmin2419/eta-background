package io.github.mangi.eta.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme

/*
 * 跟随滚动变大变小的小效果（iOS 风格）：
 * - 卡片滑到顶栏下面时缩小并淡出，往回滑时放大还原；从屏幕底边进入时轻微放大到位。
 * - 对话页滚动时顶栏的按钮和模型胶囊轻微收小，往回滑时变大。
 * 全部只改绘制层（graphicsLayer），不改布局，不影响点击区域；系统「减少动画」时关闭。
 */

/**
 * 列表项随位置缩放：[topInset] 是顶栏下沿（项滑过这条线开始收）。
 * 依据项在列表里的实际位置计算，放在 Lazy 列表项的根上。
 */
internal fun Modifier.etaScrollEdgeScale(topInset: Dp): Modifier = composed {
    if (EtaTheme.motion.reduceMotion) return@composed this
    var top by remember { mutableFloatStateOf(Float.NaN) }
    var height by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableFloatStateOf(0f) }
    onPlaced { coords ->
        top = coords.positionInParent().y
        height = coords.size.height.toFloat()
        viewport = coords.parentLayoutCoordinates?.size?.height?.toFloat() ?: 0f
    }.graphicsLayer {
        if (top.isNaN() || height <= 0f) return@graphicsLayer
        val edge = topInset.toPx()
        // 很高的卡片收得少一些，避免整页大块晃动。
        val damp = (220.dp.toPx() / height).coerceIn(0.35f, 1f)
        val out = ((edge - top) / height).coerceIn(0f, 1f)
        val enter = if (viewport > 0f) ((top + height - viewport) / height).coerceIn(0f, 1f) else 0f
        if (out > 0f) {
            val s = 1f - 0.04f * out * damp
            scaleX = s; scaleY = s
            alpha = 1f - 0.35f * out
            transformOrigin = TransformOrigin(0.5f, 1f)
        } else if (enter > 0f) {
            val s = 1f - 0.05f * enter * damp
            scaleX = s; scaleY = s
            alpha = 1f - 0.25f * enter
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}

/** 把调用方的单个 item 包上滚动缩放；items 列表行保留各自的增删动画，不包。 */
@OptIn(ExperimentalFoundationApi::class)
internal class EtaScrollEdgeListScope(private val delegate: LazyListScope, private val topInset: Dp) : LazyListScope by delegate {
    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        val inset = topInset
        delegate.item(key, contentType) {
            val scope = this
            Box(Modifier.etaScrollEdgeScale(inset)) { scope.content() }
        }
    }
}

/** 对话页顶部按钮的收缩进度：0 完全展开，1 收到最小。由消息列表的滚动驱动、顶栏读取。 */
@Stable
internal class EtaChromeState {
    var collapse by mutableFloatStateOf(0f)
        private set

    fun reset() { collapse = 0f }

    /** 手指上推（往新消息方向读）时收小，下拉（往回看）时变大；只响应用户手势。 */
    fun connection(rangePx: Float) = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y != 0f) {
                collapse = (collapse - available.y / rangePx).coerceIn(0f, 1f)
            }
            return Offset.Zero
        }
    }
}

internal val LocalEtaChrome = staticCompositionLocalOf<EtaChromeState?> { null }

/** 顶栏元素随 [LocalEtaChrome] 收缩：最多缩到 [minScale]，并略微变淡。 */
internal fun Modifier.etaChromeScale(minScale: Float = 0.86f): Modifier = composed {
    val chrome = LocalEtaChrome.current ?: return@composed this
    if (EtaTheme.motion.reduceMotion) return@composed this
    val c = androidx.compose.animation.core.animateFloatAsState(chrome.collapse,
        androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f), label = "chromeScale")
    graphicsLayer {
        val s = 1f - (1f - minScale) * c.value
        scaleX = s; scaleY = s
        alpha = 1f - 0.12f * c.value
    }
}
