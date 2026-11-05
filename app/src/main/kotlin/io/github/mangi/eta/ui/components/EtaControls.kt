package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.etaElevation
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.window.WindowDialog

internal object EtaControlDefaults {
    val ButtonCornerRadius = 999.dp
    val ButtonMinHeight = 48.dp
    val ButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    val DialogInsideMargin = DpSize(24.dp, 24.dp)
    val DialogOutsideMargin = DpSize(24.dp, 24.dp)
}

@Composable
internal fun EtaWindowDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    modifier: Modifier = Modifier,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    EtaPreferenceTheme {
        WindowDialog(
            show = show, title = null, summary = null, modifier = modifier
                .semantics { title?.let { paneTitle = it } }.etaElevation(EtaElevation.E3, EtaTheme.shapes.radiusXl),
            backgroundColor = EtaTheme.colors.surfaceRaised,
            onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished,
            cornerRadius = 28.dp,
            insideMargin = EtaControlDefaults.DialogInsideMargin,
            outsideMargin = EtaControlDefaults.DialogOutsideMargin,
            maxWidth = 360.dp,
            content = { EtaDialogHeading(title, summary); content() },
        )
    }
}

@Composable
internal fun EtaOverlayDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    modifier: Modifier = Modifier,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    EtaPreferenceTheme {
        OverlayDialog(
            show = show, title = null, summary = null, modifier = modifier
                .semantics { title?.let { paneTitle = it } }.etaElevation(EtaElevation.E3, EtaTheme.shapes.radiusXl),
            backgroundColor = EtaTheme.colors.surfaceRaised,
            onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished,
            cornerRadius = 28.dp,
            insideMargin = EtaControlDefaults.DialogInsideMargin,
            outsideMargin = EtaControlDefaults.DialogOutsideMargin,
            maxWidth = 360.dp,
            content = { EtaDialogHeading(title, summary); content() },
        )
    }
}

@Composable
internal fun EtaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = Switch(
    checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
    colors = SwitchDefaults.switchColors(
        checkedThumbColor = Color.White, uncheckedThumbColor = Color.White,
        disabledCheckedThumbColor = Color.White.copy(alpha = 0.38f),
        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.38f),
        checkedTrackColor = EtaTheme.colors.accent, uncheckedTrackColor = EtaTheme.colors.outlineStrong,
        disabledCheckedTrackColor = EtaTheme.colors.accent.copy(alpha = 0.38f),
        disabledUncheckedTrackColor = EtaTheme.colors.outlineStrong.copy(alpha = 0.38f),
    ),
    modifier = modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = 52.dp.roundToPx()
        val height = 32.dp.roundToPx()
        layout(width, height) {
            placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
                scaleX = width.toFloat() / placeable.width
                scaleY = height.toFloat() / placeable.height
            }
        }
    },
)

@Composable
private fun EtaDialogHeading(title: String?, summary: String?) {
    title?.let { Text(it, style = EtaTheme.type.title2, color = EtaTheme.colors.textPrimary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).semantics { heading() }) }
    summary?.let { Text(it, style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) }
}
