package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.etaElevation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 下拉刷新：列表在顶部继续往下拉时，顶部出现一枚爪印，脚趾随手指一颗颗冒出来；
 * 拉满（四颗都出来）时轻振一下，松手即刷新，刷新中脚趾依次按下。
 * 接在列表的 nested scroll 上，不影响顶栏折叠和越界回弹。
 */
@Stable
internal class EtaPullRefreshState(
    private val scope: CoroutineScope,
    private val thresholdPx: Float,
    private val onRefresh: State<() -> Unit>,
    private val onThreshold: () -> Unit,
) {
    private var distance by androidx.compose.runtime.mutableFloatStateOf(0f)
    var refreshing by mutableStateOf(false)
        internal set
    /** 调用方报告的真实刷新状态；刷新很快结束（或没有触发加载）时，本地的「刷新中」最多停留不到一秒。 */
    internal var external = false
    private var armed = false

    /** 0..1+：拉开的程度，1 表示松手就会刷新。 */
    val progress: Float get() = distance / thresholdPx

    private fun drag(by: Float) {
        val next = max(0f, distance + by)
        distance = next
        val nowArmed = next >= thresholdPx
        if (nowArmed && !armed) onThreshold()
        armed = nowArmed
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y >= 0f || distance <= 0f) return Offset.Zero
            val take = max(available.y, -distance)
            drag(take)
            return Offset(0f, take)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f || refreshing) return Offset.Zero
            // 越拉越沉：手指移动的一半变成拉开距离，超过阈值后再减半。
            val resistance = if (distance > thresholdPx) 0.25f else 0.5f
            drag(available.y * resistance)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (distance <= 0f) return Velocity.Zero
            if (distance >= thresholdPx && !refreshing) {
                refreshing = true
                onRefresh.value()
                scope.launch { kotlinx.coroutines.delay(900); if (!external) refreshing = false }
            }
            armed = false
            androidx.compose.animation.core.animate(distance, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ -> distance = v }
            return available
        }
    }
}

@Composable
internal fun rememberEtaPullRefreshState(refreshing: Boolean, onRefresh: () -> Unit, threshold: Dp = 72.dp): EtaPullRefreshState {
    val scope = rememberCoroutineScope()
    val haptics = rememberEtaHaptics()
    val latest = rememberUpdatedState(onRefresh)
    val px = with(LocalDensity.current) { threshold.toPx() }
    val state = remember(px) { EtaPullRefreshState(scope, px, latest) { haptics.tick() } }
    LaunchedEffect(refreshing) { state.external = refreshing; state.refreshing = refreshing }
    return state
}

/** 顶部的爪印指示器：拉动时随进度出现，刷新中保持在原位按动脚趾。 */
@Composable
internal fun EtaPullRefreshIndicator(state: EtaPullRefreshState, modifier: Modifier = Modifier, topInset: Dp = 0.dp) {
    val visibleWhileRefreshing = state.refreshing
    val description = stringResource(R.string.pull_to_refresh)
    Box(
        modifier
            .padding(top = topInset)
            .graphicsLayer {
                val p = state.progress.coerceIn(0f, 1.4f)
                val shown = if (visibleWhileRefreshing) 1f else (p * 1.6f).coerceIn(0f, 1f)
                alpha = shown
                translationY = (if (visibleWhileRefreshing) 1f else p.coerceAtMost(1f)) * 20.dp.toPx() - 8.dp.toPx()
                val sc = 0.7f + 0.3f * shown
                scaleX = sc; scaleY = sc
            }
            .size(40.dp)
            .etaElevation(EtaElevation.E2, EtaTheme.shapes.radiusFull)
            .background(EtaTheme.colors.surfaceRaised, EtaTheme.shapes.radiusFull)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (visibleWhileRefreshing) {
            EtaThinkingMark(size = 24.dp)
        } else {
            val pose = remember(state) {
                PawPose(
                    appear = { i -> ((state.progress - 0.2f - i * 0.18f) / 0.18f).coerceIn(0f, 1f) },
                    padAppear = { (state.progress / 0.25f).coerceIn(0f, 1f) },
                )
            }
            EtaPaw(pose, Modifier.size(24.dp))
        }
    }
}
