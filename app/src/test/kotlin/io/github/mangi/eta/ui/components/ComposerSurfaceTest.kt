package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h896dp-xhdpi", application = Application::class)
class ComposerSurfaceTest {

    @Test
    fun composerSurfaceFadeAndHeight() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            for (dark in listOf(false, true)) {
                for (fontScale in listOf(1f, 2f)) {
                    runCase(controller.get(), dark, fontScale)
                }
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun runCase(activity: ComponentActivity, dark: Boolean, fontScale: Float) {
        var showFade by mutableStateOf(true)
        var surf: Rect? = null
        var content: Rect? = null
        var density = 1f
        var canvasArgb = 0
        val panel = Stage3TestPanel(activity, dark, fontScale) {
            density = LocalDensity.current.density
            canvasArgb = EtaTheme.colors.canvas.toArgb()
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                ChatComposerSurface(
                    showFade = showFade,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { surf = it.boundsInRoot() }
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(100.dp)
                            .onGloballyPositioned { content = it.boundsInRoot() }
                    )
                }
            }
        }
        try {
            panel.advance(5)
            val bmp = panel.bitmap()
            try {
                panel.draw(bmp)
                val sb = requireNotNull(surf)
                val cb = requireNotNull(content)
                val x = sb.center.x.toInt()
                val red = Color.Red.toArgb()
                val top = sb.top.toInt()
                val p1 = bmp.getPixel(x, top + 1)
                val p12 = bmp.getPixel(x, top + (12 * density).toInt())
                val p23 = bmp.getPixel(x, top + (23 * density).toInt())
                val p30 = bmp.getPixel(x, top + (30 * density).toInt())
                assertNear(p1, red, 20, "顶部应接近红色")
                assertNear(p30, canvasArgb, 3, "30dp 处应接近 canvas")
                assertTrue("12dp 应为中间色", dist(p12, red) > 20 && dist(p12, canvasArgb) > 20)
                assertTrue("23dp 应更接近 canvas", dist(p23, canvasArgb) < 120)
                assertTrue(
                    "content 顶部应位于 fade 下方 (${cb.top - sb.top}px, 期望 ~${24 * density}px)",
                    abs((cb.top - sb.top) - 24f * density) < 4f * density
                )
                val hTrue = sb.bottom - sb.top

                showFade = false
                panel.advance(5)
                val bmp2 = panel.bitmap()
                try {
                    panel.draw(bmp2)
                    val sb2 = requireNotNull(surf)
                    val cb2 = requireNotNull(content)
                    val dh = hTrue - (sb2.bottom - sb2.top)
                    assertTrue(
                        "高度差应约 24dp (${(24 * density).toInt()}px)，实际 ${dh.toInt()}px",
                        abs(dh - 24f * density) <= 4f * density
                    )
                    assertTrue(
                        "Spacer 不应随 fontScale 变化（实际 ${dh / density}dp）",
                        abs(dh / density - 24f) <= 1f
                    )
                    assertNear(
                        bmp2.getPixel(x, sb2.top.toInt() + 1),
                        canvasArgb, 3, "showFade=false 时顶部应为 canvas"
                    )
                    assertTrue(
                        "showFade=false 时 content 应贴近 surface 顶部 (差 ${cb2.top - sb2.top}px)",
                        abs(cb2.top - sb2.top) < 4f * density
                    )
                } finally { bmp2.recycle() }
            } finally { bmp.recycle() }
        } finally {
            panel.close()
        }
    }

    private fun dist(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            abs((a and 0xFF) - (b and 0xFF))

    private fun assertNear(actual: Int, expected: Int, tol: Int, msg: String) {
        val d = dist(actual, expected)
        assertTrue(
            "$msg: 实际=${Integer.toHexString(actual)} 期望=${Integer.toHexString(expected)} 距离=$d",
            d <= tol
        )
    }
}
