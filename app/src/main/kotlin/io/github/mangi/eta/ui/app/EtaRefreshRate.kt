package io.github.mangi.eta.ui.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import io.github.mangi.eta.assistant.PowerKeyAdapter
import io.github.mangi.eta.assistant.PowerKeyAdapterSettings
import io.github.mangi.eta.data.model.AppearanceSettings

/**
 * 界面刷新率。小米 HyperOS 的动态刷新率默认把第三方应用压在低档，点开组件时才临时提到高档，
 * 切档的那一下过渡会顿（OPPO 上没有这个限制）。打开后胖小胖的窗口固定请求屏幕最高刷新率
 * （同分辨率下最高的显示模式），离开胖小胖就交还系统。
 */
internal object EtaRefreshRate {
    private val hyperOs by lazy { PowerKeyAdapterSettings.detected() == PowerKeyAdapter.HYPEROS }

    /** 用户没选过时：HyperOS 上默认打开，其他手机交给系统。 */
    fun enabled(appearance: AppearanceSettings): Boolean = appearance.highRefreshRate ?: hyperOs

    internal data class Mode(val id: Int, val width: Int, val height: Int, val hz: Float)

    /** 同一分辨率下刷新率最高的显示模式；只有一种刷新率时为 null（不必指定）。 */
    internal fun preferredModeId(current: Mode, modes: List<Mode>): Int? {
        val sameSize = modes.filter { it.width == current.width && it.height == current.height }
        val best = sameSize.maxByOrNull { it.hz } ?: return null
        return best.id.takeIf { sameSize.any { mode -> mode.hz < best.hz - 1f } }
    }

    private fun Display.Mode.info() = Mode(modeId, physicalWidth, physicalHeight, refreshRate)

    @Composable
    fun Apply(enabled: Boolean) {
        val view = LocalView.current
        DisposableEffect(view, enabled) {
            val activity = view.context.findActivity()
            val window = activity?.window
            val display = view.display ?: activity?.display
            val target = if (enabled && display != null) {
                runCatching { preferredModeId(display.mode.info(), display.supportedModes.map { it.info() }) }.getOrNull() ?: 0
            } else 0
            if (window != null) {
                val attributes = window.attributes
                if (attributes.preferredDisplayModeId != target) {
                    attributes.preferredDisplayModeId = target
                    window.attributes = attributes
                }
            }
            onDispose { }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
