package io.github.mangi.eta.agent.tool

import android.content.Context
import io.github.mangi.eta.agent.device.BoundedRootCommandExecutor
import io.github.mangi.eta.agent.device.DeviceToolContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceStateMutationsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun settingReadbackUsesExplicitUserAndPreservesValueWhitespace() {
        val commands = mutableListOf<String>()
        val outputs = ArrayDeque(listOf("old\n", "", " new \n"))
        val mutations = DeviceStateMutations(context) {
            commands += it
            output(outputs.removeFirst())
        }
        val result = JSONObject(mutations.setSetting(JSONObject()
            .put("namespace", "system").put("key", "eta_test").put("value", " new ")))
        assertTrue(result.getBoolean("verified"))
        assertTrue(result.getBoolean("changed"))
        assertEquals(" new ", result.getString("after"))
        assertTrue(commands.all { it.contains("--user ${DeviceToolContract.appUserId(context)} ") })
        assertTrue(commands.none { it.contains("--user current") })
    }

    @Test
    fun settingValueEndingInCarriageReturnSurvivesCommandReadback() {
        val outputs = ArrayDeque(listOf("old\n", "", "value\r\n"))
        val result = JSONObject(DeviceStateMutations(context) { output(outputs.removeFirst()) }
            .setSetting(JSONObject().put("namespace", "system").put("key", "eta_test").put("value", "value\r")))
        assertTrue(result.toString(), result.getBoolean("verified"))
        assertEquals("value\r", result.getString("after"))
    }

    @Test
    fun acceptedCommandWithUnchangedValueIsUnconfirmed() {
        val outputs = ArrayDeque(listOf("old\n", "", "old\n"))
        val result = JSONObject(DeviceStateMutations(context) { output(outputs.removeFirst()) }
            .setSetting(JSONObject().put("namespace", "secure").put("key", "eta_test").put("value", "new")))
        assertFalse(result.getBoolean("ok"))
        assertFalse(result.getBoolean("verified"))
        assertFalse(result.getBoolean("changed"))
        assertTrue(result.getBoolean("command_accepted"))
        assertEquals("STATE_CHANGE_UNCONFIRMED", result.getString("code"))
    }

    @Test
    fun unavailableReadbackDoesNotInventNoChange() {
        var call = 0
        val result = JSONObject(DeviceStateMutations(context) {
            if (++call == 2) output("") else BoundedRootCommandExecutor.Result.failed("ROOT_COMMAND_FAILED")
        }.setSetting(JSONObject().put("namespace", "secure").put("key", "eta_test").put("value", "new")))
        assertFalse(result.getBoolean("verified"))
        assertTrue(result.isNull("before"))
        assertTrue(result.isNull("after"))
        assertTrue(result.isNull("changed"))
    }

    @Test
    fun otherAndroidUserIsRejectedBeforeMutationOrQuery() {
        val mutations = DeviceStateMutations(context) { error("不应执行跨用户命令") }
        val args = JSONObject().put("user_id", DeviceToolContract.appUserId(context) + 1)
        listOf(mutations.setSetting(args), mutations.appStateControl(args), mutations.setDeviceState(args), mutations.setVolume(args))
            .forEach { assertEquals("USER_SCOPE_UNSUPPORTED", JSONObject(it).getString("code")) }
    }

    @Test
    fun bluetoothTransitionDoesNotPretendToBeAStableState() {
        assertEquals(true, DeviceStateMutations.bluetoothState("Bluetooth Status\n state: ON\n"))
        assertEquals(false, DeviceStateMutations.bluetoothState("Bluetooth Status\n state: OFF\n"))
        assertNull(DeviceStateMutations.bluetoothState("Bluetooth Status\n enabled: true\n state: TURNING_ON\n"))
        assertNull(DeviceStateMutations.bluetoothState("Permission denied"))
    }

    @Test
    fun alreadySatisfiedTargetIsVerifiedWithoutClaimingChange() {
        val result = DeviceMutationResult.observed("test", 0, "app_user", true, true, true, true)
        assertTrue(result.getBoolean("ok"))
        assertTrue(result.getBoolean("verified"))
        assertFalse(result.getBoolean("changed"))
    }

    private fun output(text: String) = BoundedRootCommandExecutor.Result(0, text, "", false, false)
}
