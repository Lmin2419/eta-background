package io.github.mangi.eta.agent.overlay

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import io.github.mangi.eta.data.model.AppearanceGlowIntensity
import io.github.mangi.eta.data.model.AppearanceGlowStyle

/** 用户对小光圈的微调（dp）；宽高为 0 表示按开孔自动算。 */
internal data class CameraRingAdjust(
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val widthDp: Float = 0f,
    val heightDp: Float = 0f,
) {
    companion object {
        val AUTO = CameraRingAdjust()
        const val OFFSET_X_RANGE = 60f
        const val OFFSET_Y_RANGE = 40f
        const val MIN_SIZE = 8f
        const val MAX_WIDTH = 260f
        const val MAX_HEIGHT = 90f
    }
}

internal fun io.github.mangi.eta.data.model.AppearanceSettings.cameraRingAdjust() =
    CameraRingAdjust(cameraRingOffsetXDp, cameraRingOffsetYDp, cameraRingWidthDp, cameraRingHeightDp)

/** 摄像头开孔在屏幕上的位置（屏幕坐标）。只取靠上的那个开孔；没有开孔返回 null。 */
internal object CameraCutoutLocator {
    fun bounds(context: android.content.Context): RectF? {
        val display = (context.getSystemService(android.content.Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY) ?: return null
        val cutout = runCatching { display.cutout }.getOrNull() ?: return null
        val path = runCatching { cutout.cutoutPath }.getOrNull()
        val bounds = RectF()
        if (path != null && !path.isEmpty) {
            path.computeBounds(bounds, true)
        } else {
            val rect = cutout.boundingRectTop.takeUnless { it.isEmpty }
                ?: cutout.boundingRects.minByOrNull { it.top } ?: return null
            bounds.set(rect)
        }
        return bounds.takeUnless { it.isEmpty }
    }
}

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
     * 小光圈的位置（窗口坐标与屏幕坐标一致）。
     * - 有开孔：以开孔中心为圆心。开孔一般是圆的，但不少机型（如 ColorOS）报给应用的范围比孔高出一大截，
     *   所以竖长的范围按宽度当直径画成圆；横长的（双摄胶囊孔）照原样套住。外圈留 [RING_GAP_DP] 空隙。
     * - 没有开孔（屏下摄像头等）：顶边正中一枚小胶囊。
     * 用户在「摄像头光圈位置」里调过的偏移和宽高（[adjust]）最后叠上去；宽高为 0 表示自动。
     */
    fun cameraRing(cutout: RectF?, screenWidth: Float, density: Float, adjust: CameraRingAdjust = CameraRingAdjust.AUTO): RectF {
        val gap = RING_GAP_DP * density
        val centerX: Float
        val centerY: Float
        val autoWidth: Float
        val autoHeight: Float
        if (cutout != null && !cutout.isEmpty) {
            centerX = cutout.centerX()
            centerY = cutout.centerY()
            val tall = cutout.height() > cutout.width()
            autoWidth = cutout.width() + gap * 2
            autoHeight = (if (tall) cutout.width() else cutout.height()) + gap * 2
        } else {
            centerX = screenWidth / 2f
            centerY = (FALLBACK_RING_TOP_DP + FALLBACK_RING_HEIGHT_DP / 2f) * density
            autoWidth = FALLBACK_RING_WIDTH_DP * density
            autoHeight = FALLBACK_RING_HEIGHT_DP * density
        }
        val width = if (adjust.widthDp > 0f) adjust.widthDp * density else autoWidth
        val height = if (adjust.heightDp > 0f) adjust.heightDp * density else autoHeight
        val x = centerX + adjust.offsetXDp * density
        val y = centerY + adjust.offsetYDp * density
        return RectF(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)
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
