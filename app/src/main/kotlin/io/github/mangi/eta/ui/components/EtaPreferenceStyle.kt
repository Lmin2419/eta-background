package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object EtaPreferenceColors {
    val Blue: Color @Composable get() = EtaTheme.colors.tileBlue
    val Green: Color @Composable get() = EtaTheme.colors.tileGreen
    val Orange: Color @Composable get() = EtaTheme.colors.tileOrange
    val Yellow: Color @Composable get() = EtaTheme.colors.tileOrange
    val Indigo: Color @Composable get() = EtaTheme.colors.tileCaramel
    val Violet: Color @Composable get() = EtaTheme.colors.tileViolet
    val Pink: Color @Composable get() = EtaTheme.colors.tilePink
    val Teal: Color @Composable get() = EtaTheme.colors.tileTeal
    val Slate: Color @Composable get() = EtaTheme.colors.tileSlate
}

internal object EtaPreferenceDefaults {
    val SidePadding = 16.dp
    val GroupSpacing = 24.dp
    val IconSize = 32.dp
    val IconTextGap = 12.dp
    val ContentStart = SidePadding + IconSize + IconTextGap
    fun contentStart(hasLeading: Boolean) = if (hasLeading) ContentStart else SidePadding
    val RowMinHeight = 56.dp
}

@Composable
internal fun EtaPreferenceTheme(content: @Composable () -> Unit) {
    // Scope typography to preferences; retain AgentAppTheme's palette and density.
    val type = EtaTheme.type
    val selection = androidx.compose.foundation.text.selection.LocalTextSelectionColors.current
    MiuixTheme(colors = MiuixTheme.colorScheme, textStyles = MiuixTheme.textStyles.copy(
        body1 = type.rowTitle, body2 = type.subhead, button = type.rowTitle,
        footnote1 = type.footnote, footnote2 = type.caption, subtitle = type.caption,
        title1 = type.title1, title2 = type.title2, title3 = type.topBarTitle, title4 = type.title2,
    )) {
        // 内层 MiuixTheme 会重设选区颜色，这里接回 App 统一的珊瑚橘。
        CompositionLocalProvider(LocalSquircleEnabled provides false,
            androidx.compose.foundation.text.selection.LocalTextSelectionColors provides selection, content = content)
    }
}

@Composable
internal fun EtaPreferenceGroup(
    modifier: Modifier = Modifier.padding(horizontal = EtaPreferenceDefaults.SidePadding).padding(bottom = EtaPreferenceDefaults.GroupSpacing),
    insideMargin: PaddingValues = PaddingValues(0.dp),
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    EtaListGroup(modifier = modifier, insideMargin = insideMargin, colors = colors, content = content)
}

@Composable
internal fun EtaPreferenceGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = EtaTheme.type.caption,
        color = EtaTheme.colors.textTertiary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
internal fun EtaPreferenceIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = EtaTheme.colors.tileSlate,
    enabled: Boolean = true,
) {
    EtaIconTile(icon = icon, modifier = modifier, tint = tint, enabled = enabled)
}

@Composable
internal fun EtaPreferenceDivider(hasLeading: Boolean = true, modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = EtaPreferenceDefaults.contentStart(hasLeading), end = EtaPreferenceDefaults.SidePadding),
        thickness = 0.5.dp,
        color = EtaTheme.colors.outline,
    )
}
