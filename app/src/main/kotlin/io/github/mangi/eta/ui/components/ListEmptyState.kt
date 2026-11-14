package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun ListEmptyState(title: String, modifier: Modifier = Modifier, summary: String? = null,
    icon: ImageVector? = null, action: (@Composable () -> Unit)? = null, tint: Color = EtaTheme.colors.tileSlate) {
    val colors = EtaTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        icon?.let {
            Box(Modifier.size(64.dp).etaGlass(EtaTheme.shapes.radiusFull, tint = colors.tileBackground(tint), elevation = io.github.mangi.eta.ui.theme.EtaElevation.E1, useLocalBackdrop = false, solidAlpha = 0.85f), contentAlignment = Alignment.Center) {
                Icon(it, null, Modifier.size(28.dp), tint = colors.tileForeground(tint))
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = EtaTheme.type.headline, color = colors.textPrimary, textAlign = TextAlign.Center)
        summary?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = EtaTheme.type.subhead, color = colors.textSecondary, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        action?.let { Spacer(Modifier.height(20.dp)); it() }
    }
}
