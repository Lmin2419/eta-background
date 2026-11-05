package io.github.mangi.eta.ui.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

enum class EtaElevation { E0, E1, E2, E3 }

/** Depth only: the caller supplies its surface fill, so this never paints over content. */
@Composable
fun Modifier.etaElevation(level: EtaElevation, shape: Shape): Modifier {
    if (level == EtaElevation.E0) return this
    val colors = EtaTheme.colors
    if (colors.isDark) {
        return border(1.dp, if (level == EtaElevation.E3) colors.outlineStrong else colors.outline, shape)
    }
    val (elevation, alpha) = when (level) {
        EtaElevation.E0 -> 0.dp to 0f
        EtaElevation.E1 -> 2.dp to 0.06f
        EtaElevation.E2 -> 8.dp to 0.10f
        EtaElevation.E3 -> 24.dp to 0.18f
    }
    return shadow(elevation, shape, clip = false,
        ambientColor = colors.shadow.copy(alpha = alpha),
        spotColor = colors.shadow.copy(alpha = alpha))
}
