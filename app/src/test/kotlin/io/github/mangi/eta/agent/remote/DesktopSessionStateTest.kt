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
}
