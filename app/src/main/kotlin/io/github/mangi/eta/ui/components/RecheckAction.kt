package io.github.mangi.eta.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.mangi.eta.agent.model.looksLikeGaveUp

/**
 * 「复核报错再试」（etax/design/07 第三期）：只挂在最新一条回答上，回答结尾像是「做不到 / 失败」时出现。
 * 点一下等于发送一句固定的复核指令；只在用户点的时候触发，不自动复审。
 */
internal class RecheckAction(
    val latestAnswerId: String?,
    val enabled: Boolean,
    val submit: (String) -> Unit,
    /** 最新回答这一轮用过工具（纯聊天里提到「失败」不出现按钮）。 */
    val turnUsedTools: Boolean = true,
    /** 这一轮最后一步工具失败（这时回答里没说「做不到」也出现）。 */
    val lastToolFailed: Boolean = false,
) {
    /** [answerGaveUp]：回答结尾像是放弃了（[looksLikeGaveUp]）。 */
    fun offers(answerGaveUp: Boolean): Boolean = turnUsedTools && (lastToolFailed || answerGaveUp)
}

/** 最新回答这一轮的工具情况：用过没有、最后一步是否失败（09 第一期 1.4）。 */
internal fun turnToolSignal(messages: List<io.github.mangi.eta.ui.model.AgentChatMessageUi>, answerId: String?): Pair<Boolean, Boolean> {
    val range = answerId?.let { turnRange(messages, it) } ?: return false to false
    val tools = messages.subList(range.first, range.last + 1).filterIsInstance<io.github.mangi.eta.ui.model.ToolActivityMessageUi>()
    return tools.isNotEmpty() to (tools.lastOrNull()?.status == io.github.mangi.eta.ui.model.ToolActivityStatusUi.Failed)
}

internal val LocalRecheckAction = staticCompositionLocalOf<RecheckAction?> { null }
