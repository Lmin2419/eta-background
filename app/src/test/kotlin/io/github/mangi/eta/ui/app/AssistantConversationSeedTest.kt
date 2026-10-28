package io.github.mangi.eta.ui.app

import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.ui.model.AgentChatUiState
import io.github.mangi.eta.ui.model.UserMessageUi
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantConversationSeedTest {
    @Test fun livePromptIsDurableAndNotDuplicatedByRepeatedRecovery() {
        val initial = AgentChatUiState(emptyList(), input = "", isStreaming = false, thinkingEnabled = false)
        val live = AssistantConversationSeed.apply(initial, "voice-1", "查询天气")
        assertEquals(listOf(UserMessageUi("user-voice-1", "查询天气")), live.messages)
        assertEquals("查询天气", live.history.single().content)
        assertEquals(live.history, live.journal)
        assertEquals(live, AssistantConversationSeed.apply(live, "voice-1", "查询天气"))
        val completed = AgentRuntimeHistoryReducer.apply(live, "voice-1",
            listOf(AgentModelClient.ConversationMessage("assistant", "晴天"))).state
        assertEquals(listOf("查询天气", "晴天"), completed.history.map { it.content })
        assertEquals(completed, AssistantConversationSeed.apply(completed, "voice-1", "查询天气"))
    }

    @Test fun seedingNextPromptPreservesUnsummarizedJournal() {
        val original = AgentModelClient.ConversationMessage("user", "旧指令")
        val summary = AgentModelClient.ConversationMessage("assistant", "摘要", contextSummary = true)
        val initial = AgentChatUiState(emptyList(), history = listOf(summary), journal = listOf(original),
            input = "", isStreaming = false, thinkingEnabled = false)
        val next = AssistantConversationSeed.apply(initial, "voice-2", "新指令")
        assertEquals(listOf("摘要", "新指令"), next.history.map { it.content })
        assertEquals(listOf("旧指令", "新指令"), next.journal.map { it.content })
    }
}
