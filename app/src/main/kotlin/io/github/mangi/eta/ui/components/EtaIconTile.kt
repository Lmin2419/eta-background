package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon

@Composable
internal fun EtaIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = EtaTheme.colors.tileSlate,
    enabled: Boolean = true,
    size: Dp = 32.dp,
    iconSize: Dp = 20.dp,
    contentDescription: String? = null,
) {
    val colors = EtaTheme.colors
    Box(modifier.size(size).alpha(if (enabled) 1f else 0.38f)
        .background(colors.tileBackground(tint), EtaTheme.shapes.radiusSm), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, Modifier.size(iconSize), tint = colors.tileForeground(tint))
    }
}
