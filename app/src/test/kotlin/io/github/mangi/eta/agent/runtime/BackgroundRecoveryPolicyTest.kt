package io.github.mangi.eta.agent.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackgroundRecoveryPolicyTest {
    @Test fun recoveryDistinguishesTakeoverReleaseLostSessionAndAppRestartConsent() {
        for ((code, action) in mapOf("APP_TAKEN_OVER" to "WAIT_FOR_USER", "CLEANUP_PENDING" to "CHECK_STATE",
            "DISPLAY_NOT_FOUND" to "REPORT_STATE_LOSS", "NEED_FORCE_STOP" to "ASK_USER", "UI_NOT_QUIET" to "OBSERVE_TARGET_OR_WAIT_TEXT")) {
            val result = JSONObject(BackgroundRecoveryPolicy.decorate("screen_open", """{"ok":false,"code":"$code","operationMayHaveRun":true}""")!!)
            assertEquals(action, result.getJSONObject("recovery").getString("next_action"))
            assertTrue(result.getBoolean("operationMayHaveRun"))
            assertFalse(result.getJSONObject("recovery").getBoolean("automatic_force_stop"))
        }
    }

    @Test fun missingObserverIsNotMisreportedAsLostDisplayAndUnknownTimeoutIsNotReplayPermission() {
        val observer = JSONObject(BackgroundRecoveryPolicy.decorate("screen_open", """{"ok":true,"inputReady":false,"inputState":{"reason":"OBSERVER_UNAVAILABLE"},"displayId":6}""")!!)
        assertEquals(6, observer.getInt("displayId"))
        assertEquals("RESTORE_OBSERVER", observer.getJSONObject("recovery").getString("next_action"))
        val unknown = """{"ok":false,"code":"TIMEOUT","operationMayHaveRun":true}"""
        assertEquals(unknown, BackgroundRecoveryPolicy.decorate("screen_tap", unknown))
    }
}
