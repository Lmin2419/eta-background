package io.github.mangi.eta.ui.screens.desktop

import io.github.mangi.eta.agent.remote.DesktopBridge
import io.github.mangi.eta.agent.remote.DesktopBlock
import io.github.mangi.eta.agent.remote.DesktopItem
import io.github.mangi.eta.agent.remote.DesktopLiveBlock
import io.github.mangi.eta.agent.remote.DesktopModelCatalog
import io.github.mangi.eta.agent.remote.DesktopSessionState
import io.github.mangi.eta.agent.remote.DesktopUsage
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.AgentModelProviderGroupUi
import io.github.mangi.eta.ui.model.QueuedMessageUi
import io.github.mangi.eta.ui.model.SystemNoticeCode
import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.TokenUsageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 把桌面会话镜像投影为胖小胖现有聊天界面使用的消息模型，界面组件因此原样复用。 */
internal object DesktopChatProjector {
    private val lenientJson = Json { isLenient = true }

    /** 界面文案；默认中文，界面层用资源字符串覆盖，单测直接用默认值。 */
    data class Labels(
        val retryPrompt: String = "继续",
        val attachment: String = "附件",
        val image: String = "图片",
        val turnTitle: (duration: String?, tokens: String?, cacheHit: Int?) -> String = { d, tk, c ->
            listOfNotNull("本轮", d, tk, c?.let { "缓存命中 $it%" }).joinToString(" · ")
        },
        val modelLine: (String) -> String = { "模型：$it" },
        val usageLine: (input: String, cacheRead: String, cacheWrite: String?, output: String, reasoning: String?) -> String =
            { i, r, w, o, re ->
                buildString {
                    append("未缓存输入 $i · 缓存读取 $r")
                    if (w != null) append(" · 缓存写入 $w")
                    append(" · 输出 $o")
                    if (re != null) append("（其中推理 $re）")
                }
            },
        val speedLine: (ttft: String?, tps: String?, tools: String?) -> String = { f, s, tl ->
            listOfNotNull(f?.let { "首字 $it" }, s?.let { "输出 $it tok/s" }, tl?.let { "工具用时 $it" }).joinToString(" · ")
        },
        val timeLine: (start: String, steps: Int) -> String = { s, n -> "开始于 $s · 共 $n 步" },
        val trigger: (kind: String, summary: String?) -> String = { kind, summary ->
            when (kind) {
                "subagent-settled" -> "子代理已结束" + (summary?.let { "：$it" } ?: "")
                "agent-message" -> "子代理发来消息"
                "schedule" -> "定时任务"
                "goal" -> "目标续跑"
                "team" -> "团队消息"
                "webhook" -> "Webhook"
                else -> summary ?: "系统消息"
            }
        },
        val duration: (Long) -> String = ::defaultDuration,
        val clock: (Long) -> String = ::defaultClock,
    )

    fun messages(state: DesktopSessionState, labels: Labels = Labels()): List<AgentChatMessageUi> =
        merge(committed(state.items, labels), state)

    /**
     * 已写进记录的部分。只取决于记录本身，界面按记录缓存它：
     * 流式输出每秒几十次的增量不会让整段历史（含每个工具参数的解析）重算。
     */
    fun committed(items: List<DesktopItem>, labels: Labels = Labels()): List<AgentChatMessageUi> {
        val results = HashMap<String, DesktopItem.ToolResult>()
        for (item in items) if (item is DesktopItem.ToolResult) results[item.callId] = item
        // 轮次结束后仍没有结果的工具（被打断）显示为未知，而不是一直转圈。
        val lastTurnEnd = items.lastOrNull { it is DesktopItem.TurnEnd }?.seq ?: -1L
        val turns = HashMap<Int, TurnAccumulator>()
        var currentTurn: TurnAccumulator? = null
        val toolStarts = HashMap<String, Long>()
        val out = ArrayList<AgentChatMessageUi>(items.size + 8)
        for (item in items) {
            when (item) {
                is DesktopItem.User -> out += userMessage(item, labels)
                is DesktopItem.Trigger -> out += triggerMarker(item, labels)
                is DesktopItem.Assistant -> {
                    val turnClosed = lastTurnEnd > item.seq
                    out += assistantMessages(item, results, turnClosed)
                    turns.getOrPut(item.turn) { TurnAccumulator(item.turn) }.addAssistant(item)
                }
                is DesktopItem.TurnStart -> currentTurn = turns.getOrPut(item.turn) { TurnAccumulator(item.turn) }.also { it.startedAt = item.time }
                is DesktopItem.StepStart -> turns.getOrPut(item.turn) { TurnAccumulator(item.turn) }.stepStarts[item.step] = item.time
                is DesktopItem.ToolCall -> toolStarts[item.callId] = item.time
                is DesktopItem.ToolResult -> toolStarts.remove(item.callId)?.let { start ->
                    currentTurn?.let { it.toolMs += (item.time - start).coerceAtLeast(0) }
                }
                is DesktopItem.TurnEnd -> {
                    turnEndNotice(item)?.let(out::add)
                    turns[item.turn]?.let { turn ->
                        turn.endedAt = item.time
                        turnUsageMarker(turn, item.seq, labels)?.let(out::add)
                    }
                }
                is DesktopItem.Notice -> noticeMessage(item)?.let(out::add)
                is DesktopItem.Title -> Unit
            }
        }
        return out.distinctBy { it.id }
    }

    /** 在已记录的消息后面接上还没落定的部分：刚发出的回显、正在生成的内容、已插队待送达的消息。 */
    fun merge(committed: List<AgentChatMessageUi>, state: DesktopSessionState): List<AgentChatMessageUi> {
        val steering = state.steering
        if (state.live == null && steering.isEmpty() && state.echoes.none { !it.queued }) return committed
        val out = ArrayList<AgentChatMessageUi>(committed.size + 8)
        out += committed
        for (echo in state.echoes) {
            if (!echo.queued) out += UserMessageUi(id = "echo-${echo.requestId}", content = echo.text, images = echo.images)
        }
        state.live?.let { live -> out += liveMessages(live.attemptId, live.blocks) }
        // 已插队的消息：桌面把它显示为已发出、等当前这一步结束送达的气泡，手机同样放在对话末尾。
        for (item in steering) out += UserMessageUi(id = "steer-${item.id}", content = item.text)
        // 列表 key 必须唯一：持久条目与临时行短暂并存时，以持久条目为准。
        return out.distinctBy { it.id }
    }

    /** 一轮内的用量与耗时，口径与桌面「本轮用量」一致。 */
    private class TurnAccumulator(val turn: Int) {
        var startedAt: Long? = null
        var endedAt: Long? = null
        val stepStarts = HashMap<Int, Long>()
        var input = 0L
        var output = 0L
        var cacheRead = 0L
        var cacheWrite = 0L
        var reasoning = 0L
        var hasUsage = false
        var hasCacheWrite = false
        var toolMs = 0L
        var ttftMs = 0L
        var ttftSamples = 0
        var decodeMs = 0L
        var decodeTokens = 0L
        var steps = 0
        val models = LinkedHashSet<String>()

        fun addAssistant(item: DesktopItem.Assistant) {
            steps++
            item.model?.let { models += "${it.provider} / ${it.model}" }
            item.usage?.let { usage ->
                hasUsage = true
                input += usage.input ?: 0
                output += usage.output ?: 0
                cacheRead += usage.cacheRead ?: 0
                usage.cacheWrite?.let { cacheWrite += it; if (it > 0) hasCacheWrite = true }
                reasoning += usage.reasoning ?: 0
            }
            val first = item.firstTokenAt ?: return
            stepStarts[item.step]?.let { start -> ttftMs += (first - start).coerceAtLeast(0); ttftSamples++ }
            val out = item.usage?.output ?: 0
            if (out > 0 && item.time > first) {
                decodeMs += item.time - first
                decodeTokens += out
            }
        }
    }

    private fun turnUsageMarker(turn: TurnAccumulator, seq: Long, labels: Labels): SystemNoticeMessageUi? {
        if (!turn.hasUsage && turn.steps == 0) return null
        val promptSide = turn.input + turn.cacheRead + turn.cacheWrite
        val cacheHit = if (promptSide > 0) kotlin.math.round(turn.cacheRead * 100.0 / promptSide).toInt() else null
        val duration = turn.startedAt?.let { start -> turn.endedAt?.let { end -> labels.duration((end - start).coerceAtLeast(0)) } }
        val total = promptSide + turn.output
        val title = labels.turnTitle(duration, if (turn.hasUsage) "${compactTokens(total)} tok" else null, if (turn.hasUsage) cacheHit else null)
        val details = buildList {
            if (turn.models.isNotEmpty()) add(labels.modelLine(turn.models.joinToString("，")))
            if (turn.hasUsage) add(labels.usageLine(
                exactTokens(turn.input),
                exactTokens(turn.cacheRead),
                if (turn.hasCacheWrite) exactTokens(turn.cacheWrite) else null,
                exactTokens(turn.output),
                if (turn.reasoning > 0) exactTokens(turn.reasoning) else null,
            ))
            val ttft = if (turn.ttftSamples > 0) labels.duration(turn.ttftMs / turn.ttftSamples) else null
            val tps = if (turn.decodeMs > 0) formatRate(turn.decodeTokens * 1000.0 / turn.decodeMs) else null
            val tools = if (turn.toolMs > 0) labels.duration(turn.toolMs) else null
            labels.speedLine(ttft, tps, tools).takeIf { it.isNotBlank() }?.let(::add)
            turn.startedAt?.let { add(labels.timeLine(labels.clock(it), turn.steps)) }
        }.joinToString("\n")
        return marker("usage-$seq", MARKER_USAGE, title, details)
    }

    private fun triggerMarker(item: DesktopItem.Trigger, labels: Labels): SystemNoticeMessageUi {
        val icon = when (item.kind) {
            "subagent-settled", "agent-message" -> MARKER_SUBAGENT
            "schedule" -> MARKER_SCHEDULE
            else -> MARKER_TRIGGER
        }
        val summary = item.summary ?: item.stopReason
        val details = listOf(labels.clock(item.time), item.text).filter { it.isNotBlank() }.joinToString("\n")
        return marker("trigger-${item.seq}", icon, labels.trigger(item.kind, summary), details)
    }

    /** 居中的小胶囊行（与「执行统计」同一种样式），点开看详情。 */
    fun marker(id: String, icon: String, title: String, details: String): SystemNoticeMessageUi =
        SystemNoticeMessageUi(
            id = id,
            code = SystemNoticeCode.Marker,
            detail = JsonObject(mapOf(
                "icon" to JsonPrimitive(icon),
                "title" to JsonPrimitive(title),
                "details" to JsonPrimitive(details),
            )).toString(),
        )

    const val MARKER_USAGE = "usage"
    const val MARKER_SUBAGENT = "subagent"
    const val MARKER_SCHEDULE = "schedule"
    const val MARKER_TRIGGER = "trigger"

    fun compactTokens(value: Long): String = when {
        value >= 1_000_000 -> trimDecimal(value / 1_000_000.0) + "M"
        value >= 1_000 -> trimDecimal(value / 1_000.0) + "k"
        else -> value.toString()
    }

    fun exactTokens(value: Long): String = "%,d".format(java.util.Locale.ROOT, value)

    fun formatRate(value: Double): String = if (value >= 100) value.toLong().toString() else trimDecimal(value)

    private fun trimDecimal(value: Double): String {
        val rounded = kotlin.math.round(value * 10) / 10
        return if (rounded == kotlin.math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    fun defaultDuration(ms: Long): String {
        if (ms < 1_000) return "${ms}毫秒"
        val seconds = ms / 1000.0
        if (seconds < 60) return trimDecimal(seconds) + "秒"
        val total = (ms / 1000)
        return if (total < 3600) "${total / 60}分${total % 60}秒" else "${total / 3600}小时${(total % 3600) / 60}分"
    }

    fun defaultClock(epochMs: Long): String {
        val zone = java.time.ZoneId.systemDefault()
        val time = java.time.Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = java.time.LocalDate.now(zone)
        val pattern = if (time.toLocalDate() == today) "HH:mm" else "M月d日 HH:mm"
        return time.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
    }

    private fun userMessage(item: DesktopItem.User, labels: Labels): UserMessageUi {
        val extras = buildList {
            // 有附件引用的图片直接显示缩略图；老插件只报数量时保留文字标记。
            val unseen = item.images - item.imageRefs.size
            if (unseen > 0) add("[${labels.image} ×$unseen]")
            item.files.forEach { add("[${labels.attachment}] $it") }
        }
        val text = item.text.ifBlank { if (item.source == "retry") labels.retryPrompt else "" }
        val content = (listOf(text) + extras).filter { it.isNotBlank() }.joinToString("\n")
        return UserMessageUi(id = "u-${item.seq}", content = content, images = item.imageRefs.map(DesktopBridge::imageAddress))
    }

    private fun assistantMessages(
        item: DesktopItem.Assistant,
        results: Map<String, DesktopItem.ToolResult>,
        turnClosed: Boolean,
    ): List<AgentChatMessageUi> {
        val out = ArrayList<AgentChatMessageUi>(item.blocks.size)
        val lastTextIndex = item.blocks.indexOfLast { it is DesktopBlock.Text && it.text.isNotBlank() }
        item.blocks.forEachIndexed { index, block ->
            val id = "a-${item.seq}-$index"
            when (block) {
                is DesktopBlock.Reasoning -> if (block.text.isNotBlank()) {
                    out += ThinkingMessageUi(id = id, content = block.text.trim(), isStreaming = false, collapsed = true)
                }
                is DesktopBlock.Text -> if (block.text.isNotBlank()) {
                    out += AgentMessageUi(
                        id = id,
                        content = block.text.trimEnd(),
                        usage = if (index == lastTextIndex) item.usage?.toUi() else null,
                    )
                }
                is DesktopBlock.Tool -> {
                    val result = results[block.id]
                    val (summary, command) = toolSummary(block.name, block.args)
                    out += ToolActivityMessageUi(
                        id = "t-${block.id.ifBlank { id }}",
                        toolName = block.name,
                        status = when {
                            result == null && turnClosed -> ToolActivityStatusUi.Unknown
                            result == null -> ToolActivityStatusUi.Running
                            result.isError -> ToolActivityStatusUi.Failed
                            else -> ToolActivityStatusUi.Success
                        },
                        argumentsSummary = summary,
                        command = command,
                        resultSummary = result?.let { r ->
                            val text = r.text.ifBlank { r.errorReason.orEmpty() }
                            if (r.truncated) "$text\n…" else text
                        },
                        imageCount = result?.images ?: 0,
                    )
                }
            }
        }
        if (item.interrupted) out += SystemNoticeMessageUi(id = "n-${item.seq}", code = SystemNoticeCode.Interrupted)
        return out
    }

    private fun liveMessages(attemptId: String, blocks: List<DesktopLiveBlock>): List<AgentChatMessageUi> {
        val lastIndex = blocks.lastOrNull()?.index
        return blocks.mapNotNull { block ->
            val id = "live-$attemptId-${block.index}"
            val streaming = block.index == lastIndex
            when (block.kind) {
                "reasoning" -> block.text.takeIf { it.isNotBlank() }?.let {
                    ThinkingMessageUi(id = id, content = it.trim(), isStreaming = streaming, collapsed = !streaming)
                }
                "tool" -> ToolActivityMessageUi(
                    id = "t-${block.toolId ?: id}",
                    toolName = block.toolName.orEmpty(),
                    status = ToolActivityStatusUi.Running,
                    argumentsSummary = toolSummary(block.toolName.orEmpty(), block.text).first,
                    command = toolSummary(block.toolName.orEmpty(), block.text).second,
                )
                else -> block.text.takeIf { it.isNotBlank() }?.let {
                    AgentMessageUi(id = id, content = it, isStreaming = streaming)
                }
            }
        }
    }

    private fun turnEndNotice(item: DesktopItem.TurnEnd): SystemNoticeMessageUi? = when (item.reason) {
        "aborted" -> SystemNoticeMessageUi(id = "n-${item.seq}", code = SystemNoticeCode.Stopped)
        "interrupted" -> SystemNoticeMessageUi(id = "n-${item.seq}", code = SystemNoticeCode.Interrupted)
        "error" -> SystemNoticeMessageUi(id = "n-${item.seq}", code = SystemNoticeCode.RuntimeFailed, detail = item.message)
        else -> null
    }

    private fun noticeMessage(item: DesktopItem.Notice): SystemNoticeMessageUi? = when (item.code) {
        "retry" -> SystemNoticeMessageUi(id = "n-${item.seq}", code = SystemNoticeCode.ModelRetry, detail = item.text.ifBlank { null })
        "compaction" -> SystemNoticeMessageUi(id = "n-${item.seq}", code = SystemNoticeCode.ContextCompaction)
        else -> null
    }

    private fun DesktopUsage.toUi(): TokenUsageUi = TokenUsageUi(
        // 与桌面上下文占用口径一致：输入侧（未缓存 + 缓存读写）加本次输出。
        contextTokens = total ?: ((input ?: 0) + (cacheRead ?: 0) + (cacheWrite ?: 0) + (output ?: 0)).takeIf { it > 0 },
        inputTokens = input,
        outputTokens = output,
        reasoningTokens = reasoning,
        cachedTokens = cacheRead,
    )

    /**
     * 工具卡标题与命令：DSH 的 bash 要求模型写 `description`，正好作为一句话标题；
     * 参数还在流式拼接、不是完整 JSON 时退回工具名。
     */
    fun toolSummary(name: String, args: String): Pair<String, String?> {
        val json = runCatching { lenientJson.parseToJsonElement(args) as? JsonObject }.getOrNull()
            ?: return "" to null
        fun field(key: String) = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val command = field("command") ?: field("cmd")
        val summary = field("description")
            ?: field("path")
            ?: field("file_path")
            ?: field("pattern")
            ?: field("query")
            ?: field("url")
            ?: ""
        return summary to command
    }

    fun queued(state: DesktopSessionState): List<QueuedMessageUi> =
        state.visibleQueue.map { QueuedMessageUi(id = it.id, content = it.text, immediateInterrupt = it.steering) }

    // ---- 模型选择 ----------------------------------------------------------

    fun optionId(provider: String, model: String) = "$provider\u0000$model"

    fun parseOptionId(id: String): Pair<String, String>? {
        val parts = id.split('\u0000', limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    fun modelPicker(catalog: DesktopModelCatalog?, state: DesktopSessionState): AgentModelPickerUiState {
        catalog ?: return AgentModelPickerUiState()
        val groups = catalog.groups.map { group ->
            val sourceType = sourceTypeFor(group.id)
            AgentModelProviderGroupUi(
                providerId = group.id,
                providerName = group.name,
                providerSourceType = sourceType,
                models = group.models.map { model ->
                    AgentModelOptionUi(
                        id = optionId(group.id, model.id),
                        providerId = group.id,
                        providerName = group.name,
                        providerSourceType = sourceType,
                        modelId = model.id,
                        displayName = model.name,
                        contextWindow = state.pressure?.contextWindow?.toInt(),
                        reasoning = model.efforts.any { it != "off" },
                    )
                },
            )
        }
        val selection = state.model ?: catalog.default
        val selected = selection?.let { s -> groups.firstOrNull { it.providerId == s.provider }?.models?.firstOrNull { it.modelId == s.model } }
        return AgentModelPickerUiState(providerGroups = groups, selectedModel = selected)
    }

    fun reasoningEfforts(catalog: DesktopModelCatalog?, state: DesktopSessionState): Pair<List<ReasoningEffort>, ReasoningEffort> {
        val selection = state.model ?: catalog?.default ?: return emptyList<ReasoningEffort>() to ReasoningEffort.DEFAULT
        val model = catalog?.find(selection.provider, selection.model) ?: return emptyList<ReasoningEffort>() to ReasoningEffort.DEFAULT
        val efforts = model.efforts.mapNotNull { id -> ReasoningEffort.entries.firstOrNull { it.wireValue == id } }
        val current = (selection.reasoningEffort ?: model.defaultEffort)
            ?.let { id -> ReasoningEffort.entries.firstOrNull { it.wireValue == id } }
            ?: efforts.firstOrNull()
            ?: ReasoningEffort.DEFAULT
        return efforts to current
    }

    private fun sourceTypeFor(providerId: String): String {
        val id = providerId.lowercase()
        return when {
            "deepseek" in id -> ProviderSourceTypes.DEEPSEEK
            "anthropic" in id || "claude" in id -> ProviderSourceTypes.ANTHROPIC
            "gemini" in id || "google" in id -> ProviderSourceTypes.GEMINI
            "openai" in id || "codex" in id -> ProviderSourceTypes.OPENAI
            "moonshot" in id || "kimi" in id -> ProviderSourceTypes.MOONSHOT
            "openrouter" in id -> ProviderSourceTypes.OPENROUTER
            else -> ProviderSourceTypes.CUSTOM
        }
    }
}
