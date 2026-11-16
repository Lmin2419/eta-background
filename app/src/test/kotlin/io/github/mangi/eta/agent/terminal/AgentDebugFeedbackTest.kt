package io.github.mangi.eta.agent.terminal

import io.github.mangi.eta.agent.model.AgentPromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** Agent 排错反馈：超长输出保留结尾、edit_file 给出相近原文、提示词含排错规则。 */
class AgentDebugFeedbackTest {
    @Test fun longOutputKeepsTheErrorAtTheEnd() {
        val log = (1..5000).joinToString("\n") { "compiling module $it" } + "\nerror: unexpected token at line 42"
        val shown = truncateKeepingTail(log, 16_000)
        assertTrue(shown.length < log.length)
        assertTrue(shown.endsWith("error: unexpected token at line 42"))
        assertTrue(shown.startsWith("compiling module 1"))
        assertTrue(shown.contains("省略中间"))
        assertEquals("short", truncateKeepingTail("short", 16_000))
    }

    @Test fun captureRetainsTailWhenOutputExceedsLimit() {
        val text = "x".repeat(200_000) + "\nTraceback: KeyError 'name'"
        val collector = ByteArrayOutputCollector()
        collector.readFrom(ByteArrayInputStream(text.toByteArray()), maxBytes = 64 * 1024, keepTailBytes = 8 * 1024)
        val captured = collector.text()
        assertTrue(collector.isTruncated())
        assertTrue(captured.endsWith("Traceback: KeyError 'name'"))
        assertTrue(captured.contains("省略中间"))
        assertTrue(captured.length < text.length)
    }

    @Test fun captureWithoutTailKeepsOldBehaviour() {
        val collector = ByteArrayOutputCollector()
        collector.readFrom(ByteArrayInputStream("abcdef".toByteArray()), maxBytes = 3)
        assertEquals("abc", collector.text())
    }

    @Test fun editHintPointsAtWhitespaceOnlyDifference() {
        val file = "fun main() {\n    val name = \"eta\"\n    println(name)\n}\n"
        val hint = editMismatchHint(file, "val name =  \"eta\"\nprintln(name)")
        assertTrue(hint, hint.contains("第 2 行"))
        assertTrue(hint, hint.contains("只差空格"))
    }

    @Test fun editHintPointsAtExtraEscaping() {
        val file = "print(\"hello\")\n"
        val hint = editMismatchHint(file, "print(\\\"hello\\\")")
        assertTrue(hint, hint.contains("第 1 行"))
        assertTrue(hint, hint.contains("转义"))
    }

    @Test fun editHintMentionsCrlf() {
        val file = "line one\r\nline two\r\n"
        val hint = editMismatchHint(file, "line one\nline  two")
        assertTrue(hint, hint.contains("CRLF"))
    }

    @Test fun editHintWhenNothingSimilar() {
        val hint = editMismatchHint("alpha\nbeta\n", "zzzzzzzzzz")
        assertTrue(hint, hint.contains("没有"))
        assertFalse(hint.contains("第 "))
    }

    @Test fun promptTellsTheAgentToFixSmallErrorsBeforeGivingUp() {
        val guidance = AgentPromptBuilder.CODING_GUIDANCE
        assertTrue(guidance.contains("write_file"))
        assertTrue(guidance.contains("至少针对具体报错修复三次"))
        assertTrue(guidance.contains("引号与转义"))
        assertTrue(guidance.contains("复核最后一条真实报错"))
    }
}
