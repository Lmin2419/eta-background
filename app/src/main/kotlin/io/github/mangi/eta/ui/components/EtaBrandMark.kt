package io.github.mangi.eta.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R

/** The protected brand vector owns its gradient; never recolor it through Icon tint. */
@Composable
internal fun EtaBrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_eta_mark), stringResource(R.string.app_name), modifier)
}

/**
 * 「正在思考」标识：胖小胖猫头轻轻呼吸（0.9↔1 倍）并左右微歪头，节奏约 1.8 秒。
 * 只在绘制层动（不触发重组）；减少动画时静止。
 */
@androidx.compose.runtime.Composable
internal fun EtaThinkingMark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val motion = io.github.mangi.eta.ui.theme.EtaTheme.motion
    val transition = if (motion.reduceMotion) null else androidx.compose.animation.core.rememberInfiniteTransition(label = "thinkingMark")
    val breath = transition?.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        androidx.compose.animation.core.RepeatMode.Reverse), label = "breath")
    val tilt = transition?.animateFloat(-6f, 6f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        androidx.compose.animation.core.RepeatMode.Reverse), label = "tilt")
    Image(painterResource(R.drawable.ic_eta_mark), null, modifier.size(size).graphicsLayer {
        val b = breath?.value ?: 1f
        val scale = 0.9f + 0.1f * b
        scaleX = scale; scaleY = scale
        alpha = 0.75f + 0.25f * b
        rotationZ = tilt?.value ?: 0f
    })
}
