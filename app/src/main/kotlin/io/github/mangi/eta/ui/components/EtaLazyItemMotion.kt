package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme

/** Uses the existing lazy item node; reduced motion never animates its placement. */
@Composable
internal fun LazyItemScope.etaAnimateItem(): Modifier {
    val motion = EtaTheme.motion
    return Modifier.animateItem(
        fadeInSpec = tween(motion.medium, easing = motion.emphasizedDecelerate),
        placementSpec = if (motion.reduceMotion) null else spring(motion.dampingRatio, motion.stiffness),
        fadeOutSpec = tween(motion.exitDuration(260), easing = motion.accelerate),
    )
}

/**
 * 新出现的条目从下方 16dp 升起（[active] 只在条目首次出现时为真，历史条目不分配动画）。
 * 只改绘制层位移，不影响布局；减少动画时不位移。
 */
@Composable
internal fun Modifier.etaRiseIn(active: Boolean): Modifier {
    val motion = EtaTheme.motion
    if (!active || motion.reduceMotion) return this
    val progress = remember { Animatable(1f) }
    // 弹簧托起：24dp，几乎不回弹，约 350ms 落定。
    LaunchedEffect(progress) { progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 220f)) }
    val distance = with(LocalDensity.current) { 24.dp.toPx() }
    return graphicsLayer { translationY = progress.value * distance }
}
