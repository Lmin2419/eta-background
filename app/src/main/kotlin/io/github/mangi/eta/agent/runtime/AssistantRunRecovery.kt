package io.github.mangi.eta.agent.runtime

/** Project assistant entries onto the existing UI recovery protocol without changing their identity. */
internal object AssistantRunRecovery {
    private val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = revision
    @Synchronized fun changed() { revision.value += 1 }

    fun payload(handoff: AgentRuntimeWire.EntryHandoff): AgentExternalArchivePayload? =
        handoff.takeIf { it.source == AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE }
            ?.let { AgentExternalArchivePayload.from(it.payload) }

    fun project(handoff: AgentRuntimeWire.EntryHandoff): AgentRuntimeWire.EntryHandoff {
        val external = payload(handoff) ?: return handoff
        return handoff.copy(
            source = AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE,
            payload = AgentUiHandoffPayload(
                AgentConversationIdentity.archiveConversationId(handoff.source, external.conversationKey),
            ).toJson(),
        )
    }
}
