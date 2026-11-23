package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.etaElevation
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
    // 与上游一致：COUI 2.1.0 对话框（手机上从底部升起，材质模糊背后的页面），按钮在底部按钮栏。
    io.github.mangi.eta.ui.components.coui.EtaCouiDialog(
        show = show,
        title = title,
        message = summary,
        onDismissRequest = { onDismissRequest?.invoke() },
        closeText = androidx.compose.ui.res.stringResource(io.github.mangi.eta.R.string.action_close),
        modifier = modifier,
        onDismissFinished = onDismissFinished,
    ) { content() }
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
    // 与上游一致：COUI 2.1.0 对话框（手机上从底部升起，材质模糊背后的页面），按钮在底部按钮栏。
    io.github.mangi.eta.ui.components.coui.EtaCouiDialog(
        show = show,
        title = title,
        message = summary,
        onDismissRequest = { onDismissRequest?.invoke() },
        closeText = androidx.compose.ui.res.stringResource(io.github.mangi.eta.R.string.action_close),
        modifier = modifier,
        onDismissFinished = onDismissFinished,
    ) { content() }
}


@Composable
internal fun EtaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 与上游一致的 COUI 开关（拖动、回弹和触感都由它处理）。打开时轨道用正文色：浅色是黑、深色是浅色，不再满屏珊瑚橘。
    val dark = EtaTheme.colors.isDark
    io.github.mangi.eta.ui.components.coui.EtaCouiSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        dark = dark,
        trackOn = EtaTheme.colors.textPrimary,
        // 圆点用页面底色：浅色模式白点配黑轨道，深色模式深色圆点配浅色轨道，两种状态都看得清。
        thumb = if (dark) EtaTheme.colors.canvas else Color.White,
    )
}

@Composable
private fun EtaDialogHeading(title: String?, summary: String?) {
    title?.let { Text(it, style = EtaTheme.type.title2, color = EtaTheme.colors.textPrimary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).semantics { heading() }) }
    summary?.let { Text(it, style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) }
}
