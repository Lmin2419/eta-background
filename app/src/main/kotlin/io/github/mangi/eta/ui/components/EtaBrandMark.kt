package io.github.mangi.eta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R

/** 静态的胖小胖爪印（`ic_eta_mark`），保持原色，不要用 Icon 着色。会动的版本见 EtaPaw.kt。 */
@Composable
internal fun EtaBrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_eta_mark), stringResource(R.string.app_name), modifier)
}
