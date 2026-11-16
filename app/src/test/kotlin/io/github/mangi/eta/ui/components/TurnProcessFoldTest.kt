package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnProcessFoldTest {
    private fun user(id: String) = UserMessageUi(id, "q $id")
    private fun answer(id: String) = AgentMessageUi(id, "a $id")
    private fun think(id: String, seconds: Int? = null) = ThinkingMessageUi(id, "t", isStreaming = false, elapsedSeconds = seconds)
    private fun tool(id: String, failed: Boolean = false) = ToolActivityMessageUi(
        id, "shell", if (failed) ToolActivityStatusUi.Failed else ToolActivityStatusUi.Success, "ls",
    )

    /** 一轮：思考、两步、说明、思考、一步失败、最终回答。 */
    private fun turn(n: Int): List<AgentChatMessageUi> = listOf(
        user("u$n"), think("t$n-1", 20), tool("s$n-1"), tool("s$n-2"), answer("note$n"),
        think("t$n-2", 114), tool("s$n-3", failed = true), answer("a$n"),
    )

    private fun fold(messages: List<AgentChatMessageUi>, running: Boolean = false, expanded: Set<String> = emptySet(),
                     previous: List<AgentTimelineEntry> = emptyList()) =
        foldFinishedTurns(messages.toTimelineEntries(), running, expanded, previous)

    @Test
    fun aFinishedTurnFoldsIntoOneSummaryBeforeTheAnswer() {
        val folded = fold(turn(1))
        assertEquals(listOf("u1", "turn-${foldKeyOf(turn(1))}", "a1"), folded.map { it.key })
        val summary = (folded[1] as AgentTimelineEntry.TurnProcess).summary
        assertEquals(TurnProcessSummary(thinking = 2, steps = 3, failed = 1, seconds = 134), summary)
    }

    @Test
    fun theRunningTurnStaysAsIs() {
        val messages = turn(1) + turn(2)
        val folded = fold(messages, running = true)
        val raw = messages.toTimelineEntries()
        // 第一轮已收起，最后一轮（进行中）原样保留。
        assertEquals(3, folded.indexOfFirst { it.key == "u2" })
        assertEquals(raw.dropWhile { it.key != "u2" }.map { it.key }, folded.drop(3).map { it.key })
    }

    @Test
    fun aTurnWithoutFinalAnswerIsNotFolded() {
        val stopped = listOf(user("u1"), think("t1"), tool("s1"))
        assertEquals(stopped.toTimelineEntries().map { it.key }, fold(stopped).map { it.key })
        val plain = listOf(user("u1"), answer("a1"))
        assertEquals(listOf("u1", "a1"), fold(plain).map { it.key })
    }

    @Test
    fun anExpandedTurnListsItsProcessAfterTheSummary() {
        val key = "turn-${foldKeyOf(turn(1))}"
        val folded = fold(turn(1), expanded = setOf(key))
        val raw = turn(1).toTimelineEntries()
        assertEquals(listOf("u1", key) + raw.drop(1).map { it.key }, folded.map { it.key })
        assertTrue((folded[1] as AgentTimelineEntry.TurnProcess).expanded)
    }

    @Test
    fun keysSurviveOlderHistoryBeingPrepended() {
        val recent = fold(turn(2))
        val withHistory = fold(turn(1) + turn(2))
        assertEquals(recent.map { it.key }, withHistory.takeLast(recent.size).map { it.key })
    }

    @Test
    fun unchangedTurnsReuseTheSameInstance() {
        val first = fold(turn(1) + turn(2))
        val again = fold(turn(1) + turn(2), previous = first)
        first.filterIsInstance<AgentTimelineEntry.TurnProcess>().zip(again.filterIsInstance<AgentTimelineEntry.TurnProcess>())
            .forEach { (a, b) -> assertSame(a, b) }
    }

    private fun foldKeyOf(messages: List<AgentChatMessageUi>): String = messages.toTimelineEntries()[1].key
}
