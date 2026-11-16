package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon

@Composable
fun MiuixBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 玻璃窗口的第一级用右上角关闭键，不显示返回箭头；窗口里进入下一级后才出现。
    val window = LocalEtaWindow.current
    if (window != null && window.depth <= 1) return
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    EtaIconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.ArrowBackIosNew, stringResource(R.string.action_back),
            Modifier.size(20.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f }, tint = EtaTheme.colors.textPrimary)
    }
}
