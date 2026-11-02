package io.github.mangi.eta.agent.runtime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.widget.ImageView
import io.github.mangi.eta.R

/** Local feedback only: never paint on the captured bitmap or send it back to VDrive. */
internal class TaskPreviewImageView(context: Context) : ImageView(context) {
    private val phone = context.getDrawable(R.drawable.ic_task_phone)!!
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val points = ArrayDeque<Pair<Float, Float>>()
    private var touching = false
    private var endedAt = 0L
    private val density = resources.displayMetrics.density

    init { scaleType = ScaleType.FIT_CENTER }
    override fun performClick(): Boolean = super.performClick()

    /** Use the ImageView's real drawable transform, including density, rounding and padding. */
    fun displayPoint(x: Float, y: Float, displayWidth: Int, displayHeight: Int): Pair<Float, Float>? {
        val image = drawable ?: return null
        if (displayWidth <= 0 || displayHeight <= 0 || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return null
        val inverse = Matrix()
        if (!imageMatrix.invert(inverse)) return null
        val xy = floatArrayOf(x - paddingLeft, y - paddingTop)
        inverse.mapPoints(xy)
        val dx = xy[0] / image.intrinsicWidth * displayWidth
        val dy = xy[1] / image.intrinsicHeight * displayHeight
        return if (dx.isFinite() && dy.isFinite() && dx >= 0 && dy >= 0 && dx < displayWidth && dy < displayHeight) dx to dy else null
    }

    fun touchStart(x: Float, y: Float) {
        points.clear(); points.addLast(x to y); touching = true; endedAt = 0; invalidate()
    }
    fun touchMove(x: Float, y: Float) {
        if (!touching) return
        if (points.size >= 64) points.removeFirst()
        points.addLast(x to y); invalidate()
    }
    fun touchEnd() {
        touching = false; endedAt = SystemClock.uptimeMillis()
        if (!ValueAnimator.areAnimatorsEnabled()) clearTouch() else postInvalidateOnAnimation()
    }
    fun clearTouch() { touching = false; points.clear(); endedAt = 0; invalidate() }
    internal val feedbackPointCount: Int get() = points.size

    override fun onDetachedFromWindow() { clearTouch(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawable == null) {
            val h = minOf(112f * density, height * .55f).toInt()
            val w = h * 2 / 3
            phone.setBounds((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            phone.draw(canvas)
        }
        val last = points.lastOrNull() ?: return
        val progress = if (touching) 0f else ((SystemClock.uptimeMillis() - endedAt) / 360f).coerceIn(0f, 1f)
        if (progress >= 1) { points.clear(); return }
        val fade = 1 - progress
        paint.color = Color.rgb(142, 165, 255)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f * density; paint.alpha = (160 * fade).toInt()
        path.reset()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second) }
        canvas.drawPath(path, paint)
        val radius = (if (touching) 12f else 12f + 14f * progress) * density
        paint.style = Paint.Style.FILL; paint.alpha = (50 * fade).toInt()
        canvas.drawCircle(last.first, last.second, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.6f * density; paint.alpha = (225 * fade).toInt()
        canvas.drawCircle(last.first, last.second, radius, paint)
        paint.style = Paint.Style.FILL; paint.alpha = (245 * fade).toInt()
        canvas.drawCircle(last.first, last.second, 3.5f * density, paint)
        if (!touching) postInvalidateOnAnimation()
    }
}

internal object PreviewFrameContent {
    /** A featureless black/white capture is not a usable input observation. */
    fun usable(bitmap: Bitmap): Boolean {
        if (bitmap.width < 2 || bitmap.height < 2 || bitmap.isRecycled) return false
        var allDark = true
        var allLight = true
        for (y in 0 until 24) for (x in 0 until 16) {
            val c = bitmap.getPixel((x * (bitmap.width - 1) / 15), (y * (bitmap.height - 1) / 23))
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            allDark = allDark && maxOf(r, g, b) <= 18
            allLight = allLight && minOf(r, g, b) >= 238
            if (!allDark && !allLight) return true
        }
        return false
    }
}
