package io.github.mangi.eta.agent.runtime

import org.junit.Assert.*
import org.junit.Test

class AssistantRunRecoveryTest {
    @Test fun assistantRunIsRecoverableBeforeItHasAFinalArchive() {
        val source = AgentRuntimeWire.EntryHandoff("wake:run", AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE,
            AgentExternalArchivePayload("查一下天气", "wake", "天气").toJson())
        val projected = AssistantRunRecovery.project(source)
        val checkpoint = AgentRunCheckpointStore.Checkpoint("run", "process", projected, emptyList(), 1, 1)
        val plan = io.github.mangi.eta.ui.app.AgentRunRecoveryCoordinator.plan(
            listOf(checkpoint), emptyList(), true, true, setOf("run"), emptySet(),
        )
        assertEquals("run", plan.reattach.single().runId)
        assertEquals(AgentConversationIdentity.fromHandoff(source, "run"),
            AgentUiHandoffPayload.from(projected.payload).conversationId)
        assertEquals("查一下天气", AssistantRunRecovery.payload(source)?.userText)
        assertEquals("wake:run", projected.id)
    }

    @Test fun otherExternalAdaptersAndOrdinaryChatKeepTheirProtocol() {
        val other = AgentRuntimeWire.EntryHandoff("id", "third-party", "opaque")
        val ui = AgentRuntimeWire.EntryHandoff("id", AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE, "chat")
        assertEquals(other, AssistantRunRecovery.project(other))
        assertEquals(ui, AssistantRunRecovery.project(ui))
    }
}
