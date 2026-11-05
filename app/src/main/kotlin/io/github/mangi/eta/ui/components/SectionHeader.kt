package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    EtaPreferenceGroupTitle(text, modifier)
}
