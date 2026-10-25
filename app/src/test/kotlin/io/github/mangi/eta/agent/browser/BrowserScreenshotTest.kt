package io.github.mangi.eta.agent.browser

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import android.webkit.WebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowserScreenshotTest {
    @Test
    fun modelScreenshotRetainsViewportPixelsWhilePreviewStaysIndependent() {
        val view = object : WebView(RuntimeEnvironment.getApplication()) {
            override fun draw(canvas: Canvas) {
                canvas.drawColor(Color.argb(128, 64, 128, 192))
                canvas.drawRect(100f, 100f, 200f, 200f, Paint().apply { color = Color.RED })
            }
        }
        // 模拟 WebView 没有真实渲染进程驱动 setFrame，直接设置要捕获的视口边界。
        view.right = 1_700
        view.bottom = 2_600
        assertEquals(1_700, view.width)
        assertEquals(2_600, view.height)
        val original = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(original))
        val capture = AgentBrowserSession::class.java.getDeclaredMethod(
            "captureViewport", WebView::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        try {
            val image = capture.invoke(AgentBrowserSession, view, false) as BrowserImage
            assertEquals("image/png", image.mimeType)
            assertEquals(1_700, image.width)
            assertEquals(2_600, image.height)
            val bytes = Base64.decode(image.dataUrl.substringAfter("base64,"), Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("截图无法解码")
            try {
                assertEquals(1_700, bitmap.width)
                assertEquals(2_600, bitmap.height)
                assertEquals(Color.RED, bitmap.getPixel(150, 150))
                assertEquals(original.getPixel(20, 20), bitmap.getPixel(20, 20))
                assertTrue(original.sameAs(bitmap))
            } finally { bitmap.recycle() }
            val preview = capture.invoke(AgentBrowserSession, view, true) as BrowserImage
            assertEquals("image/jpeg", preview.mimeType)
            assertTrue(preview.width < image.width)
            assertTrue(preview.height < image.height)
            assertEquals(image, capture.invoke(AgentBrowserSession, view, false))
        } finally {
            original.recycle()
            view.destroy()
        }
    }
}
