package io.github.mangi.eta.ui.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonTaskLaunchGuardTest {
    @Test fun replayAndDoubleTapDoNotCreateAnotherRunButLaterExplicitRunDoes() {
        val guard = CommonTaskLaunchGuard()
        assertTrue(guard.claim("request-1", "morning", 1000))
        assertFalse(guard.claim("request-1", "morning", 100_000))
        assertFalse(guard.claim("double-tap", "morning", 1100))
        assertFalse(guard.claim("double-tap", "morning", 100_000))
        assertTrue(guard.claim("request-2", "morning", 3000))
    }

    @Test fun independentTasksAreNotBlockedByTheTapGuard() {
        val guard = CommonTaskLaunchGuard()
        assertTrue(guard.claim("one", "morning", 1000))
        assertTrue(guard.claim("two", "evening", 1000))
    }
}
