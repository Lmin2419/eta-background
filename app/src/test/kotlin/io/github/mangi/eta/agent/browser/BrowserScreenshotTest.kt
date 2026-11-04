package io.github.mangi.eta.agent.browser

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import android.view.View
import java.lang.reflect.InvocationTargetException
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
    fun uncertainActionTimeoutDoesNotStopOrPretendToFinishPendingNavigation() {
        val session = AgentBrowserSession()
        var stops = 0
        val view = object : WebView(RuntimeEnvironment.getApplication()) {
            override fun stopLoading() { stops++ }
        }
        AgentBrowserSession::class.java.getDeclaredField("webView").apply { isAccessible = true }.set(session, view)
        AgentBrowserSession::class.java.getDeclaredField("currentUrl").apply { isAccessible = true }.set(session, "https://fixture.invalid/result")
        AgentBrowserSession::class.java.getDeclaredField("currentLoading").apply { isAccessible = true }.setBoolean(session, true)
        AgentBrowserSession::class.java.getDeclaredMethod("publishSnapshotOnMain").apply { isAccessible = true }.invoke(session)
        val failureClass = Class.forName("io.github.mangi.eta.agent.browser.AgentBrowserSession" + '$' + "BrowserFailure")
        val failure = failureClass.getDeclaredConstructor(String::class.java, String::class.java, String::class.java)
            .apply { isAccessible = true }.newInstance("ACTION_TIMEOUT", "Fixture pending navigation", "timeout") as RuntimeException
        val inputResult = AgentBrowserSession::class.java.getDeclaredMethod("inputResult", String::class.java, Function0::class.java)
            .apply { isAccessible = true }
        try {
            val block: () -> BrowserToolResult = { throw failure }
            val result = inputResult.invoke(session, "type", block) as BrowserToolResult
            val json = org.json.JSONObject(result.content)
            assertEquals("ACTION_TIMEOUT", json.getString("code"))
            assertTrue(json.getBoolean("is_loading"))
            assertTrue(session.snapshots.value.isLoading)
            assertTrue(json.getBoolean("operationMayHaveRun"))
            assertEquals(false, json.getBoolean("automatic_replay"))
            assertEquals(0, stops)
        } finally { view.destroy() }
    }

    @Test
    fun pendingFirstBlankCaptureRetriesOnlyTheReadWithoutChangingThePage() {
        val session = AgentBrowserSession()
        var draws = 0
        val view = object : WebView(RuntimeEnvironment.getApplication()) {
            override fun draw(canvas: Canvas) { canvas.drawColor(if (++draws == 1) Color.WHITE else Color.BLUE) }
            override fun loadUrl(url: String) { throw AssertionError("Screenshot must not navigate") }
            override fun scrollBy(x: Int, y: Int) { throw AssertionError("Screenshot must not scroll") }
        }
        view.right = 100
        view.bottom = 200
        AgentBrowserSession::class.java.getDeclaredField("activeOperationEpoch").apply { isAccessible = true }.setLong(session, 1L)
        val state = AgentBrowserSession::class.java.getDeclaredField("documentState").apply { isAccessible = true }.get(session) as BrowserDocumentState
        state.markDomReady(state.generation)
        val capture = AgentBrowserSession::class.java.getDeclaredMethod("captureReadyViewport", WebView::class.java).apply { isAccessible = true }
        try {
            val result = capture.invoke(session, view) as BrowserImage
            assertEquals(2, draws)
            assertEquals(100, result.width)
            assertEquals(false, state.visualReady)
        } finally { view.destroy() }
    }

    @Test
    fun pageFinishedDoesNotCertifyFirstPaintOrReplaceCurrentNavigationWithALateUrl() {
        val session = AgentBrowserSession()
        val view = WebView(RuntimeEnvironment.getApplication())
        AgentBrowserSession::class.java.getDeclaredField("webView").apply { isAccessible = true }.set(session, view)
        val clientClass = Class.forName("io.github.mangi.eta.agent.browser.AgentBrowserSession" + '$' + "BrowserClient")
        val client = clientClass.getDeclaredConstructor(AgentBrowserSession::class.java).apply { isAccessible = true }.newInstance(session) as android.webkit.WebViewClient
        try {
            client.onPageStarted(view, "https://fixture.invalid/new", null)
            client.onPageFinished(view, "https://fixture.invalid/old")
            assertEquals("https://fixture.invalid/new", session.snapshots.value.url)
            assertTrue(session.snapshots.value.isLoading)
            client.onPageFinished(view, "https://fixture.invalid/new")
            assertEquals(false, session.snapshots.value.isLoading)
            assertEquals(false, session.snapshots.value.isPageVisible)
            client.onPageCommitVisible(view, "https://fixture.invalid/new")
            assertTrue(session.snapshots.value.isPageVisible)
            val state = AgentBrowserSession::class.java.getDeclaredField("documentState").apply { isAccessible = true }.get(session) as BrowserDocumentState
            assertEquals(false, state.domReady)
            assertEquals(false, state.visualReady)
        } finally { view.destroy() }
    }

    @Test
    fun detachedWebViewDoesNotRequireACompositorCallback() {
        val session = AgentBrowserSession()
        val view = object : WebView(RuntimeEnvironment.getApplication()) {
            override fun postVisualStateCallback(requestId: Long, callback: VisualStateCallback) {
                throw AssertionError("Unattached screenshots must not wait for this callback")
            }
        }
        val await = AgentBrowserSession::class.java.getDeclaredMethod("awaitVisualReady", WebView::class.java).apply { isAccessible = true }
        try { assertEquals(false, await.invoke(session, view)) } finally { view.destroy() }
    }

    @Test
    fun onlyUniformBlackWhiteOrTransparentCapturesAreRejected() {
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        try {
            for (color in listOf(Color.WHITE, Color.BLACK, Color.TRANSPARENT)) {
                bitmap.eraseColor(color)
                assertTrue(BrowserCaptureReadiness.isUniformBlank(bitmap))
            }
            bitmap.eraseColor(Color.BLUE)
            assertEquals(false, BrowserCaptureReadiness.isUniformBlank(bitmap))
            bitmap.eraseColor(Color.WHITE)
            bitmap.setPixel(99, 199, Color.BLACK)
            assertEquals("A sparse control must not be discarded", false, BrowserCaptureReadiness.isUniformBlank(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test
    fun offscreenCapturesPreserveRenderingMode() {
        val session = AgentBrowserSession()
        val view = object : WebView(RuntimeEnvironment.getApplication()) {
            override fun draw(canvas: Canvas) { canvas.drawColor(Color.BLUE) }
        }
        view.right = 100
        view.bottom = 200
        val capture = AgentBrowserSession::class.java.getDeclaredMethod(
            "captureViewport", WebView::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        try {
            for (layer in listOf(View.LAYER_TYPE_NONE, View.LAYER_TYPE_HARDWARE)) {
                for (preview in listOf(false, true)) {
                    view.setLayerType(layer, null)
                    capture.invoke(session, view, preview)
                    assertEquals("截图不能改变后续前台绘制方式", layer, view.layerType)
                }
            }
        } finally { view.destroy() }
    }

    @Test
    fun failedOffscreenCapturePreservesRenderingMode() {
        val session = AgentBrowserSession()
        val view = object : WebView(RuntimeEnvironment.getApplication()) {
            override fun draw(canvas: Canvas) { error("test draw failure") }
        }
        view.right = 100
        view.bottom = 200
        val capture = AgentBrowserSession::class.java.getDeclaredMethod(
            "captureViewport", WebView::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        try {
            try {
                capture.invoke(session, view, true)
                throw AssertionError("绘制失败必须向调用方传播")
            } catch (failure: InvocationTargetException) {
                assertTrue(failure.cause is IllegalStateException)
            }
            assertEquals(View.LAYER_TYPE_NONE, view.layerType)
        } finally { view.destroy() }
    }

    @Test
    fun modelScreenshotRetainsViewportPixelsWhilePreviewStaysIndependent() {
        val session = AgentBrowserSession()
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
            val image = capture.invoke(session, view, false) as BrowserImage
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
            val preview = capture.invoke(session, view, true) as BrowserImage
            assertEquals("image/jpeg", preview.mimeType)
            assertTrue(preview.width < image.width)
            assertTrue(preview.height < image.height)
            assertEquals(image, capture.invoke(session, view, false))
        } finally {
            original.recycle()
            view.destroy()
        }
    }
}
