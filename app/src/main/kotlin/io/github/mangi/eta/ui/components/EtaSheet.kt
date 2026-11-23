package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** 半窗：COUI 2.1.0 底部面板（拖动条、材质模糊背后的页面），与上游一致；拖下、点外面或返回键关闭。 */
@Composable
internal fun EtaSheet(
    show: Boolean, modifier: Modifier = Modifier, title: String? = null,
    startAction: (@Composable () -> Unit)? = null, endAction: (@Composable () -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null, onDismissFinished: (() -> Unit)? = null,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    allowDismiss: Boolean = true, enableNestedScroll: Boolean = true, renderInRootScaffold: Boolean = true,
    content: @Composable () -> Unit,
) {
    // 内容最高占屏幕 72%，里面的列表自己滚动（和换库前一致）。
    val screen = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val maxHeight = with(androidx.compose.ui.platform.LocalDensity.current) { (screen * 0.72f).toDp() }
    @Suppress("UNUSED_VARIABLE") val unused = Triple(startAction, endAction, renderInRootScaffold)
    io.github.mangi.eta.ui.components.coui.EtaCouiSheet(
        show = show,
        title = title,
        onDismissRequest = { onDismissRequest?.invoke() },
        modifier = modifier,
        insideMargin = insideMargin,
        maxContentHeight = maxHeight,
        dismissible = allowDismiss,
        enableNestedScroll = enableNestedScroll,
        onDismissFinished = onDismissFinished,
    ) { content() }
}
