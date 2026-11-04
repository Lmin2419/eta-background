package io.github.mangi.eta.agent.web

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import io.github.mangi.eta.agent.terminal.TerminalEnvironment
import io.github.mangi.eta.agent.terminal.TerminalExecutionContext
import io.github.mangi.eta.core.AgentLogger
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScraplingRuntimeCancellationTest {
    private val context: Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getAssets(): AssetManager = error("Cancelled runtime must not load the worker asset")
    }
    private val logger = object : AgentLogger {
        override fun debug(message: () -> String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }

    private fun runtime(cancelled: AtomicBoolean) = ScraplingRuntime(
        context,
        // No installed environment: a missed owner check would incorrectly return
        // SCRAPLING_UNAVAILABLE, instead of respecting the task's terminal state.
        TerminalExecutionContext(TerminalEnvironment.DEBIAN, null),
        logger,
        rootAvailable = { error("Cancelled runtime must not start a Root process") },
        ownerCancelled = cancelled::get,
    )

    @Test fun lazilyInitializedAfterOwnerCancellationRejectsEveryReadRouteBeforePreflight() {
        val cancelled = AtomicBoolean(true)
        for (route in WebFetchRoute.entries) {
            runtime(cancelled).use { runtime ->
                val failure = assertThrows(WebRequestException::class.java) {
                    runtime.read(WebFetchUpgradeRequest("https://example.test", "fixture", route, 5_000))
                }
                assertEquals("WEB_CANCELLED", failure.code)
            }
        }
    }

    @Test fun lazilyInitializedAfterOwnerCancellationRejectsBothProbesBeforePreflight() {
        val cancelled = AtomicBoolean(true)
        for (browser in listOf(false, true)) {
            runtime(cancelled).use { runtime ->
                val failure = assertThrows(WebRequestException::class.java) { runtime.probe(checkBrowser = browser) }
                assertEquals("WEB_CANCELLED", failure.code)
            }
        }
    }

    @Test fun existingRuntimeObservesOwnerCancellationWithoutDependingOnOwnerCloseHook() {
        val cancelled = AtomicBoolean(false)
        runtime(cancelled).use { runtime ->
            cancelled.set(true)
            assertEquals("WEB_CANCELLED", assertThrows(WebRequestException::class.java) { runtime.probe() }.code)
            cancelled.set(false)
            // Cancellation is terminal for this instance, even if an external
            // status is subsequently reset for another task.
            assertEquals("WEB_CANCELLED", assertThrows(WebRequestException::class.java) { runtime.probe() }.code)
        }
    }
}
