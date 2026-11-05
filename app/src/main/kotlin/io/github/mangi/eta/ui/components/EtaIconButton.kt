package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor

/** 40dp visual, at least 48dp touch target; the icon slot owns its accessible description. */
@Composable
internal fun EtaIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    backgroundColor: Color = Color.Transparent,
    cornerRadius: Dp = 999.dp,
    minHeight: Dp = 48.dp,
    minWidth: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier.defaultMinSize(minWidth.coerceAtLeast(48.dp), minHeight.coerceAtLeast(48.dp))
        .clip(shape).clickable(enabled = enabled, role = Role.Button, interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).clip(shape)
            .background(if (backgroundColor == Color.Unspecified) Color.Transparent else backgroundColor)
            .background(if (enabled && (pressed || holdDownState)) EtaTheme.colors.pressed else Color.Transparent)
            .alpha(if (enabled) 1f else 0.38f), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides EtaTheme.colors.textSecondary, content = content)
        }
    }
}
