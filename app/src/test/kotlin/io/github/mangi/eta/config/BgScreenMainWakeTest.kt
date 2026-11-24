package io.github.mangi.eta.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** e52：息屏时后台屏醒不过来，三档里谁能点亮主屏。 */
class BgScreenMainWakeTest {
    @Test fun defaultLetsOnlyScheduledTasksLightTheScreen() {
        assertEquals(BgScreenMainWake.SCHEDULED, BgScreenMainWake.fromWireValue(null))
        assertEquals(BgScreenMainWake.SCHEDULED, BgScreenMainWake.fromWireValue("unknown"))
        assertTrue(BgScreenMainWake.SCHEDULED.allows(scheduledRun = true))
        assertFalse(BgScreenMainWake.SCHEDULED.allows(scheduledRun = false))
    }

    @Test fun neverAndAlwaysIgnoreTheRunKind() {
        assertFalse(BgScreenMainWake.NEVER.allows(true))
        assertFalse(BgScreenMainWake.NEVER.allows(false))
        assertTrue(BgScreenMainWake.ALWAYS.allows(true))
        assertTrue(BgScreenMainWake.ALWAYS.allows(false))
        BgScreenMainWake.entries.forEach { assertEquals(it, BgScreenMainWake.fromWireValue(it.wireValue)) }
    }

    @Test fun sessionIdMapsBackToItsRun() {
        assertEquals("run-1", BgScreenMainWake.runIdOf("run-1:3f2a"))
        assertEquals("scheduled:task:7", BgScreenMainWake.runIdOf("scheduled:task:7:3f2a"))
        assertEquals("plain", BgScreenMainWake.runIdOf("plain"))
    }
}
