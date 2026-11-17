package io.github.mangi.eta.agent.overlay

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import io.github.mangi.eta.data.model.AppearanceGlowIntensity
import io.github.mangi.eta.data.model.AppearanceGlowStyle
import io.github.mangi.eta.ui.theme.EtaAurora
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AmbientGlowTest {
    @Test
    fun smartGlowUsesEdgeOnlyWhileOperatingTheScreen() {
        assertEquals(GlowPresence.EDGE, AmbientGlowPolicy.presence(AppearanceGlowStyle.SMART, mainScreenOwned = true))
        assertEquals(GlowPresence.CAMERA, AmbientGlowPolicy.presence(AppearanceGlowStyle.SMART, mainScreenOwned = false))
        assertEquals(GlowPresence.EDGE, AmbientGlowPolicy.presence(AppearanceGlowStyle.FULL, mainScreenOwned = false))
    }

    @Test
    fun cameraRingWrapsTheCutoutOrFallsBackToTopCenter() {
        val density = 2f
        val ring = AmbientGlowPolicy.cameraRing(RectF(340f, 30f, 380f, 70f), 720f, density)
        val gap = AmbientGlowPolicy.RING_GAP_DP * density
        assertEquals(RectF(340f - gap, 30f - gap, 380f + gap, 70f + gap), ring)

        val fallback = AmbientGlowPolicy.cameraRing(null, 720f, density)
        assertEquals(360f, fallback.centerX(), 0.01f)
        assertEquals(AmbientGlowPolicy.FALLBACK_RING_WIDTH_DP * density, fallback.width(), 0.01f)
        assertTrue(fallback.top > 0f)
    }

    /** 画出几帧给人看：后台小光圈、操作时细边框、开始流光、结束呼吸、无开孔时的顶边小胶囊。 */
    @Test
    fun renderFrames() {
        val density = 2f
        val width = 720
        val height = 1560
        val colors = listOf(EtaAurora.honey, EtaAurora.peach, EtaAurora.rose, EtaAurora.lavender, EtaAurora.honey)
            .map { it.toArgb() }.toIntArray()
        val cutout = RectF(width / 2f - 18f, 26f, width / 2f + 18f, 62f)
        val edge = Path().apply {
            val inset = 1.5f * density
            addRoundRect(RectF(inset, inset, width - inset, height - inset), 80f, 80f, Path.Direction.CW)
        }
        val intensity = AppearanceGlowIntensity.SOFT
        val out = File("build/reports/ambient-glow").apply { mkdirs() }

        fun frame(name: String, withCutout: Boolean = true, draw: Canvas.() -> Unit) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFF4F1EC.toInt())
            val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
            for (i in 0 until 5) canvas.drawRoundRect(RectF(40f, 180f + i * 220f, width - 40f, 360f + i * 220f), 36f, 36f, card)
            if (withCutout) canvas.drawOval(cutout, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() })
            canvas.draw()
            File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // 画了东西：至少有一些像素不是底色或卡片色。
            var lit = 0
            for (y in 0 until height step 4) for (x in 0 until width step 4) {
                val p = bitmap.getPixel(x, y)
                if (p != 0xFFF4F1EC.toInt() && p != 0xFFFFFFFF.toInt() && p != 0xFF000000.toInt()) lit++
            }
            assertTrue("$name drew nothing", lit > 0)
        }

        frame("1-background-camera-ring") {
            drawCameraRing(AmbientGlowPolicy.cameraRing(cutout, width.toFloat(), density), colors, 40f, intensity, density, 1f)
        }
        frame("2-operating-edge") {
            val (halo, line) = strokePaints(edgeShader(colors, width.toFloat(), height.toFloat(), 40f), 0, intensity, density, 1f)
            drawPath(edge, halo); drawPath(edge, line)
        }
        frame("3-start-sweep") { drawStartSweep(edge, width.toFloat(), 0.35f, colors, intensity, density) }
        frame("4-end-pulse") { drawEndPulse(edge, width.toFloat(), height.toFloat(), 0.5f, colors, intensity, density) }
        frame("5-no-cutout-fallback", withCutout = false) {
            drawCameraRing(AmbientGlowPolicy.cameraRing(null, width.toFloat(), density), colors, 40f, intensity, density, 1f)
        }
    }
}
