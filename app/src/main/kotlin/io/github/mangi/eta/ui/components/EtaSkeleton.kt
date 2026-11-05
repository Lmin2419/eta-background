package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.mangi.eta.ui.theme.EtaTheme

@Composable
internal fun EtaSkeleton(modifier: Modifier = Modifier, visible: Boolean = true, lines: Int = 3) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val view = LocalView.current
    var inViewport by remember { mutableStateOf(false) }
    val animate = visible && inViewport && lifecycle.isAtLeast(Lifecycle.State.STARTED) && !EtaTheme.motion.reduceMotion
    val pulse = if (animate) rememberInfiniteTransition(label = "skeleton").animateFloat(0.5f, 1f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "skeletonOpacity") else null
    val color = EtaTheme.colors.surfaceSunken
    val lineCount = lines.coerceIn(1, 3)
    Box(modifier.fillMaxWidth().height((lineCount * 24).dp).onGloballyPositioned {
        val bounds = it.boundsInWindow()
        inViewport = bounds.width > 0 && bounds.height > 0 && bounds.right > 0 && bounds.bottom > 0 &&
            bounds.left < view.width && bounds.top < view.height
    }.drawWithContent {
        drawContent()
        listOf(0.92f, 0.72f, 0.5f).take(lineCount).forEachIndexed { index, fraction ->
            drawRoundRect(color, Offset(0f, index * 24.dp.toPx()), Size(size.width * fraction, 12.dp.toPx()),
                CornerRadius(6.dp.toPx()), alpha = pulse?.value ?: 1f)
        }
    })
}
