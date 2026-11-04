package io.github.mangi.eta.agent.runtime

import android.os.SystemClock
import io.github.mangi.eta.core.AgentLogger

/** Constant-size phase totals and at most 256 in-flight tools; no prompts, images or result bodies. */
internal class AgentRunTiming(
    private val logger: AgentLogger,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private enum class Phase { PREPARATION, MODEL, TOOLS, PAUSED, OTHER }
    private val startedAt = now()
    private var lastAt = startedAt
    private val durations = LongArray(Phase.entries.size)
    private var preparing = true
    private var paused = false
    private var cleaningUp = false
    private var requestAt: Long? = null
    private var gotFirstDelta = false
    private val activeTools = mutableMapOf<Pair<Int, String>, Pair<String, Long>>()
    private val slowTools = mutableListOf<RunPerformanceSummary.SlowTool>()
    private var requests = 0
    private var retries = 0
    private var toolCalls = 0
    private var failedTools = 0
    private var completedTools = 0
    private var inFlightTools = 0
    private var imageCount = 0
    private var imageBytes = 0L
    private var firstDeltaTotalMs = 0L
    private var firstDeltaSamples = 0
    private val toolTimingsMs = mutableMapOf<String, Long>()
    private var timedToolCalls = 0
    private var finished: RunPerformanceSummary? = null

    @Synchronized fun preparationFinished(skillCount: Int) {
        if (finished != null) return
        advance()
        preparing = false
        logger.debug { "Agent runtime preparation finished: elapsed_ms=${lastAt - startedAt}, skills=$skillCount" }
    }

    @Synchronized fun pauseChanged(value: Boolean) {
        if (finished != null) return
        advance()
        paused = value
    }

    @Synchronized fun beginCleanup() {
        if (finished != null) return
        advance()
        cleaningUp = true
    }

    @Synchronized fun accept(event: AgentEvent) {
        if (finished != null) return
        advance()
        when (event) {
            is AgentEvent.ProviderRequestStarted -> {
                requests++
                preparing = false
                requestAt = lastAt
                gotFirstDelta = false
            }
            is AgentEvent.AssistantBlockDelta -> if (event.deltaChars > 0) firstOutput()
            is AgentEvent.AssistantBlockStart -> if (event.kind == AgentEvent.AssistantBlockKind.TOOL_CALL &&
                !event.name.isNullOrBlank()) firstOutput()
            is AgentEvent.AssistantReceived -> requestAt = null
            is AgentEvent.ModelRetryScheduled -> { retries++; requestAt = null }
            is AgentEvent.ToolStarted -> {
                toolCalls++
                inFlightTools++
                if (activeTools.size < 256) activeTools[event.round to event.toolCallId] = safeName(event.name) to lastAt
            }
            is AgentEvent.ToolFinished -> {
                completedTools++
                inFlightTools = (inFlightTools - 1).coerceAtLeast(0)
                if (event.success == false) failedTools++
                imageCount += event.imageCount.coerceAtLeast(0)
                imageBytes += event.imageBytes.coerceAtLeast(0)
                activeTools.remove(event.round to event.toolCallId)?.let { (name, start) ->
                    val timings = ToolTimingMetadata.sanitize(event.timingMs)
                    if (timings.isNotEmpty()) timedToolCalls++
                    timings.forEach { (key, millis) ->
                        toolTimingsMs[key] = ToolTimingMetadata.add(toolTimingsMs[key] ?: 0L, millis)
                    }
                    slowTools += RunPerformanceSummary.SlowTool(name, lastAt - start)
                    slowTools.sortByDescending { it.elapsedMs }
                    if (slowTools.size > 3) slowTools.removeAt(3)
                }
            }
            is AgentEvent.RunFinished, is AgentEvent.RunFailed -> { requestAt = null; preparing = false }
            else -> Unit
        }
    }

    @Synchronized fun finish(): RunPerformanceSummary {
        finished?.let { return it }
        advance()
        return RunPerformanceSummary(
            elapsedMs = lastAt - startedAt,
            preparationMs = durations[Phase.PREPARATION.ordinal], modelMs = durations[Phase.MODEL.ordinal],
            toolsMs = durations[Phase.TOOLS.ordinal], pausedMs = durations[Phase.PAUSED.ordinal],
            otherMs = durations[Phase.OTHER.ordinal], requests = requests, retries = retries,
            toolCalls = toolCalls, failedTools = failedTools, unfinishedTools = (toolCalls - completedTools).coerceAtLeast(0),
            imageCount = imageCount, imageBytes = imageBytes,
            firstDeltaTotalMs = firstDeltaTotalMs, firstDeltaSamples = firstDeltaSamples, slowTools = slowTools.toList(),
            toolTimingsMs = toolTimingsMs.toMap(), timedToolCalls = timedToolCalls,
            firstDeltaIncludesTools = true,
        ).also { finished = it; activeTools.clear() }
    }

    private fun advance() {
        val current = now().coerceAtLeast(lastAt)
        val phase = when {
            cleaningUp -> Phase.OTHER
            paused -> Phase.PAUSED
            inFlightTools > 0 -> Phase.TOOLS
            requestAt != null -> Phase.MODEL
            preparing -> Phase.PREPARATION
            else -> Phase.OTHER
        }
        durations[phase.ordinal] += current - lastAt
        lastAt = current
    }

    private fun firstOutput() {
        if (gotFirstDelta) return
        requestAt?.let { firstDeltaTotalMs += lastAt - it; firstDeltaSamples++; gotFirstDelta = true }
    }

    private fun safeName(name: String): String = name.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,64}")) } ?: "tool"
}
