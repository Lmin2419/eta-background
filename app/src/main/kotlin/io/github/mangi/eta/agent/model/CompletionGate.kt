package io.github.mangi.eta.agent.model

import org.json.JSONObject

/** 一次工具调用结果的摘要：只留判断用的字段，不留结果正文。 */
internal data class ToolOutcomeSummary(
    val name: String,
    val ok: Boolean,
    val code: String = "",
    val message: String = "",
    /** 界面动作已提交、但效果没有确认（status=unconfirmed / outcome=UNKNOWN、NO_OBSERVED_CHANGE）。 */
    val unconfirmed: Boolean = false,
) {
    companion object {
        fun of(name: String, content: String): ToolOutcomeSummary {
            val json = runCatching { JSONObject(content) }.getOrNull()
                ?: return ToolOutcomeSummary(name, ok = true)
            val exit = json.opt("exit_code")?.toString()?.toIntOrNull()
            val ok = json.optBoolean("ok", exit == null || exit == 0)
            val code = json.optString("code").ifBlank { if (!ok && exit != null) "EXIT_$exit" else "" }
            val message = listOf("message", "error", "stderr")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }.orEmpty()
            val outcome = json.optString("outcome")
            val unconfirmed = json.optString("status") == "unconfirmed" || outcome == "UNKNOWN" || outcome == "NO_OBSERVED_CHANGE"
            return ToolOutcomeSummary(name, ok, code, message.takeLast(MESSAGE_LIMIT), unconfirmed)
        }

        private const val MESSAGE_LIMIT = 300
    }
}

/**
 * 交卷前自检（etax/design/10 第一期）：模型这一轮不再调用工具、准备把这段话交给用户之前，看一眼这次运行的工具结果。
 * 还有没解决的失败、或者回答像是放弃了、或者说「已完成」而最后一步界面动作效果没确认时，先让模型复核一次再交卷。
 * 每轮用户请求最多一次（[alreadyReviewed]），不会循环；没用过工具的回答不检查。
 */
internal object CompletionGate {
    enum class Reason(val wire: String) {
        LastToolFailed("last_tool_failed"),
        GaveUpAfterFailure("gave_up_after_failure"),
        UnconfirmedAction("unconfirmed_action"),
    }

    data class Request(val reason: Reason, val failures: List<ToolOutcomeSummary>) {
        fun prompt(): String = buildString {
            append("【交卷前复核】回答之前先复核一次。")
            if (reason == Reason.UnconfirmedAction) {
                val last = failures.lastOrNull()
                append("最后一个界面操作（").append(last?.name.orEmpty()).append("）已经提交，但效果没有确认。")
                append("先重新观察一次界面，核对最终结果再回答；发送、支付、删除这类已经提交的操作不要重复执行。")
                return@buildString
            }
            append("这次运行里还有没解决的失败：\n")
            failures.takeLast(MAX_LISTED).forEachIndexed { index, failure ->
                append(index + 1).append(". ").append(failure.name)
                if (failure.code.isNotBlank()) append(" · ").append(failure.code)
                if (failure.message.isNotBlank()) append(" · ").append(failure.message.replace('\n', ' '))
                append('\n')
            }
            append("请先判断是不是参数、路径、转义、权限、拼写这类小问题；能修就修好后重试，并核对最终结果。")
            append("不要重复已经成功的步骤。确实做不到时，在答复里写清楚试过什么、最后一条报错原文是什么。")
        }
    }

    fun review(outcomes: List<ToolOutcomeSummary>, answer: String, alreadyReviewed: Boolean): Request? {
        if (alreadyReviewed || outcomes.isEmpty()) return null
        val relevant = outcomes.filterNot { it.code in TerminalCodes }
        if (relevant.isEmpty()) return null
        val unresolved = unresolvedFailures(relevant)
        val last = relevant.last()
        if (!last.ok && unresolved.isNotEmpty()) return Request(Reason.LastToolFailed, unresolved)
        if (unresolved.isNotEmpty() && answer.looksLikeGaveUp()) return Request(Reason.GaveUpAfterFailure, unresolved)
        if (last.ok && last.unconfirmed && answer.claimsDone()) return Request(Reason.UnconfirmedAction, listOf(last))
        return null
    }

    /** 失败之后同名工具再没有成功过的那些失败（同名工具后来成功了，视为已经修好）。 */
    internal fun unresolvedFailures(outcomes: List<ToolOutcomeSummary>): List<ToolOutcomeSummary> =
        outcomes.withIndex().filter { (index, outcome) ->
            !outcome.ok && outcomes.drop(index + 1).none { it.name == outcome.name && it.ok }
        }.map { it.value }

    private fun String.claimsDone(): Boolean {
        val tail = takeLast(300).lowercase()
        return DonePhrases.any { it in tail }
    }

    /** 这些失败是用户或系统明确叫停的，复核也没用：工具被关掉、子代理被停止、界面操作已判定无进展、用户接管了应用。 */
    private val TerminalCodes = setOf(
        "TOOL_DISABLED", "SUBAGENT_DISABLED", "SUBAGENT_CANCELLED", "NO_PROGRESS", "APP_TAKEN_OVER",
        "TOOL_NOT_AVAILABLE",
    )

    private val DonePhrases = listOf(
        "已完成", "已发送", "已设置", "已保存", "已开启", "已关闭", "已打开", "已经发送", "已经设置", "已经完成", "搞定",
        "已發送", "已設置", "已儲存", "done", "sent", "completed",
    )

    private const val MAX_LISTED = 5
}
