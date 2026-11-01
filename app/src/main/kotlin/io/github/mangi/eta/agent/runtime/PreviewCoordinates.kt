package io.github.mangi.eta.agent.runtime

/** Fit-center coordinates: black letterbox areas never become taps. */
internal object PreviewCoordinates {
    fun map(x: Float, y: Float, viewWidth: Int, viewHeight: Int, width: Int, height: Int): Pair<Float, Float>? {
        if (viewWidth <= 0 || viewHeight <= 0 || width <= 0 || height <= 0) return null
        val scale = minOf(viewWidth.toFloat() / width, viewHeight.toFloat() / height)
        val left = (viewWidth - width * scale) / 2
        val top = (viewHeight - height * scale) / 2
        val px = (x - left) / scale
        val py = (y - top) / scale
        return if (px.isFinite() && py.isFinite() && px >= 0 && py >= 0 && px < width && py < height) px to py else null
    }
}
