package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.theme.EtaTheme

/** Keep a readable conversation width when the permanent sidebar already occupies 320dp. */
@Composable
internal fun HomeTaskPaneLayout(panel: (@Composable () -> Unit)?, onDismiss: () -> Unit, backHandlerEnabled: Boolean = true,
    content: @Composable () -> Unit) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = backHandlerEnabled && panel != null && lifecycle == Lifecycle.State.RESUMED, onBackCompleted = onDismiss)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (panel == null) content()
        else if (maxWidth >= 720.dp) Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            Box(Modifier.width(360.dp)) { panel() }
        } else {
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}) { content() }
            Box(Modifier.fillMaxSize().background(EtaTheme.colors.scrim)
                .clickable(onClickLabel = stringResource(R.string.action_close), onClick = onDismiss))
            Box(Modifier.align(Alignment.CenterEnd).width(360.dp)) { panel() }
        }
    }
}
