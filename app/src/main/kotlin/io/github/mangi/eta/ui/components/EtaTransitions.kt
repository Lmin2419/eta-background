package io.github.mangi.eta.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.mangi.eta.ui.theme.EtaTheme

/** Shared accessibility fallback, including callers which supply a spatial transition. */
@Composable
internal fun EtaAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition? = null,
    exit: ExitTransition? = null,
    label: String = "etaVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motion = EtaTheme.motion
    val fadeEnter = fadeIn(tween(motion.medium, easing = motion.emphasizedDecelerate))
    val fadeExit = fadeOut(tween(motion.exitDuration(260), easing = motion.accelerate))
    AnimatedVisibility(visible, modifier,
        enter = if (motion.reduceMotion) fadeEnter else enter ?: (fadeEnter + expandVertically(tween(motion.medium, easing = motion.emphasizedDecelerate))),
        exit = if (motion.reduceMotion) fadeExit else exit ?: (fadeExit + shrinkVertically(tween(motion.exitDuration(260), easing = motion.accelerate))),
        label = label, content = content)
}

@Composable
internal fun <T> EtaCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: FiniteAnimationSpec<Float>? = null,
    label: String = "etaCrossfade",
    content: @Composable (T) -> Unit,
) {
    val motion = EtaTheme.motion
    Crossfade(targetState, modifier,
        animationSpec = if (motion.reduceMotion) tween(motion.short, easing = motion.standard)
            else animationSpec ?: tween(motion.short, easing = motion.standard),
        label = label, content = content)
}

@Composable
internal fun <T> EtaAnimatedContent(
    targetState: T,
    modifier: Modifier = Modifier,
    transitionSpec: (AnimatedContentTransitionScope<T>.() -> ContentTransform)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    label: String = "etaContent",
    contentKey: (T) -> Any? = { it },
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    val motion = EtaTheme.motion
    AnimatedContent(targetState, modifier, transitionSpec = {
        if (!motion.reduceMotion && transitionSpec != null) transitionSpec()
        else (fadeIn(tween(motion.short, easing = motion.standard)) togetherWith
            fadeOut(tween(motion.exitDuration(180), easing = motion.accelerate))).using(null)
    }, contentAlignment = contentAlignment, label = label, contentKey = contentKey, content = content)
}
