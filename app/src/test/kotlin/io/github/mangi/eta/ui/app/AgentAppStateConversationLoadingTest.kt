package io.github.mangi.eta.ui.app

import android.content.Context
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.ConversationMessageEntity
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.ConversationHistoryLoadState
import io.github.mangi.eta.ui.model.UserMessageUi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class AgentAppStateConversationLoadingTest {
    private lateinit var context: Context
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication()).declareActionUnbindable(AgentRuntimeWire.ACTION_BIND)
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
    }

    @After
    fun tearDown() {
        scope.cancel()
        awaitCondition { job.isCompleted }
        EtaDatabase.closeForTests()
    }

    @Test
    fun fastConversationSwitchesKeepTheLastSelectionAndRestoreEveryBody() {
        seed("first" to conversation("first"), "second" to conversation("second"), "third" to conversation("third"))
        val state = AgentAppState(context, scope)

        state.selectConversation("second")
        state.selectConversation("third")
        awaitLoaded(state, "third")
        assertEquals(listOf("third-0", "third-1", "third-2"), state.homeState.messages.map { it.id })

        state.selectConversation("second")
        awaitLoaded(state, "second")
        assertEquals(listOf("second-0", "second-1", "second-2"), state.homeState.messages.map { it.id })
        state.selectConversation("first")
        awaitLoaded(state, "first")
        assertEquals(listOf("first-0", "first-1", "first-2"), state.homeState.messages.map { it.id })
        assertEquals(9, runBlocking {
            val dao = EtaDatabase.get(context).conversationDao()
            listOf("first", "second", "third").sumOf { dao.messageCount(it) }
        })
    }

    @Test
    fun deletingTheSelectedConversationLoadsTheNextConversation() {
        seed("first" to conversation("first"), "second" to conversation("second"))
        val state = AgentAppState(context, scope)

        state.deleteConversation("first")

        awaitLoaded(state, "second")
        assertEquals(listOf("second-0", "second-1", "second-2"), state.homeState.messages.map { it.id })
        awaitCondition { runBlocking { EtaDatabase.get(context).conversationDao().messageCount("first") == 0 } }
        assertEquals(3, runBlocking { EtaDatabase.get(context).conversationDao().messageCount("second") })
    }

    @Test
    fun deletingAnUnloadedConversationKeepsOtherStoredConversationsIntact() {
        seed("first" to conversation("first"), "second" to conversation("second"), "third" to conversation("third"))
        val state = AgentAppState(context, scope)

        state.deleteConversation("second")
        awaitCondition { runBlocking { EtaDatabase.get(context).conversationDao().messageCount("second") == 0 } }
        state.selectConversation("third")

        awaitLoaded(state, "third")
        assertEquals("third", state.conversationPaneState.selectedConversationId)
        assertEquals(3, state.homeState.messages.size)
        assertEquals(3, runBlocking { EtaDatabase.get(context).conversationDao().messageCount("first") })
        assertEquals(setOf("first", "third"), state.conversationPaneState.conversations.map { it.id }.toSet())
    }

    @Test
    fun loadingAnEarlierPageThenDeletingATurnPreservesTheEarlierPrefix() {
        seed("first" to conversation("first", count = 150))
        val state = AgentAppState(context, scope)
        assertTrue(state.homeState.hasEarlierMessages)
        assertEquals(128, state.homeState.messages.size)

        state.loadEarlierMessages()
        awaitCondition { !state.isLoadingEarlierMessages && !state.homeState.hasEarlierMessages }
        assertEquals((0 until 150).map { "first-$it" }, state.homeState.messages.map { it.id })
        state.deleteMessageTurn("first-149")
        awaitCondition { runBlocking { EtaDatabase.get(context).conversationDao().messageCount("first") == 149 } }

        val messages = runBlocking { AgentConversationStore.loadAllMessages(context, "first") }
        assertEquals((0 until 149).map { "first-$it" }, messages.map { it.id })
    }

    @Test
    fun searchAndExportIncludeMessagesOutsideTheLoadedWindow() {
        seed("first" to conversation("first"), "second" to conversation("second", count = 150))
        val state = AgentAppState(context, scope)

        state.updateSearchQuery("second-message-0-end")
        awaitCondition { state.conversationPaneState.conversations.map { it.id } == listOf("second") }
        assertEquals("first", state.conversationPaneState.selectedConversationId)

        val markdown = runBlocking { state.exportConversationMarkdown("second") }
        assertTrue(requireNotNull(markdown).contains("second-message-0-end"))
        assertTrue(markdown.contains("second-message-149-end"))
        assertFalse(state.isLoadingConversation)
    }

    @Test
    fun repeatedPrefetchRequestsLoadOnlyOnePage() {
        seed("first" to conversation("first", count = 600))
        val state = AgentAppState(context, scope)

        repeat(10) { state.loadEarlierMessages() }
        awaitCondition { !state.isLoadingEarlierMessages }

        assertEquals(256, state.homeState.messages.size)
        assertEquals(256, state.homeState.messages.map { it.id }.distinct().size)
        assertEquals("first-344", state.homeState.messages.first().id)
    }

    @Test
    fun switchingConversationCancelsAnOutstandingHistoryRead() {
        seed("first" to conversation("first", count = 600), "second" to conversation("second"))
        val state = AgentAppState(context, scope)

        state.loadEarlierMessages()
        state.selectConversation("second")
        awaitLoaded(state, "second")

        assertEquals(ConversationHistoryLoadState.Idle, state.historyLoadState)
        assertEquals(listOf("second-0", "second-1", "second-2"), state.homeState.messages.map { it.id })
        assertEquals(600, runBlocking { EtaDatabase.get(context).conversationDao().messageCount("first") })
    }

    @Test
    fun failedPageKeepsTheCurrentWindowAndCanBeRetried() {
        val original = conversation("first", count = 300)
        val longMessage = UserMessageUi("first-100", "历史正文".repeat(6_000))
        seed("first" to original.copy(messages = original.messages.map { if (it.id == longMessage.id) longMessage else it }))
        val state = AgentAppState(context, scope)
        EtaDatabase.get(context).openHelper.writableDatabase.execSQL(
            "DELETE FROM agent_text_chunks WHERE owner_table = 'conversation_messages' AND owner_id = 'first-100'",
        )

        state.loadEarlierMessages()
        awaitCondition { state.historyLoadState == ConversationHistoryLoadState.Failed }
        assertEquals(128, state.homeState.messages.size)
        assertEquals("first-172", state.homeState.messages.first().id)

        runBlocking {
            EtaDatabase.get(context).conversationDao().insertMessages(listOf(
                ConversationMessageEntity(longMessage.id, "first", 100, "user", longMessage.content),
            ))
        }
        state.loadEarlierMessages()
        awaitCondition { !state.isLoadingEarlierMessages }
        assertEquals(ConversationHistoryLoadState.Idle, state.historyLoadState)
        assertEquals(longMessage, state.homeState.messages.first { it.id == longMessage.id })
        assertEquals(300, runBlocking { EtaDatabase.get(context).conversationDao().messageCount("first") })
    }

    @Test
    fun returningToTheLatestMessagesReleasesOldPagesWithoutDeletingHistory() {
        seed("first" to conversation("first", count = 600))
        val state = AgentAppState(context, scope)
        repeat(3) {
            state.loadEarlierMessages()
            awaitCondition { !state.isLoadingEarlierMessages }
        }
        assertEquals(512, state.homeState.messages.size)

        state.updateHistoryViewport(atLatest = true)
        awaitCondition { !state.isLoadingEarlierMessages }
        assertEquals(128, state.homeState.messages.size)
        assertEquals("first-472", state.homeState.messages.first().id)
        assertEquals(600, runBlocking { EtaDatabase.get(context).conversationDao().messageCount("first") })

        state.loadEarlierMessages()
        awaitCondition { !state.isLoadingEarlierMessages }
        assertEquals(256, state.homeState.messages.size)
        assertEquals("first-344", state.homeState.messages.first().id)
    }

    @Test
    fun scrollingBackToHistoryCancelsPageReleaseWithoutCancellingTheNextRead() {
        seed("first" to conversation("first", count = 600))
        val state = AgentAppState(context, scope)
        repeat(3) {
            state.loadEarlierMessages()
            awaitCondition { !state.isLoadingEarlierMessages }
        }
        state.updateHistoryViewport(atLatest = true)
        state.updateHistoryViewport(atLatest = false)
        state.loadEarlierMessages()
        awaitCondition { !state.isLoadingEarlierMessages }

        assertEquals(600, state.homeState.messages.size)
        assertFalse(state.homeState.hasEarlierMessages)
        assertEquals(ConversationHistoryLoadState.Idle, state.historyLoadState)
    }

    private fun conversation(id: String, count: Int = 3): AgentChatHomeUiState {
        val messages = (0 until count).map { index -> UserMessageUi("$id-$index", "$id-message-$index-end") }
        val history = messages.map { message ->
            AgentModelClient.ConversationMessage(role = "user", content = message.content, messageId = message.id)
        }
        return AgentChatHomeUiState(
            messages = messages, history = history, journal = history,
            input = "", isStreaming = false, thinkingEnabled = false,
        )
    }

    private fun seed(vararg conversations: Pair<String, AgentChatHomeUiState>) = runBlocking {
        val states = linkedMapOf(*conversations)
        AgentConversationStore.save(
            context, states.keys.first(), states,
            states.keys.associateWith { it },
            states.keys.associateWith { 1L },
        )
    }

    private fun awaitLoaded(state: AgentAppState, id: String) {
        awaitCondition {
            state.conversationPaneState.selectedConversationId == id &&
                state.homeState.isConversationLoaded && !state.isLoadingConversation
        }
        assertFalse(state.conversationLoadFailed)
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper(10, TimeUnit.MILLISECONDS)
            Thread.sleep(1)
        }
        assertTrue("异步会话操作未在限定时间内完成", condition())
    }
}
