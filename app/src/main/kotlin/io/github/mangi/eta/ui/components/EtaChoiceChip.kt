package io.github.mangi.eta.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun EtaChoiceChip(text: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val colors = EtaTheme.colors
    val container by animateColorAsState(if (selected) colors.accentSoft else colors.surfaceSunken,
        tween(EtaTheme.motion.micro), label = "chipContainer")
    Row(modifier.heightIn(min = 32.dp).alpha(if (enabled) 1f else 0.38f)
        .semantics { this.selected = selected }.etaGlass(EtaTheme.shapes.radiusFull, tint = container, elevation = null, useLocalBackdrop = false, solidAlpha = if (selected) 1f else 0.82f, edgeWidth = 0.5.dp).clip(EtaTheme.shapes.radiusFull)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null || selected) Icon(leadingIcon ?: Icons.Outlined.Check, null, Modifier.size(16.dp), tint = if (selected) colors.onAccentSoft else colors.textSecondary)
        Text(text, style = EtaTheme.type.footnote, color = if (selected) colors.onAccentSoft else colors.textSecondary)
    }
}
