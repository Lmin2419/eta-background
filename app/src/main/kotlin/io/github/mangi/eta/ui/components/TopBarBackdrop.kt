package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/** Compatibility facade: callers retain their structure, but no blur layers are allocated/captured. */
@Composable
internal fun rememberTopBarBackdrop(): LayerBackdrop? = null

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun TopBarBackdrop(backdrop: LayerBackdrop?, content: @Composable () -> Unit) {
    val colors = EtaTheme.colors
    Box(Modifier.background(colors.canvas).drawWithContent {
        drawContent()
        drawLine(colors.outline, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
    }) { content() }
}

@Suppress("UNUSED_PARAMETER")
internal fun Modifier.captureForTopBar(backdrop: LayerBackdrop?): Modifier = this

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun topBarContainerColor(backdrop: LayerBackdrop?): Color = EtaTheme.colors.canvas
