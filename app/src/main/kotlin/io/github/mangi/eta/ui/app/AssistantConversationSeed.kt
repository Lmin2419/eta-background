package io.github.mangi.eta.ui.app

import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.UserMessageUi

/** The prompt belongs to both the visible conversation and the durable model history. */
internal object AssistantConversationSeed {
    fun apply(state: AgentChatHomeUiState, runId: String, prompt: String): AgentChatHomeUiState {
        if (AgentRuntimeHistoryReducer.wasApplied(state, runId) || state.messages.any { it.id == "user-$runId" }) return state
        val user = AgentModelClient.buildUserHistoryMessage(prompt, emptyList()).copy(messageId = "user-$runId")
        return state.copy(
            messages = state.messages + UserMessageUi(id = "user-$runId", content = prompt),
            history = state.history + user,
            journal = state.journal.ifEmpty { state.history } + user,
        )
    }
}
