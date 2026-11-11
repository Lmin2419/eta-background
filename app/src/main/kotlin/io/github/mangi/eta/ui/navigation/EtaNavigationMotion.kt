package io.github.mangi.eta.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
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
                // 点击进入 / 返回：480ms、前段不过猛的减速曲线。原来 380ms + emphasized-decelerate
                // 在前 20% 时间里走完 70% 位移，进入二级页显得「啪」一下。
                programmatic = if (eta.reduceMotion) settle else NavSettleSpec.Tween(480, PageEasing))
            override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier =
                if (eta.reduceMotion) graphicsLayer {
                    // Keep the covered page opaque; only fade the entering/leaving top page.
                    alpha = if (scope.relativeDepth > 0f) 1f else (1f + scope.relativeDepth).coerceIn(0f, 1f)
                }
                else with(NavTransitions.MiuixDefault) { transformEntry(scope) }
        }
    }
}

/** 页面切换曲线：起步柔和、收尾很长，接近系统原生的整页推入手感。 */
private val PageEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
