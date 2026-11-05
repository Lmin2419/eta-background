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
    CompositionLocalProvider(LocalEtaColors provides colors, LocalEtaMotion provides motion, content = content)
}
