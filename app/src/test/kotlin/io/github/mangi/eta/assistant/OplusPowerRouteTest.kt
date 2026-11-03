package io.github.mangi.eta.assistant

import org.junit.Assert.*
import org.junit.Test

class OplusPowerRouteTest {
    private val original = OplusPowerSnapshot(0, "", null, true, "firmware-a")
    private val store = Store()
    private val device = Device(original)
    private val route = OplusPowerRoute(device, store) { AssistantTick(3, 200) }

    @Test fun enablesOnlyAfterBothSettingsHaveBeenVerifiedAndRestoresInSafeOrder() {
        route.enable()
        assertTrue(route.configured(device.snapshot))
        assertEquals(original, store.saved!!.original)
        assertEquals(listOf("broadcast:1", "gate:1"), device.writes)
        device.writes.clear()
        route.restore()
        assertEquals(listOf("gate:null", "broadcast:"), device.writes)
        assertEquals(original, device.snapshot)
        assertNull(store.saved)
    }

    @Test fun preservesExistingBroadcastPreferenceAndEmptyPolicy() {
        device.snapshot = original.copy(broadcast = "true", gate = "")
        val before = device.snapshot
        route.enable()
        route.restore()
        assertEquals(before, device.snapshot)
    }

    @Test fun matchingModelWithoutMatchingFrameworkIsRejectedBeforeAnyWrite() {
        assertFalse(OplusPowerContract.supported("PMA110", 37, 0, "different-framework"))
        assertFalse(OplusPowerContract.supported("OTHER", 37, 0, OplusPowerContract.FRAMEWORK_SHA256))
        assertFalse(OplusPowerContract.supported("PMA110", 37, 10, OplusPowerContract.FRAMEWORK_SHA256))
        device.snapshot = original.copy(supported = false)
        code("COLOROS_FIRMWARE_UNSUPPORTED") { route.enable() }
        assertTrue(device.writes.isEmpty())
        assertNull(store.saved)
    }

    @Test fun anExistingVendorPolicyIsNeverClaimedAsOurs() {
        device.snapshot = original.copy(gate = "1")
        code("COLOROS_POLICY_OWNED") { route.enable() }
        assertTrue(device.writes.isEmpty())
        assertNull(store.saved)
    }

    @Test fun backupFailurePreventsAllDeviceChanges() {
        store.failSave = true
        code("BACKUP_UNAVAILABLE") { route.enable() }
        assertTrue(device.writes.isEmpty())
    }

    @Test fun noSecondEnableCanOverwriteTheOriginalSnapshot() {
        route.enable()
        val saved = store.saved
        val count = device.writes.size
        code("RESTORE_FIRST") { route.enable() }
        assertEquals(saved, store.saved)
        assertEquals(count, device.writes.size)
    }

    @Test fun deniedVendorWriteRollsBackTheBroadcastProperty() {
        device.gateFailure = "COLOROS_POLICY_UNAVAILABLE"
        code("COLOROS_POLICY_UNAVAILABLE") { route.enable() }
        assertEquals(original, device.snapshot)
        assertNull(store.saved)
    }

    @Test fun unknownPolicyResultIsNotReplayedOrUndoneAutomatically() {
        device.gateFailure = "COMMAND_TIMEOUT"
        device.failAfterGateWrite = true
        code("COMMAND_TIMEOUT") { route.enable() }
        assertEquals(listOf("broadcast:1", "gate:1"), device.writes)
        assertNull(store.saved!!.appliedAt)
        assertFalse(route.configured(device.snapshot))
        device.gateFailure = null
        route.restore()
        assertEquals(original, device.snapshot)
    }

    @Test fun rootLossKeepsTheJournalForExplicitRestoration() {
        device.gateFailure = "ROOT_REQUIRED"
        code("ROOT_REQUIRED") { route.enable() }
        assertEquals("1", device.snapshot.broadcast)
        assertNotNull(store.saved)
        device.gateFailure = null
        route.restore()
        assertEquals(original, device.snapshot)
    }

    @Test fun interruptedRestorationDisarmsEtaAndReopensTheStockRouteFirst() {
        route.enable()
        device.broadcastFailure = "COMMAND_TIMEOUT"
        code("COMMAND_TIMEOUT") { route.restore() }
        assertNull(device.snapshot.gate)
        assertNull(store.saved!!.appliedAt)
        assertFalse(route.configured(device.snapshot))
        device.broadcastFailure = null
        route.restore()
        assertEquals(original, device.snapshot)
        assertNull(store.saved)
    }

    @Test fun interruptedActivationCanBeRestoredAfterAppProcessDeath() {
        store.saved = OplusPowerBackup(original, broadcastAttempted = true, gateAttempted = true)
        device.snapshot = original.copy(broadcast = "1", gate = "1")
        route.restore()
        assertEquals(original, device.snapshot)
        assertNull(store.saved)
    }

    @Test fun anotherUsersSessionCannotMutateTheGlobalPolicy() {
        device.snapshot = original.copy(user = 10)
        code("USER_CHANGED") { route.enable() }
        assertTrue(device.writes.isEmpty())
    }

    @Test fun userSwitchAfterActivationPreventsRestoreWrites() {
        route.enable()
        device.snapshot = device.snapshot.copy(user = 10)
        val count = device.writes.size
        code("USER_CHANGED") { route.restore() }
        assertEquals(count, device.writes.size)
        assertNotNull(store.saved)
    }

    @Test fun externalPolicyChangeStopsRestorationWithoutOverwritingIt() {
        route.enable()
        device.snapshot = device.snapshot.copy(gate = "managed-by-owner")
        val count = device.writes.size
        code("CONFIGURATION_CHANGED") { route.restore() }
        assertEquals(count, device.writes.size)
        assertNotNull(store.saved)
    }

    @Test fun newFirmwareDoesNotInheritOldWakeAcceptanceButCanRestoreOwnedValues() {
        route.enable()
        device.snapshot = device.snapshot.copy(supported = false, fingerprint = "firmware-b")
        assertFalse(route.configured(device.snapshot))
        route.restore()
        assertNull(device.snapshot.gate)
        assertEquals("", device.snapshot.broadcast)
    }

    @Test fun unpersistedFinalStateRollsBackInsteadOfReportingEnabled() {
        store.failAppliedSave = true
        code("BACKUP_UNAVAILABLE") { route.enable() }
        assertEquals(original, device.snapshot)
        assertNull(store.saved)
    }

    private class Store : OplusPowerStore {
        var saved: OplusPowerBackup? = null
        var failSave = false
        var failAppliedSave = false
        override fun load() = saved
        override fun save(backup: OplusPowerBackup) {
            if (failSave || (failAppliedSave && backup.appliedAt != null)) throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
            saved = backup
        }
        override fun clear() { saved = null }
    }

    private class Device(var snapshot: OplusPowerSnapshot) : OplusPowerDevice {
        val writes = mutableListOf<String>()
        var gateFailure: String? = null
        var broadcastFailure: String? = null
        var failAfterGateWrite = false
        override fun read() = snapshot
        override fun changeBroadcast(expected: String, target: String) {
            assertEquals(expected, snapshot.broadcast)
            broadcastFailure?.let { throw AssistantSetupFailure(it) }
            writes.add("broadcast:$target")
            snapshot = snapshot.copy(broadcast = target)
        }
        override fun changeGate(expected: String?, target: String?) {
            assertEquals(expected, snapshot.gate)
            if (!failAfterGateWrite) gateFailure?.let { throw AssistantSetupFailure(it) }
            writes.add("gate:$target")
            snapshot = snapshot.copy(gate = target)
            if (failAfterGateWrite) gateFailure?.let { throw AssistantSetupFailure(it) }
        }
    }

    private fun code(expected: String, body: () -> Unit) {
        try { body(); fail("Expected $expected") }
        catch (failure: AssistantSetupFailure) { assertEquals(expected, failure.code) }
    }
}
