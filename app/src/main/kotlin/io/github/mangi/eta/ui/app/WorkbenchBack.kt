package io.github.mangi.eta.ui.app

/**
 * 首页返回键（2026111617）：工作台（新对话页）是首页的总入口。
 *
 * - 在某个对话里按返回 → 回到工作台；
 * - 从某个对话点「+」来到工作台 → 按返回回到那个对话；
 * - 工作台本身（没有来处，或来处已被删掉）→ 交给系统，退回桌面。
 */
internal object WorkbenchBack {
    sealed interface Action {
        data object ToWorkbench : Action
        data class ToConversation(val id: String) : Action
        data object Exit : Action
    }

    fun decide(selectedConversationId: String?, returnConversationId: String?, conversationExists: (String) -> Boolean): Action = when {
        selectedConversationId != null -> Action.ToWorkbench
        returnConversationId != null && conversationExists(returnConversationId) -> Action.ToConversation(returnConversationId)
        else -> Action.Exit
    }

    /** 点「+」进工作台时记下来处；已经在工作台上再点「+」，保留原来的来处。 */
    fun returnTargetOnNew(selectedConversationId: String?, currentReturnId: String?): String? =
        selectedConversationId ?: currentReturnId
}
