package io.github.mangi.eta.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

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

/**
 * 回答结尾（最后约 600 字）里出现「做不到、失败、报错」一类说法，且结尾没有「已修复、已通过」一类收尾。
 * 宁可少出现：按钮只是捷径，用户随时可以自己打字让它再试。
 */
internal fun String.looksLikeGaveUp(): Boolean {
    val tail = takeLast(600).lowercase()
    if (GaveUpPhrases.none { it in tail }) return false
    val ending = tail.takeLast(200)
    return SolvedPhrases.none { it in ending }
}

private val GaveUpPhrases = listOf(
    "做不到", "无法完成", "无法实现", "无法解决", "没能", "没有成功", "未能", "未成功", "失败", "报错", "出错", "行不通", "不可行",
    "无能为力", "搞不定", "無法完成", "無法實現", "無法解決", "沒能", "沒有成功", "失敗", "報錯", "出錯",
    "cannot", "can't", "unable to", "failed", "couldn't",
)

private val SolvedPhrases = listOf(
    "已修复", "已解决", "修好了", "已经修复", "已经解决", "全部通过", "已通过", "都通过", "已成功", "成功了", "搞定了",
    "已修復", "已解決", "修好", "全部通過", "已通過",
    "fixed", "resolved", "all tests pass", "now works", "succeeded",
)
