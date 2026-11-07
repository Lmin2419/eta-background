package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.*
import java.time.LocalDate

internal data class ConversationPaneSection(
    val key: String,
    val timeSection: Int? = null,
    val group: ConversationGroupUi? = null,
    val conversations: List<ConversationSummaryUi>,
) {
    val visibleConversations: List<ConversationSummaryUi> get() = if (group?.collapsed == true) emptyList() else conversations
}

/** One projection for normal, archived and search lists; pinned rows never repeat inside a group. */
internal fun projectConversationSections(state: ConversationPaneUiState, archived: Boolean, today: LocalDate): List<ConversationPaneSection> {
    val searching = state.searchQuery.isNotBlank()
    val visible = state.conversations.filter { searching || (it.archivedAt != null) == archived }
        .sortedWith(compareByDescending<ConversationSummaryUi> { it.updatedAtMillis }.thenBy { it.id })
    if (searching || archived) return listOf(ConversationPaneSection("flat", conversations = visible))
    val groups = state.groups.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val groupIds = groups.mapTo(hashSetOf()) { it.id }
    val pinned = visible.filter { it.isPinned }.sortedWith(compareByDescending<ConversationSummaryUi> { it.pinnedAt ?: 0L }
        .thenByDescending { it.updatedAtMillis }.thenBy { it.id })
    val unpinned = visible.filterNot { it.isPinned }
    val byGroup = unpinned.groupBy { it.groupId }
    return buildList {
        if (pinned.isNotEmpty()) add(ConversationPaneSection("pinned", timeSection = 0, conversations = pinned))
        groups.forEach { group -> add(ConversationPaneSection("group:${group.id}", group = group, conversations = byGroup[group.id].orEmpty())) }
        unpinned.filter { it.groupId !in groupIds }.groupBy { conversationSection(it, today) }.toSortedMap().forEach { (section, rows) ->
            add(ConversationPaneSection("time:$section", timeSection = section, conversations = rows))
        }
    }
}
