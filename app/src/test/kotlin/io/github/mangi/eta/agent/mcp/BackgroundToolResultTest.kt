package io.github.mangi.eta.agent.mcp

import io.github.mangi.eta.agent.model.AgentTraceFormatter
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackgroundToolResultTest {
    @Test fun feedbackImagesReachTheModelWithoutBecomingCoordinatesOrRawHistoryText() {
        val raw = """{"ok":true,"verified":false,"code":"ACTION_OUTCOME_UNKNOWN","action_feedback":{"texts":[{"text":"校验失败"}]},"_feedback_images":[{"image":"AA==","width":540,"height":960}]}"""
        val result = backgroundToolResult("screen_tap", raw)
        val content = JSONObject(result.content)
        assertEquals("ACTION_OUTCOME_UNKNOWN", content.getString("code"))
        assertFalse(content.getBoolean("verified"))
        assertFalse(content.has("_feedback_images"))
        assertFalse(content.has("observation_id"))
        assertEquals("action_feedback", result.images.single().source)
        assertTrue(result.sensitive)
    }

    @Test
    fun engineAndObserverFailureHintsCannotAppearAsSuccessfulCompletion() {
        for (hint in listOf("后台虚拟屏引擎正在启动中，请过几秒重试该操作。", "屏幕正在回收", "无障碍尚未连接")) {
            val result = backgroundToolResult("screen_open", hint)
            assertFalse(AgentTraceFormatter().isSuccessResult(result))
            assertEquals(hint, JSONObject(result.content).getString("message"))
        }
    }

    @Test
    fun structuredEvidenceAndSpecificEngineCodesArePreserved() {
        val result = backgroundToolResult("screen_swipe", """{"ok":true,"accepted":true,"verified":false,"outcome":"UNKNOWN","code":"ACTION_OUTCOME_UNKNOWN"}""")
        assertTrue(JSONObject(result.content).getBoolean("accepted"))
        assertEquals("操作已提交，效果未确认", AgentTraceFormatter().summarizeResult("screen_swipe", result))
        assertEquals("INPUT_NOT_READY", JSONObject(backgroundToolResult("screen_tap", """{"ok":false,"code":"INPUT_NOT_READY"}""").content).getString("code"))
        assertEquals("GUI_TOOL_REQUIRED", JSONObject(backgroundToolResult("terminal", "请使用 GUI 工具").content).getString("code"))
    }
}
