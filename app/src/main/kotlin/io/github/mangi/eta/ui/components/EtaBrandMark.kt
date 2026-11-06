package io.github.mangi.eta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R

/** The protected brand vector owns its gradient; never recolor it through Icon tint. */
@Composable
internal fun EtaBrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_eta_mark), stringResource(R.string.app_name), modifier)
}
