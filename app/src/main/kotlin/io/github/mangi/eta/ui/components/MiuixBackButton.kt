package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
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
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    EtaIconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.action_back),
            Modifier.size(20.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f }, tint = EtaTheme.colors.textPrimary)
    }
}
