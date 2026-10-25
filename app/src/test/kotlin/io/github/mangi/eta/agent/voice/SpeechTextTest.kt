package io.github.mangi.eta.agent.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechTextTest {
    @Test fun spokenTextOmitsCodeAndKeepsLinkLabelAndInlineIdentifiers() {
        val text = SpeechText.readable("""
            # 操作说明

            请查看 **设置** 与 [帮助](https://example.invalid)，参数是 `user_name`。

            ```kotlin
            secretCommand()
            ```

                anotherSecretCommand()

            完成。
        """.trimIndent())
        assertTrue(text.contains("操作说明"))
        assertTrue(text.contains("设置"))
        assertTrue(text.contains("帮助"))
        assertTrue(text.contains("user_name"))
        assertFalse(text.contains("https://"))
        assertFalse(text.contains("secretCommand"))
        assertFalse(text.contains("anotherSecretCommand"))
        assertFalse(text.contains("**"))
        assertTrue(text.endsWith("完成。"))
    }

    @Test fun sentenceChunkingPreservesContentAndSurrogatePairs() {
        val text = "第一句话。第二句话。" + "你好😀".repeat(30)
        val chunks = SpeechText.chunks(text, 17)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 17 && !it.last().isHighSurrogate() && !it.first().isLowSurrogate() })
    }

    @Test fun codeOnlyAnswerHasNothingToRead() {
        assertEquals("", SpeechText.readable("```\nhello\n```"))
    }
}
