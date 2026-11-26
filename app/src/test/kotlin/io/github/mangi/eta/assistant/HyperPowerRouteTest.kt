package io.github.mangi.eta.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HyperPowerRouteTest {
    private var time = AssistantTick(3, 50)
    private val store = object : HyperPowerStore {
        var backup: HyperPowerBackup? = null
        override fun load() = backup
        override fun save(backup: HyperPowerBackup) { this.backup = backup }
        override fun clear() { backup = null }
    }
    private val device = object : HyperPowerDevice {
        var snapshot = HyperPowerSnapshot(0, "launch_voice_assistant")
        var writes = 0
        var failWith: String? = null
        override fun read() = snapshot
        override fun changeAction(expected: String?, target: String?) {
            assertEquals(expected, snapshot.action)
            failWith?.let { failWith = null; throw AssistantSetupFailure(it) }
            writes++
            snapshot = snapshot.copy(action = target)
        }
    }
    private val route = HyperPowerRoute(device, store, "Xiaomi|17 Max|x") { time }

    @Test fun enableSwitchesLongPressToTheDefaultAssistantAndRestoreGoesBack() {
        route.enable()
        assertEquals(HyperPowerContract.ASSISTANT_ACTION, device.snapshot.action)
        assertTrue(route.configured(device.snapshot))
        assertEquals("launch_voice_assistant", store.backup!!.original.action)
        assertEquals("Xiaomi|17 Max|x", store.backup!!.device)
        route.restore()
        assertEquals("launch_voice_assistant", device.snapshot.action)
        assertNull(store.backup)
    }

    @Test fun anAbsentSettingIsRestoredAsAbsent() {
        device.snapshot = HyperPowerSnapshot(0, null)
        route.enable()
        route.restore()
        assertNull(device.snapshot.action)
    }

    @Test fun systemRevertIsDetectedAndStillRestorable() {
        route.enable()
        // HyperOS 重启后把长按改回唤醒小爱。
        device.snapshot = device.snapshot.copy(action = "launch_voice_assistant")
        assertTrue(route.reverted(device.snapshot))
        assertFalse(route.configured(device.snapshot))
        route.restore()
        assertNull(store.backup)
        assertEquals("launch_voice_assistant", device.snapshot.action)
    }

    @Test fun secondEnableKeepsTheFirstBackup() {
        route.enable()
        val saved = store.backup
        expect("RESTORE_FIRST") { route.enable() }
        assertEquals(saved, store.backup)
    }

    @Test fun aRejectedWriteRollsBackButAnUncertainOneKeepsTheJournal() {
        device.failWith = "CONFIGURATION_NOT_APPLIED"
        expect("CONFIGURATION_NOT_APPLIED") { route.enable() }
        assertNull(store.backup)
        device.failWith = "COMMAND_TIMEOUT"
        expect("COMMAND_TIMEOUT") { route.enable() }
        assertTrue(store.backup!!.attempted)
    }

    @Test fun vendorDetectionAndChoice() {
        val none: (String) -> String = { "" }
        assertEquals(PowerKeyAdapter.HYPEROS, PowerKeyVendor.detect("Xiaomi", "Xiaomi", none))
        assertEquals(PowerKeyAdapter.HYPEROS, PowerKeyVendor.detect("Xiaomi", "Redmi", none))
        assertEquals(PowerKeyAdapter.HYPEROS, PowerKeyVendor.detect("unknown", "unknown") { if (it == "ro.mi.os.version.name") "OS4.0" else "" })
        assertEquals(PowerKeyAdapter.COLOROS, PowerKeyVendor.detect("OnePlus", "OnePlus", none))
        assertEquals(PowerKeyAdapter.COLOROS, PowerKeyVendor.detect("x", "x") { if (it == "ro.build.version.oplusrom") "V16" else "" })
        assertEquals(PowerKeyAdapter.GENERIC, PowerKeyVendor.detect("Google", "google", none))
        assertEquals(PowerKeyAdapter.HYPEROS, PowerKeyVendor.resolve(PowerKeyAdapter.AUTO, PowerKeyAdapter.HYPEROS))
        assertEquals(PowerKeyAdapter.GENERIC, PowerKeyVendor.resolve(PowerKeyAdapter.GENERIC, PowerKeyAdapter.HYPEROS))
    }

    @Test fun colorOsBackupsFromAnotherPhoneAreRecognized() {
        val oneplus = "OnePlus/PMA110/OP5D0DL1:17/AP3A/1:user/release-keys"
        assertTrue(OplusPowerContract.sameDevice(oneplus, "OP5D0DL1", "PMA110"))
        assertFalse(OplusPowerContract.sameDevice(oneplus, "xuanyuan", "25113PN0EC"))
        assertFalse(OplusPowerContract.sameDevice("", "xuanyuan", "25113PN0EC"))
        assertTrue(OplusPowerContract.sameDevice("", "OP5D0DL1", "PMA110"))
    }

    @Test fun onlyPlainSettingValuesAreAccepted() {
        assertTrue(HyperPowerContract.validValue("launch_google_search"))
        assertTrue(HyperPowerContract.validValue(null))
        assertFalse(HyperPowerContract.validValue("a; rm -rf /"))
        assertFalse(HyperPowerContract.validValue("x".repeat(60)))
    }

    private fun expect(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") } catch (e: AssistantSetupFailure) { assertEquals(code, e.code) }
    }
}
