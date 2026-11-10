package io.github.mangi.eta.agent.remote

import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.SystemNoticeCode
import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.screens.desktop.DesktopChatProjector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSessionStateTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val snapshot = json(
        """
        {"t":"snapshot","sessionId":"s","cursor":4,"hasMore":true,"resumed":false,"title":"Fix bug","cwd":"/repo",
         "model":{"lastUsed":null,"next":{"provider":"deepseek","model":"chat"}},
         "queue":[],"live":null,
         "items":[
           {"k":"turn_start","seq":1,"time":1,"turn":1},
           {"k":"user","seq":2,"time":2,"id":"u","text":"列出文件","images":0,"files":[],"source":"user","rpcId":"r0"},
           {"k":"assistant","seq":3,"time":3,"id":"a","turn":1,"step":1,
            "blocks":[{"type":"reasoning","text":"想一下"},{"type":"tool","id":"c1","name":"bash","args":"{\"command\":\"ls\",\"description\":\"列出文件\"}"}]},
           {"k":"tool_result","seq":4,"time":4,"callId":"c1","isError":false,"text":"a.txt"}
         ]}
        """,
    )

    @Test
    fun snapshotLoadsItemsAndProjections() {
        val state = DesktopSessionState("s").reduce(snapshot)
        assertTrue(state.loaded)
        assertEquals(4L, state.cursor)
        assertTrue(state.hasMore)
        assertEquals("Fix bug", state.title)
        assertEquals(DesktopModelSelection("deepseek", "chat", null), state.model)
        assertEquals(listOf(1L, 2L, 3L, 4L), state.items.map { it.seq })
    }

    @Test
    fun resumedSnapshotAppendsOnlyNewItems() {
        val first = DesktopSessionState("s").reduce(snapshot)
        val resumed = first.reduce(
            json("""{"t":"snapshot","sessionId":"s","cursor":5,"hasMore":false,"resumed":true,"items":[{"k":"turn_end","seq":5,"time":5,"turn":1,"reason":"completed"}],"queue":[]}"""),
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), resumed.items.map { it.seq })
        assertEquals(5L, resumed.cursor)
        assertTrue("resume keeps the older-history marker", resumed.hasMore)
    }

    @Test
    fun liveStreamAccumulatesAndHandsOverToCommittedItem() {
        var state = DesktopSessionState("s").reduce(snapshot)
        state = state.reduce(json("""{"t":"live","op":"start","sessionId":"s","attemptId":"a2","turn":1,"step":2}"""))
        state = state.reduce(json("""{"t":"live","op":"delta","sessionId":"s","attemptId":"a2","deltas":[{"index":0,"kind":"text","text":"有 "}]}"""))
        state = state.reduce(json("""{"t":"live","op":"delta","sessionId":"s","attemptId":"a2","deltas":[{"index":0,"kind":"text","text":"a.txt"}]}"""))
        assertEquals("有 a.txt", state.live?.blocks?.single()?.text)
        assertTrue(state.running)

        // end arrives before the durable item: keep the live row until the item lands.
        state = state.reduce(json("""{"t":"live","op":"end","sessionId":"s","attemptId":"a2","committed":true,"seq":6}"""))
        assertEquals(6L, state.pendingCommitSeq)
        assertTrue(state.live != null)
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"assistant","seq":6,"time":6,"id":"m","turn":1,"step":2,"blocks":[{"type":"text","text":"有 a.txt"}]}}"""))
        assertNull(state.live)
        assertNull(state.pendingCommitSeq)
    }

    @Test
    fun committedItemBeforeLiveEndReplacesLiveRow() {
        var state = DesktopSessionState("s").reduce(snapshot)
        state = state.reduce(json("""{"t":"live","op":"start","sessionId":"s","attemptId":"a2","turn":1,"step":2}"""))
        state = state.reduce(json("""{"t":"live","op":"delta","sessionId":"s","attemptId":"a2","deltas":[{"index":0,"kind":"tool","text":"{}","id":"c9","name":"bash"}]}"""))
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"assistant","seq":6,"time":6,"id":"m","turn":1,"step":2,"blocks":[{"type":"tool","id":"c9","name":"bash","args":"{}"}]}}"""))
        assertNull(state.live)
        state = state.reduce(json("""{"t":"live","op":"end","sessionId":"s","attemptId":"a2","committed":true,"seq":6}"""))
        assertNull(state.live)
        val ids = DesktopChatProjector.messages(state).map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun deltasForAnotherAttemptAreIgnored() {
        var state = DesktopSessionState("s").reduce(snapshot)
        state = state.reduce(json("""{"t":"live","op":"start","sessionId":"s","attemptId":"a2"}"""))
        state = state.reduce(json("""{"t":"live","op":"delta","sessionId":"s","attemptId":"other","deltas":[{"index":0,"kind":"text","text":"x"}]}"""))
        assertTrue(state.live!!.blocks.isEmpty())
    }

    @Test
    fun idleEchoStaysInTranscriptUntilRecorded() {
        var state = DesktopSessionState("s").reduce(snapshot).reduce(json("""{"t":"status","sessionId":"s","running":false}"""))
        state = state.addEcho(DesktopEcho("r1", "再看看", queued = false))
        // The prompt passes through the inbox first; the transcript echo keeps representing it.
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":7,"queue":[{"id":"q","text":"再看看","placement":"queued","rpcId":"r1"}]}"""))
        assertEquals(1, state.echoes.size)
        assertTrue(state.visibleQueue.isEmpty())
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"user","seq":8,"time":8,"id":"q","text":"再看看","source":"user","rpcId":"r1"}}"""))
        assertTrue(state.echoes.isEmpty())
    }

    @Test
    fun queuedEchoShowsInQueueUntilDesktopQueueHasIt() {
        var state = DesktopSessionState("s").reduce(snapshot)
        state = state.addEcho(DesktopEcho("r2", "顺便跑测试", queued = true))
        assertEquals(listOf("顺便跑测试"), state.visibleQueue.map { it.text })
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":7,"queue":[{"id":"q2","text":"顺便跑测试","placement":"queued","rpcId":"r2"}]}"""))
        assertTrue(state.echoes.isEmpty())
        assertEquals(listOf("q2"), state.visibleQueue.map { it.id })
    }

    @Test
    fun turnEndStopsRunningAndClearsLive() {
        var state = DesktopSessionState("s").reduce(snapshot)
        state = state.reduce(json("""{"t":"live","op":"start","sessionId":"s","attemptId":"a2"}"""))
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"turn_end","seq":9,"time":9,"turn":1,"reason":"aborted","cause":"user"}}"""))
        assertFalse(state.running)
        assertNull(state.live)
    }

    @Test
    fun olderHistoryIsPrependedInOrder() {
        val state = DesktopSessionState("s").reduce(snapshot)
            .prependOlder(listOf(DesktopItem.TurnStart(0, 0, 0)), hasMore = false)
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), state.items.map { it.seq })
        assertFalse(state.hasMore)
    }

    @Test
    fun projectorMapsTranscriptOntoChatModels() {
        var state = DesktopSessionState("s").reduce(snapshot)
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"assistant","seq":5,"time":5,"id":"m","turn":1,"step":2,"blocks":[{"type":"text","text":"有 a.txt"}],"usage":{"input":100,"output":5,"cacheRead":80}}}"""))
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"turn_end","seq":6,"time":6,"turn":1,"reason":"error","message":"boom"}}"""))
        val messages = DesktopChatProjector.messages(state)
        assertEquals("列出文件", (messages[0] as UserMessageUi).content)
        assertEquals("想一下", (messages[1] as ThinkingMessageUi).content)
        val tool = messages[2] as ToolActivityMessageUi
        assertEquals("bash", tool.toolName)
        assertEquals("列出文件", tool.argumentsSummary)
        assertEquals("ls", tool.command)
        assertEquals(ToolActivityStatusUi.Success, tool.status)
        assertEquals("a.txt", tool.resultSummary)
        val answer = messages[3] as AgentMessageUi
        assertEquals("有 a.txt", answer.content)
        assertEquals(80, answer.usage?.cachedTokens)
        val notice = messages[4] as SystemNoticeMessageUi
        assertEquals(SystemNoticeCode.RuntimeFailed, notice.code)
        assertEquals("boom", notice.detail)
    }

    @Test
    fun toolWithoutResultIsUnknownAfterTurnEnds() {
        var state = DesktopSessionState("s").reduce(
            json("""{"t":"snapshot","sessionId":"s","cursor":2,"items":[{"k":"assistant","seq":1,"time":1,"id":"a","blocks":[{"type":"tool","id":"c","name":"bash","args":"{}"}]}],"queue":[]}"""),
        )
        assertEquals(ToolActivityStatusUi.Running, (DesktopChatProjector.messages(state).single() as ToolActivityMessageUi).status)
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"turn_end","seq":2,"time":2,"turn":1,"reason":"aborted"}}"""))
        val messages = DesktopChatProjector.messages(state)
        assertEquals(ToolActivityStatusUi.Unknown, (messages[0] as ToolActivityMessageUi).status)
        assertEquals(SystemNoticeCode.Stopped, (messages[1] as SystemNoticeMessageUi).code)
    }

    @Test
    fun liveBlocksRenderAsStreamingMessages() {
        var state = DesktopSessionState("s").reduce(snapshot)
        state = state.reduce(json("""{"t":"live","op":"start","sessionId":"s","attemptId":"a2"}"""))
        state = state.reduce(json("""{"t":"live","op":"delta","sessionId":"s","attemptId":"a2","deltas":[{"index":0,"kind":"reasoning","text":"嗯"},{"index":1,"kind":"text","text":"好"}]}"""))
        val tail = DesktopChatProjector.messages(state).takeLast(2)
        assertFalse((tail[0] as ThinkingMessageUi).isStreaming)
        assertTrue((tail[1] as AgentMessageUi).isStreaming)
    }

    @Test
    fun modelPickerSelectsSessionModel() {
        val catalog = DesktopBridgeProtocol.parseCatalog(
            json("""{"default":{"provider":"deepseek","model":"flash"},"groups":[{"id":"deepseek","name":"DeepSeek","models":[{"id":"flash","name":"Flash"},{"id":"chat","name":"Chat","reasoning":{"efforts":[{"id":"low"},{"id":"high"}],"defaultEffort":"high"}}]}]}"""),
        )
        val state = DesktopSessionState("s").reduce(snapshot)
        val picker = DesktopChatProjector.modelPicker(catalog, state)
        assertEquals("chat", picker.selectedModel?.modelId)
        assertEquals("deepseek", picker.selectedModel?.providerSourceType)
        val (efforts, current) = DesktopChatProjector.reasoningEfforts(catalog, state)
        assertEquals(listOf("low", "high"), efforts.map { it.wireValue })
        assertEquals("high", current.wireValue)
        assertEquals("deepseek" to "chat", DesktopChatProjector.parseOptionId(picker.selectedModel!!.id))
    }

    @Test
    fun webSocketUrlNormalization() {
        assertEquals("wss://desk.example.com/v1/ws", DesktopBridgeConfig.normalizeWebSocketUrl("desk.example.com"))
        assertEquals("wss://desk.example.com/v1/ws", DesktopBridgeConfig.normalizeWebSocketUrl("https://desk.example.com/"))
        assertEquals("wss://desk.example.com/pxp/v1/ws", DesktopBridgeConfig.normalizeWebSocketUrl("wss://desk.example.com/pxp/v1/ws"))
        assertEquals("ws://192.168.1.5:7788/v1/ws", DesktopBridgeConfig.normalizeWebSocketUrl("http://192.168.1.5:7788"))
        assertNull(DesktopBridgeConfig.normalizeWebSocketUrl(""))
        assertNull(DesktopBridgeConfig.normalizeWebSocketUrl("ftp://x"))
        assertNull(DesktopBridgeConfig.normalizeWebSocketUrl("https://bad host"))
    }

    @Test
    fun steeredQueueItemMovesIntoTranscriptImmediately() {
        var state = DesktopSessionState("s").reduce(snapshot)
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":7,"queue":[{"id":"q1","text":"插队消息","placement":"queued","rpcId":"r9"}]}"""))
        assertEquals(listOf("q1"), state.visibleQueue.map { it.id })
        // 本地先标记插队：立刻离开队列条，出现在对话末尾。
        state = state.markSteering("q1")
        assertTrue(state.visibleQueue.isEmpty())
        val tail = DesktopChatProjector.messages(state).last() as UserMessageUi
        assertEquals("steer-q1", tail.id)
        assertEquals("插队消息", tail.content)
        // 电脑确认 steering，随后在下一步取走：只剩对话里的正式消息。
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":8,"queue":[{"id":"q1","text":"插队消息","placement":"steering","rpcId":"r9"}]}"""))
        assertTrue(state.visibleQueue.isEmpty())
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":9,"queue":[]}"""))
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"user","seq":10,"time":10,"id":"q1","text":"插队消息","source":"user","rpcId":"r9"}}"""))
        val users = DesktopChatProjector.messages(state).filterIsInstance<UserMessageUi>().filter { it.content == "插队消息" }
        assertEquals(1, users.size)
    }

    @Test
    fun steeredMessageStaysVisibleBetweenLeavingTheQueueAndBeingRecorded() {
        var state = DesktopSessionState("s").reduce(snapshot).copy(running = true)
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":7,"queue":[{"id":"q1","text":"插队","placement":"steering","rpcId":"r1"}]}"""), now = 1_000)
        // 电脑先把它移出队列，记录稍后才到：中间这一段仍显示一条，不闪。
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":8,"queue":[]}"""), now = 1_050)
        assertEquals(listOf("steer-q1"), DesktopChatProjector.messages(state).filterIsInstance<UserMessageUi>().filter { it.content == "插队" }.map { it.id })
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"user","seq":9,"time":9,"id":"q1","text":"插队","source":"user","rpcId":"r1"}}"""), now = 1_080)
        assertEquals(1, DesktopChatProjector.messages(state).filterIsInstance<UserMessageUi>().count { it.content == "插队" })
        assertTrue(state.handoff.isEmpty())
    }

    @Test
    fun recordedMessageRemovesItsQueueCopyEvenWithoutAQueuePush() {
        var state = DesktopSessionState("s").reduce(snapshot)
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":7,"queue":[{"id":"q1","text":"排队","placement":"queued","rpcId":"r1"}]}"""))
        state = state.reduce(json("""{"t":"item","sessionId":"s","item":{"k":"user","seq":9,"time":9,"id":"q1","text":"排队","source":"user","rpcId":"r1"}}"""))
        assertTrue(state.visibleQueue.isEmpty())
        assertEquals(1, DesktopChatProjector.messages(state).filterIsInstance<UserMessageUi>().count { it.content == "排队" })
    }

    @Test
    fun cancelledSteerHandoffExpires() {
        var state = DesktopSessionState("s").reduce(snapshot).copy(running = true)
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":7,"queue":[{"id":"q1","text":"撤回","placement":"steering"}]}"""), now = 0)
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":8,"queue":[]}"""), now = 10)
        assertEquals(1, state.steering.size)
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":9,"title":"x"}"""), now = 10 + DesktopSessionState.HANDOFF_MS)
        assertTrue(state.steering.isEmpty())
    }

    @Test
    fun justSentEchoCanBeSteeredAndMovedBack() {
        var state = DesktopSessionState("s").reduce(snapshot).addEcho(DesktopEcho("r5", "刚发的", queued = true))
        assertEquals(listOf("echo-r5"), state.visibleQueue.map { it.id })
        state = state.moveEcho("r5", queued = false)
        assertTrue(state.visibleQueue.isEmpty())
        assertTrue(DesktopChatProjector.messages(state).any { it.id == "echo-r5" })
        // 电脑已把它当作插队：队列里的 steering 影子不再重复显示。
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":7,"queue":[{"id":"q5","text":"刚发的","placement":"steering","rpcId":"r5"}]}"""))
        assertEquals(1, DesktopChatProjector.messages(state).filterIsInstance<UserMessageUi>().count { it.content == "刚发的" })
        state = state.moveEcho("r5", queued = true)
        assertTrue(DesktopChatProjector.messages(state).none { it.id == "echo-r5" })
    }

    @Test
    fun committedProjectionIsReusedForLiveDeltas() {
        val state = DesktopSessionState("s").reduce(snapshot)
        val committed = DesktopChatProjector.committed(state.items)
        val live = state.reduce(json("""{"t":"live","op":"start","sessionId":"s","attemptId":"x","turn":2,"step":1}"""))
            .reduce(json("""{"t":"live","op":"delta","sessionId":"s","attemptId":"x","deltas":[{"index":0,"kind":"text","text":"正在写"}]}"""))
        assertEquals(live.items, state.items)
        val merged = DesktopChatProjector.merge(committed, live)
        assertEquals(committed, merged.take(committed.size))
        assertEquals(DesktopChatProjector.messages(live), merged)
    }

    @Test
    fun turnUsageMarkerSummarisesTheTurnLikeTheDesktop() {
        val state = DesktopSessionState("s").reduce(json(
            """
            {"t":"snapshot","sessionId":"s","cursor":9,"queue":[],"items":[
              {"k":"turn_start","seq":1,"time":1000,"turn":1},
              {"k":"user","seq":2,"time":1000,"id":"u","text":"hi","source":"user"},
              {"k":"step_start","seq":3,"time":1100,"turn":1,"step":1},
              {"k":"assistant","seq":4,"time":2100,"id":"a","turn":1,"step":1,"firstTokenAt":1600,
               "model":{"provider":"deepseek","model":"v4-pro"},
               "blocks":[{"type":"tool","id":"c1","name":"bash","args":"{}"}],
               "usage":{"input":200,"output":50,"cacheRead":800,"reasoning":10}},
              {"k":"tool_call","seq":5,"time":2200,"callId":"c1","name":"bash"},
              {"k":"tool_result","seq":6,"time":2700,"callId":"c1","isError":false,"text":"ok"},
              {"k":"step_start","seq":7,"time":2800,"turn":1,"step":2},
              {"k":"assistant","seq":8,"time":3800,"id":"b","turn":1,"step":2,"firstTokenAt":3300,
               "model":{"provider":"deepseek","model":"v4-pro"},
               "blocks":[{"type":"text","text":"done"}],"usage":{"input":100,"output":50,"cacheRead":900}},
              {"k":"turn_end","seq":9,"time":4000,"turn":1,"reason":"completed"}
            ]}
            """,
        ))
        val marker = DesktopChatProjector.messages(state).last() as SystemNoticeMessageUi
        assertEquals(SystemNoticeCode.Marker, marker.code)
        val detail = json(marker.detail!!)
        // 2000 输入侧中 1700 命中缓存 → 85%；总量 2000 + 100 输出。
        assertEquals("本轮 · 3秒 · 2.1k tok · 缓存命中 85%", detail.string("title"))
        val details = detail.string("details")!!
        assertTrue(details, details.contains("模型：deepseek / v4-pro"))
        assertTrue(details, details.contains("未缓存输入 300 · 缓存读取 1,700 · 输出 100（其中推理 10）"))
        // 首字：(500 + 500) / 2；输出速度：100 tok / 1 s；工具 0.5 秒。
        assertTrue(details, details.contains("首字 500毫秒 · 输出 100 tok/s · 工具用时 500毫秒"))
    }

    @Test
    fun runtimeTriggersRenderAsMarkersNotUserBubbles() {
        val state = DesktopSessionState("s").reduce(json(
            """{"t":"snapshot","sessionId":"s","cursor":1,"queue":[],"items":[{"k":"trigger","seq":1,"time":5,"id":"t","kind":"subagent-settled","text":"Background subagent finished","summary":"lister finished"}]}""",
        ))
        val only = DesktopChatProjector.messages(state).single() as SystemNoticeMessageUi
        assertEquals(SystemNoticeCode.Marker, only.code)
        assertEquals("子代理已结束：lister finished", json(only.detail!!).string("title"))
    }

    @Test
    fun snapshotAndProjectionsCarryStatsUsageAndSubagents() {
        var state = DesktopSessionState("s").reduce(json(
            """{"t":"snapshot","sessionId":"s","cursor":1,"queue":[],"items":[],
               "stats":{"turns":2,"steps":5,"llmMs":4000,"toolMs":1000,"ttftMs":900,"ttftSteps":3,"decodeMs":2000,"decodeTokens":100},
               "usage":{"uncachedInputTokens":100,"outputTokens":50,"cacheReadTokens":300,"cacheWriteTokens":0},
               "pressure":{"pressureTokens":400,"contextWindow":1000000},
               "subagents":[{"id":"c1","mode":"continuable","label":"查看目录","createdAt":1}]}""",
        ))
        assertEquals(75, state.usage?.cacheHitPercent)
        assertEquals(450L, state.usage?.total)
        assertEquals(300L, state.stats?.averageTtftMs)
        assertEquals(50.0, state.stats?.tokensPerSecond!!, 0.001)
        assertEquals("查看目录", state.subagents.single().displayTitle)
        state = state.reduce(json("""{"t":"projection","sessionId":"s","seq":5,"pressure":{"pressureTokens":800}}"""))
        assertEquals(800L, state.pressure?.pressureTokens)
        assertEquals(1000000L, state.pressure?.contextWindow)
    }
}
