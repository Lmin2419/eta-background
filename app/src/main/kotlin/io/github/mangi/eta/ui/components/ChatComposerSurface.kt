package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme

/** The fade occupies layout space above every composer element, including task and queue strips. */
@Composable
internal fun ChatComposerSurface(
    showFade: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val canvas = EtaTheme.colors.canvas
    val glass = LocalEtaGlassBackdrop.current != null
    Column(
        modifier.fillMaxWidth().imePadding().drawWithCache {
            if (glass) {
                // 玻璃模式：底部一层很淡的渐变，压住胶囊外露出的文字，胶囊本身透出模糊的消息。
                val brush = Brush.verticalGradient(listOf(canvas.copy(alpha = 0f), canvas.copy(alpha = 0.55f)))
                return@drawWithCache onDrawBehind { drawRect(brush) }
            }
            val fade = if (showFade) 24.dp.toPx().coerceAtMost(size.height) else 0f
            val brush = if (fade > 0f) Brush.verticalGradient(
                listOf(canvas.copy(alpha = 0f), canvas), startY = 0f, endY = fade,
            ) else null
            onDrawBehind {
                if (brush != null) drawRect(brush, size = Size(size.width, fade))
                drawRect(canvas, topLeft = Offset(0f, fade), size = Size(size.width, size.height - fade))
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showFade) Spacer(Modifier.height(24.dp))
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            content = content,
        )
    }
}
