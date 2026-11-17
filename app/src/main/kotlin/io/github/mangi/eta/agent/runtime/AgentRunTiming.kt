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
    private var completionReviews = 0
    // 11 号方案第零期：失败原因、用到界面的轮数、只看不动的轮数。
    private val failureCodes = linkedMapOf<String, Int>()
    // 11 号方案第四期：每次请求的输入 token 和命中缓存的 token（量化系统提示瘦身的效果）。
    // 每轮结束发来的用量是「这一轮用户请求到目前为止」的累计值，用户中途补充指令时从零重计；
    // 所以按段记最后一次累计值，段落切换（步数没有增加）时把上一段并入总数。
    private var stepsCommitted = 0
    private var inputCommitted = 0L
    private var cachedCommitted = 0L
    private var segmentSteps = 0
    private var segmentInput = 0L
    private var segmentCached = 0L
    private val roundTools = linkedMapOf<Int, MutableSet<String>>()
    private var finished: RunPerformanceSummary? = null

    private fun commitUsageSegment() {
        stepsCommitted += segmentSteps
        inputCommitted += segmentInput
        cachedCommitted += segmentCached
        segmentSteps = 0
        segmentInput = 0
        segmentCached = 0
    }

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
                if (roundTools.size < 512) roundTools.getOrPut(event.round) { linkedSetOf() } += safeName(event.name)
                if (activeTools.size < 256) activeTools[event.round to event.toolCallId] = safeName(event.name) to lastAt
            }
            is AgentEvent.ToolFinished -> {
                completedTools++
                inFlightTools = (inFlightTools - 1).coerceAtLeast(0)
                if (event.success == false) {
                    failedTools++
                    val code = event.failureCode?.takeIf { it.matches(Regex("[A-Z0-9_]{2,48}")) } ?: "UNKNOWN"
                    if (code in failureCodes || failureCodes.size < 16) failureCodes[code] = (failureCodes[code] ?: 0) + 1
                }
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
            is AgentEvent.CompletionReview -> completionReviews++
            is AgentEvent.UsageReceived -> {
                val usage = event.usage
                val steps = usage.stepCount
                val input = usage.inputTokens
                if ((usage.requestKind ?: AgentTokenUsage.REQUEST_KIND_CHAT) == AgentTokenUsage.REQUEST_KIND_CHAT &&
                    steps != null && steps > 0 && input != null && input > 0
                ) {
                    if (steps <= segmentSteps) commitUsageSegment()
                    segmentSteps = steps
                    segmentInput = input.toLong()
                    segmentCached = (usage.cachedTokens ?: 0).toLong().coerceIn(0L, segmentInput)
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
            completionReviews = completionReviews,
            failureCodes = failureCodes.toMap(),
            avgInputTokens = commitUsageSegment().let { if (stepsCommitted > 0) (inputCommitted / stepsCommitted).toInt() else 0 },
            avgCachedTokens = if (stepsCommitted > 0) (cachedCommitted / stepsCommitted).toInt() else 0,
            guiRounds = roundTools.values.count { tools -> tools.any(GuiRounds::isScreenTool) },
            observeOnlyRounds = roundTools.values.count { tools ->
                tools.any(GuiRounds::isScreenTool) && tools.filter(GuiRounds::isScreenTool).all(GuiRounds::isLookOnly)
            },
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

/** 屏幕类工具的划分（只用于运行摘要统计）。 */
internal object GuiRounds {
    private val lookOnly = setOf("observe_screen", "wait", "wait_for_text", "wait_for_package")
    fun isScreenTool(name: String): Boolean =
        name in lookOnly || io.github.mangi.eta.agent.tool.AgentToolRequirements.requiresGuiControl(name)
    fun isLookOnly(name: String): Boolean = name in lookOnly
    fun isStale(code: String): Boolean = code.startsWith("OBSERVATION_") || code.endsWith("_STALE") ||
        code == "OBSERVE_AFTER_RESUME" || code == "BACKGROUND_OBSERVATION_REQUIRED" || code == "BACKGROUND_OBSERVATION_CHANGED"
}
