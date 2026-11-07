package io.github.mangi.eta.ui.app

import android.content.Context
import androidx.lifecycle.viewModelScope
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.ConversationMessageEntity
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.ConversationHistoryLoadState
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun pinMoveCollapseRenameAndDeleteGroupsPreserveMessageDatesAcrossRestart() {
        seed("old" to conversation("old"), "other" to conversation("other"))
        val state = AgentAppState(context, scope); awaitLoaded(state, "old")
        state.setConversationsPinned(setOf("old", "other"), true)
        assertTrue(state.conversationPaneState.conversations.all { it.isPinned && it.pinnedAt != null })
        assertNull(state.createConversationGroup(" 工作 ", setOf("old")))
        val work = state.conversationPaneState.groups.single()
        assertEquals("工作", work.name)
        assertNull(state.createConversationGroup("学习", setOf("other")))
        val learn = state.conversationPaneState.groups.last()
        state.toggleConversationGroup(learn.id); state.moveConversationGroup(learn.id, -1)
        assertEquals(learn.id, state.conversationPaneState.groups.first().id)
        org.junit.Assert.assertNotNull(state.renameConversationGroup(learn.id, " 工作 "))
        org.junit.Assert.assertNotNull(state.createConversationGroup(" "))
        org.junit.Assert.assertNotNull(state.createConversationGroup("x".repeat(21)))
        assertNull(state.renameConversationGroup(work.id, "Work"))
        org.junit.Assert.assertNotNull(state.createConversationGroup("work"))
        state.renameConversation("old", "旧会话新标题")
        assertTrue(state.conversationPaneState.conversations.all { it.updatedAtMillis == 1L })
        awaitCondition { runBlocking { EtaDatabase.get(context).conversationDao().conversationGroups().firstOrNull()?.id == learn.id } }
        val reloaded = runBlocking { AgentConversationStore.loadAsync(context) }
        assertTrue(reloaded.groups.first().collapsed)
        assertEquals(work.id, reloaded.conversationsById.getValue("old").groupId)
        org.junit.Assert.assertNotNull(reloaded.conversationsById.getValue("old").pinnedAt)
        state.archiveConversations(setOf("old"))
        val archived = state.conversationPaneState.conversations.first { it.id == "old" }
        assertFalse(archived.isPinned); assertEquals(work.id, archived.groupId)
        state.unarchiveConversations(setOf("old")); state.deleteConversationGroup(work.id)
        assertNull(state.conversationPaneState.conversations.first { it.id == "old" }.groupId)
        awaitCondition { runBlocking { EtaDatabase.get(context).conversationDao().conversationGroups().none { it.id == work.id } } }
        assertEquals(2, runBlocking { EtaDatabase.get(context).conversationDao().conversationEntities().size })
        assertTrue(state.conversationPaneState.conversations.all { it.updatedAtMillis == 1L })
    }

    @Test
    fun renamingAnOldConversationKeepsItsMessageTimestampAndPersistsTheTitle() {
        seed("old" to conversation("old"))
        val state = AgentAppState(context, scope)
        awaitLoaded(state, "old")
        state.renameConversation("old", "Renamed old conversation")
        assertEquals(1L, state.conversationPaneState.conversations.single().updatedAtMillis)
        awaitCondition { runBlocking { EtaDatabase.get(context).conversationDao().conversationEntities().single().title == "Renamed old conversation" } }
        assertEquals(1L, runBlocking { EtaDatabase.get(context).conversationDao().conversationEntities().single().updatedAt })
    }

    @Test
    fun notificationReplyPromptIsVisibleFromCheckpointBeforeAResultExists() {
        seed("first" to conversation("first").copy(archivedAt = 42L))
        val payload = io.github.mangi.eta.agent.runtime.AgentUiHandoffPayload("first",
            promptSupplement = io.github.mangi.eta.agent.runtime.AgentUiHandoffPayload.Supplement(1, "收左边的菜", 1))
        val request = AgentRuntimeWire.RunRequest("inline-running", "收左边的菜",
            AgentModelClient.ModelConfig(baseUrl = "https://example.invalid", apiKey = "test", model = "test", systemPrompt = ""),
            emptyList(), emptyList(), handoff = AgentRuntimeWire.EntryHandoff("inline-running",
                AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE, payload.toJson()))
        assertTrue(io.github.mangi.eta.agent.runtime.AgentRunCheckpointStore.start(context, request))
        val state = AgentAppState(context, scope)
        awaitCondition { state.homeState.messages.filterIsInstance<UserMessageUi>().any { it.content == "收左边的菜" } }
        assertNull(state.homeState.archivedAt)
        state.refreshRuntimeResults()
        awaitCondition { !state.isLoadingEarlierMessages }
        assertEquals(1, state.homeState.messages.filterIsInstance<UserMessageUi>().count { it.content == "收左边的菜" })
    }

    @Test
    fun notificationContinuationImportsTheAssistantArchiveBeforeSelectingItsChat() {
        seed("first" to conversation("first"))
        val source = AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE
        val handoff = AgentRuntimeWire.EntryHandoff("game-run", source,
            io.github.mangi.eta.agent.runtime.AgentExternalArchivePayload("收菜", "game", "游戏任务").toJson())
        io.github.mangi.eta.agent.runtime.AgentRunArchiveStore.add(context,
            io.github.mangi.eta.agent.runtime.AgentRunArchiveStore.ArchivedRun(
                handoff, emptyList(), AgentRuntimeWire.RunResult("game-run", true, "请决定下一步"), System.currentTimeMillis()))
        val state = AgentAppState(context, scope)
        val chat = io.github.mangi.eta.agent.runtime.AgentConversationIdentity.archiveConversationId(source, "game")
        var opened: Boolean? = null
        scope.launch { opened = state.openConversationById(chat) }
        awaitCondition { opened != null }
        assertTrue(opened == true)
        awaitLoaded(state, chat)
        assertTrue(state.homeState.messages.filterIsInstance<AgentMessageUi>().any { it.content == "请决定下一步" })
        opened = null
        scope.launch { opened = state.openConversationById("missing-chat") }
        awaitCondition { opened != null }
        assertFalse(opened == true)
        assertEquals(chat, state.conversationPaneState.selectedConversationId)
    }

    @Test
    fun archiveDeepLinkRecoversWhenLaunchedFromMain() {
        val source = AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE
        val handoff = AgentRuntimeWire.EntryHandoff("archive-run", source,
            io.github.mangi.eta.agent.runtime.AgentExternalArchivePayload("User prompt", "archive", "Archived").toJson())
        io.github.mangi.eta.agent.runtime.AgentRunArchiveStore.add(context,
            io.github.mangi.eta.agent.runtime.AgentRunArchiveStore.ArchivedRun(
                handoff, emptyList(), AgentRuntimeWire.RunResult("archive-run", true, "Result"), 1L))
        val state = AgentAppState(context, scope)
        val chat = io.github.mangi.eta.agent.runtime.AgentConversationIdentity.archiveConversationId(source, "archive")
        val opened = CompletableDeferred<Boolean>()
        scope.launch { opened.complete(state.openArchivedConversation(source, "archive")) }
        awaitCondition { opened.isCompleted }
        assertTrue(runBlocking { opened.await() })
        awaitLoaded(state, chat)
        assertTrue(state.homeState.messages.filterIsInstance<AgentMessageUi>().any { it.content == "Result" })
    }

    @Test
    fun startupExposesNoWritableStateUntilTheSnapshotLoadsAndAllowsRetry() {
        seed("saved" to conversation("saved"))
        val snapshot = AgentConversationStore.load(context)
        val attempts = listOf(
            CompletableDeferred<AgentConversationStore.Snapshot>(),
            CompletableDeferred<AgentConversationStore.Snapshot>(),
        )
        var loadCount = 0
        val viewModel = AgentAppViewModel(RuntimeEnvironment.getApplication()) {
            attempts[loadCount++].await()
        }
        val viewModelJob = checkNotNull(viewModel.viewModelScope.coroutineContext[Job])
        try {
            assertNull(viewModel.state)
            viewModel.loadInitialConversations()
            assertEquals(1, loadCount)
            attempts[0].completeExceptionally(IllegalStateException("load failed"))
            awaitCondition { viewModel.conversationStartupFailed }
            assertNull(viewModel.state)

            viewModel.loadInitialConversations()
            assertFalse(viewModel.conversationStartupFailed)
            assertNull(viewModel.state)
            attempts[1].complete(snapshot)
            awaitCondition { viewModel.state != null }
            val restored = checkNotNull(viewModel.state)
            assertEquals("saved", restored.conversationPaneState.selectedConversationId)
            assertEquals(snapshot.conversationsById.getValue("saved").messages, restored.homeState.messages)
            viewModel.loadInitialConversations()
            assertSame(restored, viewModel.state)
            assertEquals(2, loadCount)
        } finally {
            viewModel.viewModelScope.cancel()
            awaitCondition { viewModelJob.isCompleted }
        }
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

    @Test
    fun concurrentResultsAndStopRemainBoundToTheirConversations() {
        seed("first" to conversation("first"), "second" to conversation("second"))
        val state = AgentAppState(context, scope)
        val runs = state.runStoreForTest()
        state.registerRunForTest("run-a", "first")
        state.selectConversation("second")
        awaitLoaded(state, "second")
        state.registerRunForTest("run-b", "second")
        state.selectConversation("first")
        awaitLoaded(state, "first")

        state.stopCurrentRun()
        assertTrue(runs.isStopRequested("run-a"))
        assertFalse(runs.isStopRequested("run-b"))
        state.completeRunForTest("run-b", "乙任务结果")
        awaitCondition { runs.observedRunIds == setOf("run-a") }

        assertEquals("first", state.conversationPaneState.selectedConversationId)
        assertTrue(state.homeState.isStreaming)
        assertFalse(state.homeState.messages.filterIsInstance<AgentMessageUi>().any { it.content == "乙任务结果" })
        assertEquals(setOf("run-a"), runs.observedRunIds)

        state.completeRunForTest("run-a", "甲任务结果")
        awaitCondition { !state.homeState.isStreaming }
        state.selectConversation("second")
        awaitLoaded(state, "second")
        assertTrue(state.homeState.messages.filterIsInstance<AgentMessageUi>().any { it.content == "乙任务结果" })
        assertFalse(state.homeState.messages.filterIsInstance<AgentMessageUi>().any { it.content == "甲任务结果" })
    }

    @Test
    fun deletingAnActiveConversationWaitsForItsResultAndKeepsOtherRuns() {
        seed("first" to conversation("first"), "second" to conversation("second"))
        val state = AgentAppState(context, scope)
        val runs = state.runStoreForTest()
        state.registerRunForTest("run-a", "first")
        state.selectConversation("second")
        awaitLoaded(state, "second")
        state.registerRunForTest("run-b", "second")

        state.deleteConversation("first")
        assertTrue(state.conversationPaneState.conversations.any { it.id == "first" })
        assertTrue(runs.isStopRequested("run-a"))
        assertFalse(runs.isStopRequested("run-b"))

        state.completeRunForTest("run-a", "已保存的结果")
        awaitCondition { state.conversationPaneState.conversations.none { it.id == "first" } }
        assertEquals(setOf("run-b"), runs.observedRunIds)
        assertTrue(state.homeState.isStreaming)
        state.completeRunForTest("run-a", "迟到的结果")
        assertFalse(state.conversationPaneState.conversations.any { it.id == "first" })
        state.completeRunForTest("run-b", "另一个任务结果")
    }

    @Test
    fun backgroundQueueContinuesWithItsCompletedHistoryWhileAnotherConversationRuns() {
        seed("first" to conversation("first"), "second" to conversation("second"))
        val state = AgentAppState(context, scope)
        val runs = state.runStoreForTest()
        state.registerRunForTest("run-a", "first")
        state.selectConversation("second")
        awaitLoaded(state, "second")
        state.registerRunForTest("run-b", "second")
        state.sendCurrentMessage("后台后续消息")
        state.selectConversation("first")
        awaitLoaded(state, "first")

        state.completeRunForTest("run-b", "后台已完成的回复")
        awaitCondition {
            runBlocking { AgentConversationStore.loadConversation(context, "second") }
                ?.journal?.any { it.role == "user" && it.content == "后台后续消息" } == true
        }
        val saved = requireNotNull(runBlocking { AgentConversationStore.loadConversation(context, "second") })
        val answerIndex = saved.journal.indexOfFirst { it.content == "后台已完成的回复" }
        val followupIndex = saved.journal.indexOfFirst { it.content == "后台后续消息" }
        assertTrue(answerIndex >= 0 && answerIndex < followupIndex)
        assertEquals("first", state.conversationPaneState.selectedConversationId)
        assertTrue(state.homeState.isStreaming)
        assertEquals("run-a", runs.activeRunId("first"))
        awaitCondition { runs.observedRunIds == setOf("run-a") }
        state.completeRunForTest("run-a", "前台任务完成")
    }

    @Test
    fun bulkDeletePublishesOneListAndPersistsOnceIncludingSelectionChange() {
        seed("first" to conversation("first"), "second" to conversation("second"), "third" to conversation("third"))
        val state = AgentAppState(context, scope)
        awaitLoaded(state, "first")
        awaitPersistence(state)
        val db = EtaDatabase.get(context).openHelper.writableDatabase
        db.execSQL("CREATE TABLE selection_write_audit (value INTEGER)")
        db.execSQL("CREATE TRIGGER count_selection_insert AFTER INSERT ON conversation_state BEGIN INSERT INTO selection_write_audit VALUES(1); END")
        db.execSQL("CREATE TRIGGER count_selection_update AFTER UPDATE ON conversation_state BEGIN INSERT INTO selection_write_audit VALUES(1); END")
        var deleted: Int? = null
        state.deleteConversations(setOf("first", "second", "missing")) { deleted = it }
        assertEquals(listOf("third"), state.conversationPaneState.conversations.map { it.id })
        awaitCondition { deleted != null }
        awaitLoaded(state, "third")
        awaitPersistence(state)
        assertEquals(2, deleted)
        val writes = db.query("SELECT COUNT(*) FROM selection_write_audit").use { it.moveToFirst(); it.getInt(0) }
        assertEquals("A batch and its replacement selection share one persistence", 1, writes)
        assertEquals(listOf("third"), runBlocking { EtaDatabase.get(context).conversationDao().conversations().map { it.id } })
    }

    @Test
    fun bulkDeleteStopsBothRunsAndWaitsForAllSettlementsWithoutResurrection() {
        seed("first" to conversation("first"), "second" to conversation("second"), "third" to conversation("third"))
        val state = AgentAppState(context, scope)
        awaitLoaded(state, "first")
        state.registerRunForTest("bulk-a", "first")
        state.selectConversation("second"); awaitLoaded(state, "second")
        state.registerRunForTest("bulk-b", "second")
        var deleted: Int? = null
        state.deleteConversations(setOf("first", "second")) { deleted = it }
        val runs = state.runStoreForTest()
        assertTrue(runs.isStopRequested("bulk-a")); assertTrue(runs.isStopRequested("bulk-b"))
        state.completeRunForTest("bulk-a", "已停止甲")
        awaitCondition { runs.activeRunId("first") == null }
        assertEquals(3, state.conversationPaneState.conversations.size)
        assertNull(deleted)
        state.completeRunForTest("bulk-b", "已停止乙")
        awaitCondition { deleted != null }
        assertEquals(2, deleted)
        state.completeRunForTest("bulk-a", "迟到的甲")
        state.completeRunForTest("bulk-b", "迟到的乙")
        awaitPersistence(state)
        assertEquals(setOf("third"), AgentConversationStore.load(context).conversationsById.keys)
    }

    @Test
    fun archiveSkipsRunningPersistsUnloadedRowsAndUndoCannotOverwriteNewerArchive() {
        seed("first" to conversation("first"), "second" to conversation("second"), "third" to conversation("third"))
        val state = AgentAppState(context, scope)
        awaitLoaded(state, "first")
        state.registerRunForTest("archive-running", "first")
        val change = state.archiveConversations(setOf("first", "second", "third"))
        assertEquals(setOf("second", "third"), change.ids); assertEquals(1, change.skipped)
        assertEquals("first", state.conversationPaneState.selectedConversationId)
        awaitPersistence(state)
        assertEquals(change.archivedAt, runBlocking { EtaDatabase.get(context).conversationDao().conversation("second") }?.archivedAt)
        assertEquals(change.archivedAt, AgentConversationStore.load(context).conversationsById["third"]?.archivedAt)
        state.updateSearchQuery("second")
        assertEquals("second", state.conversationPaneState.conversations.single().id)
        assertEquals(change.archivedAt, state.conversationPaneState.conversations.single().archivedAt)
        assertEquals(1, state.unarchiveConversations(setOf("second"), change.archivedAt))
        val newer = state.archiveConversations(setOf("second"))
        assertTrue(newer.archivedAt > change.archivedAt)
        assertEquals(1, state.unarchiveConversations(change.ids, change.archivedAt))
        awaitPersistence(state)
        assertEquals(newer.archivedAt, runBlocking { EtaDatabase.get(context).conversationDao().conversation("second") }?.archivedAt)
        assertNull(runBlocking { EtaDatabase.get(context).conversationDao().conversation("third") }?.archivedAt)
        state.completeRunForTest("archive-running", "完成")
    }

    @Test
    fun archivingCurrentConversationSwitchesAndAllArchivedRestartHasEmptySelection() {
        seed("first" to conversation("first"), "second" to conversation("second"))
        val state = AgentAppState(context, scope)
        awaitLoaded(state, "first")
        state.archiveConversations(setOf("first"))
        awaitLoaded(state, "second")
        state.archiveConversations(setOf("second"))
        assertNull(state.conversationPaneState.selectedConversationId)
        awaitPersistence(state)
        val restored = AgentConversationStore.load(context)
        assertNull(restored.selectedConversationId)
        assertEquals(2, restored.conversationsById.size)
        assertTrue(restored.conversationsById.values.all { it.archivedAt != null })
    }

    @Test
    fun openingArchiveKeepsItArchivedButSendingMessageUnarchivesDurably() {
        seed("first" to conversation("first").copy(archivedAt = 42L))
        val state = AgentAppState(context, scope)
        awaitLoaded(state, "first")
        assertEquals(42L, state.homeState.archivedAt)
        state.sendCurrentMessage("新消息取消归档")
        assertNull(state.homeState.archivedAt)
        awaitPersistence(state)
        assertNull(runBlocking { EtaDatabase.get(context).conversationDao().conversation("first") }?.archivedAt)
    }

    private fun awaitPersistence(state: AgentAppState) {
        awaitCondition {
            val job = AgentAppState::class.java.getDeclaredField("persistenceJob").apply { isAccessible = true }.get(state) as? Job
            job == null || job.isCompleted
        }
    }

    private fun AgentAppState.runStoreForTest(): AgentRunStore =
        AgentAppState::class.java.getDeclaredField("runStore").apply { isAccessible = true }.get(this) as AgentRunStore

    private fun AgentAppState.registerRunForTest(runId: String, conversationId: String) {
        check(runStoreForTest().register(runId, conversationId))
        AgentAppState::class.java.getDeclaredMethod("setConversationStreaming", String::class.java,
            Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(this, runId, true)
    }

    private fun AgentAppState.completeRunForTest(runId: String, content: String) {
        val result = AgentRuntimeWire.RunResult(
            runId, true, content,
            transcript = listOf(AgentModelClient.ConversationMessage(role = "assistant", content = content)),
        )
        AgentAppState::class.java.getDeclaredMethod("applyRunResult", String::class.java,
            AgentRuntimeWire.RunResult::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(this, runId, result, false)
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
