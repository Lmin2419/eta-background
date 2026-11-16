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
    val stats: DesktopSessionStats? = null,
    val usage: DesktopTokenTotals? = null,
    val pressure: DesktopContextPressure? = null,
    val subagents: List<DesktopSubagent> = emptyList(),
    val timing: DesktopSubagentTiming? = null,
    /** 仍可回答的限时提问（投影 `questions`）；`continued` 的只能在这里回答。 */
    val pendingQuestions: List<DesktopPendingQuestion> = emptyList(),
    /**
     * 刚被模型取走的插队消息：电脑先把它移出队列，随后才把它写进记录，两次推送之间
     * 继续显示这条气泡，免得闪一下。写进记录、本轮结束或超时后清掉。
     */
    val handoff: List<DesktopHandoff> = emptyList(),
) {
    val firstSeq: Long? get() = items.firstOrNull()?.seq

    /**
     * 已插队、等当前这一步结束就会被处理的消息（桌面显示为已发出的待送达气泡）。
     * 手机自己发的那条已由对话里的回显代表，不再重复。
     */
    val steering: List<DesktopQueueItem>
        get() {
            if (queue.none { it.steering } && handoff.isEmpty()) return emptyList()
            val echoed = echoes.filterNot { it.queued }.mapTo(HashSet()) { it.requestId }
            return (queue.filter { it.steering } + handoff.map { it.item })
                .filterNot { it.rpcId != null && it.rpcId in echoed }
                .distinctBy { it.id }
        }

    /** 点了插队后先在本地挪到对话里，不等电脑回执；电脑的队列推送随后会给出权威结果。 */
    fun markSteering(itemId: String, steering: Boolean = true): DesktopSessionState =
        copy(queue = queue.map { if (it.id == itemId) it.copy(steering = steering) else it })

    /** 取消排队：先从本地队列拿掉；电脑拒绝时用 [restoreQueued] 放回去。 */
    fun removeQueued(itemId: String): DesktopSessionState = copy(queue = queue.filterNot { it.id == itemId })

    fun restoreQueued(item: DesktopQueueItem): DesktopSessionState =
        if (queue.any { it.id == item.id } || items.any { it is DesktopItem.User && it.id == item.id }) this else copy(queue = queue + item)

    fun addEcho(echo: DesktopEcho): DesktopSessionState = copy(echoes = echoes + echo)

    fun removeEcho(requestId: String): DesktopSessionState = copy(echoes = echoes.filterNot { it.requestId == requestId })

    /** 刚发出、电脑还没回执的排队消息：插队时挪进对话（[queued] 为 false），失败时挪回队列条。 */
    fun moveEcho(requestId: String, queued: Boolean): DesktopSessionState =
        copy(echoes = echoes.map { if (it.requestId == requestId) it.copy(queued = queued) else it })

    /**
     * 换上电脑推来的新队列。已插队的消息离开队列时先进 [handoff]，
     * 等它作为用户消息写进记录再撤掉。
     */
    private fun withQueue(next: List<DesktopQueueItem>, now: Long): DesktopSessionState {
        val present = next.mapTo(HashSet()) { it.id }
        val recorded = if (queue.any { it.steering } || handoff.isNotEmpty()) {
            items.mapNotNullTo(HashSet()) { (it as? DesktopItem.User)?.id }
        } else {
            emptySet()
        }
        val leaving = queue.filter { it.steering && it.id !in present && it.id !in recorded }.map { DesktopHandoff(it, now) }
        val kept = (handoff + leaving).filter { it.item.id !in present && it.item.id !in recorded && now - it.at < HANDOFF_MS }
        return copy(queue = next, handoff = if (running) kept.distinctBy { it.item.id } else emptyList())
    }

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

    /**
     * 队列条显示的条目：桌面队列减去已由记录回显代表的消息、减去已插队（steering）的消息，
     * 再加上尚未确认的排队回显。插队消息和桌面一样直接显示在对话末尾，见 [steering]。
     */
    val visibleQueue: List<DesktopQueueItem>
        get() {
            val transcriptEchoes = echoes.filterNot { it.queued }.mapTo(HashSet()) { it.requestId }
            val fromDesktop = queue.filterNot { it.steering || (it.rpcId != null && it.rpcId in transcriptEchoes) }
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
            handoff = emptyList(),
            live = live,
            pendingCommitSeq = null,
            running = running || live != null,
            loaded = true,
            error = null,
            stats = DesktopBridgeProtocol.parseStats(push["stats"]) ?: stats,
            usage = DesktopBridgeProtocol.parseTokenTotals(push["usage"]) ?: usage,
            pressure = DesktopBridgeProtocol.parsePressure(push["pressure"]) ?: pressure,
            subagents = if (push.containsKey("subagents")) DesktopBridgeProtocol.parseSubagentEntries(push["subagents"]) else subagents,
            timing = DesktopBridgeProtocol.parseTiming(push["timing"]) ?: timing,
            pendingQuestions = if (push.containsKey("questions")) DesktopBridgeProtocol.parsePendingQuestions(push["questions"]) else pendingQuestions,
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
            is DesktopItem.TurnEnd -> next = next.copy(running = false, live = null, pendingCommitSeq = null, handoff = emptyList())
            is DesktopItem.Title -> next = next.copy(title = item.title.ifBlank { title })
            // 消息已写进记录：它在队列和交接区里的影子一并撤掉，同一条消息只显示一次，
            // 即使队列的更新推送晚到或丢了。
            is DesktopItem.User -> return next.copy(
                queue = next.queue.filterNot { it.id == item.id || (it.rpcId != null && it.rpcId == item.rpcId) },
                handoff = next.handoff.filterNot { it.item.id == item.id || (it.item.rpcId != null && it.item.rpcId == item.rpcId) },
            ).settleEchoes()
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

    fun applyProjection(push: JsonObject, now: Long = System.currentTimeMillis()): DesktopSessionState {
        var next = this
        if (push.containsKey("title")) next = next.copy(title = push.string("title") ?: next.title)
        if (push.containsKey("model")) next = next.copy(model = DesktopBridgeProtocol.parseModelSelection(push["model"]) ?: next.model)
        if (push.containsKey("queue")) {
            next = next.withQueue(DesktopBridgeProtocol.parseQueue(push["queue"]), now).settleEchoes()
        }
        if (push.containsKey("stats")) next = next.copy(stats = DesktopBridgeProtocol.parseStats(push["stats"]) ?: next.stats)
        if (push.containsKey("usage")) next = next.copy(usage = DesktopBridgeProtocol.parseTokenTotals(push["usage"]) ?: next.usage)
        if (push.containsKey("pressure")) {
            // 压力帧可能只带部分字段（例如只有窗口大小），保留已知值。
            val incoming = DesktopBridgeProtocol.parsePressure(push["pressure"])
            if (incoming != null) next = next.copy(pressure = DesktopContextPressure(
                incoming.pressureTokens ?: next.pressure?.pressureTokens,
                incoming.projectedTokens ?: next.pressure?.projectedTokens,
                incoming.contextWindow ?: next.pressure?.contextWindow,
            ))
        }
        if (push.containsKey("subagents")) next = next.copy(subagents = DesktopBridgeProtocol.parseSubagentEntries(push["subagents"]))
        if (push.containsKey("timing")) next = next.copy(timing = DesktopBridgeProtocol.parseTiming(push["timing"]) ?: next.timing)
        if (push.containsKey("questions")) next = next.copy(pendingQuestions = DesktopBridgeProtocol.parsePendingQuestions(push["questions"]))
        return next
    }

    fun prependOlder(older: List<DesktopItem>, hasMore: Boolean): DesktopSessionState =
        copy(items = mergeItems(items, older), hasMore = hasMore, loadingOlder = false)

    /** 处理一条属于本会话的推送；不认识的推送原样返回。 */
    fun reduce(push: JsonObject, now: Long = System.currentTimeMillis()): DesktopSessionState {
        val next = when (push.string("t")) {
            "snapshot" -> applySnapshot(push)
            "item" -> (push["item"] as? JsonObject)?.let(DesktopBridgeProtocol::parseItem)?.let(::applyItem) ?: this
            "live" -> applyLive(push)
            "projection" -> applyProjection(push, now)
            "status" -> copy(running = push.bool("running") == true).let {
                if (!it.running) it.copy(live = null, pendingCommitSeq = null, handoff = emptyList()) else it
            }
            "follow-error" -> copy(error = (push["error"] as? JsonObject)?.string("message") ?: "订阅失败")
            else -> this
        }
        // 插队后又在电脑上被取消的消息不会写进记录：交接区按时间清掉，不留假气泡。
        if (next.handoff.none { now - it.at >= HANDOFF_MS }) return next
        return next.copy(handoff = next.handoff.filter { now - it.at < HANDOFF_MS })
    }

    private fun DesktopLiveAttempt.matches(item: DesktopItem.Assistant): Boolean =
        turn != null && step != null && turn == item.turn && step == item.step

    companion object {
        /** 交接区里的条目最多留这么久；被取走的消息几十毫秒内就会写进记录。 */
        const val HANDOFF_MS = 3_000L

        fun mergeItems(existing: List<DesktopItem>, incoming: List<DesktopItem>): List<DesktopItem> {
            if (incoming.isEmpty()) return existing
            val bySeq = LinkedHashMap<Long, DesktopItem>(existing.size + incoming.size)
            for (item in existing) bySeq[item.seq] = item
            for (item in incoming) bySeq[item.seq] = item
            return bySeq.values.sortedBy { it.seq }
        }
    }
}
