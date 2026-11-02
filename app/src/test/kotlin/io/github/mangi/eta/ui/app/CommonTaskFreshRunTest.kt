package io.github.mangi.eta.ui.app

import android.content.Context
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.roleplay.RoleplayBinding
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.PendingImageUi
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
class CommonTaskFreshRunTest {
    private lateinit var context: Context
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication()).declareActionUnbindable(AgentRuntimeWire.ACTION_BIND)
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
    }

    @After fun cleanup() {
        scope.cancel()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!job.isCompleted && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper(10, TimeUnit.MILLISECONDS)
            Thread.sleep(1)
        }
        try {
            assertTrue(job.isCompleted)
        } finally { EtaDatabase.closeForTests() }
    }

    @Test fun savedInstructionStartsNewOrdinaryConversationWithoutOldHistoryDraftAttachmentsOrRoleplay() {
        val oldHistory = listOf(AgentModelClient.ConversationMessage("user", "private old conversation", messageId = "old-message"))
        val old = AgentChatHomeUiState(
            messages = listOf(UserMessageUi("old-message", "private old conversation")),
            history = oldHistory, journal = oldHistory, input = "unsent draft",
            isStreaming = false, thinkingEnabled = false,
            pendingImages = listOf(PendingImageUi("image", "file:///old.jpg", "data:image/jpeg;base64,AA==", "image/jpeg")),
            roleplay = RoleplayBinding("character", "{}", "Character"),
        )
        runBlocking { AgentConversationStore.save(context, "old", mapOf("old" to old), mapOf("old" to "Old"), mapOf("old" to 1L)) }
        val state = AgentAppState(context, scope,
            AgentConversationStore.Snapshot("old", mapOf("old" to old), mapOf("old" to "Old"), mapOf("old" to 1L)))

        assertTrue(state.startCommonTask("morning", "  Count today’s messages  ", "launch-1", 1000))
        val newId = state.conversationPaneState.selectedConversationId
        assertNotEquals("old", newId)
        assertTrue(newId != null)
        assertEquals(listOf("Count today’s messages"), state.homeState.history.map { it.content })
        assertEquals(listOf("Count today’s messages"), state.homeState.messages.filterIsInstance<UserMessageUi>().map { it.content })
        assertTrue(state.homeState.pendingImages.isEmpty())
        assertTrue(state.homeState.pendingFileReferences.isEmpty())
        assertEquals("", state.homeState.input)
        assertNull(state.homeState.roleplay)
        assertTrue(state.homeState.queuedMessages.isEmpty())
        assertTrue(state.conversationPaneState.conversations.any { it.id == "old" })

        assertFalse(state.startCommonTask("morning", "Count today’s messages", "launch-1", 10_000))
        assertEquals(newId, state.conversationPaneState.selectedConversationId)
        assertTrue(state.startCommonTask("morning", "Count today’s messages", "launch-2", 10_000))
        assertNotEquals(newId, state.conversationPaneState.selectedConversationId)
        assertEquals(1, state.homeState.history.size)
    }

    @Test fun invalidInstructionDoesNotReplaceTheOpenConversation() {
        val state = AgentAppState(context, scope)
        val before = state.homeState
        assertFalse(state.startCommonTask("task", " ", "blank", 1000))
        assertFalse(state.startCommonTask("task", "x".repeat(8001), "long", 1000))
        assertEquals(before, state.homeState)
    }
}
