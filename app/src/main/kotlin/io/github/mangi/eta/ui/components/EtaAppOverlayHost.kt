package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Scaffold

/** One full-window sheet host, also above the sibling drawer and the wide task panel. */
@Composable
internal fun EtaAppOverlayHost(content: @Composable () -> Unit) {
    Scaffold(Modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0.dp)) { content() }
}
