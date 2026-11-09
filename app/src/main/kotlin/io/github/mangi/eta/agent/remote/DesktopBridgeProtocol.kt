package io.github.mangi.eta.agent.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * dsh-bridge 协议 v1 的客户端视图（见仓库 dsh-bridge/PROTOCOL.md）。
 *
 * 解析全部手写且容忍未知字段：桌面端插件可以先升级，旧 App 只忽略看不懂的内容。
 */
internal object DesktopBridgeProtocol {
    const val VERSION = 1

    fun parseItem(json: JsonObject): DesktopItem? {
        val seq = json.long("seq") ?: return null
        val time = json.long("time") ?: 0L
        return when (json.string("k")) {
            "user" -> DesktopItem.User(
                seq = seq,
                time = time,
                id = json.string("id") ?: "u$seq",
                text = json.string("text").orEmpty(),
                images = json.int("images") ?: 0,
                files = json.array("files")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                source = json.string("source") ?: "user",
                rpcId = json.string("rpcId"),
            )
            "assistant" -> DesktopItem.Assistant(
                seq = seq,
                time = time,
                id = json.string("id") ?: "a$seq",
                turn = json.int("turn") ?: 0,
                step = json.int("step") ?: 0,
                blocks = json.array("blocks")?.mapNotNull { parseBlock(it as? JsonObject) }.orEmpty(),
                usage = (json["usage"] as? JsonObject)?.let(::parseUsage),
                interrupted = json.bool("interrupted") == true,
                model = (json["model"] as? JsonObject)?.let { m ->
                    val provider = m.string("provider"); val model = m.string("model")
                    if (provider != null && model != null) DesktopModelSelection(provider, model, null) else null
                },
                firstTokenAt = json.long("firstTokenAt"),
            )
            "tool_result" -> DesktopItem.ToolResult(
                seq = seq,
                time = time,
                callId = json.string("callId").orEmpty(),
                isError = json.bool("isError") == true,
                text = json.string("text").orEmpty(),
                truncated = json.bool("truncated") == true,
                images = json.int("images") ?: 0,
                errorReason = (json["error"] as? JsonObject)?.let { it.string("reason") ?: it.string("code") },
            )
            "turn_start" -> DesktopItem.TurnStart(seq, time, json.int("turn") ?: 0)
            "step_start" -> DesktopItem.StepStart(seq, time, json.int("turn") ?: 0, json.int("step") ?: 0)
            "tool_call" -> DesktopItem.ToolCall(seq, time, json.string("callId").orEmpty(), json.string("name").orEmpty())
            "trigger" -> DesktopItem.Trigger(
                seq = seq,
                time = time,
                id = json.string("id") ?: "t$seq",
                kind = json.string("kind").orEmpty(),
                text = json.string("text").orEmpty(),
                summary = json.string("summary"),
                stopReason = json.string("stopReason"),
                sender = json.string("sender"),
            )
            "turn_end" -> DesktopItem.TurnEnd(
                seq = seq,
                time = time,
                turn = json.int("turn") ?: 0,
                reason = json.string("reason") ?: "unknown",
                message = json.string("message"),
                cause = json.string("cause"),
            )
            "notice" -> DesktopItem.Notice(seq, time, json.string("code").orEmpty(), json.string("text").orEmpty())
            "title" -> DesktopItem.Title(seq, time, json.string("title").orEmpty())
            else -> null
        }
    }

    private fun parseBlock(json: JsonObject?): DesktopBlock? {
        json ?: return null
        return when (json.string("type")) {
            "reasoning" -> DesktopBlock.Reasoning(json.string("text").orEmpty())
            "text" -> DesktopBlock.Text(json.string("text").orEmpty())
            "tool" -> DesktopBlock.Tool(
                id = json.string("id").orEmpty(),
                name = json.string("name").orEmpty(),
                args = json.string("args").orEmpty(),
            )
            else -> null
        }
    }

    private fun parseUsage(json: JsonObject) = DesktopUsage(
        input = json.int("input"),
        output = json.int("output"),
        total = json.int("total"),
        cacheRead = json.int("cacheRead"),
        reasoning = json.int("reasoning"),
        cacheWrite = json.int("cacheWrite"),
    )

    fun parseItems(element: JsonElement?): List<DesktopItem> =
        (element as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::parseItem) }.orEmpty()

    fun parseSessionRow(json: JsonObject): DesktopSessionRow? {
        val id = json.string("id") ?: return null
        return DesktopSessionRow(
            id = id,
            title = json.string("title"),
            updatedAt = json.long("updatedAt") ?: 0L,
            running = json.bool("running") == true,
            blank = json.bool("blank") == true,
            live = json.bool("live") == true,
            cwd = json.string("cwd"),
            parentId = json.string("parentId"),
            subagent = json.bool("subagent") == true,
            workspaceId = json.string("workspaceId"),
            pinned = json.bool("pinned") == true,
            archived = json.bool("archived") == true,
        )
    }

    fun parseServerInfo(json: JsonObject): DesktopServerInfo = DesktopServerInfo(
        version = json.string("version"),
        runtime = json.string("runtime"),
        host = json.string("host"),
        features = json.array("features")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet().orEmpty(),
    )

    fun parseQuestion(json: JsonObject): DesktopQuestion? {
        val id = json.string("id") ?: return null
        val items = parseQuestionItems(json["questions"])
        if (items.isEmpty()) return null
        return DesktopQuestion(
            id = id,
            sessionId = json.string("sessionId").orEmpty(),
            callId = json.string("callId"),
            timed = json.bool("timed") == true,
            createdAt = json.long("createdAt") ?: 0L,
            items = items,
        )
    }

    fun parseQuestionItems(element: JsonElement?): List<DesktopQuestionItem> =
        (element as? JsonArray)?.mapNotNull { entry ->
            val json = entry as? JsonObject ?: return@mapNotNull null
            val id = json.string("id") ?: return@mapNotNull null
            DesktopQuestionItem(
                id = id,
                question = json.string("question").orEmpty(),
                detail = json.string("detail"),
                header = json.string("header"),
                options = json.array("options")?.mapNotNull { option ->
                    val o = option as? JsonObject ?: return@mapNotNull null
                    o.string("label")?.let { DesktopQuestionOption(it, o.string("description")) }
                }.orEmpty(),
                multiSelect = json.bool("multiSelect") == true,
                intent = (json["intent"] as? JsonObject)?.let { intent ->
                    intent.string("kind")?.let { kind ->
                        DesktopQuestionIntent(kind, intent.string("approve"), intent.string("reject"), intent.string("always"))
                    }
                },
            )
        }.orEmpty()

    /** `questions` 投影：仍可回答的限时提问（`continued` 表示前台等待已结束，回答会作为新消息送达）。 */
    fun parsePendingQuestions(element: JsonElement?): List<DesktopPendingQuestion> =
        ((element as? JsonObject)?.get("active") as? JsonArray)?.mapNotNull { entry ->
            val json = entry as? JsonObject ?: return@mapNotNull null
            val callId = json.string("callId") ?: return@mapNotNull null
            val items = parseQuestionItems(json["questions"])
            if (items.isEmpty()) null else DesktopPendingQuestion(callId, json.string("state") == "continued", items)
        }.orEmpty()

    fun parseTurn(json: JsonObject): DesktopTurnEvent? {
        val sessionId = json.string("sessionId") ?: return null
        return DesktopTurnEvent(
            sessionId = sessionId,
            turn = json.int("turn") ?: 0,
            reason = json.string("reason") ?: "completed",
            message = json.string("message"),
            text = json.string("text").orEmpty(),
            time = json.long("time") ?: System.currentTimeMillis(),
        )
    }

    fun answersJson(answers: List<DesktopQuestionAnswer>): JsonArray = buildJsonArray {
        for (answer in answers) {
            add(buildJsonObject {
                put("id", answer.id)
                put("selected", buildJsonArray { answer.selected.forEach { add(JsonPrimitive(it)) } })
                answer.custom?.takeIf { it.isNotBlank() }?.let { put("custom", it) }
            })
        }
    }

    fun parseWorkspace(json: JsonObject): DesktopWorkspace? {
        val id = json.string("id") ?: return null
        return DesktopWorkspace(id, json.string("title") ?: json.string("path").orEmpty(), json.string("path").orEmpty())
    }

    fun parseApproval(json: JsonObject): DesktopApproval? {
        val id = json.string("id") ?: return null
        val display = json["displayReason"] as? JsonObject
        return DesktopApproval(
            id = id,
            sessionId = json.string("sessionId").orEmpty(),
            toolName = json.string("toolName").orEmpty(),
            callId = json.string("callId"),
            reason = display?.string("zh") ?: display?.string("en") ?: json.string("reason"),
            createdAt = json.long("createdAt") ?: 0L,
        )
    }

    fun parseQueue(element: JsonElement?): List<DesktopQueueItem> =
        (element as? JsonArray)?.mapNotNull { entry ->
            val json = entry as? JsonObject ?: return@mapNotNull null
            DesktopQueueItem(
                id = json.string("id") ?: return@mapNotNull null,
                text = json.string("text").orEmpty(),
                steering = json.string("placement") == "steering",
                rpcId = json.string("rpcId"),
            )
        }.orEmpty()

    /** `modelSelection` 投影：`{ lastUsed, next }`，取下一步将使用的选择。 */
    fun parseModelSelection(element: JsonElement?): DesktopModelSelection? {
        val json = element as? JsonObject ?: return null
        val pick = (json["next"] as? JsonObject) ?: (json["lastUsed"] as? JsonObject) ?: json
        val provider = pick.string("provider") ?: return null
        val model = pick.string("model") ?: return null
        return DesktopModelSelection(provider, model, pick.string("reasoningEffort"))
    }

    fun parseLive(element: JsonElement?): DesktopLiveAttempt? {
        val json = element as? JsonObject ?: return null
        val attemptId = json.string("attemptId") ?: return null
        val blocks = json.array("blocks")?.mapNotNull { parseLiveBlock(it as? JsonObject) }.orEmpty()
        return DesktopLiveAttempt(attemptId, blocks, json.int("turn"), json.int("step"))
    }

    fun parseLiveBlock(json: JsonObject?): DesktopLiveBlock? {
        json ?: return null
        return DesktopLiveBlock(
            index = json.int("index") ?: return null,
            kind = json.string("kind") ?: "text",
            text = json.string("text").orEmpty(),
            toolId = json.string("id"),
            toolName = json.string("name"),
        )
    }

    fun parseStats(element: JsonElement?): DesktopSessionStats? {
        val json = element as? JsonObject ?: return null
        return DesktopSessionStats(
            turns = json.int("turns") ?: 0,
            steps = json.int("steps") ?: 0,
            llmMs = json.long("llmMs") ?: 0,
            toolMs = json.long("toolMs") ?: 0,
            ttftMs = json.long("ttftMs") ?: 0,
            ttftSteps = json.int("ttftSteps") ?: 0,
            decodeMs = json.long("decodeMs") ?: 0,
            decodeTokens = json.long("decodeTokens") ?: 0,
        )
    }

    fun parseTokenTotals(element: JsonElement?): DesktopTokenTotals? {
        val json = element as? JsonObject ?: return null
        return DesktopTokenTotals(
            uncachedInput = json.long("uncachedInputTokens") ?: 0,
            output = json.long("outputTokens") ?: 0,
            cacheRead = json.long("cacheReadTokens") ?: 0,
            cacheWrite = json.long("cacheWriteTokens") ?: 0,
        )
    }

    fun parsePressure(element: JsonElement?): DesktopContextPressure? {
        val json = element as? JsonObject ?: return null
        return DesktopContextPressure(json.long("pressureTokens"), json.long("projectedTokens"), json.long("contextWindow"))
    }

    fun parseTiming(element: JsonElement?): DesktopSubagentTiming? {
        val json = element as? JsonObject ?: return null
        val active = json["active"] as? JsonObject
        return DesktopSubagentTiming(
            settledMs = json.long("settledMs") ?: 0,
            activeSince = active?.long("since"),
            activeThrough = active?.long("through"),
            lastTurnCompleted = json.bool("lastTurnCompleted"),
        )
    }

    fun parseSubagentEntries(element: JsonElement?): List<DesktopSubagent> =
        (element as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::parseSubagent) }.orEmpty()

    fun parseSubagent(json: JsonObject): DesktopSubagent? {
        val id = json.string("id") ?: return null
        return DesktopSubagent(
            id = id,
            mode = json.string("mode") ?: "unknown",
            label = json.string("label"),
            name = json.string("name"),
            createdAt = json.long("createdAt") ?: 0,
            title = json.string("title"),
            usage = parseTokenTotals(json["usage"]),
            timing = parseTiming(json["timing"]),
            running = json.bool("running") == true,
        )
    }

    fun parseCatalog(json: JsonObject): DesktopModelCatalog {
        val default = (json["default"] as? JsonObject)?.let { d ->
            val provider = d.string("provider")
            val model = d.string("model")
            if (provider != null && model != null) DesktopModelSelection(provider, model, d.string("reasoningEffort")) else null
        }
        val groups = json.array("groups")?.mapNotNull { entry ->
            val group = entry as? JsonObject ?: return@mapNotNull null
            val groupId = group.string("id") ?: return@mapNotNull null
            DesktopModelGroup(
                id = groupId,
                name = group.string("name") ?: groupId,
                models = group.array("models")?.mapNotNull { modelEntry ->
                    val model = modelEntry as? JsonObject ?: return@mapNotNull null
                    val reasoning = model["reasoning"] as? JsonObject
                    DesktopModel(
                        id = model.string("id") ?: return@mapNotNull null,
                        name = model.string("name") ?: model.string("id").orEmpty(),
                        efforts = reasoning?.array("efforts")?.mapNotNull { (it as? JsonObject)?.string("id") }.orEmpty(),
                        defaultEffort = reasoning?.string("defaultEffort"),
                    )
                }.orEmpty(),
            )
        }.orEmpty()
        return DesktopModelCatalog(default, groups)
    }
}

internal sealed interface DesktopItem {
    val seq: Long
    val time: Long

    data class User(
        override val seq: Long,
        override val time: Long,
        val id: String,
        val text: String,
        val images: Int,
        val files: List<String>,
        val source: String,
        val rpcId: String?,
    ) : DesktopItem

    data class Assistant(
        override val seq: Long,
        override val time: Long,
        val id: String,
        val turn: Int,
        val step: Int,
        val blocks: List<DesktopBlock>,
        val usage: DesktopUsage?,
        val interrupted: Boolean,
        val model: DesktopModelSelection? = null,
        val firstTokenAt: Long? = null,
    ) : DesktopItem

    data class ToolResult(
        override val seq: Long,
        override val time: Long,
        val callId: String,
        val isError: Boolean,
        val text: String,
        val truncated: Boolean,
        val images: Int,
        val errorReason: String?,
    ) : DesktopItem

    data class TurnStart(override val seq: Long, override val time: Long, val turn: Int) : DesktopItem

    data class StepStart(override val seq: Long, override val time: Long, val turn: Int, val step: Int) : DesktopItem

    data class ToolCall(override val seq: Long, override val time: Long, val callId: String, val name: String) : DesktopItem

    /** 运行时触发的输入（子代理结束、定时任务、团队消息等），不是用户本人发的。 */
    data class Trigger(
        override val seq: Long,
        override val time: Long,
        val id: String,
        val kind: String,
        val text: String,
        val summary: String?,
        val stopReason: String?,
        val sender: String?,
    ) : DesktopItem

    data class TurnEnd(
        override val seq: Long,
        override val time: Long,
        val turn: Int,
        val reason: String,
        val message: String?,
        val cause: String?,
    ) : DesktopItem

    data class Notice(override val seq: Long, override val time: Long, val code: String, val text: String) : DesktopItem

    data class Title(override val seq: Long, override val time: Long, val title: String) : DesktopItem
}

internal sealed interface DesktopBlock {
    data class Reasoning(val text: String) : DesktopBlock
    data class Text(val text: String) : DesktopBlock
    data class Tool(val id: String, val name: String, val args: String) : DesktopBlock
}

internal data class DesktopUsage(
    val input: Int?,
    val output: Int?,
    val total: Int?,
    val cacheRead: Int?,
    val reasoning: Int?,
    val cacheWrite: Int? = null,
)

/** 整个会话的累计 token（DSH `tokenUsage` 投影）。 */
internal data class DesktopTokenTotals(val uncachedInput: Long, val output: Long, val cacheRead: Long, val cacheWrite: Long) {
    val promptSide: Long get() = uncachedInput + cacheRead + cacheWrite
    val total: Long get() = promptSide + output

    /** 与桌面一致：缓存读取 ÷ 全部输入侧 token，没有输入时为 null。 */
    val cacheHitPercent: Int? get() = if (promptSide <= 0) null else ((cacheRead * 100.0) / promptSide).let { kotlin.math.round(it).toInt() }
}

/** DSH `sessionStats` 投影：整段日志的轮次、步数与耗时。 */
internal data class DesktopSessionStats(
    val turns: Int,
    val steps: Int,
    val llmMs: Long,
    val toolMs: Long,
    val ttftMs: Long,
    val ttftSteps: Int,
    val decodeMs: Long,
    val decodeTokens: Long,
) {
    val averageTtftMs: Long? get() = if (ttftSteps > 0) ttftMs / ttftSteps else null
    val tokensPerSecond: Double? get() = if (decodeMs > 0 && decodeTokens > 0) decodeTokens * 1000.0 / decodeMs else null
}

internal data class DesktopContextPressure(val pressureTokens: Long?, val projectedTokens: Long?, val contextWindow: Long?)

internal data class DesktopSubagentTiming(
    val settledMs: Long,
    val activeSince: Long?,
    val activeThrough: Long?,
    val lastTurnCompleted: Boolean?,
)

/** 主会话下的一个子代理（目录条目，附带状态、用量与耗时）。 */
internal data class DesktopSubagent(
    val id: String,
    val mode: String,
    val label: String?,
    val name: String?,
    val createdAt: Long,
    val title: String? = null,
    val usage: DesktopTokenTotals? = null,
    val timing: DesktopSubagentTiming? = null,
    val running: Boolean = false,
) {
    val displayTitle: String get() = label?.takeIf { it.isNotBlank() } ?: title?.takeIf { it.isNotBlank() } ?: name ?: id
    val continuable: Boolean get() = mode == "continuable"
    val openable: Boolean get() = mode != "external"
}

internal data class DesktopSessionRow(
    val id: String,
    val title: String?,
    val updatedAt: Long,
    val running: Boolean,
    val blank: Boolean,
    val live: Boolean,
    val cwd: String?,
    val parentId: String?,
    val subagent: Boolean,
    val workspaceId: String?,
    val pinned: Boolean,
    val archived: Boolean = false,
)

/** 插件自报的信息：版本、运行在桌面版（desktop）还是独立 dsh 进程（node）。 */
internal data class DesktopServerInfo(
    val version: String?,
    val runtime: String?,
    val host: String?,
    val features: Set<String>,
)

internal data class DesktopQuestionOption(val label: String, val description: String?)

/** 提问的呈现意图：`plan-review` 是计划审阅，`approval` 是授权；其余按普通选择题显示。 */
internal data class DesktopQuestionIntent(val kind: String, val approve: String?, val reject: String?, val always: String?)

internal data class DesktopQuestionItem(
    val id: String,
    val question: String,
    val detail: String?,
    val header: String?,
    val options: List<DesktopQuestionOption>,
    val multiSelect: Boolean,
    val intent: DesktopQuestionIntent?,
) {
    val isPlanReview: Boolean get() = intent?.kind == "plan-review"
}

/** Agent 正在等待回答的一次提问（一批问题），手机和桌面先答者生效。 */
internal data class DesktopQuestion(
    val id: String,
    val sessionId: String,
    val callId: String?,
    val timed: Boolean,
    val createdAt: Long,
    val items: List<DesktopQuestionItem>,
) {
    val isPlanReview: Boolean get() = items.any { it.isPlanReview }
}

/** 投影里仍可回答的限时提问；[continued] 时只能经 `question.reply` 回答。 */
internal data class DesktopPendingQuestion(val callId: String, val continued: Boolean, val items: List<DesktopQuestionItem>)

internal data class DesktopQuestionAnswer(val id: String, val selected: List<String>, val custom: String? = null)

/** 任一会话的一轮结束（插件全局推送），用于完成提醒。 */
internal data class DesktopTurnEvent(
    val sessionId: String,
    val turn: Int,
    val reason: String,
    val message: String?,
    val text: String,
    val time: Long,
)

internal data class DesktopWorkspace(val id: String, val title: String, val path: String)

internal data class DesktopApproval(
    val id: String,
    val sessionId: String,
    val toolName: String,
    val callId: String?,
    val reason: String?,
    val createdAt: Long,
)

internal data class DesktopQueueItem(val id: String, val text: String, val steering: Boolean, val rpcId: String? = null)

/** 手机刚发出、桌面还没确认的消息；[queued] 表示发送时会话正在运行，会进入待处理队列。 */
internal data class DesktopEcho(val requestId: String, val text: String, val queued: Boolean)

internal data class DesktopModelSelection(val provider: String, val model: String, val reasoningEffort: String?)

internal data class DesktopLiveBlock(
    val index: Int,
    val kind: String,
    val text: String,
    val toolId: String? = null,
    val toolName: String? = null,
)

internal data class DesktopLiveAttempt(
    val attemptId: String,
    val blocks: List<DesktopLiveBlock>,
    val turn: Int? = null,
    val step: Int? = null,
) {
    /** 追加一批增量；同 index 的文本拼接，工具名与 id 以首次出现为准。 */
    fun append(deltas: List<DesktopLiveBlock>): DesktopLiveAttempt {
        if (deltas.isEmpty()) return this
        val byIndex = blocks.associateByTo(LinkedHashMap()) { it.index }
        for (delta in deltas) {
            val existing = byIndex[delta.index]
            byIndex[delta.index] = if (existing == null) {
                delta
            } else {
                existing.copy(
                    text = existing.text + delta.text,
                    toolId = existing.toolId ?: delta.toolId,
                    toolName = existing.toolName ?: delta.toolName,
                )
            }
        }
        return copy(blocks = byIndex.values.sortedBy { it.index })
    }
}

internal data class DesktopModel(val id: String, val name: String, val efforts: List<String>, val defaultEffort: String?)

internal data class DesktopModelGroup(val id: String, val name: String, val models: List<DesktopModel>)

internal data class DesktopModelCatalog(val default: DesktopModelSelection?, val groups: List<DesktopModelGroup>) {
    fun find(provider: String, model: String): DesktopModel? =
        groups.firstOrNull { it.id == provider }?.models?.firstOrNull { it.id == model }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

internal fun JsonObject.int(key: String): Int? = long(key)?.toInt()

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
