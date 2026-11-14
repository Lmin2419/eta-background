package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.size
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

/** 与上游 COUI 设置页一致的尺寸（CouiPageDefaults）：组间 16、图标格 24 + 间距 16、行高 48、分隔线 0.33。 */
internal object EtaPreferenceDefaults {
    val SidePadding = com.itos.coui.layout.CouiPageDefaults.rowPadding
    val GroupSpacing = com.itos.coui.layout.CouiPageDefaults.groupSpacing
    val IconSize = com.itos.coui.layout.CouiPageDefaults.iconSize
    val IconTextGap = com.itos.coui.layout.CouiPageDefaults.iconSpacing
    val ContentStart = SidePadding + IconSize + IconTextGap
    fun contentStart(hasLeading: Boolean) = if (hasLeading) ContentStart else SidePadding
    val RowMinHeight = com.itos.coui.layout.CouiPageDefaults.rowMinHeight
    val RowVerticalPadding = com.itos.coui.layout.CouiPageDefaults.contentPadding
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
@Suppress("UNUSED_PARAMETER")
internal fun EtaPreferenceIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = EtaTheme.colors.tileSlate,
    enabled: Boolean = true,
) {
    // 设置行只画线条图标，不再画底块：列表更安静。图标格 24dp（与上游 COUI 一致）。
    androidx.compose.foundation.layout.Box(
        modifier.size(EtaPreferenceDefaults.IconSize).alpha(if (enabled) 1f else 0.38f),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        top.yukonga.miuix.kmp.basic.Icon(icon, null, Modifier.size(22.dp), tint = EtaTheme.colors.textSecondary)
    }
}

@Composable
internal fun EtaPreferenceDivider(hasLeading: Boolean = true, modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = EtaPreferenceDefaults.contentStart(hasLeading), end = EtaPreferenceDefaults.SidePadding),
        thickness = com.itos.coui.layout.CouiPageDefaults.dividerHeight,
        color = EtaTheme.colors.outline,
    )
}
