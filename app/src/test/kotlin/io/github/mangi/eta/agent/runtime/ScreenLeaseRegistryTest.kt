package io.github.mangi.eta.agent.runtime

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class ScreenLeaseRegistryTest {
    @Test fun thirdTaskWaitsForCleanupAcknowledgementWithoutEvictingOwners() {
        val registry = ScreenLeaseRegistry()
        registry.acquire("a", "app.a", {})
        registry.acquire("b", "app.b", {})
        val admitted = CountDownLatch(1)
        val worker = thread { registry.acquire("c", "app.c", {}); admitted.countDown() }
        try {
            assertFalse(admitted.await(150, TimeUnit.MILLISECONDS))
            assertEquals(listOf("a", "b"), registry.owners())
            registry.release("a")
            assertTrue(admitted.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("b", "c"), registry.owners())
        } finally { worker.interrupt(); worker.join(2000) }
    }

    @Test fun sameAppWaitsWhileUnrelatedAppsCanAdvance() {
        val registry = ScreenLeaseRegistry()
        registry.acquire("a", "shared.app", {})
        val admitted = CountDownLatch(1)
        val worker = thread { registry.acquire("b", "shared.app", {}); admitted.countDown() }
        try {
            registry.acquire("c", "another.app", {})
            assertFalse(admitted.await(150, TimeUnit.MILLISECONDS))
            assertEquals(listOf("a", "c"), registry.owners())
            registry.release("a")
            assertTrue(admitted.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("c", "b"), registry.owners())
        } finally { worker.interrupt(); worker.join(2000) }
    }

    @Test fun cancelledWaiterNeverTakesOwnership() {
        val registry = ScreenLeaseRegistry()
        val cancelled = AtomicBoolean(false)
        val finished = CountDownLatch(1)
        registry.acquire("a", "shared.app", {})
        val worker = thread {
            try {
                registry.acquire("b", "shared.app", { if (cancelled.get()) throw AgentRunCancelledException() })
            } catch (_: AgentRunCancelledException) { finished.countDown() }
        }
        try {
            cancelled.set(true)
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("a"), registry.owners())
        } finally { worker.interrupt(); worker.join(2000) }
    }
}
