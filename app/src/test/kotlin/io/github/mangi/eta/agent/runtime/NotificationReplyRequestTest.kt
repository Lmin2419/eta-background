package io.github.mangi.eta.agent.runtime

import android.content.Context
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.AgentContextSnapshot
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.ui.app.AgentConversationStore
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationReplyRequestTest {
    private lateinit var context: Context
    private val config = AgentModelClient.ModelConfig(baseUrl = "https://example.invalid/v1", apiKey = "test", model = "test", systemPrompt = "")
    private val history = listOf(AgentModelClient.ConversationMessage("user", "帮我收菜"),
        AgentModelClient.ConversationMessage("assistant", "需要你决定收哪一块"))

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        EtaDatabase.closeForTests(); context.deleteDatabase("eta.db")
    }
    @After fun teardown() { EtaDatabase.closeForTests() }

    @Test fun coldAssistantReplyKeepsSameConversationAndNewUserText() = runBlocking {
        val handoff = AgentRuntimeWire.EntryHandoff("game", AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE,
            AgentExternalArchivePayload("帮我收菜", "original-game", "游戏").toJson())
        AgentRunArchiveStore.add(context, AgentRunArchiveStore.ArchivedRun(handoff, emptyList(),
            AgentRuntimeWire.RunResult("game", true, "需要你决定收哪一块",
                contextSnapshot = AgentContextSnapshot(operationId = "game", messages = history)), 1))
        val conversation = AgentConversationIdentity.fromHandoff(handoff, "game")
        val request = NotificationReplyRequest.load(context, "game", conversation, "收左边的") { config }
        assertEquals(conversation, AgentConversationIdentity.fromHandoff(request.handoff, request.runId))
        assertEquals(history, request.history)
        assertEquals("收左边的", AgentExternalArchivePayload.from(request.handoff!!.payload)?.userText)
        assertEquals("收左边的", request.prompt)
        assertNotEquals("game", request.runId)
    }

    @Test fun acknowledgedUiReplyUsesSavedHistoryAndRecoverablePrompt() = runBlocking {
        val state = AgentChatHomeUiState(messages = emptyList(), input = "", isStreaming = false,
            thinkingEnabled = false, history = history, appliedRuntimeRunIds = listOf("done"))
        AgentConversationStore.save(context, "chat", mapOf("chat" to state), mapOf("chat" to "测试"), mapOf("chat" to 1L))
        val request = NotificationReplyRequest.load(context, "done", "chat", "继续") { config }
        assertEquals(history, request.history)
        val payload = AgentUiHandoffPayload.from(request.handoff!!.payload)
        assertEquals("chat", payload.conversationId)
        assertEquals("继续", payload.promptSupplement?.text)
    }

    @Test fun missingContextNeverStartsAnUnrelatedEmptyConversation() = runBlocking {
        val failure = runCatching { NotificationReplyRequest.load(context, "missing", "other", "继续") { config } }.exceptionOrNull()
        assertTrue(failure.toString(), failure is IllegalArgumentException)
        assertEquals("REPLY_CONTEXT_MISSING", failure?.message)
    }

    @Test fun wrongConversationCannotUseAnotherTasksArchive() = runBlocking {
        val handoff = AgentRuntimeWire.EntryHandoff("a", AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE,
            AgentExternalArchivePayload("任务 A", "a", "A").toJson())
        AgentRunArchiveStore.add(context, AgentRunArchiveStore.ArchivedRun(handoff, emptyList(),
            AgentRuntimeWire.RunResult("a", true, "完成", contextSnapshot = AgentContextSnapshot(operationId = "a", messages = history)), 1))
        assertTrue(runCatching { NotificationReplyRequest.load(context, "a", "chat-b", "继续") { config } }.isFailure)
    }

    @Test fun emptyReplyIsNotSubmitted() = runBlocking {
        assertEquals("EMPTY_OR_LONG_REPLY", runCatching {
            NotificationReplyRequest.load(context, "a", "a", " ") { config }
        }.exceptionOrNull()?.message)
    }
}
