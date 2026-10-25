package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 长列表保持逐项组合，只对分组首尾绘制外侧圆角。 */
@Composable
internal fun EtaPreferenceGroupItem(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    hasLeading: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val radius = EtaCardDefaults.CornerRadius
    val surface = if (isFirst || isLast) {
        Modifier.squircleSurface(
            color = colors.surfaceContainer,
            topStart = if (isFirst) radius else 0.dp,
            topEnd = if (isFirst) radius else 0.dp,
            bottomStart = if (isLast) radius else 0.dp,
            bottomEnd = if (isLast) radius else 0.dp,
        )
    } else Modifier.background(colors.surfaceContainer)
    CompositionLocalProvider(LocalContentColor provides colors.onSurfaceContainer) {
        Column(modifier.fillMaxWidth().padding(horizontal = EtaPreferenceDefaults.SidePadding).then(surface)) {
            content()
            if (!isLast) EtaPreferenceDivider(hasLeading = hasLeading)
        }
    }
}
