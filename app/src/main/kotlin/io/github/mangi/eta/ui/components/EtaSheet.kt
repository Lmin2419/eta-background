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
    // 半窗：与上游一致的 COUI 底部对话框（材质模糊背后的页面），底部「关闭」。
    val screen = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val maxHeight = with(androidx.compose.ui.platform.LocalDensity.current) { (screen * 0.72f).toDp() }
    @Suppress("UNUSED_VARIABLE") val unusedHeader = startAction ?: endAction
    io.github.mangi.eta.ui.components.coui.EtaCouiDialog(
        show = show,
        title = title,
        message = null,
        onDismissRequest = { if (allowDismiss) onDismissRequest?.invoke() },
        closeText = androidx.compose.ui.res.stringResource(io.github.mangi.eta.R.string.action_close),
        modifier = modifier,
        contentPadding = insideMargin.width,
        maxContentHeight = maxHeight,
    ) { content() }
    if (onDismissFinished != null) androidx.compose.runtime.LaunchedEffect(show) {
        if (!show) { kotlinx.coroutines.delay(320); onDismissFinished() }
    }
}

