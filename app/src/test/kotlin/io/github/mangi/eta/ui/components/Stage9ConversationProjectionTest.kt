package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class Stage9ConversationProjectionTest {
    private val today = LocalDate.of(2026, 11, 7)
    private fun row(id: String, days: Long = 0, pin: Long? = null, group: String? = null, archived: Long? = null) =
        ConversationSummaryUi(id, id, "", "", mode = ConversationModeUi.Chat,
            updatedAtMillis = today.minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            isPinned = pin != null, pinnedAt = pin, groupId = group, archivedAt = archived)
    private val groups = listOf(ConversationGroupUi("b", "B", 1, collapsed = true), ConversationGroupUi("a", "A", 0),
        ConversationGroupUi("empty", "Empty", 2))
    private val rows = listOf(row("p-old", pin = 1, group = "a"), row("p-new", 100, 2), row("a-old", 2, group = "a"),
        row("a-new", group = "a"), row("hidden", group = "b"), row("today"), row("yesterday", 1),
        row("week", 7), row("old", 8), row("orphan", group = "missing"), row("archived", group = "a", archived = 1))
    @Test fun pinsGroupsAndDatesAreOrderedWithoutDuplicateRowsAndEmptyGroupsRemain() {
        val sections = projectConversationSections(ConversationPaneUiState(rows, null, "", groups), false, today)
        assertEquals(listOf("pinned", "group:a", "group:b", "group:empty", "time:1", "time:2", "time:3", "time:4"), sections.map { it.key })
        assertEquals(listOf("p-new", "p-old"), sections.first().conversations.map { it.id })
        assertEquals(listOf("a-new", "a-old"), sections[1].conversations.map { it.id })
        assertEquals(1, sections[2].conversations.size); assertTrue(sections[2].visibleConversations.isEmpty())
        assertTrue(sections[3].conversations.isEmpty())
        val ids = sections.flatMap { it.conversations }.map { it.id }
        assertEquals(ids.size, ids.distinct().size); assertFalse("archived" in ids); assertTrue("orphan" in ids)
    }
    @Test fun searchAndArchiveAreFlatAndIgnoreCollapsedGroups() {
        val state = ConversationPaneUiState(rows, null, "query", groups)
        val search = projectConversationSections(state, false, today).single()
        assertEquals("flat", search.key); assertEquals(rows.size, search.visibleConversations.size)
        assertTrue(search.conversations.any { it.id == "hidden" }); assertTrue(search.conversations.any { it.archivedAt != null })
        val archive = projectConversationSections(state.copy(searchQuery = ""), true, today).single()
        assertEquals(listOf("archived"), archive.conversations.map { it.id }); assertNull(archive.group); assertNull(archive.timeSection)
    }
}
