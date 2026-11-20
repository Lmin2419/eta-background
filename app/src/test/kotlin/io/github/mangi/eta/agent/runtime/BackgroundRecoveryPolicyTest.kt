package io.github.mangi.eta.agent.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackgroundRecoveryPolicyTest {
    @Test fun recoveryDistinguishesTakeoverReleaseLostSessionAndAppRestartConsent() {
        for ((code, action) in mapOf("APP_TAKEN_OVER" to "RECLAIM_TO_BACKGROUND", "CLEANUP_PENDING" to "CHECK_STATE",
            "DISPLAY_NOT_FOUND" to "REPORT_STATE_LOSS", "NEED_FORCE_STOP" to "ASK_USER", "UI_NOT_QUIET" to "OBSERVE_TARGET_OR_WAIT_TEXT")) {
            val result = JSONObject(BackgroundRecoveryPolicy.decorate("screen_open", """{"ok":false,"code":"$code","operationMayHaveRun":true}""")!!)
            assertEquals(action, result.getJSONObject("recovery").getString("next_action"))
            assertTrue(result.getBoolean("operationMayHaveRun"))
            assertFalse(result.getJSONObject("recovery").getBoolean("automatic_force_stop"))
        }
    }

    /** 2026111625：模型读到「已被接管」时用户已点继续，提示改成用 launch_app 接回后台屏，而不是「不要拉回」。 */
    @Test fun takeoverAdviceIsToReclaimTheAppNotToWait() {
        val result = JSONObject(BackgroundRecoveryPolicy.decorate("screen_tap",
            """{"ok":false,"code":"APP_TAKEN_OVER","message":"不要自动重新打开或拉回应用"}""")!!)
        assertTrue(result.getString("message").contains("launch_app"))
        assertFalse(result.getString("message").contains("不要自动重新打开"))
        assertFalse(result.getJSONObject("recovery").getBoolean("automatic_force_stop"))
    }

    @Test fun missingObserverIsNotMisreportedAsLostDisplayAndUnknownTimeoutIsNotReplayPermission() {
        val observer = JSONObject(BackgroundRecoveryPolicy.decorate("screen_open", """{"ok":true,"inputReady":false,"inputState":{"reason":"OBSERVER_UNAVAILABLE"},"displayId":6}""")!!)
        assertEquals(6, observer.getInt("displayId"))
        assertEquals("RESTORE_OBSERVER", observer.getJSONObject("recovery").getString("next_action"))
        val unknown = """{"ok":false,"code":"TIMEOUT","operationMayHaveRun":true}"""
        assertEquals(unknown, BackgroundRecoveryPolicy.decorate("screen_tap", unknown))
    }
}
