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
    // 09 第四期 4.1：新对话页「接着做」——未归档、有标题的最近 3 个对话；点一行打开。
    // 「正在进行」直接读全局任务状态（rememberBackgroundItems），只在新对话页组合，不会让对话列表跟着任务进度重组。
    val recent: List<io.github.mangi.eta.ui.model.ConversationSummaryUi> = emptyList(),
    val onOpenConversation: (String) -> Unit = {},
    val onOpenDesktopSession: (String) -> Unit = {},
)
internal val LocalChatHomeShortcuts = staticCompositionLocalOf { ChatHomeShortcuts() }

internal data class ChatInlineEditor(val messageId: String, val initialText: String,
    val preserveFollowing: Boolean, val onSubmit: (String) -> Unit, val onCancel: () -> Unit)
internal val LocalChatInlineEditor = staticCompositionLocalOf<ChatInlineEditor?> { null }

/**
 * 能「分支」的回复由外部给定时用这里的集合（电脑会话按桌面的切点算），为空时按手机对话的规则自己算。
 */
internal val LocalBranchableMessageIds = androidx.compose.runtime.compositionLocalOf<Set<String>?> { null }

/** 消息菜单里是否有「删除」；电脑会话的历史不能改写，不显示。 */
internal val LocalMessageDeleteSupported = androidx.compose.runtime.compositionLocalOf { true }
