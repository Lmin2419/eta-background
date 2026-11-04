package io.github.mangi.eta.agent.browser

import android.app.Application
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import io.github.mangi.eta.agent.runtime.AgentRunCancelledException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BrowserSessionRegistryTest {
    private val keys = mutableListOf<String>()
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        keys.forEach(BrowserSessionRegistry::requestRemove)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun conversationsOwnIndependentPagesAndCancellation() {
        val firstKey = newKey()
        val first = BrowserSessionRegistry.get(firstKey)
        val second = BrowserSessionRegistry.get(newKey())
        seedPage(first, "https://first.example")
        seedPage(second, "https://second.example")
        assertSame(first, BrowserSessionRegistry.get(firstKey))
        assertNotSame(first, second)
        assertEquals("https://first.example", first.snapshots.value.url)
        assertEquals("https://second.example", second.snapshots.value.url)
        setField(first, "activeAgentRunId", "run-a")
        setField(second, "activeAgentRunId", "run-b")

        first.interruptAgentAction("run-b")
        assertFalse(interrupted(first))
        first.interruptAgentAction("run-a")
        assertTrue(interrupted(first))
        assertFalse(interrupted(second))
    }

    @Test
    fun memoryPressureKeepsRunningAndAttachedPages() {
        val activeKey = newKey()
        val active = BrowserSessionRegistry.get(activeKey)
        val idle = BrowserSessionRegistry.get(newKey())
        val visible = BrowserSessionRegistry.get(newKey())
        seedPage(active, "https://active.example")
        seedPage(idle, "https://idle.example")
        seedPage(visible, "https://visible.example")
        val container = FrameLayout(context)
        visible.attachTo(container, context)
        val lease = BrowserSessionRegistry.acquireRun(activeKey, "run-a")
        try {
            BrowserSessionRegistry.releaseIdlePages()
            assertEquals("https://active.example", active.snapshots.value.url)
            assertEquals("https://visible.example", visible.snapshots.value.url)
            assertEquals("", idle.snapshots.value.url)
        } finally {
            lease.close()
            visible.detachFrom(container)
        }
        assertEquals("https://active.example", active.snapshots.value.url)
        BrowserSessionRegistry.releaseIdlePages()
        assertEquals("", active.snapshots.value.url)
    }

    @Test
    fun deletionWaitsForTheRunAndAttachedPageToRelease() {
        val key = newKey()
        val session = BrowserSessionRegistry.get(key)
        val container = FrameLayout(context)
        session.attachTo(container, context)
        val lease = BrowserSessionRegistry.acquireRun(key, "run-a")
        BrowserSessionRegistry.requestRemove(key)
        assertSame(session, BrowserSessionRegistry.get(key))
        lease.close()
        lease.close()
        assertSame(session, BrowserSessionRegistry.get(key))
        session.detachFrom(container)
        assertNotSame(session, BrowserSessionRegistry.get(key))
    }

    @Test
    fun attachingAnOffscreenPageRestoresWindowRenderingWithoutReplacingThePage() {
        val session = BrowserSessionRegistry.get(newKey())
        val webView = seedPage(session, "https://example.test")
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val container = FrameLayout(context)
        try {
            session.attachTo(container, context)
            assertSame(webView, container.getChildAt(0))
            assertEquals(View.LAYER_TYPE_NONE, webView.layerType)
            assertEquals(0, webView.loads)
            session.detachFrom(container)
            session.attachTo(container, context)
            assertSame(webView, container.getChildAt(0))
            assertEquals(View.LAYER_TYPE_NONE, webView.layerType)
            assertEquals(0, webView.loads)
        } finally {
            session.detachFrom(container)
        }
    }

    @Test
    fun releasingOldWindowDoesNotDetachTheReopenedBrowser() {
        val session = BrowserSessionRegistry.get(newKey())
        val webView = seedPage(session, "https://example.test")
        val oldWindow = FrameLayout(context)
        val newWindow = FrameLayout(context)
        try {
            session.attachTo(oldWindow, context)
            session.attachTo(newWindow, context)
            session.detachFrom(oldWindow)
            assertSame(newWindow, webView.parent)
            assertEquals(0, oldWindow.childCount)
            assertTrue(session.snapshots.value.isUserControlling)
            assertEquals(0, webView.loads)
        } finally {
            session.detachFrom(newWindow)
        }
        assertFalse(session.snapshots.value.isUserControlling)
        assertNull(webView.parent)
    }

    @Test
    fun resettingOnePageKeepsOtherPagesCookiesAndSharedCache() {
        val first = BrowserSessionRegistry.get(newKey())
        val second = BrowserSessionRegistry.get(newKey())
        val firstView = seedPage(first, "https://first.example")
        seedPage(second, "https://second.example")
        CookieManager.getInstance().setCookie("https://shared.example", "session=kept")

        first.resetFromUser()

        assertEquals("", first.snapshots.value.url)
        assertEquals("https://second.example", second.snapshots.value.url)
        assertEquals("session=kept", CookieManager.getInstance().getCookie("https://shared.example"))
        assertFalse(firstView.cacheCleared)
    }

    @Test
    fun lateCallbacksFromAnOldPageCannotReplaceTheNewPage() {
        val session = BrowserSessionRegistry.get(newKey())
        val oldView = seedPage(session, "https://old.example")
        val clientType = AgentBrowserSession::class.java.declaredClasses.single { it.simpleName == "BrowserClient" }
        val client = clientType.getDeclaredConstructor(AgentBrowserSession::class.java).apply {
            isAccessible = true
        }.newInstance(session) as android.webkit.WebViewClient
        session.resetFromUser()
        seedPage(session, "https://new.example")

        client.onPageFinished(oldView, "https://old.example/late")

        assertEquals("https://new.example", session.snapshots.value.url)
    }

    @Test
    fun progressCallbacksCannotFinishOrRestartMainPageLoading() {
        val session = BrowserSessionRegistry.get(newKey())
        val view = seedPage(session, "https://example.test")
        val clientType = AgentBrowserSession::class.java.declaredClasses.single { it.simpleName == "BrowserClient" }
        val client = clientType.getDeclaredConstructor(AgentBrowserSession::class.java).apply {
            isAccessible = true
        }.newInstance(session) as android.webkit.WebViewClient
        val chromeType = AgentBrowserSession::class.java.declaredClasses.single { it.simpleName == "BrowserChrome" }
        val chrome = chromeType.getDeclaredConstructor(AgentBrowserSession::class.java).apply {
            isAccessible = true
        }.newInstance(session) as android.webkit.WebChromeClient

        client.onPageStarted(view, "https://example.test", null)
        for (progress in listOf(10, 100, 40)) {
            chrome.onProgressChanged(view, progress)
            assertTrue(session.snapshots.value.isLoading)
            assertEquals(progress, session.snapshots.value.progress)
        }
        client.onPageFinished(view, "https://example.test")
        for (progress in listOf(80, 100, 20)) {
            chrome.onProgressChanged(view, progress)
            assertFalse(session.snapshots.value.isLoading)
        }
        client.onPageStarted(view, "https://example.test/next", null)
        assertTrue(session.snapshots.value.isLoading)
    }

    @Test
    fun cancellingAnAgentWaitingForThePageLockDoesNotExecuteItsAction() {
        val session = BrowserSessionRegistry.get(newKey())
        val view = seedPage(session, "https://original.example")
        val operationLock = AgentBrowserSession::class.java.getDeclaredField("operationLock").apply {
            isAccessible = true
        }.get(session) as ReentrantLock
        val cancellation = AtomicBoolean(false)
        val waiting = CountDownLatch(1)
        val task = FutureTask {
            session.execute(context, JSONObject().put("action", "navigate").put("url", "https://late.example"),
                "run-cancelled", "tool-cancelled", checkCancellation = {
                    waiting.countDown()
                    if (cancellation.get()) throw AgentRunCancelledException()
                })
        }
        val worker = Thread(task)
        operationLock.lock()
        try {
            worker.start()
            assertTrue(waiting.await(1L, TimeUnit.SECONDS))
            cancellation.set(true)
            try {
                task.get(2L, TimeUnit.SECONDS)
                throw AssertionError("已取消的等待者执行了浏览器动作")
            } catch (failure: ExecutionException) {
                assertTrue(failure.cause is AgentRunCancelledException)
            }
            assertEquals(0, view.loads)
            assertEquals("https://original.example", session.snapshots.value.url)
        } finally {
            operationLock.unlock()
            worker.interrupt()
            worker.join(1_000L)
        }
    }

    @Test
    fun globalClearingInterruptsNavigationAndResetsEveryPageAndCookie() {
        val first = BrowserSessionRegistry.get(newKey())
        val second = BrowserSessionRegistry.get(newKey())
        seedPage(first, "https://first.example")
        seedPage(second, "https://second.example")
        CookieManager.getInstance().setCookie("https://shared.example", "session=removed")
        val navigation = FutureTask {
            first.execute(context, JSONObject().put("action", "navigate").put("url", "https://loading.example"),
                "run-a", "tool-a")
        }
        val navigationThread = Thread(navigation).apply { start() }
        val clearing = FutureTask { BrowserSessionRegistry.clearSharedBrowsingData(context) }
        val clearingThread = Thread(clearing)
        try {
            pumpUntil { first.snapshots.value.isLoading }
            clearingThread.start()
            pumpUntil { clearing.isDone && navigation.isDone }
            clearing.get(1L, TimeUnit.SECONDS)
            assertEquals("CANCELLED", JSONObject(navigation.get().content).getString("code"))
            assertEquals("", first.snapshots.value.url)
            assertEquals("", second.snapshots.value.url)
            assertNull(CookieManager.getInstance().getCookie("https://shared.example"))
        } finally {
            navigationThread.interrupt()
            clearingThread.interrupt()
            navigationThread.join(1_000L)
            clearingThread.join(1_000L)
        }
    }

    @Test
    fun oldCleanupCannotStopNewUserNavigation() {
        val session = BrowserSessionRegistry.get(newKey())
        val view = seedPage(session, "https://old.example")
        val epoch = AgentBrowserSession::class.java.getDeclaredField("operationEpoch").apply { isAccessible = true }.get(session) as AtomicLong
        val generation = AgentBrowserSession::class.java.getDeclaredField("navigationGeneration").apply { isAccessible = true }.get(session) as AtomicLong
        epoch.set(5L); generation.set(7L)
        val stop = AgentBrowserSession::class.java.getDeclaredMethod("stopOwnedNavigation", WebView::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
        stop.invoke(session, view, 7L, 5L)
        epoch.incrementAndGet(); generation.incrementAndGet()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, view.stops)
        stop.invoke(session, view, 8L, 6L)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, view.stops)
    }

    @Test
    fun navigationTotalDeadlineStopsOwnedLoadWithoutReplayingIt() {
        val session = BrowserSessionRegistry.get(newKey())
        val view = seedPage(session, "https://original.example")
        val navigation = FutureTask {
            session.execute(context, JSONObject().put("action", "navigate").put("url", "https://slow.example")
                .put("timeout_ms", 1200L), "run-timeout", "tool-timeout")
        }
        val worker = Thread(navigation).apply { start() }
        try {
            pumpUntil { view.loads == 1 }
            pumpUntil { navigation.isDone && view.stops > 0 }
            val result = JSONObject(navigation.get().content)
            assertFalse(result.getBoolean("ok"))
            assertTrue(result.getBoolean("operationMayHaveRun"))
            assertEquals(1, view.loads)
            assertFalse(session.snapshots.value.isLoading)
        } finally {
            worker.interrupt(); worker.join(1_000L)
        }
    }

    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5L)
        }
        assertTrue("异步浏览器操作未在期限内完成", condition())
    }

    private fun newKey(): String = UUID.randomUUID().toString().also(keys::add)

    private fun seedPage(session: AgentBrowserSession, url: String): RecordingWebView {
        session.initialize(context)
        val view = RecordingWebView()
        setField(session, "webView", view)
        setField(session, "currentUrl", url)
        AgentBrowserSession::class.java.getDeclaredMethod("publishSnapshotOnMain").apply {
            isAccessible = true
        }.invoke(session)
        return view
    }

    private fun setField(session: AgentBrowserSession, name: String, value: Any) {
        AgentBrowserSession::class.java.getDeclaredField(name).apply { isAccessible = true }.set(session, value)
    }

    private fun interrupted(session: AgentBrowserSession): Boolean =
        (AgentBrowserSession::class.java.getDeclaredField("interrupted").apply {
            isAccessible = true
        }.get(session) as AtomicBoolean).get()

    private inner class RecordingWebView : WebView(this@BrowserSessionRegistryTest.context) {
        var cacheCleared = false
        var loads = 0
        var stops = 0
        override fun stopLoading() { stops += 1 }
        override fun clearCache(includeDiskFiles: Boolean) { cacheCleared = true }
        override fun loadUrl(url: String) { loads += 1 }
    }
}
