package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentEvent
import io.github.mangi.eta.agent.runtime.AgentRunController
import org.json.JSONArray

/** 管理可替换的模型上下文；持久快照先提交，运行 transcript 始终追加。 */
internal class AgentContextSession(
    private val config: AgentModelClient.ModelConfig,
    private val messages: JSONArray,
    private val systemCount: Int,
    private val operationId: String,
    private val provider: AgentProviderClient,
    private val runController: AgentRunController,
    private val sensitiveIds: () -> Set<String>,
    private val onEvent: (AgentEvent) -> Unit,
    private val onContextSnapshot: (AgentContextSnapshot) -> Unit,
    private val transcriptSize: () -> Int = { 0 },
    private val roleplay: Boolean = false,
    private val mainToolsProvider: () -> JSONArray = { JSONArray() },
    private val mainSessionId: String = operationId,
    private val messageProjector: ((JSONArray) -> JSONArray)? = null,
    private val enableCacheReuse: Boolean = false,
    private val background: BackgroundCompactionPolicy? = null,
) {
    private val contextWindow = config.knownContextWindow
    private var inputTokens: Int? = null
    private var compacted = false
    private var consumedSupplementCount = 0
    private var consumedUserTurns = (systemCount until messages.length()).sumOf {
        val message = messages.getJSONObject(it)
        message.optInt("_eta_compacted_users") + if (message.optString("role") == "user") 1 else 0
    }
    private var committedSnapshot: AgentContextSnapshot? = null

    fun snapshot(): AgentContextSnapshot? = committedSnapshot

    fun observeInputTokens(tokens: Int?) {
        inputTokens = tokens?.takeIf { it >= 0 }
    }

    fun userAppended() {
        consumedUserTurns++
        consumedSupplementCount++
    }

    private fun publishSnapshot(candidate: JSONArray = messages) {
        if (!compacted) return
        val snapshot = createSnapshot(candidate)
        snapshot.encode()
        onContextSnapshot(snapshot)
        committedSnapshot = snapshot
    }

    private fun createSnapshot(candidate: JSONArray): AgentContextSnapshot {
        val history = durableHistory(candidate)
        return AgentContextSnapshot(
            operationId = operationId,
            messages = history,
            coveredUserTurns = history.sumOf { it.compactedUserTurns },
            consumedUserTurns = consumedUserTurns,
            consumedSupplementCount = consumedSupplementCount,
            consumedTranscriptMessages = transcriptSize(),
        )
    }

    fun compact(force: Boolean = false, final: Boolean = false) {
        if (SignedToolContext.hasPendingResponse(messages)) {
            if (force) {
                throw SignedToolContext.compactionFailure(messages)
            }
            return
        }
        if (!force && background != null && compactInBackground(background, final)) return
        val before = inputTokens
        if (!force && (!config.autoCompactionEnabled || contextWindow == null || before == null ||
                before < contextWindow * TRIGGER_RATIO)
        ) {
            try {
                publishSnapshot()
            } catch (failure: Exception) {
                runController.throwIfCancelled()
                if (!final) throw failure
                committedSnapshot = createSnapshot(messages)
                onEvent(AgentEvent.ContextCompaction(operationId, "failed", before,
                    reasonCode = "CONTEXT_CHECKPOINT_FAILED"))
            }
            return
        }
        val operation = java.util.UUID.randomUUID().toString()
        onEvent(AgentEvent.ContextCompaction(operation, AgentEvent.ContextCompaction.PHASE_STARTED, before))
        try {
            val outcome = AgentContextCompactor(
                config = config,
                provider = provider,
                controller = runController,
                roleplay = roleplay,
                mainTools = mainToolsProvider(),
                mainSessionId = mainSessionId,
                messageProjector = messageProjector,
                enableCacheReuse = enableCacheReuse,
            ).compactWithOutcome(
                messages, systemCount, sensitiveIds(),
            )
            val candidate = outcome.messages
            runController.throwIfCancelled()
            val wasCompacted = compacted
            compacted = true
            try {
                publishSnapshot(candidate)
            } catch (failure: Exception) {
                compacted = wasCompacted
                throw failure
            }
            while (messages.length() > 0) messages.remove(messages.length() - 1)
            for (index in 0 until candidate.length()) messages.put(candidate.getJSONObject(index))
            inputTokens = null
            onEvent(
                AgentEvent.ContextCompaction(
                    operationId = operation,
                    phase = AgentEvent.ContextCompaction.PHASE_COMPLETED,
                    tokensBefore = before,
                    usage = outcome.usage,
                    archiveId = outcome.archiveId,
                ),
            )
        } catch (failure: Exception) {
            runController.throwIfCancelled()
            onEvent(AgentEvent.ContextCompaction(operation, "failed", before,
                reasonCode = (failure as? AgentModelFailure)?.code ?: "CONTEXT_SUMMARY_FAILED"))
            if (!final) throw failure
            // 已完成的回答仍成功交付；完整快照随终态 outbox 保存，不依赖先前检查点写入成功。
            committedSnapshot = createSnapshot(messages)
        }
    }

    /**
     * 后台压缩（§6.4 d）。返回 true 表示本次已处理完（应用了后台结果或无需同步压缩），调用方直接结束；
     * false 时继续走原来的同步逻辑（窗口未知、未开自动压缩、到了硬线且没有可等的后台任务等）。
     */
    private fun compactInBackground(policy: BackgroundCompactionPolicy, final: Boolean): Boolean {
        if (applyReady(policy)) return true
        val window = contextWindow ?: return false
        if (!config.autoCompactionEnabled) return false
        val before = inputTokens ?: return false
        if (before >= window * TRIGGER_RATIO) {
            if (!final && BackgroundCompactionRegistry.state(policy.key) == BackgroundCompactionRegistry.State.RUNNING) {
                val deadline = System.currentTimeMillis() + WAIT_LIMIT_MS
                while (BackgroundCompactionRegistry.state(policy.key) == BackgroundCompactionRegistry.State.RUNNING &&
                    System.currentTimeMillis() < deadline) {
                    runController.throwIfCancelled()
                    Thread.sleep(WAIT_STEP_MS)
                }
                if (applyReady(policy)) return true
            }
            return false
        }
        if (before >= window * policy.startRatio &&
            BackgroundCompactionRegistry.state(policy.key) == null &&
            BackgroundCompactionRegistry.failures(policy.key) < MAX_BACKGROUND_FAILURES) {
            startBackground(policy, before)
        }
        return false
    }

    private fun historyCopy(): List<org.json.JSONObject> =
        (systemCount until messages.length()).map { org.json.JSONObject(messages.getJSONObject(it).toString()) }

    private fun startBackground(policy: BackgroundCompactionPolicy, before: Int) {
        val history = historyCopy()
        var seen = 0
        var keepFrom = -1
        for (index in history.indices.reversed()) {
            val message = history[index]
            if (message.optString("role") == "user" && !message.has("_eta_observation")) {
                seen++
                if (seen == policy.keepRecentTurns) { keepFrom = index; break }
            }
        }
        if (keepFrom < 2) return
        val cut = (keepFrom downTo 2).firstOrNull { AgentContextCompactor.canSplit(history, it) } ?: return
        val fingerprint = BackgroundCompactionRegistry.fingerprint(history, cut)
        val systemMessages = (0 until systemCount).map { org.json.JSONObject(messages.getJSONObject(it).toString()) }
        val followsConversation = policy.modelConfig == null
        val compactionConfig = policy.modelConfig ?: config
        val client = if (followsConversation) provider
            else (policy.providerFactory ?: ProviderClientFactory::getClient)(compactionConfig)
        val sensitive = sensitiveIds().toSet()
        val tools = JSONArray(mainToolsProvider().toString())
        val projector = messageProjector
        val sessionId = "$mainSessionId-compact"
        val cacheReuse = enableCacheReuse && followsConversation
        val started = BackgroundCompactionRegistry.start(policy.key) {
            val source = JSONArray().apply {
                systemMessages.forEach(::put)
                history.take(cut).forEach(::put)
            }
            // 独立的运行控制：前台这一轮结束或被取消，后台整理照常完成，下一轮开头接上。
            val outcome = AgentContextCompactor(
                config = compactionConfig,
                provider = client,
                controller = AgentRunController(),
                roleplay = roleplay,
                mainTools = tools,
                mainSessionId = sessionId,
                messageProjector = projector,
                enableCacheReuse = cacheReuse,
            ).compactWithOutcome(source, systemCount, sensitive, protectLatestUser = false)
            val summary = (systemCount until outcome.messages.length()).map { outcome.messages.getJSONObject(it) }
            BackgroundCompactionRegistry.Result(cut, fingerprint, summary, outcome.usage, outcome.archiveId)
        }
        if (started) {
            onEvent(AgentEvent.ContextCompaction(java.util.UUID.randomUUID().toString(),
                AgentEvent.ContextCompaction.PHASE_BACKGROUND_STARTED, before))
        }
    }

    /** 在安全点应用已完成的后台结果；被压范围变过（编辑、删除、重新生成）时丢弃。 */
    private fun applyReady(policy: BackgroundCompactionPolicy): Boolean {
        if (BackgroundCompactionRegistry.clearFailed(policy.key)) {
            onEvent(AgentEvent.ContextCompaction(java.util.UUID.randomUUID().toString(),
                AgentEvent.ContextCompaction.PHASE_BACKGROUND_DISCARDED, inputTokens, reasonCode = "BACKGROUND_FAILED"))
            return false
        }
        val ready = BackgroundCompactionRegistry.takeReady(policy.key) ?: return false
        val before = inputTokens
        val history = (systemCount until messages.length()).map { messages.getJSONObject(it) }
        if (ready.cut > history.size || BackgroundCompactionRegistry.fingerprint(history, ready.cut) != ready.fingerprint) {
            onEvent(AgentEvent.ContextCompaction(java.util.UUID.randomUUID().toString(),
                AgentEvent.ContextCompaction.PHASE_BACKGROUND_DISCARDED, before, reasonCode = "BACKGROUND_STALE"))
            return false
        }
        val candidate = JSONArray()
        for (index in 0 until systemCount) candidate.put(messages.getJSONObject(index))
        ready.summary.forEach(candidate::put)
        history.drop(ready.cut).forEach(candidate::put)
        val wasCompacted = compacted
        compacted = true
        try {
            publishSnapshot(candidate)
        } catch (failure: Exception) {
            compacted = wasCompacted
            runController.throwIfCancelled()
            onEvent(AgentEvent.ContextCompaction(java.util.UUID.randomUUID().toString(),
                AgentEvent.ContextCompaction.PHASE_BACKGROUND_DISCARDED, before, reasonCode = "CONTEXT_CHECKPOINT_FAILED"))
            return false
        }
        while (messages.length() > 0) messages.remove(messages.length() - 1)
        for (index in 0 until candidate.length()) messages.put(candidate.getJSONObject(index))
        inputTokens = null
        onEvent(
            AgentEvent.ContextCompaction(
                operationId = java.util.UUID.randomUUID().toString(),
                phase = AgentEvent.ContextCompaction.PHASE_COMPLETED,
                tokensBefore = before,
                usage = ready.usage,
                archiveId = ready.archiveId,
            ),
        )
        return true
    }

    private fun durableHistory(source: JSONArray): List<AgentModelClient.ConversationMessage> {
        val durable = JSONArray()
        for (index in systemCount until source.length()) {
            val message = source.getJSONObject(index)
            if (!message.optBoolean("_eta_observation")) durable.put(message)
        }
        return AgentConversationCodec.transcript(durable, 0, sensitiveIds())
    }

    private companion object {
        const val TRIGGER_RATIO = 0.85
        const val WAIT_LIMIT_MS = 90_000L
        const val WAIT_STEP_MS = 100L
        const val MAX_BACKGROUND_FAILURES = 2
    }
}
