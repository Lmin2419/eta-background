package io.github.mangi.eta.agent.overlay

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import io.github.mangi.eta.data.model.AppearanceGlowIntensity
import io.github.mangi.eta.data.model.AppearanceGlowStyle

/** 运行光效画在哪里（2026111618「智能」流光）。 */
internal enum class GlowPresence { EDGE, CAMERA }

/**
 * 「智能」流光的规则：
 * - AI 正在操作你眼前的屏幕（拿着屏幕操作权、没暂停）→ 屏幕四周一圈细光，提醒你别和它抢；
 * - 只在后台干活（后台屏幕、终端、查网页、思考）→ 只在摄像头开孔外圈亮一个小光圈；
 * - 「整圈」样式一直是四周细光。
 * 后台屏幕的操作不拿屏幕操作权（TaskScreenSession），所以操作权就是「是否在动你的屏幕」。
 */
internal object AmbientGlowPolicy {
    fun presence(style: AppearanceGlowStyle, mainScreenOwned: Boolean): GlowPresence =
        if (style == AppearanceGlowStyle.FULL || mainScreenOwned) GlowPresence.EDGE else GlowPresence.CAMERA

    /**
     * 小光圈的位置（窗口坐标与屏幕坐标一致）：有开孔时套在开孔外面，留 [gapPx] 的空隙；
     * 没有开孔（屏下摄像头等）时退到顶边正中，画成一枚小胶囊。
     */
    fun cameraRing(cutout: RectF?, screenWidth: Float, density: Float): RectF {
        val gap = RING_GAP_DP * density
        if (cutout != null && !cutout.isEmpty) {
            return RectF(cutout.left - gap, cutout.top - gap, cutout.right + gap, cutout.bottom + gap)
        }
        val halfWidth = FALLBACK_RING_WIDTH_DP / 2f * density
        val top = FALLBACK_RING_TOP_DP * density
        return RectF(screenWidth / 2f - halfWidth, top, screenWidth / 2f + halfWidth, top + FALLBACK_RING_HEIGHT_DP * density)
    }

    const val RING_GAP_DP = 3.5f
    const val FALLBACK_RING_WIDTH_DP = 64f
    const val FALLBACK_RING_HEIGHT_DP = 22f
    const val FALLBACK_RING_TOP_DP = 8f
    const val START_SWEEP_MS = 1100
    const val END_PULSE_MS = 900
}

internal fun edgeShader(colors: IntArray, width: Float, height: Float, rotation: Float, centerX: Float = width / 2f, centerY: Float = height / 2f) =
    SweepGradient(centerX, centerY, colors, null).apply {
        setLocalMatrix(Matrix().apply { setRotate(rotation, centerX, centerY) })
    }

/** 外层柔光 + 内层亮线，共用一个渐变，看起来是一体的光带。 */
internal fun strokePaints(
    shader: android.graphics.Shader?,
    color: Int,
    intensity: AppearanceGlowIntensity,
    density: Float,
    alpha: Float,
    haloScale: Float = 1f,
): Pair<Paint, Paint> {
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = intensity.haloWidthDp * haloScale * density
        maskFilter = BlurMaskFilter(intensity.haloWidthDp * haloScale * 0.85f * density, BlurMaskFilter.Blur.NORMAL)
        if (shader != null) this.shader = shader else this.color = color
        this.alpha = (intensity.haloAlpha * alpha * 255).toInt().coerceIn(0, 255)
    }
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = intensity.lineWidthDp * density
        strokeCap = Paint.Cap.ROUND
        if (shader != null) this.shader = shader else this.color = color
        this.alpha = (intensity.lineAlpha * alpha * 255).toInt().coerceIn(0, 255)
    }
    return halo to line
}

/** 开孔外圈的小光圈：圆孔画成圆环，胶囊孔画成胶囊环，刘海画成套住刘海的圆角框。 */
internal fun android.graphics.Canvas.drawCameraRing(
    ring: RectF,
    colors: IntArray,
    rotation: Float,
    intensity: AppearanceGlowIntensity,
    density: Float,
    alpha: Float,
    pausedColor: Int? = null,
) {
    if (alpha <= 0f) return
    val radius = minOf(ring.width(), ring.height()) / 2f
    val shader = if (pausedColor == null) edgeShader(colors, ring.width(), ring.height(), rotation, ring.centerX(), ring.centerY()) else null
    // 光圈面积很小，柔光略放宽、亮线比边框更实一点，才看得出是在「流」。
    val (halo, line) = strokePaints(shader, pausedColor ?: 0, intensity, density, alpha, haloScale = 0.6f)
    line.alpha = (minOf(1f, intensity.lineAlpha + 0.2f) * alpha * 255).toInt().coerceIn(0, 255)
    halo.alpha = (minOf(1f, intensity.haloAlpha * 1.5f) * alpha * 255).toInt().coerceIn(0, 255)
    drawRoundRect(ring, radius, radius, halo)
    drawRoundRect(ring, radius, radius, line)
}

/**
 * 开始提示：两道光从顶边正中（摄像头那里）分头沿屏幕边缘跑一圈，在底边会合后淡出。
 * [progress] 0→1。
 */
internal fun android.graphics.Canvas.drawStartSweep(
    edge: android.graphics.Path,
    width: Float,
    progress: Float,
    colors: IntArray,
    intensity: AppearanceGlowIntensity,
    density: Float,
) {
    if (progress <= 0f || progress >= 1f) return
    val measure = PathMeasure(edge, true)
    val length = measure.length
    if (length <= 0f) return
    val origin = topCenterDistance(measure, width)
    val head = progress * length / 2f
    val tail = minOf(head, length * 0.12f)
    val fade = (1f - progress).coerceIn(0f, 1f).let { kotlin.math.sqrt(it) }
    val shader = edgeShader(colors, width, width, 0f)
    val (halo, line) = strokePaints(shader, 0, intensity, density, fade)
    line.strokeWidth = (intensity.lineWidthDp + 0.5f) * density
    for (direction in intArrayOf(1, -1)) {
        val end = origin + direction * head
        val start = end - direction * tail
        val segment = segmentOf(measure, length, minOf(start, end), maxOf(start, end))
        drawPath(segment, halo)
        drawPath(segment, line)
    }
}

/** 结束提示：屏幕四周的细光亮起再暗下去一次（像呼吸一下）。[progress] 0→1。 */
internal fun android.graphics.Canvas.drawEndPulse(
    edge: android.graphics.Path,
    width: Float,
    height: Float,
    progress: Float,
    colors: IntArray,
    intensity: AppearanceGlowIntensity,
    density: Float,
) {
    if (progress <= 0f || progress >= 1f) return
    val alpha = kotlin.math.sin(progress * Math.PI).toFloat()
    val (halo, line) = strokePaints(edgeShader(colors, width, height, progress * 90f), 0, intensity, density, alpha)
    drawPath(edge, halo)
    drawPath(edge, line)
}

/** 路径上离顶边中点最近的位置（路径起点因机型而异，这里采样找）。 */
private fun topCenterDistance(measure: PathMeasure, width: Float): Float {
    val length = measure.length
    val pos = FloatArray(2)
    var best = 0f
    var bestScore = Float.MAX_VALUE
    val steps = 240
    for (i in 0 until steps) {
        val d = length * i / steps
        measure.getPosTan(d, pos, null)
        val score = kotlin.math.abs(pos[0] - width / 2f) + pos[1] * 4f
        if (score < bestScore) { bestScore = score; best = d }
    }
    return best
}

/** 取闭合路径上 [from, to] 一段，跨过起点时分两段拼起来。 */
private fun segmentOf(measure: PathMeasure, length: Float, from: Float, to: Float): android.graphics.Path {
    val out = android.graphics.Path()
    fun wrap(d: Float) = ((d % length) + length) % length
    val a = wrap(from)
    val b = wrap(to)
    if (to - from >= length) { measure.getSegment(0f, length, out, true); return out }
    if (a <= b) measure.getSegment(a, b, out, true)
    else { measure.getSegment(a, length, out, true); measure.getSegment(0f, b, out, true) }
    return out
}
