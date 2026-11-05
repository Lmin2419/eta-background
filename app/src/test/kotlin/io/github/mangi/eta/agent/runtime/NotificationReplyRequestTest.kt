package io.github.mangi.eta.agent.runtime

import android.content.Context
import androidx.room.withTransaction
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.AgentContextSnapshot
import io.github.mangi.eta.data.db.ChunkedTextDao
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.ui.app.AgentConversationStore
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    private val missingChunk = "${ChunkedTextDao.REFERENCE_PREFIX}1:4"

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

    @Test fun replySnapshotUsesPendingResultBeforeAckAndSavedHistoryAfterAck() = runBlocking {
        val database = EtaDatabase.get(context)
        val dao = database.runtimeRunDao()
        val conversationId = "legacy-ui-chat"
        val runId = "ui-ack-race"
        val alreadyAppliedId = "applied-before-ack"
        val handoff = AgentRuntimeWire.EntryHandoff(runId, AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE, conversationId)
        val result = AgentRuntimeWire.RunResult(runId, true, history.last().content,
            contextSnapshot = AgentContextSnapshot(operationId = runId, messages = history))
        AgentRuntimeResultStore.add(context, AgentRuntimeWire.CompletedRun(handoff, result, 1L))
        AgentRuntimeResultStore.add(context, AgentRuntimeWire.CompletedRun(
            handoff.copy(id = alreadyAppliedId), AgentRuntimeWire.RunResult(alreadyAppliedId, true, "已写入"), 0L,
        ))
        val appliedRow = dao.runtimeResultRows().single { it.runId == alreadyAppliedId }
        dao.upsertRuntimeResultRow(appliedRow.copy(transcriptJson = missingChunk))
        val state = AgentChatHomeUiState(messages = emptyList(), input = "", isStreaming = false,
            thinkingEnabled = false, history = history.take(1), appliedRuntimeRunIds = listOf(alreadyAppliedId))
        suspend fun saveConversation(saved: AgentChatHomeUiState) {
            AgentConversationStore.save(context, conversationId, mapOf(conversationId to saved),
                mapOf(conversationId to "测试"), mapOf(conversationId to 1L))
        }
        saveConversation(state)

        withTimeout(15_000) {
            val allowAck = CompletableDeferred<Unit>()
            val ackStarted = CompletableDeferred<Unit>()
            val acknowledgement = launch(Dispatchers.IO) {
                allowAck.await()
                ackStarted.complete(Unit)
                database.withTransaction {
                    saveConversation(state.copy(history = history, appliedRuntimeRunIds = listOf(alreadyAppliedId, runId)))
                    dao.acknowledgeRuntimeResult(runId)
                }
            }
            val beforeAck = database.withTransaction {
                allowAck.complete(Unit)
                ackStarted.await()
                NotificationReplyRequest.load(context, runId, conversationId, "继续") { config }
            }
            acknowledgement.join()
            val afterAck = NotificationReplyRequest.load(context, runId, conversationId, "继续") { config }

            assertEquals(history, beforeAck.history)
            assertEquals(history, afterAck.history)
            assertEquals(listOf(alreadyAppliedId), AgentRuntimeResultStore.pendingPage(context).map { it.result.runId })
        }
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

    @Test fun replyRestoresMatchingLegacyRunsWithoutReadingOtherConversationsOrArchiveEvents() = runBlocking {
        fun handoff(id: String, prompt: String, key: String = "same-conversation") = AgentRuntimeWire.EntryHandoff(
            id, AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE, AgentExternalArchivePayload(prompt, key, "归档").toJson(),
        )
        val older = handoff("older-archive", "第一轮")
        val latest = handoff("latest-pending", "第二轮")
        val other = handoff("other-run", "其他对话", "unrelated")
        val legacyAnswer = "较早的回答".repeat(4_000)
        AgentRunArchiveStore.add(context, AgentRunArchiveStore.ArchivedRun(
            older, listOf(AgentEvent.RunFinished(round = 1, contentChars = legacyAnswer.length)),
            AgentRuntimeWire.RunResult("", true, legacyAnswer), 2L,
        ))
        AgentRunArchiveStore.add(context, AgentRunArchiveStore.ArchivedRun(
            latest, emptyList(), AgentRuntimeWire.RunResult(latest.id, true, "重复的归档结果"), 1L,
        ))
        AgentRuntimeResultStore.add(context, AgentRuntimeWire.CompletedRun(
            latest, AgentRuntimeWire.RunResult(latest.id, true, "待确认结果"), 3L,
        ))
        AgentRuntimeResultStore.add(context, AgentRuntimeWire.CompletedRun(
            other, AgentRuntimeWire.RunResult(other.id, true, "其他结果"), 4L,
        ))
        AgentRunArchiveStore.add(context, AgentRunArchiveStore.ArchivedRun(
            other, emptyList(), AgentRuntimeWire.RunResult(other.id, true, "其他归档"), 4L,
        ))
        val dao = EtaDatabase.get(context).runtimeRunDao()
        val unrelatedPending = dao.runtimeResultRows().single { it.runId == other.id }
        dao.upsertRuntimeResultRow(unrelatedPending.copy(transcriptJson = missingChunk))
        val archives = dao.archivedRunRows()
        val unrelatedArchive = archives.single { it.run.archiveRunId == other.id }
        dao.upsertArchivedRunRow(unrelatedArchive.run.copy(transcriptJson = missingChunk))
        val duplicateArchive = archives.single { it.run.archiveRunId == latest.id }
        dao.upsertArchivedRunRow(duplicateArchive.run.copy(transcriptJson = missingChunk))
        val unusedEvent = archives.single { it.run.archiveRunId == older.id }.events.single()
        dao.insertArchivedEventRow(unusedEvent.copy(eventJson = missingChunk))

        val conversation = AgentConversationIdentity.fromHandoff(older, older.id)
        val request = NotificationReplyRequest.load(context, older.id, conversation, "继续") { config }

        assertEquals(listOf("第一轮", legacyAnswer, "第二轮", "待确认结果"), request.history.map { it.content })
        assertEquals(conversation, AgentConversationIdentity.fromHandoff(request.handoff, request.runId))
        assertEquals(listOf(latest.id, other.id), AgentRuntimeResultStore.pendingPage(context).map { it.result.runId })
    }

    @Test fun emptyReplyIsNotSubmitted() = runBlocking {
        assertEquals("EMPTY_OR_LONG_REPLY", runCatching {
            NotificationReplyRequest.load(context, "a", "a", " ") { config }
        }.exceptionOrNull()?.message)
    }
}
