package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 跑完了还没看的本机对话（etax/design/09 第一期 1.1）：对话 id → 是否出错。
 * 完成提示条只停 6 秒，错过了靠这里留下的圆点找回来（侧栏会话行、首页左上角历史按钮）。只在内存里，不落盘。
 */
internal object FinishedUnseen {
    private val unseen = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val state: StateFlow<Map<String, Boolean>> = unseen.asStateFlow()

    /** 任务结束时调用：有对话、不是用户自己停的、用户没正停在这个对话上，才记成未读。 */
    fun record(task: TaskNotificationState) {
        val conversationId = task.conversationId ?: return
        if (task.status == AgentOverlayStatus.Stopped || ForegroundConversation.isShowing(conversationId)) return
        unseen.update { it + (conversationId to task.finishedWithFailure()) }
    }

    fun clear(conversationId: String) {
        if (conversationId in unseen.value) unseen.update { it - conversationId }
    }

    internal fun resetForTest() { unseen.value = emptyMap() }
}

/** 任务以出错结束（回复失败、运行失败、无法继续）。 */
internal fun TaskNotificationState.finishedWithFailure(): Boolean = replyFailed ||
    status == AgentOverlayStatus.RunFailed || status == AgentOverlayStatus.ContinuationUnavailable
