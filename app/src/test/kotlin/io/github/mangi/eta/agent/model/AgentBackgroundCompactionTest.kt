package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentEvent
import io.github.mangi.eta.agent.runtime.AgentRunController
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** etax/design/05 §6.4 f：后台异步压缩。 */
class AgentBackgroundCompactionTest {
    private val config = AgentModelClient.ModelConfig(
        baseUrl = "https://example.invalid", apiKey = "fixture", model = "fixture", systemPrompt = "固定约束",
        contextWindow = 100_000, autoCompactionEnabled = true,
    )
    private val key = "conversation-under-test"
    private val pending = mutableListOf<Runnable>()

    @Before fun setUp() {
        BackgroundCompactionRegistry.clear(key)
        // 手动驱动后台任务：start() 只登记，测试决定何时「完成」。
        BackgroundCompactionRegistry.launcher = { pending += it }
    }

    @After fun tearDown() {
        BackgroundCompactionRegistry.clear(key)
        BackgroundCompactionRegistry.launcher = { Thread(it, "agent-context-precompact").apply { isDaemon = true }.start() }
    }

    private fun runPending() { val jobs = pending.toList(); pending.clear(); jobs.forEach(Runnable::run) }

    private fun history(turns: Int = 8) = JSONArray().put(JSONObject().put("role", "system").put("content", "固定规则")).also { array ->
        (1..turns).forEach { turn ->
            array.put(AgentConversationCodec.toJsonObject(AgentModelClient.ConversationMessage("user", "问题 $turn")))
            array.put(AgentConversationCodec.toJsonObject(AgentModelClient.ConversationMessage("assistant", "事实 $turn ".repeat(400))))
        }
    }

    private fun response(text: String) = ProviderResponse(
        JSONObject().put("role", "assistant").put("content", text).put("finish_reason", "stop"),
    )

    private fun provider(block: (ProviderRequest) -> ProviderResponse) = object : AgentProviderClient {
        override val id = "fixture"
        override val capabilities = ProviderCapabilities(EndpointKind.CHAT_COMPLETIONS, true, true, true, true, false, false)
        override fun complete(request: ProviderRequest, runController: AgentRunController, onEvent: (ProviderEvent) -> Unit) =
            block(request)
    }

    private fun session(
        messages: JSONArray,
        events: MutableList<AgentEvent>,
        provider: AgentProviderClient = provider { response("较早对话的摘要") },
        policy: BackgroundCompactionPolicy = BackgroundCompactionPolicy(key, startRatio = 0.6, keepRecentTurns = 4),
        snapshots: MutableList<AgentContextSnapshot> = mutableListOf(),
    ) = AgentContextSession(config, messages, 1, "operation", provider, AgentRunController(), { emptySet() },
        { events += it }, { snapshots += it }, background = policy)

    private fun users(messages: JSONArray) = (0 until messages.length()).map(messages::getJSONObject)
        .filter { it.optString("role") == "user" }.map { it.optString("content") }

    @Test fun softLineStartsBackgroundAndLeavesForegroundUntouched() {
        val messages = history()
        val before = messages.toString()
        val events = mutableListOf<AgentEvent>()
        val session = session(messages, events)
        session.observeInputTokens(65_000)
        session.compact()
        assertEquals(BackgroundCompactionRegistry.State.RUNNING, BackgroundCompactionRegistry.state(key))
        assertEquals(before, messages.toString())
        assertTrue(events.any { it is AgentEvent.ContextCompaction && it.phase == AgentEvent.ContextCompaction.PHASE_BACKGROUND_STARTED })
        assertFalse(events.any { it is AgentEvent.ContextCompaction && it.phase == AgentEvent.ContextCompaction.PHASE_STARTED })
    }

    @Test fun readyResultReplacesPrefixAndKeepsRecentTurnsInOrder() {
        val messages = history()
        val events = mutableListOf<AgentEvent>()
        val session = session(messages, events)
        session.observeInputTokens(65_000)
        session.compact()
        runPending()
        // 后台整理期间前台又多了一轮。
        messages.put(AgentConversationCodec.toJsonObject(AgentModelClient.ConversationMessage("user", "问题 9")))
        messages.put(AgentConversationCodec.toJsonObject(AgentModelClient.ConversationMessage("assistant", "回答 9")))
        session.observeInputTokens(66_000)
        session.compact()
        assertTrue(messages.getJSONObject(1).optBoolean("_eta_context_summary"))
        assertEquals(listOf("问题 5", "问题 6", "问题 7", "问题 8", "问题 9"), users(messages))
        assertTrue(events.any { it is AgentEvent.ContextCompaction && it.phase == AgentEvent.ContextCompaction.PHASE_COMPLETED })
        assertNull(BackgroundCompactionRegistry.state(key))
    }

    @Test fun resultCarriesOverToNextTurnWhoseHistoryWasRebuiltFromStorage() {
        // 本轮：带服务商临时字段（推理内容）和一条屏幕观察消息。
        val list = (0 until history().length()).map(history()::getJSONObject).toMutableList()
        list[2].put("reasoning_content", "PRIVATE_THINKING")
        list.add(3, JSONObject().put("role", "user").put("content", "屏幕观察").put("_eta_observation", true))
        val first = JSONArray().also { array -> list.forEach(array::put) }
        val events = mutableListOf<AgentEvent>()
        val turn1 = session(first, events)
        turn1.observeInputTokens(65_000)
        turn1.compact(final = true)
        runPending()
        // 下一轮：历史从存储重建（没有推理内容和观察消息），末尾是新问题。
        val second = history().also { it.put(AgentConversationCodec.toJsonObject(AgentModelClient.ConversationMessage("user", "问题 9"))) }
        val turn2 = session(second, events)
        turn2.compact()
        assertTrue(second.getJSONObject(1).optBoolean("_eta_context_summary"))
        assertEquals(listOf("问题 5", "问题 6", "问题 7", "问题 8", "问题 9"), users(second))
    }

    @Test fun changedPrefixDiscardsBackgroundResult() {
        val messages = history()
        val events = mutableListOf<AgentEvent>()
        val session = session(messages, events)
        session.observeInputTokens(65_000)
        session.compact()
        runPending()
        messages.getJSONObject(1).put("content", "用户改过的问题 1")
        val edited = messages.toString()
        session.observeInputTokens(65_000)
        session.compact()
        assertEquals(edited, messages.toString())
        assertTrue(events.any { it is AgentEvent.ContextCompaction && it.phase == AgentEvent.ContextCompaction.PHASE_BACKGROUND_DISCARDED })
    }

    @Test fun hardLineWaitsForRunningTaskInsteadOfCompactingAgain() {
        BackgroundCompactionRegistry.launcher = { Thread(it).start() }
        val gate = CountDownLatch(1)
        var calls = 0
        val messages = history()
        val events = mutableListOf<AgentEvent>()
        val session = session(messages, events, provider { calls++; gate.await(5, TimeUnit.SECONDS); response("摘要") })
        session.observeInputTokens(65_000)
        session.compact()
        session.observeInputTokens(90_000)
        Thread { Thread.sleep(150); gate.countDown() }.start()
        session.compact()
        assertEquals(1, calls)
        assertTrue(messages.getJSONObject(1).optBoolean("_eta_context_summary"))
        assertFalse(events.any { it is AgentEvent.ContextCompaction && it.phase == AgentEvent.ContextCompaction.PHASE_STARTED })
    }

    @Test fun failedBackgroundFallsBackToSyncAndStopsRetryingAfterTwoFailures() {
        var fail = true
        var syncCalls = 0
        val events = mutableListOf<AgentEvent>()
        val p = provider { request ->
            if (fail) throw AgentModelFailure("FIXTURE", false, "fixture failure")
            syncCalls++
            assertTrue(request.messages.length() > 0)
            response("同步摘要")
        }
        repeat(2) {
            val s = session(history(), events, p)
            s.observeInputTokens(65_000)
            s.compact()
            runPending()
            s.compact()
        }
        assertEquals(2, BackgroundCompactionRegistry.failures(key))
        val messages = history()
        val s = session(messages, events, p)
        s.observeInputTokens(65_000)
        s.compact()
        assertNull("失败两次后不再后台重试", BackgroundCompactionRegistry.state(key))
        fail = false
        s.observeInputTokens(90_000)
        s.compact()
        assertEquals(1, syncCalls)
        assertTrue(events.any { it is AgentEvent.ContextCompaction && it.phase == AgentEvent.ContextCompaction.PHASE_STARTED })
    }

    @Test fun finalCheckpointDoesNotBlockOnRunningTask() {
        val messages = history()
        val events = mutableListOf<AgentEvent>()
        val session = session(messages, events, provider { response("同步摘要") })
        session.observeInputTokens(65_000)
        session.compact()
        session.observeInputTokens(90_000)
        val started = System.currentTimeMillis()
        session.compact(final = true)
        assertTrue(System.currentTimeMillis() - started < 5_000)
        assertEquals(BackgroundCompactionRegistry.State.RUNNING, BackgroundCompactionRegistry.state(key))
    }

    @Test fun forcedCompactionKeepsItsOriginalBehaviour() {
        val messages = history()
        val events = mutableListOf<AgentEvent>()
        val session = session(messages, events, provider { response("手动摘要") })
        session.compact(force = true)
        assertNull(BackgroundCompactionRegistry.state(key))
        assertTrue(events.any { it is AgentEvent.ContextCompaction && it.phase == AgentEvent.ContextCompaction.PHASE_STARTED })
        assertEquals(listOf("问题 8"), users(messages))
    }

    @Test fun cutNeverSplitsToolBatchesAndKeepsRecentTurns() {
        val messages = history(6)
        // 第 3 轮的回答带一个工具调用：切点不能落在调用与结果之间。
        val call = JSONObject().put("role", "assistant").put("content", "")
            .put("tool_calls", JSONArray().put(JSONObject().put("id", "call-1").put("type", "function")
                .put("function", JSONObject().put("name", "read").put("arguments", "{}"))))
        val result = JSONObject().put("role", "tool").put("tool_call_id", "call-1").put("content", "结果")
        val list = (0 until messages.length()).map(messages::getJSONObject).toMutableList()
        list.add(6, call); list.add(7, result)
        val rebuilt = JSONArray().also { array -> list.forEach(array::put) }
        val events = mutableListOf<AgentEvent>()
        val session = session(rebuilt, events, policy = BackgroundCompactionPolicy(key, 0.6, keepRecentTurns = 2))
        session.observeInputTokens(65_000)
        session.compact()
        runPending()
        session.compact()
        val remaining = users(rebuilt)
        assertEquals(listOf("问题 5", "问题 6"), remaining)
        val tail = (0 until rebuilt.length()).map(rebuilt::getJSONObject)
        assertFalse(tail.any { it.optString("role") == "tool" && tail.none { m -> m.has("tool_calls") } })
    }
}
