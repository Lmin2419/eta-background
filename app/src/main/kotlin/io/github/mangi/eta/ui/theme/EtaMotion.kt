package io.github.mangi.eta.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Immutable
data class EtaMotion(val reduceMotion: Boolean = false) {
    val micro: Int get() = duration(100)
    val short: Int get() = duration(180)
    val medium: Int get() = duration(260)
    val long: Int get() = duration(380)
    fun duration(millis: Int): Int = if (reduceMotion) millis.coerceAtMost(120) else millis
    fun exitDuration(enterMillis: Int): Int = duration((enterMillis * 0.7f).toInt())
    val standard get() = Standard
    val emphasizedDecelerate get() = EmphasizedDecelerate
    val accelerate get() = Accelerate
    val dampingRatio: Float get() = 0.86f
    val stiffness: Float get() = 420f
    /** 跟手：位置、尺寸。 */
    fun <T> follow(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(dampingRatio = FollowDamping, stiffness = FollowStiffness)
    /** 弹出：按钮、对勾、爪印这类小东西冒出来。 */
    fun <T> pop(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(dampingRatio = PopDamping, stiffness = PopStiffness)
    /** 页面切换（唯一超过 400ms 的动画）。 */
    val page: Int get() = duration(480)
    val ringRotationMillis: Int get() = 1_100
    val auroraMillis: Int get() = 2_400

    companion object {
        val Default = EtaMotion()
        const val FollowDamping = 0.86f
        const val FollowStiffness = 420f
        const val PopDamping = 0.62f
        const val PopStiffness = 520f
        private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        private val Accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    }
}

/** Android's accessibility "remove animations" setting also writes this duration scale. */
@Composable
internal fun rememberEtaMotion(): EtaMotion {
    val resolver = LocalContext.current.contentResolver
    fun reduced() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduceMotion by remember(resolver) { mutableStateOf(reduced()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduceMotion = reduced() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduceMotion = reduced()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return remember(reduceMotion) { EtaMotion(reduceMotion) }
}
