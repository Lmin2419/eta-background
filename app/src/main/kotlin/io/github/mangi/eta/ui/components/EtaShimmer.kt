package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme

/**
 * 加载占位：浅灰圆角条，一道柔光从左往右缓缓扫过（1.3 秒一轮）。
 * 减少动画时只显示静态灰条。占位不进入无障碍树。
 */
internal fun Modifier.etaShimmer(): Modifier = composed {
    val colors = EtaTheme.colors
    val base = colors.surfaceSunken
    val glow = colors.canvas.copy(alpha = 0.75f)
    val reduce = EtaTheme.motion.reduceMotion
    val sweep = if (reduce) null else rememberInfiniteTransition(label = "shimmer")
        .animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    background(base).drawWithCache {
        onDrawWithContent {
            drawContent()
            val p = sweep?.value ?: return@onDrawWithContent
            val w = size.width
            val x = w * p
            drawRect(Brush.linearGradient(listOf(Color.Transparent, glow, Color.Transparent),
                start = Offset(x - w * 0.35f, 0f), end = Offset(x + w * 0.35f, size.height)))
        }
    }
}

@Composable
private fun Bar(width: Float, height: Int = 12, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(width).height(height.dp).clip(EtaTheme.shapes.radiusFull).etaShimmer())
}

/** 列表加载中：几行「标题 + 时间」形状的占位。 */
@Composable
internal fun EtaSkeletonRows(count: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Bar(width = listOf(0.62f, 0.48f, 0.7f, 0.55f, 0.66f, 0.42f)[i % 6], modifier = Modifier.weight(1f))
                Spacer(Modifier.width(24.dp))
                Box(Modifier.size(width = 36.dp, height = 10.dp).clip(EtaTheme.shapes.radiusFull).etaShimmer())
            }
        }
    }
}

/** 会话加载中：一条靠右的「你」气泡 + 几行回答文字的占位。 */
@Composable
internal fun EtaSkeletonChat(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.fillMaxWidth(0.55f).height(40.dp).clip(EtaTheme.shapes.radiusLg).etaShimmer())
        }
        Spacer(Modifier.height(4.dp))
        Bar(0.92f); Bar(0.86f); Bar(0.95f); Bar(0.6f)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.fillMaxWidth(0.4f).height(40.dp).clip(EtaTheme.shapes.radiusLg).etaShimmer())
        }
        Spacer(Modifier.height(4.dp))
        Bar(0.9f); Bar(0.75f)
    }
}
