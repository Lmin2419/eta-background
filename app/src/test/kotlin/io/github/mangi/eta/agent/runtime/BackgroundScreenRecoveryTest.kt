package io.github.mangi.eta.agent.runtime

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class BackgroundScreenRecoveryTest {
    private class Connection : ScreenToolConnection {
        val attempts = AtomicInteger()
        @Volatile var failures = 0
        @Volatile var releaseAllowed = true
        var pendingLaunches = 0
        val calls = CopyOnWriteArrayList<String>()
        val keeps = CopyOnWriteArrayList<Boolean>()
        var lost = false
        override fun execute(name: String, arguments: String): String {
            calls += name
            if (lost && name == "screen_tap") return "{\"ok\":false,\"code\":\"STALE_SCREEN\"}"
            if (name == "screen_open" && pendingLaunches-- > 0) return "{\"ok\":false,\"code\":\"CLEANUP_PENDING\"}"
            return "{\"ok\":true}"
        }
        override fun release(keepFinished: Boolean): Boolean {
            keeps += keepFinished
            return attempts.incrementAndGet() > failures && releaseAllowed
        }
        override fun keepAlive() = Unit
    }

    @Test fun resumedTaskMustObserveBeforeApplyingOldCoordinates() {
        val connection = Connection()
        val controller = AgentRunController()
        val tools = BackgroundScreenTools("resume", controller, registry = ScreenLeaseRegistry(), connectionFactory = { connection })
        try {
            tools.execute("screen_open", "{\"package\":\"app.one\"}")
            controller.pause(); controller.resume()
            assertTrue(tools.execute("screen_tap", "{}")!!.contains("OBSERVE_AFTER_RESUME"))
            assertFalse(connection.calls.contains("screen_tap"))
            tools.execute("screen_dump", "{}")
            assertEquals("{\"ok\":true}", tools.execute("screen_tap", "{}"))
        } finally { tools.close() }
    }

    @Test fun retriesOnlyEngineConfirmedPendingCleanupBeforeLaunch() {
        val connection = Connection().apply { pendingLaunches = 2 }
        val opened = mutableListOf<String>()
        val tools = BackgroundScreenTools("parked-reclaim", AgentRunController(), onAppOpened = { opened += it }, registry = ScreenLeaseRegistry(),
            connectionFactory = { connection }, retryDelayMs = 20)
        try {
            assertEquals("{\"ok\":true}", tools.execute("screen_open", "{\"package\":\"app.one\"}"))
            assertEquals(3, connection.calls.size)
            assertEquals(listOf("app.one"), opened)
        } finally { tools.close() }
    }

    @Test fun cleanupRecoversAfterMoreThanThreeFailuresAndNextAppCanOpen() {
        val registry = ScreenLeaseRegistry()
        val connections = CopyOnWriteArrayList<Connection>()
        val tools = BackgroundScreenTools("recovery", AgentRunController(), registry = registry,
            connectionFactory = { Connection().also { connections += it } }, retryDelayMs = 20)
        try {
            tools.execute("screen_open", "{\"package\":\"app.one\"}")
            connections.single().failures = 4
            tools.execute("screen_open", "{\"package\":\"app.two\"}")
            assertTrue(connections.first().attempts.get() >= 5)
            assertEquals(2, connections.size)
            assertTrue(connections.first().keeps.all { it })
            assertEquals(1, registry.owners().size)
            assertEquals(listOf("screen_open"), connections.last().calls.toList())
        } finally { tools.close() }
    }

    @Test fun lostEngineSessionClearsBindingWithoutReplayingTheFailedInput() {
        val registry = ScreenLeaseRegistry()
        val connections = CopyOnWriteArrayList<Connection>()
        val tools = BackgroundScreenTools("lost", AgentRunController(), registry = registry,
            connectionFactory = { Connection().also { connections += it } }, retryDelayMs = 20)
        try {
            tools.execute("screen_open", "{\"package\":\"app.one\"}")
            connections.first().lost = true
            assertTrue(tools.execute("screen_tap", "{}")!!.contains("STALE_SCREEN"))
            tools.execute("screen_open", "{\"package\":\"app.one\"}")
            assertEquals(2, connections.size)
            assertEquals(listOf("screen_open", "screen_tap"), connections.first().calls.toList())
            assertEquals(listOf("screen_open"), connections.last().calls.toList())
            assertTrue(connections.first().keeps.all { !it })
        } finally { tools.close() }
    }

    @Test fun finishReleasesOwnershipBeforeModelReplyAndDoesNotStopAnotherTask() {
        val registry = ScreenLeaseRegistry()
        val a = Connection().apply { releaseAllowed = false }
        val b = Connection()
        val first = BackgroundScreenTools("a", AgentRunController(), registry = registry, connectionFactory = { a }, retryDelayMs = 20)
        val second = BackgroundScreenTools("b", AgentRunController(), registry = registry, connectionFactory = { b }, retryDelayMs = 20)
        val opened = CountDownLatch(1)
        var waiter: Thread? = null
        try {
            first.execute("screen_open", "{\"package\":\"app.shared\"}")
            first.execute("screen_finish", "{}")
            waiter = thread {
                second.execute("screen_open", "{\"package\":\"app.shared\"}")
                opened.countDown()
            }
            assertFalse(opened.await(100, TimeUnit.MILLISECONDS))
            a.releaseAllowed = true
            assertTrue(opened.await(3, TimeUnit.SECONDS))
            first.close() // A late close must never release B's screen.
            assertEquals("{\"ok\":true}", second.execute("screen_tap", "{}"))
            assertEquals(0, b.attempts.get())
        } finally {
            a.releaseAllowed = true
            first.close(); second.close()
            waiter?.join(3000)
        }
    }
}
