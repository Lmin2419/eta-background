package io.github.mangi.eta.agent.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class TaskPreviewControlTest {
    @Test fun takeoverWaitsForAnActualOperationAndClosingDoesNotResume() {
        val c = AgentRunController()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = thread { c.withScreenOperation { entered.countDown(); release.await() } }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        try {
            assertFalse(c.acquireManual("preview", 20))
            assertTrue(c.isPaused)
            release.countDown(); worker.join(2000)
            assertTrue(c.acquireManual("preview"))
            c.resume() // Notification cannot resume AI under an active manual finger.
            assertTrue(c.isPaused)
            c.releaseManual("someone-else")
            c.resume(); assertTrue(c.isPaused)
            c.releaseManual("preview"); assertTrue(c.isPaused)
            c.resume(); assertFalse(c.isPaused); assertEquals(1L, c.resumeGeneration)
        } finally { release.countDown(); c.cancel(); worker.join(2000) }
    }

    @Test fun acquiringManualControlPreventsNewOperationsAndDoesNotBlockOtherRuns() {
        val a = AgentRunController(); val b = AgentRunController()
        assertTrue(a.acquireManual("view"))
        val done = CountDownLatch(1)
        val worker = thread { a.withScreenOperation { done.countDown() } }
        try {
            assertFalse(done.await(40, TimeUnit.MILLISECONDS))
            assertEquals(7, b.withScreenOperation { 7 })
            a.releaseManual("view"); assertFalse(done.await(40, TimeUnit.MILLISECONDS))
            a.resume(); assertTrue(done.await(2, TimeUnit.SECONDS))
        } finally { a.releaseManual("view"); a.resume(); worker.join(2000) }
    }

    @Test fun portraitAndLandscapeLetterboxesNeverProduceOffsetTaps() {
        assertEquals(540f to 1200f, PreviewCoordinates.map(150f, 150f, 300, 300, 1080, 2400))
        assertNull(PreviewCoordinates.map(0f, 150f, 300, 300, 1080, 2400))
        assertEquals(1200f to 540f, PreviewCoordinates.map(150f, 150f, 300, 300, 2400, 1080))
        assertNull(PreviewCoordinates.map(150f, 0f, 300, 300, 2400, 1080))
        assertNull(PreviewCoordinates.map(Float.NaN, 1f, 300, 300, 2400, 1080))
    }
}
