package io.github.mangi.eta.ui.components

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 触感反馈：发送、开关、长按菜单弹出、任务完成时轻轻振动一下。设置里可关（默认开），
 * 系统关闭了触感时由 View 自己忽略。
 */
internal object EtaHaptics {
    private const val PREFS = "eta_ui_prefs"
    private const val KEY = "haptics_enabled"
    private val state = MutableStateFlow(true)
    @Volatile private var loaded = false
    val enabled: StateFlow<Boolean> get() = state

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        state.value = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        state.value = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    /** 轻点：发送、开关。 */
    fun tick(view: View) = perform(view, HapticFeedbackConstants.CLOCK_TICK)
    /** 长按菜单弹出。 */
    fun longPress(view: View) = perform(view, HapticFeedbackConstants.LONG_PRESS)
    /** 完成：任务结束、复制成功。 */
    fun confirm(view: View) = perform(view, HapticFeedbackConstants.CONFIRM)

    private fun perform(view: View, type: Int) {
        if (state.value) view.performHapticFeedback(type)
    }
}

/** 当前 View 上的触感入口；首次使用时读取设置。 */
@Composable
internal fun rememberEtaHaptics(): EtaHapticsHandle {
    val view = LocalView.current
    return remember(view) { EtaHaptics.init(view.context); EtaHapticsHandle(view) }
}

internal class EtaHapticsHandle(private val view: View) {
    fun tick() = EtaHaptics.tick(view)
    fun longPress() = EtaHaptics.longPress(view)
    fun confirm() = EtaHaptics.confirm(view)
}

/**
 * 按压回弹：按下时很快缩到 [pressedScale]，松手后带一点弹性回到原大小。
 * 只改绘制层，不影响布局与点击区域；减少动画时不缩放。
 */
internal fun Modifier.etaPressScale(interactionSource: InteractionSource, enabled: Boolean = true, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = EtaTheme.motion
    val down = pressed && enabled && !motion.reduceMotion
    val scale by animateFloatAsState(
        if (down) pressedScale else 1f,
        if (down) tween(motion.micro, easing = motion.standard) else spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * 不需要 InteractionSource 的按压回弹：自己观察触点（不消费事件），按住超过一小会儿才缩，
 * 列表滑动时触点被滚动消费，不会误缩。用于本身已有 combinedClickable 的列表行。
 */
internal fun Modifier.etaTouchScale(pressedScale: Float = 0.98f): Modifier = composed {
    val motion = EtaTheme.motion
    var pressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val down = pressed && !motion.reduceMotion
    val scale by animateFloatAsState(
        if (down) pressedScale else 1f,
        if (down) tween(motion.micro, easing = motion.standard) else spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "touchScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }.pointerInput(Unit) {
        kotlinx.coroutines.coroutineScope {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val job = launch { kotlinx.coroutines.delay(80); pressed = true }
                waitForUpOrCancellation()
                job.cancel()
                pressed = false
            }
        }
    }
}
