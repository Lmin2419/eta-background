package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.etaElevation
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType

internal object EtaCardDefaults {
    val CornerRadius = 20.dp
}

@Composable
internal fun EtaCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(16.dp),
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalSquircleEnabled provides false) {
        val (cardModifier, cardColors) = etaGlassCard(modifier, colors)
        Card(
            modifier = cardModifier,
            cornerRadius = EtaCardDefaults.CornerRadius,
            insideMargin = insideMargin,
            colors = cardColors,
            content = content,
        )
    }
}

@Composable
internal fun EtaCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(16.dp),
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.Sink,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalSquircleEnabled provides false) {
        val (cardModifier, cardColors) = etaGlassCard(modifier, colors)
        Card(
            modifier = cardModifier,
            cornerRadius = EtaCardDefaults.CornerRadius,
            insideMargin = insideMargin,
            colors = cardColors,
            pressFeedbackType = pressFeedbackType,
            showIndication = showIndication,
            holdDownState = holdDownState,
            onClick = onClick,
            onLongPress = onLongPress,
            content = content,
        )
    }
}

/**
 * 默认配色的卡片改为玻璃卡片：半透明白（深色为半透明灰）+ 高光边 + 顶部反光，透出页面的环境光；
 * 调用方自定义了卡片颜色（如提醒类卡片）时保持原样。
 */
@Composable
private fun etaGlassCard(modifier: Modifier, colors: CardColors): Pair<Modifier, CardColors> {
    val default = CardDefaults.defaultColors()
    if (colors.color != default.color) return modifier.etaElevation(EtaElevation.E1, EtaTheme.shapes.radiusLg) to colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(EtaCardDefaults.CornerRadius)
    return modifier.etaGlass(shape, tint = EtaTheme.colors.surfaceRaised, elevation = EtaElevation.E1,
        useLocalBackdrop = false, solidAlpha = 0.72f) to colors.copy(color = androidx.compose.ui.graphics.Color.Transparent)
}
