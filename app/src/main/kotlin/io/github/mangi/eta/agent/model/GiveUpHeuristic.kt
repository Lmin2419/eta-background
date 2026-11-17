package io.github.mangi.eta.agent.model

/**
 * 回答结尾（最后约 600 字）里出现「做不到、失败、报错」一类说法，且结尾没有「已修复、已通过」一类收尾。
 * 界面的「复核报错再试」按钮和运行时的交卷前自检（[CompletionGate]）共用；宁可少判，不误伤正常回答。
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
