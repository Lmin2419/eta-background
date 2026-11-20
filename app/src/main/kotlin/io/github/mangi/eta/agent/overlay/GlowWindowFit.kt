package io.github.mangi.eta.agent.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.WindowManager

/**
 * 光效窗口跟着横竖屏走（2026111625）。
 *
 * 光效窗口的高度是创建那一刻的真实屏幕高度（MATCH_PARENT 在部分机型上不含系统栏）。在横屏时创建、再转回竖屏，
 * 窗口还是横屏那么高，光效的下沿就卡在屏幕中间；摄像头开孔的位置也停在横屏时的地方。
 * 这里监听主屏旋转，转了就按新的屏幕大小重新铺满，并让调用方重新取开孔位置。
 */
internal class DisplayRotationWatcher(context: Context, private val onRotated: () -> Unit) : DisplayManager.DisplayListener {
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var rotation = currentRotation()
    private var started = false

    fun start() {
        if (started) return
        started = true
        rotation = currentRotation()
        runCatching { displays?.registerDisplayListener(this, handler) }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { displays?.unregisterDisplayListener(this) }
    }

    override fun onDisplayChanged(displayId: Int) {
        if (displayId != Display.DEFAULT_DISPLAY) return
        val now = currentRotation()
        if (now == rotation) return
        rotation = now
        onRotated()
        // 有的机型旋转时先报旋转、稍后才更新屏幕尺寸：过一会儿再铺一次。
        handler.postDelayed({ if (started) onRotated() }, SETTLE_MS)
    }

    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit

    private fun currentRotation(): Int = runCatching { displays?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation }.getOrNull() ?: 0

    companion object {
        private const val SETTLE_MS = 350L

        /** 主屏真实高度（含状态栏、导航栏）。 */
        @Suppress("DEPRECATION")
        fun realHeight(windowManager: WindowManager): Int = runCatching {
            val point = android.graphics.Point()
            windowManager.defaultDisplay.getRealSize(point)
            point.y
        }.getOrNull()?.takeIf { it > 0 } ?: WindowManager.LayoutParams.MATCH_PARENT

        /** 把一个全屏光效窗口按现在的屏幕大小重新铺满；[params] 是它当初加窗口用的参数。 */
        fun refit(windowManager: WindowManager, view: View?, params: WindowManager.LayoutParams?) {
            if (view == null || params == null || !view.isAttachedToWindow) return
            // 只保持屏幕常亮、不画光效的 1×1 窗口不用动。
            if (params.width == 1 && params.height == 1) return
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = realHeight(windowManager)
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }
}
