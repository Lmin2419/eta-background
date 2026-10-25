package io.github.mangi.eta.agent.memory

import io.github.mangi.eta.data.repository.AgentMemorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ConversationMemoryPinsTest {
    private fun snapshot(content: String, revision: String) =
        AgentMemorySnapshot(content = content, revision = revision, byteSize = content.length, lineCount = 1)

    @Test
    fun ownWritesKeepTheSessionSnapshotSoTheSystemPromptDoesNotChange() {
        val id = "conv-own-${System.nanoTime()}"
        val first = ConversationMemoryPins.contextFor(id, snapshot("# 核心记忆\n旧", "r1"), 128_000)
        ConversationMemoryPins.recordOwnWrite(id, "r2")
        val next = ConversationMemoryPins.contextFor(id, snapshot("# 核心记忆\n新", "r2"), 128_000)
        assertSame(first, next)
    }

    @Test
    fun externalChangesCompactionOrModelSwitchReinjectTheLatestMemory() {
        val id = "conv-ext-${System.nanoTime()}"
        ConversationMemoryPins.contextFor(id, snapshot("# 核心记忆\n旧", "r1"), 128_000)
        val external = ConversationMemoryPins.contextFor(id, snapshot("# 核心记忆\n他处修改", "r9"), 128_000)
        assertEquals("r9", external.revision)

        ConversationMemoryPins.recordOwnWrite(id, "r10")
        ConversationMemoryPins.forget(id)
        assertEquals("r10", ConversationMemoryPins.contextFor(id, snapshot("# 核心记忆\n写入", "r10"), 128_000).revision)

        val switched = ConversationMemoryPins.contextFor(id, snapshot("# 核心记忆\n写入", "r10"), 32_000)
        assertEquals(AgentMemoryContextBuilder.coreBudgetChars(32_000), switched.coreBudgetChars)
    }
}
