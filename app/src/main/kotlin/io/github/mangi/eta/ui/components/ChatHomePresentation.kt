package io.github.mangi.eta.ui.components

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.mangi.eta.data.repository.CommonTask

internal data class ChatDraftRequest(val text: String, val sequence: Long)
internal val LocalChatDraftRequest = staticCompositionLocalOf<State<ChatDraftRequest?>?> { null }

/** UI-only shortcuts; execution still goes through the same application callbacks. */
internal data class ChatHomeShortcuts(
    val conversationId: String? = null,
    val onOpenTasks: () -> Unit = {},
    val tasks: List<CommonTask> = emptyList(),
    val missingSetupCount: Int = 0,
    val onRunTask: (CommonTask) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onExportConversation: () -> Unit = {},
    val onOpenSpeechSettings: () -> Unit = {},
)
internal val LocalChatHomeShortcuts = staticCompositionLocalOf { ChatHomeShortcuts() }

internal data class ChatInlineEditor(val messageId: String, val initialText: String,
    val preserveFollowing: Boolean, val onSubmit: (String) -> Unit, val onCancel: () -> Unit)
internal val LocalChatInlineEditor = staticCompositionLocalOf<ChatInlineEditor?> { null }
