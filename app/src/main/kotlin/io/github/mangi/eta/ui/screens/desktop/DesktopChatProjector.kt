package io.github.mangi.eta.ui.screens.desktop

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

    data class Labels(
        val retryPrompt: String = "继续",
        val attachment: String = "附件",
        val image: String = "图片",
    )

    fun messages(state: DesktopSessionState, labels: Labels = Labels()): List<AgentChatMessageUi> {
        val results = HashMap<String, DesktopItem.ToolResult>()
        for (item in state.items) if (item is DesktopItem.ToolResult) results[item.callId] = item
        // 轮次结束后仍没有结果的工具（被打断）显示为未知，而不是一直转圈。
        val endedTurns = state.items.filterIsInstance<DesktopItem.TurnEnd>().map { it.seq }
        val out = ArrayList<AgentChatMessageUi>(state.items.size + 4)
        for (item in state.items) {
            when (item) {
                is DesktopItem.User -> out += userMessage(item, labels)
                is DesktopItem.Assistant -> {
                    val turnClosed = endedTurns.any { it > item.seq }
                    out += assistantMessages(item, results, turnClosed)
                }
                is DesktopItem.TurnEnd -> turnEndNotice(item)?.let(out::add)
                is DesktopItem.Notice -> noticeMessage(item)?.let(out::add)
                is DesktopItem.ToolResult, is DesktopItem.TurnStart, is DesktopItem.Title -> Unit
            }
        }
        for (echo in state.echoes) {
            if (!echo.queued) out += UserMessageUi(id = "echo-${echo.requestId}", content = echo.text)
        }
        state.live?.let { live -> out += liveMessages(live.attemptId, live.blocks) }
        // 列表 key 必须唯一：持久条目与临时行短暂并存时，以持久条目为准。
        return out.distinctBy { it.id }
    }

    private fun userMessage(item: DesktopItem.User, labels: Labels): UserMessageUi {
        val extras = buildList {
            if (item.images > 0) add("[${labels.image} ×${item.images}]")
            item.files.forEach { add("[${labels.attachment}] $it") }
        }
        val text = item.text.ifBlank { if (item.source == "retry") labels.retryPrompt else "" }
        val content = (listOf(text) + extras).filter { it.isNotBlank() }.joinToString("\n")
        return UserMessageUi(id = "u-${item.seq}", content = content)
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
        contextTokens = total ?: input?.let { it + (output ?: 0) },
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
                        contextWindow = null,
                        reasoning = model.efforts.isNotEmpty(),
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
