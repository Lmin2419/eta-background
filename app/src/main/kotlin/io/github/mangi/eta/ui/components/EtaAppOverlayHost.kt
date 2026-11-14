package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Scaffold

/** One full-window sheet host, also above the sibling drawer and the wide task panel. */
@Composable
internal fun EtaAppOverlayHost(content: @Composable () -> Unit) {
    Scaffold(Modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0.dp)) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            // 聊天图片点开后原位放大到全屏（上游 3.4.2 的查看器），覆盖在所有页面之上。
            ChatImagePreviewHost { content() }
            // 全局提示条：在输入框上方，避开导航栏和键盘。
            EtaToastHost(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 24.dp).padding(bottom = 84.dp))
        }
    }
}
