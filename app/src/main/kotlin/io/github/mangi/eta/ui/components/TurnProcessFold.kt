package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi

/** 一轮过程的摘要：思考几次、几步工具、几步失败、思考用时（秒，取不到为 null）。 */
internal data class TurnProcessSummary(val thinking: Int, val steps: Int, val failed: Int, val seconds: Int?)

/**
 * 整轮过程收成一行（etax/design/09 第二期 2.1）：回复完成后，一轮里「用户消息之后、最终回答之前」的全部条目
 * （思考、工具步骤、中间说明）换成一条 [AgentTimelineEntry.TurnProcess]；key 在 [expandedKeys] 里时摘要行后面照常列出这些条目。
 *
 * 不折叠：正在进行的最后一轮（[latestTurnRunning]）、没有最终回答的轮（被停止、出错）、过程里没有思考或工具步骤的轮。
 * key 取「turn-」加这一轮第一条过程条目的 key，历史前插后不变；摘要和展开状态都没变时复用 [previous] 里的同一实例。
 */
internal fun foldFinishedTurns(
    entries: List<AgentTimelineEntry>,
    latestTurnRunning: Boolean,
    expandedKeys: Set<String>,
    previous: List<AgentTimelineEntry> = emptyList(),
): List<AgentTimelineEntry> {
    val userIndices = entries.indices.filter { entries[it].isUserMessage() }
    if (userIndices.isEmpty()) return entries
    val previousFolds = previous.asSequence().filterIsInstance<AgentTimelineEntry.TurnProcess>().associateBy { it.key }
    val result = ArrayList<AgentTimelineEntry>(entries.size)
    result.addAll(entries.subList(0, userIndices.first()))
    userIndices.forEachIndexed { n, start ->
        val end = userIndices.getOrNull(n + 1) ?: entries.size
        result.add(entries[start])
        val body = entries.subList(start + 1, end)
        val finalIndex = body.indexOfLast { it is AgentTimelineEntry.Message && it.message is AgentMessageUi }
        val process = if (finalIndex > 0) body.subList(0, finalIndex) else emptyList()
        val running = latestTurnRunning && n == userIndices.lastIndex
        if (running || process.none { it is AgentTimelineEntry.WorkProcess }) {
            result.addAll(body)
            return@forEachIndexed
        }
        val key = "turn-${process.first().key}"
        val summary = summarize(process)
        val expanded = key in expandedKeys
        val reused = previousFolds[key]?.takeIf { it.summary == summary && it.expanded == expanded }
        result.add(reused ?: AgentTimelineEntry.TurnProcess(key, summary, expanded))
        if (expanded) result.addAll(process)
        result.addAll(body.subList(finalIndex, body.size))
    }
    return result
}

private fun AgentTimelineEntry.isUserMessage(): Boolean =
    this is AgentTimelineEntry.Message && message is UserMessageUi

private fun summarize(process: List<AgentTimelineEntry>): TurnProcessSummary {
    var thinking = 0; var steps = 0; var failed = 0; var seconds = 0; var timed = false
    process.forEach { entry ->
        if (entry !is AgentTimelineEntry.WorkProcess) return@forEach
        entry.messages.forEach { message ->
            when (message) {
                is ThinkingMessageUi -> {
                    thinking++
                    message.elapsedSeconds?.let { seconds += it; timed = true }
                }
                is ToolActivityMessageUi -> {
                    steps++
                    if (message.status == ToolActivityStatusUi.Failed) failed++
                }
                else -> Unit
            }
        }
    }
    return TurnProcessSummary(thinking, steps, failed, seconds.takeIf { timed })
}
