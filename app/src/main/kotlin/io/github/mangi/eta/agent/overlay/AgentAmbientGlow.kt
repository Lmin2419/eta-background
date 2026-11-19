package io.github.mangi.eta.agent.overlay

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import io.github.mangi.eta.data.model.AppearanceGlowIntensity
import io.github.mangi.eta.data.model.AppearanceGlowMark
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

/** 手机任务的流光窗口正亮着时，电脑任务的流光让开，免得同一处画两个光圈。 */
internal object AmbientGlowRegistry {
    val phoneGlowShowing = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/** 运行光效画在哪里（2026111618「智能」流光）。 */
internal enum class GlowPresence { EDGE, CAMERA, TOP_BAR }

/**
 * 「智能」流光的规则：
 * - AI 正在操作你眼前的屏幕（拿着屏幕操作权、没暂停）→ 屏幕四周一圈细光，提醒你别和它抢；
 * - 只在后台干活（后台屏幕、终端、查网页、思考）→ 只在摄像头开孔外圈亮一个小光圈；
 * - 「整圈」样式一直是四周细光。
 * 后台屏幕的操作不拿屏幕操作权（TaskScreenSession），所以操作权就是「是否在动你的屏幕」。
 */
internal object AmbientGlowPolicy {
    fun presence(style: AppearanceGlowStyle, mainScreenOwned: Boolean, mark: AppearanceGlowMark = AppearanceGlowMark.CAMERA): GlowPresence =
        when {
            style == AppearanceGlowStyle.FULL || mainScreenOwned -> GlowPresence.EDGE
            mark == AppearanceGlowMark.TOP_BAR -> GlowPresence.TOP_BAR
            else -> GlowPresence.CAMERA
        }

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

/**
 * 顶部光条的几何（2026111624）。光条不是一条独立的直线，而是「整圈流光」那条屏幕边缘路径（已按屏幕圆角画）
 * 的上沿一段：中间贴着顶边，两端顺着左上、右上角的圆弧往下走大约半个圆弧（约 45°）。
 * 亮度沿横向有一个包络：在圆弧里从 0 渐亮，过了圆角再走 [FADE_PAST_CORNER_DP] 才到全亮。
 * 这样既不会画成一条被圆角切掉的直线，也不会在角上留下生硬的端点；没有圆角的屏幕就在两端各淡出一小段。
 */
internal object TopBarGeometry {
    /** 从顶边正中往两侧各取多长的路径：直边的一半 + 圆弧的大约一半。 */
    fun halfSpan(width: Float, cornerRadius: Float): Float = width / 2f - cornerRadius * (1f - ARC_SHARE)

    /** 光条横向亮度包络：两端为 0、过了圆角后为 1，中间平滑过渡。 */
    fun envelope(x: Float, width: Float, cornerRadius: Float, density: Float): Float {
        val fromEdge = minOf(x, width - x)
        val start = cornerRadius * (1f - kotlin.math.sin(ARC_SHARE * Math.PI.toFloat() / 2f)) // 圆弧上光条端点离屏幕边的距离
        val full = cornerRadius + FADE_PAST_CORNER_DP * density
        if (fromEdge <= start) return 0f
        if (fromEdge >= full) return 1f
        val t = (fromEdge - start) / (full - start)
        return t * t * (3f - 2f * t)
    }

    /** 圆弧里取多少（0~1，0.5 约等于走到 45°）。 */
    const val ARC_SHARE = 0.5f
    const val FADE_PAST_CORNER_DP = 28f
    /** 亮光从正中流到两端一次的时长。 */
    const val FLOW_MS = 2_400
}

/**
 * 顶部光条：底光是一道两端淡出、中间偏暖的细光；上面两团亮光从顶边正中（摄像头、流体云那里）
 * 同时向左右流出去，流到圆角处淡掉，循环往复。[phase] 0→1 是一次流出。
 */
internal fun android.graphics.Canvas.drawTopBar(
    edge: android.graphics.Path,
    width: Float,
    cornerRadius: Float,
    colors: IntArray,
    phase: Float,
    intensity: AppearanceGlowIntensity,
    density: Float,
    alpha: Float,
    pausedColor: Int? = null,
) {
    if (alpha <= 0f || width <= 0f) return
    val measure = PathMeasure(edge, true)
    val length = measure.length
    if (length <= 0f) return
    val center = topCenterDistance(measure, width)
    val half = TopBarGeometry.halfSpan(width, cornerRadius)
    val bar = segmentOf(measure, length, center - half, center + half)
    fun env(x: Float) = TopBarGeometry.envelope(x, width, cornerRadius, density)
    fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    // 底光：沿横向按包络淡出，两侧偏蜂蜜色、中间偏玫瑰色（和整圈流光同一组颜色）。
    val stops = 24
    val positions = FloatArray(stops + 1) { it / stops.toFloat() }
    val sideColor = pausedColor ?: colors.first()
    val midColor = pausedColor ?: colors[colors.size / 2]
    val baseColors = IntArray(stops + 1) { i ->
        val x = positions[i] * width
        val towardCenter = 1f - kotlin.math.abs(positions[i] - 0.5f) * 2f
        withAlpha(blend(sideColor, midColor, towardCenter), env(x))
    }
    val base = LinearGradient(0f, 0f, width, 0f, baseColors, positions, Shader.TileMode.CLAMP)
    // 运行时底光压暗一些，流动的亮光才看得出来；暂停时只有底光，用原亮度。
    val (halo, line) = strokePaints(base, 0, intensity, density, alpha * (if (pausedColor == null) BASE_DIM else 1f), haloScale = 0.8f)
    line.strokeWidth = maxOf(line.strokeWidth, BAR_MIN_WIDTH_DP * density)
    drawPath(bar, halo)
    drawPath(bar, line)
    if (pausedColor != null) return

    // 流动的亮光：两团从正中往两侧走，刚冒出来和走到圆角时都是暗的。
    val spot = width * 0.16f
    val travel = width / 2f
    val emerge = (phase / 0.15f).coerceIn(0f, 1f)
    // 亮光用饱和一点的桃红色：浅色、深色背景上都看得出在流（掺白在浅色页面上会看不见）。
    val bright = blend(colors[1.coerceAtMost(colors.lastIndex)], colors[(colors.size / 2)], 0.5f)
    val spotStops = 64
    val spotPositions = FloatArray(spotStops + 1) { it / spotStops.toFloat() }
    val spotColors = IntArray(spotStops + 1) { i ->
        val x = spotPositions[i] * width
        val left = width / 2f - phase * travel
        val right = width / 2f + phase * travel
        val d = minOf(kotlin.math.abs(x - left), kotlin.math.abs(x - right))
        val falloff = (1f - d / spot).coerceIn(0f, 1f)
        withAlpha(bright, falloff * falloff * (3f - 2f * falloff) * env(x) * emerge)
    }
    val flow = LinearGradient(0f, 0f, width, 0f, spotColors, spotPositions, Shader.TileMode.CLAMP)
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = intensity.haloWidthDp * 0.7f * density
        maskFilter = BlurMaskFilter(intensity.haloWidthDp * 0.6f * density, BlurMaskFilter.Blur.NORMAL)
        shader = flow
        this.alpha = (minOf(1f, intensity.haloAlpha * 2f) * alpha * 255).toInt().coerceIn(0, 255)
    }
    val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = line.strokeWidth * 1.3f
        shader = flow
        this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
    }
    drawPath(bar, glow)
    drawPath(bar, glint)
}

private fun blend(from: Int, to: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    fun ch(shift: Int) = (((from shr shift) and 0xFF) * (1f - k) + ((to shr shift) and 0xFF) * k).toInt() shl shift
    return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
}

private const val BAR_MIN_WIDTH_DP = 2.5f
private const val BASE_DIM = 0.55f

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
