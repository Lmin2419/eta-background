package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults

/** Restyles the existing modal host; nested scrolling, back, drag and dismissal stay with Miuix. */
@Composable
internal fun EtaSheet(
    show: Boolean, modifier: Modifier = Modifier, title: String? = null,
    startAction: (@Composable () -> Unit)? = null, endAction: (@Composable () -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null, onDismissFinished: (() -> Unit)? = null,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    allowDismiss: Boolean = true, enableNestedScroll: Boolean = true, renderInRootScaffold: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = EtaTheme.colors
    EtaPreferenceTheme {
        OverlayBottomSheet(show = show, title = null,
            startAction = if (title != null || startAction != null || endAction != null) ({
                Row(Modifier.fillMaxWidth().padding(horizontal = if (insideMargin.width == 0.dp) 16.dp else 0.dp)
                    .offset(y = (-6).dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    startAction?.invoke()
                    Text(title.orEmpty(), Modifier.weight(1f).semantics { heading() }, style = EtaTheme.type.title2, color = colors.textPrimary)
                    endAction?.invoke()
                }
            }) else null,
            modifier = modifier.semantics { title?.let { paneTitle = it } }
                .etaElevation(EtaElevation.E3, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).drawWithContent {
                drawContent()
                // Paint only the handle. Its original 24dp drag/tap area and gesture recognizer remain.
                drawRect(colors.surfaceRaised.copy(alpha = 0.94f), Offset(size.width / 2 - 32.dp.toPx(), 0f), Size(64.dp.toPx(), 24.dp.toPx()))
                drawRoundRect(colors.outlineStrong, Offset((size.width - 36.dp.toPx()) / 2, 8.dp.toPx()),
                    Size(36.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            },
            // 玻璃面板：略透出背后的页面。
            backgroundColor = colors.surfaceRaised.copy(alpha = 0.94f), cornerRadius = 28.dp,
            sheetMaxWidth = BottomSheetDefaults.maxWidth,
            insideMargin = insideMargin, onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished,
            allowDismiss = allowDismiss, enableNestedScroll = enableNestedScroll, renderInRootScaffold = renderInRootScaffold,
        ) {
            content()
        }
    }
}
