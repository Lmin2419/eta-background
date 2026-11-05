package io.github.mangi.eta.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButtonColors

/** Caller places this above the input area; this does not own application notifications. */
@Composable
internal fun EtaToast(
    message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null,
) {
    val latestDismiss by rememberUpdatedState(onDismiss)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message, actionLabel, accessibility) {
        if (message != null) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(3000, containsIcons = false, containsText = true,
                containsControls = actionLabel != null && onAction != null) ?: 3000)
            latestDismiss()
        }
    }
    val colors = EtaTheme.colors
    val foreground = colors.toastForeground
    var displayed by remember { mutableStateOf(message.orEmpty()) }
    SideEffect { if (message != null) displayed = message }
    AnimatedVisibility(message != null, modifier.padding(bottom = 12.dp),
        enter = fadeIn(tween(EtaTheme.motion.short)) + slideInVertically(tween(EtaTheme.motion.short)) { it / 2 },
        exit = fadeOut(tween(EtaTheme.motion.short)) + slideOutVertically(tween(EtaTheme.motion.short)) { it / 2 }) {
        Row(Modifier.heightIn(min = 44.dp).background(colors.toastBackground, EtaTheme.shapes.radiusFull)
            .padding(horizontal = 16.dp, vertical = if (actionLabel != null && onAction != null) 6.dp else 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(displayed, Modifier.weight(1f, fill = false), color = foreground, style = EtaTheme.type.subhead)
            if (actionLabel != null && onAction != null) EtaButton(actionLabel, onAction,
                kind = EtaButtonKind.Ghost, size = EtaButtonSize.Small,
                colors = TextButtonColors(Color.Transparent, Color.Transparent, colors.toastAction, colors.toastAction))
        }
    }
}
