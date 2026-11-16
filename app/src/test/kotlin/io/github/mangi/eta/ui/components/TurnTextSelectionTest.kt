package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「选择文字」取整轮的思考与回答：可以跨思考选段（手机对话、电脑会话共用）。 */
class TurnTextSelectionTest {
    private val messages = listOf(
        UserMessageUi("u1", "第一问"),
        AgentMessageUi("a0", "上一轮的回答"),
        UserMessageUi("u2", "第二问"),
        ThinkingMessageUi("t1", "先想一想。", isStreaming = false),
        ToolActivityMessageUi("tool", "read_file", ToolActivityStatusUi.Success, "README.md"),
        AgentMessageUi("a1", "中间说明。"),
        ThinkingMessageUi("t2", "  再想一想。 ", isStreaming = false),
        AgentMessageUi("a2", "最终回答。"),
        UserMessageUi("u3", "第三问"),
    )

    @Test fun theWholeTurnIsSelectableAcrossThinking() {
        assertEquals(listOf(
            TurnSegment("先想一想。", thinking = true),
            TurnSegment("中间说明。", thinking = false),
            TurnSegment("再想一想。", thinking = true),
            TurnSegment("最终回答。", thinking = false),
        ), turnSegments(messages, "a2"))
        assertEquals("any answer in the turn gives the same segments", turnSegments(messages, "a2"), turnSegments(messages, "a1"))
    }

    @Test fun otherTurnsAndToolStepsAreLeftOut() {
        assertEquals(listOf(TurnSegment("上一轮的回答", thinking = false)), turnSegments(messages, "a0"))
        assertEquals(emptyList<TurnSegment>(), turnSegments(messages, "missing"))
    }

    @Test fun oneTapCopyJoinsParagraphs() {
        val segments = turnSegments(messages, "a2")
        assertEquals("先想一想。\n\n中间说明。\n\n再想一想。\n\n最终回答。", turnPlainText(segments, includeThinking = true))
        assertEquals("中间说明。\n\n最终回答。", turnPlainText(segments, includeThinking = false))
    }

    @Test fun theRecheckButtonNeedsToolUseInTheTurn() {
        // 第二轮用过工具（read_file 成功）；第一轮是纯聊天。
        assertEquals(true to false, turnToolSignal(messages, "a2"))
        assertEquals(false to false, turnToolSignal(messages, "a0"))
        val failed = messages.map { if (it is ToolActivityMessageUi) it.copy(status = ToolActivityStatusUi.Failed) else it }
        assertEquals(true to true, turnToolSignal(failed, "a2"))
        val action = { used: Boolean, lastFailed: Boolean -> RecheckAction("a2", true, {}, used, lastFailed) }
        assertEquals(false, action(false, false).offers(answerGaveUp = true))   // 纯聊天里说「失败」不出现
        assertEquals(true, action(true, true).offers(answerGaveUp = false))     // 最后一步失败时没说也出现
        assertEquals(false, action(true, false).offers(answerGaveUp = false))
        assertEquals(true, action(true, false).offers(answerGaveUp = true))
    }
}
