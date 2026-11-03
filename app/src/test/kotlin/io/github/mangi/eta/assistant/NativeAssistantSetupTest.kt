package io.github.mangi.eta.assistant

import org.junit.Assert.*
import org.junit.Test

class NativeAssistantSetupTest {
    private val eta = "io.github.mangi.etax"
    private val voice = "$eta/io.github.mangi.eta.agent.voice.EtaVoiceInteractionService"
    private val recognizer = "$eta/io.github.mangi.eta.agent.voice.EtaRecognitionService"
    private val original = AssistantSnapshot(0, listOf("com.vendor.assistant"), mapOf(
        AssistantSetting.ASSISTANT to "com.vendor.assistant/.Voice",
        AssistantSetting.VOICE to "com.vendor.assistant/.Voice",
        AssistantSetting.RECOGNIZER to "com.other.recognizer/.Service",
        AssistantSetting.LONG_PRESS to null,
    ))
    private var time = AssistantTick(7, 100)
    private val store = MemoryStore()
    private val device = FakeDevice(original)
    private val setup = NativeAssistantSetup(eta, 0, device, store) { time }

    @Test fun configurationIsReversibleWithoutLosingAbsentOrEmptySettings() {
        device.snapshot = original.copy(settings = original.settings + (AssistantSetting.VOICE to ""))
        val before = device.snapshot
        setup.enable()
        assertTrue(setup.configured(device.snapshot))
        assertNull(store.backup!!.confirmedAt)
        assertEquals(original.settings[AssistantSetting.RECOGNIZER], device.snapshot.settings[AssistantSetting.RECOGNIZER])
        setup.restore()
        assertEquals(before, device.snapshot)
        assertNull(store.backup)
    }

    @Test fun changingRolePreservesThePreviousSpeechRecognizer() {
        device.roleSideEffects = true
        setup.enable()
        assertEquals(original.settings[AssistantSetting.RECOGNIZER], device.snapshot.settings[AssistantSetting.RECOGNIZER])
        setup.restore()
        assertEquals(original, device.snapshot)
    }

    @Test fun noOriginalRoleCanBeRestoredWithoutInventingAVendorPackage() {
        device.snapshot = original.copy(holders = emptyList())
        val before = device.snapshot
        setup.enable()
        setup.restore()
        assertEquals(before, device.snapshot)
        assertEquals(emptyList<String>(), device.rolesChanged.last())
    }

    @Test fun durableBackupFailurePreventsEveryDeviceWrite() {
        store.failSave = true
        expectCode("BACKUP_UNAVAILABLE") { setup.enable() }
        assertEquals(0, device.writes)
    }

    @Test fun secondEnableDoesNotOverwriteTheOriginalBackup() {
        setup.enable()
        val saved = store.backup
        val writes = device.writes
        expectCode("RESTORE_FIRST") { setup.enable() }
        assertEquals(saved, store.backup)
        assertEquals(writes, device.writes)
    }

    @Test fun knownConfigurationFailureRollsBack() {
        device.failNextSetting = "COMMAND_FAILED"
        expectCode("COMMAND_FAILED") { setup.enable() }
        assertEquals(original, device.snapshot)
        assertNull(store.backup)
    }

    @Test fun timeoutAfterRoleChangeIsNotAutomaticallyReplayedOrUndone() {
        device.roleFailureAfterApply = "COMMAND_TIMEOUT"
        expectCode("COMMAND_TIMEOUT") { setup.enable() }
        assertEquals(1, device.writes)
        assertNotNull(store.backup)
        assertTrue(store.backup!!.roleAttempted)
        device.roleFailureAfterApply = null
        setup.restore()
        assertEquals(original, device.snapshot)
    }

    @Test fun rootLossRetainsBackupForExplicitRestoration() {
        device.failNextSetting = "ROOT_REQUIRED"
        expectCode("ROOT_REQUIRED") { setup.enable() }
        assertNotNull(store.backup)
        setup.restore()
        assertEquals(original, device.snapshot)
    }

    @Test fun userChangeStopsBeforeMutation() {
        device.snapshot = original.copy(userId = 10)
        expectCode("USER_CHANGED") { setup.enable() }
        assertEquals(0, device.writes)
        assertNull(store.backup)
    }

    @Test fun switchingAndroidUserAfterSetupDoesNotRestoreGlobalSettings() {
        setup.enable()
        device.snapshot = device.snapshot.copy(userId = 10)
        val writes = device.writes
        expectCode("USER_CHANGED") { setup.restore() }
        assertEquals(writes, device.writes)
        assertNotNull(store.backup)
    }

    @Test fun externallySelectedAssistantIsNeverOverwrittenByRestore() {
        setup.enable()
        device.snapshot = device.snapshot.copy(holders = listOf("com.new.assistant"))
        val writes = device.writes
        expectCode("CONFIGURATION_CHANGED") { setup.restore() }
        assertEquals(writes, device.writes)
        assertNotNull(store.backup)
    }

    @Test fun externallyChangedPowerBehaviorStopsTheEntireUndo() {
        setup.enable()
        device.snapshot = device.snapshot.copy(settings = device.snapshot.settings + (AssistantSetting.LONG_PRESS to "1"))
        val writes = device.writes
        expectCode("CONFIGURATION_CHANGED") { setup.restore() }
        assertEquals(writes, device.writes)
    }

    @Test fun pendingTransactionAfterProcessDeathCanRestore() {
        store.backup = AssistantBackup(original, roleAttempted = true, attempted = setOf(AssistantSetting.ASSISTANT))
        device.snapshot = original.copy(holders = listOf(eta), settings = original.settings + (AssistantSetting.ASSISTANT to voice))
        setup.restore()
        assertEquals(original, device.snapshot)
        assertNull(store.backup)
    }

    @Test fun partialRestoreCanResumeWithoutDiscardingBackup() {
        setup.enable()
        device.failNextSetting = "COMMAND_FAILED"
        expectCode("COMMAND_FAILED") { setup.restore() }
        assertNotNull(store.backup)
        setup.restore()
        assertEquals(original, device.snapshot)
    }

    @Test fun anAlreadyRestoredDeviceOnlyClearsTheBackup() {
        setup.enable()
        device.snapshot = original
        val writes = device.writes
        setup.restore()
        assertEquals(writes, device.writes)
        assertNull(store.backup)
    }

    @Test fun configurationAloneCannotConfirmAPowerKeyWake() {
        setup.enable()
        expectCode("WAKE_NOT_SEEN") { setup.confirmWake(null) }
        expectCode("WAKE_NOT_SEEN") { setup.confirmWake(AssistantTick(7, 99)) }
        expectCode("WAKE_NOT_SEEN") { setup.confirmWake(AssistantTick(6, 101)) }
        assertNull(store.backup!!.confirmedAt)
    }

    @Test fun confirmationRequiresFreshVisibleOverlayAndMatchingConfiguration() {
        setup.enable()
        time = AssistantTick(7, 200)
        setup.confirmWake(AssistantTick(7, 150))
        assertEquals(time, store.backup!!.confirmedAt)
        device.snapshot = original
        expectCode("CONFIGURATION_CHANGED") { setup.confirmWake(AssistantTick(7, 160)) }
    }

    @Test fun oldOrFutureOverlayCannotVerify() {
        setup.enable()
        time = AssistantTick(7, 400_000)
        expectCode("WAKE_NOT_SEEN") { setup.confirmWake(AssistantTick(7, 150)) }
        expectCode("WAKE_NOT_SEEN") { setup.confirmWake(AssistantTick(7, 500_000)) }
    }

    @Test fun persistedConfigurationCanBeConfirmedOnALaterBootWithoutReapplyingIt() {
        setup.enable()
        val writes = device.writes
        time = AssistantTick(8, 1000)
        setup.confirmWake(AssistantTick(8, 900))
        assertEquals(time, store.backup!!.confirmedAt)
        assertEquals(writes, device.writes)
    }

    @Test fun unknownBootIdentityCannotBeConfirmed() {
        time = AssistantTick(-1, 100)
        setup.enable()
        time = AssistantTick(-1, 200)
        expectCode("WAKE_NOT_SEEN") { setup.confirmWake(AssistantTick(-1, 150)) }
    }

    @Test fun roleSnapshotWithMultipleHoldersIsRejectedBeforeWrites() {
        device.snapshot = original.copy(holders = listOf("one.app", "two.app"))
        expectCode("UNSUPPORTED_ROLE_STATE") { setup.enable() }
        assertEquals(0, device.writes)
    }

    private fun expectCode(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") } catch (e: AssistantSetupFailure) { assertEquals(code, e.code) }
    }

    private class MemoryStore : AssistantBackupStore {
        var backup: AssistantBackup? = null
        var failSave = false
        override fun load() = backup
        override fun save(backup: AssistantBackup) {
            if (failSave) throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
            this.backup = backup
        }
        override fun clear() { backup = null }
    }

    private inner class FakeDevice(var snapshot: AssistantSnapshot) : AssistantSetupDevice {
        var writes = 0
        var roleSideEffects = false
        var failNextSetting: String? = null
        var roleFailureAfterApply: String? = null
        val rolesChanged = mutableListOf<List<String>>()
        override fun read() = snapshot.copy(settings = snapshot.settings.toMap())
        override fun changeRole(expected: List<String>, target: List<String>) {
            assertNotNull("backup must precede mutation", store.backup)
            assertEquals(expected, snapshot.holders)
            writes++
            rolesChanged.add(target)
            snapshot = snapshot.copy(holders = target)
            if (roleSideEffects) {
                snapshot = snapshot.copy(settings = if (target == listOf(eta)) snapshot.settings + mapOf(
                    AssistantSetting.ASSISTANT to voice, AssistantSetting.VOICE to voice,
                    AssistantSetting.RECOGNIZER to recognizer,
                ) else original.settings + (AssistantSetting.LONG_PRESS to snapshot.settings[AssistantSetting.LONG_PRESS]))
            }
            roleFailureAfterApply?.let { throw AssistantSetupFailure(it) }
        }
        override fun changeSetting(setting: AssistantSetting, expected: String?, target: String?) {
            assertNotNull("backup must precede mutation", store.backup)
            assertTrue(store.backup!!.roleAttempted || setting in store.backup!!.attempted)
            assertEquals(expected, snapshot.settings[setting])
            failNextSetting?.let { failNextSetting = null; throw AssistantSetupFailure(it) }
            writes++
            snapshot = snapshot.copy(settings = snapshot.settings + (setting to target))
        }
    }
}
