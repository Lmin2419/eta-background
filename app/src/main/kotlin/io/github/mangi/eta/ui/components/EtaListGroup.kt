package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults

/** A section for short lists. Long lists continue to use lazy EtaPreferenceGroupItem rows. */
@Composable
internal fun EtaListGroup(
    modifier: Modifier = Modifier.padding(horizontal = EtaPreferenceDefaults.SidePadding).padding(bottom = EtaPreferenceDefaults.GroupSpacing),
    title: String? = null,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        title?.let { EtaPreferenceGroupTitle(it) }
        EtaCard(modifier, insideMargin, colors, content)
    }
}
