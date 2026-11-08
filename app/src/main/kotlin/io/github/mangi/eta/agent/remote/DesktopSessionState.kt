package io.github.mangi.eta.agent.remote

import kotlinx.serialization.json.JsonObject

/**
 * 一个桌面会话在手机上的镜像。桌面是唯一真源：这里只按协议推送做纯函数归约，
 * 不做本地持久化，断线后靠 `cursor` 让插件只补差量。
 */
internal data class DesktopSessionState(
    val sessionId: String,
    val items: List<DesktopItem> = emptyList(),
    /** 已收到的最后一个持久事件 seq；-1 表示还没有快照。 */
    val cursor: Long = -1,
    val hasMore: Boolean = false,
    val title: String? = null,
    val cwd: String? = null,
    val model: DesktopModelSelection? = null,
    val queue: List<DesktopQueueItem> = emptyList(),
    val live: DesktopLiveAttempt? = null,
    /** live 已结束并提交为该 seq 的 assistant 条目，但条目还没到：先保留临时行避免闪烁。 */
    val pendingCommitSeq: Long? = null,
    val running: Boolean = false,
    val loaded: Boolean = false,
    val loadingOlder: Boolean = false,
    val error: String? = null,
    val echoes: List<DesktopEcho> = emptyList(),
) {
    val firstSeq: Long? get() = items.firstOrNull()?.seq

    fun addEcho(echo: DesktopEcho): DesktopSessionState = copy(echoes = echoes + echo)

    fun removeEcho(requestId: String): DesktopSessionState = copy(echoes = echoes.filterNot { it.requestId == requestId })

    /**
     * 桌面已经确认的回显不再需要：进入记录的都撤掉；排队发送的回显在出现于队列时撤掉。
     * 空闲时发送的消息会短暂经过队列，那时继续由记录里的回显代表它，避免气泡闪烁。
     */
    private fun settleEchoes(): DesktopSessionState {
        if (echoes.isEmpty()) return this
        val recorded = HashSet<String>()
        for (item in items) if (item is DesktopItem.User) item.rpcId?.let(recorded::add)
        val queued = queue.mapNotNullTo(HashSet()) { it.rpcId }
        val remaining = echoes.filterNot { it.requestId in recorded || (it.queued && it.requestId in queued) }
        return if (remaining.size == echoes.size) this else copy(echoes = remaining)
    }

    /** 队列条显示的条目：桌面队列减去已由记录回显代表的消息，再加上尚未确认的排队回显。 */
    val visibleQueue: List<DesktopQueueItem>
        get() {
            val transcriptEchoes = echoes.filterNot { it.queued }.mapTo(HashSet()) { it.requestId }
            val fromDesktop = queue.filterNot { it.rpcId != null && it.rpcId in transcriptEchoes }
            val pendingQueued = echoes.filter { it.queued }.map { DesktopQueueItem("echo-${it.requestId}", it.text, steering = false, rpcId = it.requestId) }
            return fromDesktop + pendingQueued
        }

    fun applySnapshot(push: JsonObject): DesktopSessionState {
        val incoming = DesktopBridgeProtocol.parseItems(push["items"])
        val resumed = push.bool("resumed") == true && loaded
        val merged = if (resumed) mergeItems(items, incoming) else incoming.sortedBy { it.seq }
        val live = DesktopBridgeProtocol.parseLive(push["live"])
        return copy(
            items = merged,
            cursor = push.long("cursor") ?: merged.lastOrNull()?.seq ?: -1,
            hasMore = if (resumed) hasMore else push.bool("hasMore") == true,
            title = push.string("title") ?: title,
            cwd = push.string("cwd") ?: cwd,
            model = DesktopBridgeProtocol.parseModelSelection(push["model"]) ?: model,
            queue = DesktopBridgeProtocol.parseQueue(push["queue"]),
            live = live,
            pendingCommitSeq = null,
            running = running || live != null,
            loaded = true,
            error = null,
        ).settleEchoes()
    }

    fun applyItem(item: DesktopItem): DesktopSessionState {
        var next = copy(items = mergeItems(items, listOf(item)), cursor = maxOf(cursor, item.seq))
        when (item) {
            // 插件可能先推持久条目、后推 live end：同一轮同一步的条目到了，临时行就该让位。
            is DesktopItem.Assistant -> if (pendingCommitSeq == item.seq || live?.matches(item) == true) {
                next = next.copy(live = null, pendingCommitSeq = null)
            }
            is DesktopItem.TurnStart -> next = next.copy(running = true)
            is DesktopItem.TurnEnd -> next = next.copy(running = false, live = null, pendingCommitSeq = null)
            is DesktopItem.Title -> next = next.copy(title = item.title.ifBlank { title })
            is DesktopItem.User -> return next.settleEchoes()
            else -> Unit
        }
        return next
    }

    fun applyLive(push: JsonObject): DesktopSessionState {
        val attemptId = push.string("attemptId") ?: return this
        return when (push.string("op")) {
            "start" -> copy(
                live = DesktopLiveAttempt(attemptId, emptyList(), push.int("turn"), push.int("step")),
                pendingCommitSeq = null,
                running = true,
            )
            "delta" -> {
                val current = live?.takeIf { it.attemptId == attemptId } ?: return this
                val deltas = push.array("deltas")?.mapNotNull { DesktopBridgeProtocol.parseLiveBlock(it as? JsonObject) }.orEmpty()
                copy(live = current.append(deltas))
            }
            "end" -> {
                if (live?.attemptId != attemptId) return this
                val seq = push.long("seq")
                if (push.bool("committed") == true && seq != null && items.none { it.seq == seq }) {
                    copy(pendingCommitSeq = seq)
                } else {
                    copy(live = null, pendingCommitSeq = null)
                }
            }
            else -> this
        }
    }

    fun applyProjection(push: JsonObject): DesktopSessionState {
        var next = this
        if (push.containsKey("title")) next = next.copy(title = push.string("title") ?: next.title)
        if (push.containsKey("model")) next = next.copy(model = DesktopBridgeProtocol.parseModelSelection(push["model"]) ?: next.model)
        if (push.containsKey("queue")) next = next.copy(queue = DesktopBridgeProtocol.parseQueue(push["queue"])).settleEchoes()
        return next
    }

    fun prependOlder(older: List<DesktopItem>, hasMore: Boolean): DesktopSessionState =
        copy(items = mergeItems(items, older), hasMore = hasMore, loadingOlder = false)

    /** 处理一条属于本会话的推送；不认识的推送原样返回。 */
    fun reduce(push: JsonObject): DesktopSessionState = when (push.string("t")) {
        "snapshot" -> applySnapshot(push)
        "item" -> (push["item"] as? JsonObject)?.let(DesktopBridgeProtocol::parseItem)?.let(::applyItem) ?: this
        "live" -> applyLive(push)
        "projection" -> applyProjection(push)
        "status" -> copy(running = push.bool("running") == true).let { if (!it.running) it.copy(live = null, pendingCommitSeq = null) else it }
        "follow-error" -> copy(error = (push["error"] as? JsonObject)?.string("message") ?: "订阅失败")
        else -> this
    }

    private fun DesktopLiveAttempt.matches(item: DesktopItem.Assistant): Boolean =
        turn != null && step != null && turn == item.turn && step == item.step

    companion object {
        fun mergeItems(existing: List<DesktopItem>, incoming: List<DesktopItem>): List<DesktopItem> {
            if (incoming.isEmpty()) return existing
            val bySeq = LinkedHashMap<Long, DesktopItem>(existing.size + incoming.size)
            for (item in existing) bySeq[item.seq] = item
            for (item in incoming) bySeq[item.seq] = item
            return bySeq.values.sortedBy { it.seq }
        }
    }
}
