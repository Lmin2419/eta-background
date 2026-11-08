package io.github.mangi.eta.agent.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
        )
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
)

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
