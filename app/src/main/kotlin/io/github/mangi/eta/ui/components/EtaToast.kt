package io.github.mangi.eta.ui.components

import androidx.compose.animation.*
import io.github.mangi.eta.ui.components.EtaAnimatedVisibility as AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.etaElevation
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButtonColors

/** Caller places this above the input area; this does not own application notifications. */
@Composable
internal fun EtaToast(
    message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null,
    durationMillis: Long = 3000,
) {
    val latestDismiss by rememberUpdatedState(onDismiss)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message, actionLabel, accessibility) {
        if (message != null) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(durationMillis, containsIcons = true, containsText = true,
                containsControls = actionLabel != null && onAction != null) ?: durationMillis)
            latestDismiss()
        }
    }
    val colors = EtaTheme.colors
    val foreground = colors.toastForeground
    var displayed by remember { mutableStateOf(message.orEmpty()) }
    SideEffect { if (message != null) displayed = message }
    // 黑色胶囊提示条：左边一枚小爪印，从下方带一点弹性浮上来，淡出时往下沉。
    val motion = EtaTheme.motion
    AnimatedVisibility(message != null, modifier.padding(bottom = 12.dp),
        enter = fadeIn(tween(motion.short)) + slideInVertically(
            if (motion.reduceMotion) tween(motion.short) else androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f)) { it } +
            scaleIn(tween(motion.short), initialScale = 0.94f),
        exit = fadeOut(tween(motion.short)) + slideOutVertically(tween(motion.short)) { it / 2 }) {
        Row(Modifier.heightIn(min = 44.dp)
            .etaGlass(EtaTheme.shapes.radiusFull, tint = colors.toastBackground, elevation = io.github.mangi.eta.ui.theme.EtaElevation.E2, useLocalBackdrop = false)
            .padding(start = 14.dp, end = 16.dp, top = if (actionLabel != null && onAction != null) 6.dp else 10.dp,
                bottom = if (actionLabel != null && onAction != null) 6.dp else 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(io.github.mangi.eta.R.drawable.ic_notification), null,
                Modifier.size(16.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(foreground.copy(alpha = 0.9f)))
            Text(displayed, Modifier.weight(1f, fill = false), color = foreground, style = EtaTheme.type.subhead)
            if (actionLabel != null && onAction != null) EtaButton(actionLabel, onAction,
                kind = EtaButtonKind.Ghost, size = EtaButtonSize.Small,
                colors = TextButtonColors(Color.Transparent, Color.Transparent, colors.toastAction, colors.toastAction))
        }
    }
}

/**
 * App 内的全局提示：界面在前台时，以黑色胶囊提示条显示在底部；App 不在前台时退回系统 Toast。
 * 调用方式与 Toast.makeText(...).show() 相同（文字或字符串资源 + 时长）。
 */
internal object EtaToastCenter {
    data class Item(val id: Long, val text: String, val long: Boolean)
    private val state = kotlinx.coroutines.flow.MutableStateFlow<Item?>(null)
    val current: kotlinx.coroutines.flow.StateFlow<Item?> get() = state
    private val visibleHosts = java.util.concurrent.atomic.AtomicInteger(0)
    private val ids = java.util.concurrent.atomic.AtomicLong(0)

    fun show(context: android.content.Context, text: CharSequence, duration: Int = android.widget.Toast.LENGTH_SHORT) {
        if (visibleHosts.get() > 0) state.value = Item(ids.incrementAndGet(), text.toString(), duration == android.widget.Toast.LENGTH_LONG)
        else android.widget.Toast.makeText(context, text, duration).show()
    }

    fun show(context: android.content.Context, @androidx.annotation.StringRes res: Int, duration: Int = android.widget.Toast.LENGTH_SHORT) =
        show(context, context.getString(res), duration)

    internal fun dismiss(id: Long) { state.compareAndSet(state.value?.takeIf { it.id == id }, null) }
    internal fun hostVisible(visible: Boolean) { if (visible) visibleHosts.incrementAndGet() else visibleHosts.decrementAndGet() }
}

/** 放在 App 最外层：界面可见（started）期间接管全局提示。 */
@Composable
internal fun EtaToastHost(modifier: Modifier = Modifier) {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var counted = false
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            val started = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            if (started != counted) { counted = started; EtaToastCenter.hostVisible(started) }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); if (counted) EtaToastCenter.hostVisible(false) }
    }
    val item by EtaToastCenter.current.collectAsState()
    EtaToast(item?.text, { item?.let { EtaToastCenter.dismiss(it.id) } }, modifier,
        durationMillis = if (item?.long == true) 3500 else 2200)
}
