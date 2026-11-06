package io.github.mangi.eta.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import io.github.mangi.eta.R
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Inflates every brand icon (launcher layers, themed layer, status-bar and task icons) and
 * draws them the way the system does: launcher masks, themed tint, and the single-colour
 * status-bar tint. The sheet is written to build/reports/eta-icons for review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandIconRenderingTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun brandIconsInflateAndStayVisibleUnderSystemTreatments() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val size = 432
        val sheet = Bitmap.createBitmap(size * 4 + 200, size * 2 + 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(228, 230, 236))

        // Launcher: circle and squircle masks.
        drawMasked(canvas, icon, 40f, 40f, size, circle = true)
        drawMasked(canvas, icon, 80f + size, 40f, size, circle = false)
        // Themed icon: monochrome layer tinted on a tonal plate.
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 219, 255) }
        canvas.drawCircle(120f + size * 2.5f, 40f + size / 2f, size / 2f, plate)
        icon.monochrome!!.apply {
            setBounds(120 + size * 2 - size / 4, 40 - size / 4, 120 + size * 3 + size / 4, 40 + size + size / 4)
            colorFilter = PorterDuffColorFilter(Color.rgb(50, 64, 217), PorterDuff.Mode.SRC_IN)
            draw(canvas)
        }
        // Brand mark on light canvas.
        context.getDrawable(R.drawable.ic_eta_mark)!!.apply {
            setBounds(160 + size * 3 + 48, 88, 160 + size * 4 - 48, 40 + size - 48); draw(canvas)
        }

        // Status bar: white tint on dark, dark tint on light, at real 24dp (72px) and enlarged.
        val bar = Paint().apply { color = Color.rgb(13, 14, 18) }
        val row = 80f + size
        canvas.drawRect(40f, row, 40f + size, row + size / 2f, bar)
        tinted(canvas, R.drawable.ic_notification, 70, row.toInt() + 30, 72, Color.WHITE)
        tinted(canvas, R.drawable.ic_notification, 180, row.toInt() + 20, 180, Color.WHITE)
        tinted(canvas, R.drawable.ic_notification, 80 + size, row.toInt() + 30, 72, Color.rgb(21, 22, 26))
        tinted(canvas, R.drawable.ic_notification, 190 + size, row.toInt() + 20, 180, Color.rgb(21, 22, 26))
        // Task large icon as drawn by the notification (96x144) and fully tinted by a template.
        context.getDrawable(R.drawable.ic_task_phone)!!.apply {
            setBounds(120 + size * 2, row.toInt(), 120 + size * 2 + 192, row.toInt() + 288); draw(canvas)
        }
        context.getDrawable(R.drawable.ic_task_phone)!!.mutate().apply {
            colorFilter = PorterDuffColorFilter(Color.rgb(80, 80, 80), PorterDuff.Mode.SRC_IN)
            setBounds(140 + size * 2 + 200, row.toInt(), 140 + size * 2 + 392, row.toInt() + 288); draw(canvas)
        }

        // The status-bar mark must cover a real share of its 24dp box once tinted.
        val bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
        context.getDrawable(R.drawable.ic_notification)!!.apply { setBounds(0, 0, 72, 72); draw(Canvas(bitmap)) }
        var covered = 0
        for (x in 0 until 72) for (y in 0 until 72) if (Color.alpha(bitmap.getPixel(x, y)) > 128) covered++
        assertTrue("status-bar icon covers ${covered * 100 / (72 * 72)}% of its box", covered > 72 * 72 / 8)

        val file = File("build/reports/eta-icons/brand-icons.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun drawMasked(canvas: Canvas, icon: AdaptiveIconDrawable, left: Float, top: Float, size: Int, circle: Boolean) {
        val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(layer)
        val inset = size / 4 // Adaptive layers are 108dp; the visible mask is the centre 72dp.
        icon.background.setBounds(-inset, -inset, size + inset, size + inset); icon.background.draw(layerCanvas)
        icon.foreground.setBounds(-inset, -inset, size + inset, size + inset); icon.foreground.draw(layerCanvas)
        val mask = Path().apply {
            if (circle) addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
            else addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.3f, size * 0.3f, Path.Direction.CW)
        }
        canvas.save(); canvas.translate(left, top); canvas.clipPath(mask); canvas.drawBitmap(layer, 0f, 0f, null); canvas.restore()
    }

    private fun tinted(canvas: Canvas, id: Int, left: Int, top: Int, size: Int, color: Int) {
        context.getDrawable(id)!!.mutate().apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            setBounds(left, top, left + size, top + size); draw(canvas)
        }
    }
}
