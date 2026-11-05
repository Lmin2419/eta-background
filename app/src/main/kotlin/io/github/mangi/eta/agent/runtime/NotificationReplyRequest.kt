package io.github.mangi.eta.agent.runtime

import android.content.Context
import androidx.room.withTransaction
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.app.AgentConversationStore
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.model.ReasoningEffort
import java.util.UUID

/** Reuse durable conversation history without constructing an Activity or a UI state holder. */
internal object NotificationReplyRequest {
    suspend fun load(context: Context, sourceRunId: String, conversationId: String, text: String,
        resolveConfig: suspend (String?) -> AgentModelClient.ModelConfig? = RuntimeConfigRepository::runtimeConfigFor,
    ): AgentRuntimeWire.RunRequest {
        require(text.isNotBlank() && text.length <= 16_000) { "EMPTY_OR_LONG_REPLY" }
        val database = EtaDatabase.get(context.applicationContext)
        // Read the saved conversation and pending results together, so an ACK cannot
        // remove a result after we read history but before we check the outbox.
        val (stored, completed) = database.withTransaction {
            val conversation = AgentConversationStore.loadConversation(context, conversationId)
            val seenRunIds = conversation?.appliedRuntimeRunIds.orEmpty().toMutableSet()
            val dao = database.runtimeRunDao()
            // Pending headers come first, so an already-loaded result wins over its archive.
            val turns = dao.notificationReplyHeaders().mapNotNull { header ->
                val runId = header.runId.ifBlank { header.rowId }
                if (runId in seenRunIds) return@mapNotNull null
                val handoff = AgentRuntimeWire.EntryHandoff(header.handoffId, header.handoffSource, header.handoffPayload)
                if (AgentConversationIdentity.fromHandoff(handoff, runId) != conversationId) return@mapNotNull null
                val turn = if (header.archived) AgentRunArchiveStore.readReplyRun(dao, header.rowId)
                else AgentRuntimeResultStore.readOwned(dao, header.rowId, header.handoffPayload)
                turn?.also { seenRunIds.add(runId) }
            }
            conversation to turns.sortedBy { it.createdAt }
        }
        require(sourceRunId in stored?.appliedRuntimeRunIds.orEmpty() || completed.any { it.result.runId == sourceRunId }) {
            "REPLY_CONTEXT_MISSING"
        }
        require(stored?.roleplay == null) { "REPLY_ROLEPLAY_UNAVAILABLE" }
        var history = stored?.history.orEmpty()
        for (turn in completed) {
            val result = turn.result
            require(result.operation == AgentRuntimeWire.OP_CHAT) { "REPLY_CONTEXT_UNAVAILABLE" }
            val snapshot = result.contextSnapshot
            if (snapshot != null) history = snapshot.messages
            else {
                val external = AgentExternalArchivePayload.from(turn.handoff.payload)
                val supplement = if (external == null) AgentUiHandoffPayload.from(turn.handoff.payload).promptSupplement else null
                val prompt = external?.userText ?: supplement?.text
                val messageId = if (supplement != null) "user-${result.runId}-supplement-${supplement.index}" else "user-${result.runId}"
                if (prompt != null && history.none { it.messageId == messageId }) {
                    history = history + AgentModelClient.buildUserHistoryMessage(prompt, emptyList()).copy(messageId = messageId)
                }
                // Ordinary UI turns persist the user prompt before execution.
                require(history.isNotEmpty()) { "REPLY_CONTEXT_MISSING" }
                history = history + result.transcript
            }
        }
        require(history.isNotEmpty()) { "REPLY_CONTEXT_MISSING" }
        val config = resolveConfig(stored?.modelId)
            ?: throw IllegalStateException("REPLY_MODEL_UNAVAILABLE")
        val runId = "run-${UUID.randomUUID()}"
        val externalTurn = completed.lastOrNull()?.takeIf {
            AgentExternalArchivePayload.from(it.handoff.payload) != null
        }
        val handoff = if (stored == null && externalTurn != null) {
            val payload = requireNotNull(AgentExternalArchivePayload.from(externalTurn.handoff.payload))
            externalTurn.handoff.copy(id = runId, payload = payload.copy(userText = text).toJson())
        } else AgentRuntimeWire.EntryHandoff(runId, AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE,
            AgentUiHandoffPayload(conversationId,
                promptSupplement = AgentUiHandoffPayload.Supplement(1, text, System.currentTimeMillis())).toJson())
        val effort = if (Prefs.isEnabled(Prefs.Keys.AGENT_THINKING_ENABLED)) stored?.reasoningEffort ?: config.effectiveReasoningEffort
            else ReasoningEffort.OFF
        return AgentRuntimeWire.RunRequest(runId, text, config.copy(supportsSearchSuggestions = true,
            terminalTools = Prefs.isEnabled(Prefs.Keys.AGENT_TERMINAL_TOOLS),
            browserTools = Prefs.isEnabled(Prefs.Keys.AGENT_BROWSER_TOOLS),
            deviceDirectTools = Prefs.isEnabled(Prefs.Keys.AGENT_DEVICE_DIRECT_TOOLS),
            deviceSensitiveReadTools = Prefs.isEnabled(Prefs.Keys.AGENT_DEVICE_SENSITIVE_READ_TOOLS),
            deviceSensitiveActionTools = Prefs.isEnabled(Prefs.Keys.AGENT_DEVICE_SENSITIVE_ACTION_TOOLS),
            thinkingEnabled = effort.enablesReasoning, reasoningEffort = effort),
            images = emptyList(), history = history, handoff = handoff, modelSessionId = conversationId)
    }
}
