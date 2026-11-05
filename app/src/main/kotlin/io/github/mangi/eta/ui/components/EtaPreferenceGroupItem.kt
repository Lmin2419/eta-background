package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor

/** Preserve per-item lazy composition; only the first/last items own the outside corners. */
@Composable
internal fun EtaPreferenceGroupItem(
    isFirst: Boolean, isLast: Boolean, modifier: Modifier = Modifier, hasLeading: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = EtaTheme.colors
    val radius = EtaCardDefaults.CornerRadius
    val shape = RoundedCornerShape(
        topStart = if (isFirst) radius else 0.dp, topEnd = if (isFirst) radius else 0.dp,
        bottomStart = if (isLast) radius else 0.dp, bottomEnd = if (isLast) radius else 0.dp,
    )
    CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
        Column(modifier.fillMaxWidth().padding(horizontal = EtaPreferenceDefaults.SidePadding)
            .clip(shape).background(colors.surface).drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val stroke = 1.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    if (colors.isDark) clipRect(top = if (isFirst) 0f else stroke,
                        bottom = if (isLast) size.height else size.height - stroke) {
                        // Twice the stroke centers a visible 1dp inside the clipped boundary.
                        drawOutline(outline, colors.outline, style = Stroke(stroke * 2))
                    }
                }
            }) {
            content()
            if (!isLast) EtaPreferenceDivider(hasLeading = hasLeading)
        }
    }
}
