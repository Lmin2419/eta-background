package io.github.mangi.eta.agent.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 10 号方案第二期：失败结果补上 retryable / hint，重复失败提醒。 */
class ToolFailureAnnotatorTest {
    private fun call(name: String, args: String) = AgentModelClient.ToolCall("id", name, args)
    private fun result(content: String) = AgentModelClient.ToolResult(content)

    @Test fun failuresGetHintAndRetryable() {
        val annotator = ToolFailureAnnotator()
        val out = JSONObject(annotator.annotate(call("read_file", """{"path":"/a"}"""),
            result("""{"ok":false,"code":"FILE_NOT_FOUND","message":"no such file"}""")).content)
        assertTrue(out.getString("hint").contains("父目录"))
        assertFalse(out.getBoolean("retryable"))
        val terminal = JSONObject(annotator.annotate(call("terminal", """{"command":"pyhton x.py"}"""),
            result("""{"exit_code":127,"stderr":"pyhton: not found"}""")).content)
        assertTrue(terminal.getString("hint").contains("命令不存在"))
        val crash = JSONObject(annotator.annotate(call("x", "{}"), result("""{"ok":false,"code":"TOOL_ERROR","message":"IOException: closed"}""")).content)
        assertTrue(crash.getBoolean("retryable"))
    }

    @Test fun successesAndToolProvidedHintsAreLeftAlone() {
        val annotator = ToolFailureAnnotator()
        val ok = result("""{"ok":true,"content":"hi"}""")
        assertSame(ok, annotator.annotate(call("read_file", "{}"), ok))
        val plain = result("not json")
        assertSame(plain, annotator.annotate(call("web_search", "{}"), plain))
        val own = JSONObject(annotator.annotate(call("y", "{}"), result("""{"ok":false,"code":"FILE_NOT_FOUND","hint":"工具自己的提示","retryable":true}""")).content)
        assertEquals("工具自己的提示", own.getString("hint")); assertTrue(own.getBoolean("retryable"))
    }

    @Test fun secondIdenticalFailureIsFlaggedButDifferentArgumentsAreNot() {
        val annotator = ToolFailureAnnotator()
        val fail = result("""{"ok":false,"code":"COMMAND_TIMEOUT"}""")
        assertFalse(JSONObject(annotator.annotate(call("terminal", """{"a":1,"b":2}"""), fail).content).has("repeat_failures"))
        // 键顺序不同算同一个调用。
        val second = JSONObject(annotator.annotate(call("terminal", """{"b":2,"a":1}"""), fail).content)
        assertEquals(2, second.getInt("repeat_failures"))
        assertTrue(second.getString("hint").startsWith(ToolFailureAnnotator.REPEAT_HINT))
        assertFalse(JSONObject(annotator.annotate(call("terminal", """{"a":1,"b":3}"""), fail).content).has("repeat_failures"))
    }

    @Test fun canonicalArgumentsSortNestedKeys() {
        assertEquals(ToolFailureAnnotator.canonicalArguments("""{"x":{"b":1,"a":[1,{"d":2,"c":3}]}}"""),
            ToolFailureAnnotator.canonicalArguments("""{"x":{"a":[1,{"c":3,"d":2}],"b":1}}"""))
    }
}
