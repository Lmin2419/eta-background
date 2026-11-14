package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * 全窗口宿主：页面内容 → 浮层（小窗、玻璃窗口）→ 提示条，三层都在同一棵 Compose 树里，
 * 浮层和提示条因此能对背后的整页内容做真实模糊。玻璃窗口打开时，背后的页面缩到 0.94、
 * 圆角 28、盖一层 18% 的黑（由窗口的展开进度驱动）。
 */
@Composable
internal fun EtaAppOverlayHost(content: @Composable () -> Unit) {
    Scaffold(Modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0.dp)) {
        val appBackdrop = rememberEtaGlassBackdrop()
        val overlay = remember { EtaOverlayController() }
        val reduceMotion = io.github.mangi.eta.ui.theme.EtaTheme.motion.reduceMotion
        androidx.compose.runtime.CompositionLocalProvider(
            LocalEtaOverlay provides overlay,
            LocalEtaAppBackdrop provides appBackdrop,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()
                .onGloballyPositioned { overlay.origin = it.positionInWindow() }) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()
                    .graphicsLayer {
                        val p = overlay.windowProgress.coerceIn(0f, 1f)
                        if (p > 0f && !reduceMotion) {
                            val s = 1f - 0.06f * p
                            scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            shape = androidx.compose.foundation.shape.RoundedCornerShape((28 * p).dp)
                            clip = true
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val p = overlay.windowProgress.coerceIn(0f, 1f)
                        if (p > 0f) drawRect(Color.Black.copy(alpha = 0.18f * p))
                    }
                    .then(if (appBackdrop != null) Modifier.layerBackdrop(appBackdrop) else Modifier)) {
                    // 聊天图片点开后原位放大到全屏（上游 3.4.2 的查看器），覆盖在所有页面之上。
                    ChatImagePreviewHost { content() }
                }
                EtaOverlayLayer(overlay, appBackdrop)
                // 全局提示条：在输入框上方，避开导航栏和键盘。
                androidx.compose.runtime.CompositionLocalProvider(LocalEtaGlassBackdrop provides appBackdrop) {
                    EtaToastHost(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(horizontal = 24.dp).padding(bottom = 84.dp))
                }
            }
        }
    }
}
