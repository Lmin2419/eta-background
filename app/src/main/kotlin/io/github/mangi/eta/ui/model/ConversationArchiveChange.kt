package io.github.mangi.eta.ui.model

internal data class ConversationArchiveChange(
    val ids: Set<String>,
    val skipped: Int,
    val archivedAt: Long,
)
