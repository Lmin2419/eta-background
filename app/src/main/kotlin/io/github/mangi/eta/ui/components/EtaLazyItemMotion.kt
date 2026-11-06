package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
