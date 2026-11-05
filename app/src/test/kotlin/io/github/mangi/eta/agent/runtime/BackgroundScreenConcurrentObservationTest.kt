package io.github.mangi.eta.agent.runtime

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class BackgroundScreenConcurrentObservationTest {
    private class Connection(private val parties: Int = 2) : ScreenToolConnection {
        val calls = CopyOnWriteArrayList<String>()
        val releases = AtomicInteger()
        private val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        private val bothArrived = CountDownLatch(parties)
        @Volatile var shotCode: String? = null

        override fun execute(name: String, arguments: String): String {
            calls += name
            if (name == "screen_open") return """{"ok":true}"""
            peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try {
                // A sequential caller would wait here for a call that never starts.
                bothArrived.countDown()
                if (!bothArrived.await(3, TimeUnit.SECONDS)) return """{"ok":false,"code":"NOT_CONCURRENT"}"""
                if (name == "screen_shot") shotCode?.let { return """{"ok":false,"code":"$it"}""" }
                return """{"ok":true}"""
            } finally { inFlight.decrementAndGet() }
        }
        override fun release(keepFinished: Boolean): Boolean { releases.incrementAndGet(); return true }
        override fun keepAlive() = Unit
    }

    private fun tools(connection: Connection) = BackgroundScreenTools(
        "concurrent-observe", AgentRunController(), registry = ScreenLeaseRegistry(), connectionFactory = { connection },
    )

    private val observation = listOf("screen_dump" to """{"limit":10}""", "screen_shot" to """{"scale":0.5}""")

    @Test fun nodeReadAndCaptureRunAtTheSameTime() {
        val connection = Connection()
        val tools = tools(connection)
        try {
            tools.execute("screen_open", """{"package":"app.one"}""")
            val results = tools.executeConcurrently(observation).map { JSONObject(it!!) }
            assertTrue(results.all { it.getBoolean("ok") })
            assertEquals(2, connection.peak.get())
        } finally { tools.close() }
    }

    @Test fun aLostScreenDuringTheCaptureReleasesTheBindingLikeASingleCall() {
        val connection = Connection().apply { shotCode = "STALE_SCREEN" }
        val tools = tools(connection)
        try {
            tools.execute("screen_open", """{"package":"app.one"}""")
            val results = tools.executeConcurrently(observation).map { JSONObject(it!!) }
            assertTrue(results[0].getBoolean("ok"))
            assertEquals("STALE_SCREEN", results[1].getString("code"))
            // Cleanup runs on the maintenance thread; wait until it has released the binding.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            var after = JSONObject(tools.execute("screen_dump", "{}")!!)
            while (after.optString("code") == "CLEANUP_PENDING" && System.nanoTime() < deadline) {
                Thread.sleep(10)
                after = JSONObject(tools.execute("screen_dump", "{}")!!)
            }
            assertEquals("NO_SESSION", after.getString("code"))
            assertEquals(1, connection.releases.get())
        } finally { tools.close() }
    }

    @Test fun withoutAScreenItFallsBackToSequentialCallsAndExplainsTheMissingSession() {
        val connection = Connection(parties = 1)
        val tools = tools(connection)
        try {
            val results = tools.executeConcurrently(observation).map { JSONObject(it!!) }
            assertTrue(results.all { it.getString("code") == "NO_SESSION" })
            assertFalse(connection.calls.contains("screen_dump"))
        } finally { tools.close() }
    }

    @Test fun inputsAreNeverRunConcurrently() {
        val connection = Connection(parties = 1)
        val tools = tools(connection)
        try {
            tools.execute("screen_open", """{"package":"app.one"}""")
            tools.executeConcurrently(listOf("screen_dump" to "{}", "screen_tap" to "{}"))
            assertEquals(1, connection.peak.get())
        } finally { tools.close() }
    }
}
