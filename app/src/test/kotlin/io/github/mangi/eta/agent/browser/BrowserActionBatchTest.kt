package io.github.mangi.eta.agent.browser

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BrowserActionBatchTest {
    @Test fun validatesAllStepsBeforeAnyActionCanRun() {
        val args = JSONObject().put("actions", JSONArray()
            .put(JSONObject().put("action", "click").put("selector", "#pay"))
            .put(JSONObject().put("action", "batch")))
        assertThrows(JSONException::class.java) { BrowserActionBatch.parse(args) }
        assertThrows(JSONException::class.java) {
            BrowserActionBatch.parse(JSONObject().put("actions", JSONArray((1..9).map { JSONObject().put("action", "get_page_info") })))
        }
    }

    @Test fun stopAtUncertainSubmitAndNeverExecuteFollowingAction() {
        val steps = listOf("type", "click", "click").map { JSONObject().put("action", it) }
        var calls = 0
        val result = BrowserActionBatch.execute(steps, check = {}, action = {
            calls++
            BrowserToolResult(if (calls == 1) "{\"ok\":true}" else "{\"ok\":false,\"code\":\"ACTION_TIMEOUT\",\"operationMayHaveRun\":true}")
        })
        val json = JSONObject(result.content)
        assertEquals(2, calls)
        assertEquals(1, json.getInt("completed_count"))
        assertEquals(1, json.getInt("failed_index"))
        assertEquals(1, json.getInt("remaining_count"))
        assertTrue(json.getBoolean("operationMayHaveRun"))
        assertFalse(json.getBoolean("automatic_replay"))
    }

    @Test fun pendingDelayedFeedbackStopsBeforeNextInput() {
        val steps = listOf("type", "click", "get_readable").map { JSONObject().put("action", it) }
        var calls = 0
        val result = BrowserActionBatch.execute(steps, check = {}, action = {
            calls++
            BrowserToolResult(if (calls == 1) "{\"ok\":true}" else "{\"ok\":false,\"code\":\"PENDING_ACTION_FEEDBACK\",\"operationMayHaveRun\":false}")
        })
        assertEquals(2, calls)
        assertTrue(JSONObject(result.content).getBoolean("operationMayHaveRun"))
        assertFalse(JSONObject(result.content).getBoolean("failed_step_operation_may_have_run"))
        assertEquals("PENDING_ACTION_FEEDBACK", JSONObject(result.content).getString("code"))
    }

    @Test fun returnsFinalReadAndOnlyLastImageWithoutRepeatingEarlierBody() {
        var calls = 0
        val result = BrowserActionBatch.execute(listOf("screenshot", "screenshot", "get_readable").map { JSONObject().put("action", it) }, check = {}, action = {
            calls++
            BrowserToolResult("{\"ok\":true,\"text\":\"final-$calls\"}", if (calls < 3) listOf(BrowserImage("image-$calls", "image/png", 1, 1, 1)) else emptyList())
        })
        assertEquals("final-3", JSONObject(result.content).getString("text"))
        assertEquals(3, JSONObject(result.content).getInt("completed_count"))
        assertEquals("image-2", result.images.single().dataUrl)
        assertFalse(result.content.contains("final-1"))
    }

    @Test fun oversizedFeedbackCannotDiscardUncertainFailureIdentity() {
        val result = BrowserActionBatch.execute(listOf(JSONObject().put("action", "click")), check = {}, action = {
            BrowserToolResult(JSONObject().put("ok", false).put("code", "ACTION_TIMEOUT").put("operationMayHaveRun", true)
                .put("feedback", "x".repeat(30000)).toString())
        })
        val json = JSONObject(result.content)
        assertEquals(0, json.getInt("failed_index"))
        assertEquals(0, json.getInt("completed_count"))
        assertTrue(json.getBoolean("operationMayHaveRun"))
        assertTrue(result.content.toByteArray().size <= BrowserPayloadLimiter.MAX_BYTES)
    }
}
