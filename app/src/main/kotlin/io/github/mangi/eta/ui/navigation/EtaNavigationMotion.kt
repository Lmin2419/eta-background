package io.github.mangi.eta.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.nav.transition.*

/** Preserve the navigation driver's gestures and lifecycle; change only its visual contract. */
@Composable
internal fun rememberEtaNavigationTransition(): NavTransition {
    val eta = EtaTheme.motion
    return remember(eta) {
        val settle = if (eta.reduceMotion) NavSettleSpec.Tween(eta.short, eta.standard)
            else NavSettleSpec.Spring(eta.dampingRatio, eta.stiffness, clampOvershoot = false)
        object : NavTransition {
            override val motion = NavMotion(commit = settle, cancel = settle,
                programmatic = NavSettleSpec.Tween(eta.long, eta.emphasizedDecelerate))
            override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier =
                if (eta.reduceMotion) graphicsLayer {
                    // Keep the covered page opaque; only fade the entering/leaving top page.
                    alpha = if (scope.relativeDepth > 0f) 1f else (1f + scope.relativeDepth).coerceIn(0f, 1f)
                }
                else with(NavTransitions.MiuixDefault) { transformEntry(scope) }
        }
    }
}
