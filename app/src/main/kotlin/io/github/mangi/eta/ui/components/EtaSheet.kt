package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * 「半窗」：点开设置项、选项列表时弹出的小窗——居中的圆角卡片，背后的页面模糊，底部一个「关闭」，
 * 点外面或返回键也能关。2026111635 起一度换成了 COUI 底部面板（贴底、带拖动条），2026111642 按反馈换回小窗。
 * 调用处不变（名字仍叫 EtaSheet），所以全 App 一起换回。
 */
@Composable
internal fun EtaSheet(
    show: Boolean, modifier: Modifier = Modifier, title: String? = null,
    startAction: (@Composable () -> Unit)? = null, endAction: (@Composable () -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null, onDismissFinished: (() -> Unit)? = null,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    allowDismiss: Boolean = true, enableNestedScroll: Boolean = true, renderInRootScaffold: Boolean = true,
    content: @Composable () -> Unit,
) {
    // 内容最高占屏幕 72%，里面的列表自己滚动（和换成底部面板之前一致）。
    val screen = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val maxHeight = with(androidx.compose.ui.platform.LocalDensity.current) { (screen * 0.72f).toDp() }
    @Suppress("UNUSED_VARIABLE") val unused = listOf(startAction, endAction, enableNestedScroll, renderInRootScaffold)
    io.github.mangi.eta.ui.components.coui.EtaCouiDialog(
        show = show,
        title = title,
        message = null,
        onDismissRequest = { onDismissRequest?.invoke() },
        closeText = androidx.compose.ui.res.stringResource(io.github.mangi.eta.R.string.action_close),
        modifier = modifier,
        contentPadding = insideMargin.width,
        maxContentHeight = maxHeight,
        dismissible = allowDismiss,
        onDismissFinished = onDismissFinished,
    ) { content() }
}
