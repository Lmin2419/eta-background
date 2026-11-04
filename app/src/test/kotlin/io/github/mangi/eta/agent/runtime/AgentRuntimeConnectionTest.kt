package io.github.mangi.eta.agent.runtime

import android.os.Handler
import io.github.mangi.eta.core.AgentLogger
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgentRuntimeConnectionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        shadowOf(RuntimeEnvironment.getApplication()).declareActionUnbindable(AgentRuntimeWire.ACTION_BIND)
        resetConnection()
    }

    @After
    fun tearDown() = resetConnection()

    @Test
    fun anUnbindableServiceFailsFast() {
        val elapsedMs = timedAcquire()
        assertTrue("acquire took ${elapsedMs}ms", elapsedMs < 2_000)
    }

    @Test
    fun aQueuedBindThatNeverRanDoesNotStallTheNextCaller() {
        // A caller starts binding, then its UI scope is cancelled before the main thread runs bind().
        val first = thread { runCatching { AgentRuntimeConnection.acquire(context, NoOpLogger) } }
        awaitTrue { field<Boolean>("binding") }
        first.interrupt()
        first.join(2_000)
        // Robolectric clears queued main-thread work between tests, so that bind() never runs.
        mainHandler().removeCallbacksAndMessages(null)

        val elapsedMs = timedAcquire()
        assertTrue("acquire waited ${elapsedMs}ms on a bind that was never dispatched", elapsedMs < 2_000)
    }

    /** Acquires on a worker thread while idling the main looper, as the UI does. */
    private fun timedAcquire(): Long {
        val started = System.nanoTime()
        val result = AtomicReference<Any?>()
        val done = CountDownLatch(1)
        thread {
            result.set(AgentRuntimeConnection.acquire(context, NoOpLogger))
            done.countDown()
        }
        while (!done.await(10, TimeUnit.MILLISECONDS)) ShadowLooper.idleMainLooper()
        assertNull("the runtime service is unbindable in tests", result.get())
        return (System.nanoTime() - started) / 1_000_000
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not reached" }
            Thread.sleep(1)
        }
    }

    private fun mainHandler(): Handler = field("mainHandler")

    private fun <T> field(name: String): T =
        ReflectionHelpers.getStaticField(AgentRuntimeConnection::class.java, name)

    private fun resetConnection() {
        mainHandler().removeCallbacksAndMessages(null)
        val type = AgentRuntimeConnection::class.java
        ReflectionHelpers.setStaticField(type, "messenger", null)
        ReflectionHelpers.setStaticField(type, "bound", false)
        ReflectionHelpers.setStaticField(type, "binding", false)
        ReflectionHelpers.setStaticField(type, "bindDispatched", false)
        ReflectionHelpers.setStaticField(type, "activeLeases", 0)
        ReflectionHelpers.setStaticField(type, "connectionLatch", CountDownLatch(0))
    }

    private object NoOpLogger : AgentLogger {
        override fun debug(message: () -> String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }
}
