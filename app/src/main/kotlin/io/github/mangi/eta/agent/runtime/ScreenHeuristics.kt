package io.github.mangi.eta.agent.runtime

import kotlin.math.abs

/**
 * 在新的节点树里重新找到「同一个元素」（11 号方案第二期）。
 * 同一个元素 = 类名、viewId、文字、描述都一样；位置允许挪一点（屏幕宽度的 5%，至少 48 像素）。
 */
internal object NodeRelocation {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
    }

    data class Identity(val className: String, val viewId: String, val text: String, val description: String, val box: Box?)

    sealed interface Result {
        data object Missing : Result
        data object Ambiguous : Result
        data class At(val index: Int) : Result
    }

    fun same(a: Identity, b: Identity): Boolean =
        a.className == b.className && a.viewId == b.viewId && a.text == b.text && a.description == b.description

    fun find(target: Identity, current: List<Identity>, screenWidth: Int): Result {
        val tolerance = maxOf(48, screenWidth / 20)
        val matches = current.withIndex().filter { same(target, it.value) }
        if (matches.isEmpty()) return Result.Missing
        val box = target.box ?: return matches.singleOrNull()?.let { Result.At(it.index) } ?: Result.Ambiguous
        val near = matches.mapNotNull { (index, node) ->
            node.box?.let { index to (abs(it.centerX - box.centerX) + abs(it.centerY - box.centerY)) }
        }.filter { it.second <= tolerance }.sortedBy { it.second }
        if (near.isEmpty()) return Result.Missing
        // 两个一样的元素都在附近、离得差不多近：分不清是哪一个，宁可不点。
        if (near.size > 1 && near[1].second - near[0].second < tolerance / 2) return Result.Ambiguous
        return Result.At(near[0].first)
    }
}

/**
 * 程序自己判断要不要截图（用户要求：不再让模型去判断树还是截图）。
 * 树里有文字或描述的元素少于 2 个，或者一大块画布类视图（SurfaceView / TextureView / WebView 等）占了屏幕四成以上、
 * 而可读元素不到 6 个，就认为树读不出内容，需要截图。
 */
internal object ScreenSparseness {
    data class View(val className: String, val readable: Boolean, val area: Long)

    private val canvasClasses = listOf("SurfaceView", "TextureView", "GLSurfaceView", "WebView", "FlutterView")

    fun needsScreenshot(views: List<View>, screenWidth: Int, screenHeight: Int): Boolean {
        val readable = views.count { it.readable }
        if (readable < 2) return true
        val screenArea = screenWidth.toLong() * screenHeight.toLong()
        if (screenArea <= 0) return false
        val canvas = views.any { view -> canvasClasses.any { view.className.endsWith(it) } && view.area * 10 >= screenArea * 4 }
        return canvas && readable < 6
    }
}
