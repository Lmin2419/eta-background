package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/** Compatibility facade: callers retain their structure, but no blur layers are allocated/captured. */
@Composable
internal fun rememberTopBarBackdrop(): LayerBackdrop? = null

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun TopBarBackdrop(backdrop: LayerBackdrop?, color: Color = EtaTheme.colors.canvas, content: @Composable () -> Unit) {
    // 分隔线由 AdaptiveTopAppBar 按滚动位置淡入淡出，这里只铺底色。
    Box(Modifier.background(color)) { content() }
}

@Suppress("UNUSED_PARAMETER")
internal fun Modifier.captureForTopBar(backdrop: LayerBackdrop?): Modifier = this

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun topBarContainerColor(backdrop: LayerBackdrop?): Color = EtaTheme.colors.canvas
