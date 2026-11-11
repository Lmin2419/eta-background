package io.github.mangi.eta.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalEtaColors = staticCompositionLocalOf { lightEtaColors() }
private val LocalEtaMotion = staticCompositionLocalOf { EtaMotion() }

object EtaTheme {
    val colors: EtaColors @Composable @ReadOnlyComposable get() = LocalEtaColors.current
    val type get() = EtaTypography
    val shapes get() = EtaShapes
    val spacing get() = EtaSpacing
    val motion: EtaMotion @Composable @ReadOnlyComposable get() = LocalEtaMotion.current
}

@Composable
fun EtaTheme(
    colors: EtaColors,
    motion: EtaMotion = rememberEtaMotion(),
    content: @Composable () -> Unit,
) {
    // 光标、选中文字的高亮与拖动手柄统一用珊瑚橘。
    val selection = androidx.compose.foundation.text.selection.TextSelectionColors(
        handleColor = colors.accent, backgroundColor = colors.accent.copy(alpha = 0.26f))
    CompositionLocalProvider(LocalEtaColors provides colors, LocalEtaMotion provides motion,
        androidx.compose.foundation.text.selection.LocalTextSelectionColors provides selection, content = content)
}
