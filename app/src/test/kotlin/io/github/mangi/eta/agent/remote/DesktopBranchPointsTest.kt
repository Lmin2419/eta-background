package io.github.mangi.eta.agent.remote

import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import io.github.mangi.eta.ui.screens.desktop.DesktopChatProjector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 电脑会话的分支 / 重新回答 / 编辑重发切点，以及斜杠命令提示行。 */
class DesktopBranchPointsTest {
    private fun items(text: String): List<DesktopItem> =
        DesktopBridgeProtocol.parseItems(Json.parseToJsonElement(text))

    // DSH 的顺序：turn/start → step/start → user/message → assistant → turn/end（与真机日志一致）。
    private val transcript = items(
        """
        [
          {"k":"turn_start","seq":5,"time":1,"turn":1},
          {"k":"step_start","seq":7,"time":1,"turn":1,"step":1},
          {"k":"user","seq":9,"time":1,"id":"u1","text":"第一问","images":1,"imageRefs":[{"attachmentId":"img1"}],"files":["a.txt"],"source":"user"},
          {"k":"assistant","seq":16,"time":2,"id":"a1","turn":1,"step":1,"blocks":[{"type":"text","text":"第一答"}]},
          {"k":"turn_end","seq":18,"time":3,"turn":1,"reason":"completed"},
          {"k":"turn_start","seq":21,"time":4,"turn":2},
          {"k":"step_start","seq":23,"time":4,"turn":2,"step":1},
          {"k":"user","seq":24,"time":4,"id":"u2","text":"第二问","images":0,"files":[],"source":"user"},
          {"k":"assistant","seq":28,"time":5,"id":"a2","turn":2,"step":1,"blocks":[{"type":"text","text":"先看看"},{"type":"tool","id":"c1","name":"bash","args":"{}"}]},
          {"k":"tool_call","seq":29,"time":5,"callId":"c1","name":"bash"},
          {"k":"tool_result","seq":30,"time":5,"callId":"c1","isError":false,"text":"ok"},
          {"k":"user","seq":32,"time":6,"id":"u3","text":"补充一句","images":0,"files":[],"source":"user"},
          {"k":"assistant","seq":35,"time":6,"id":"a3","turn":2,"step":2,"blocks":[{"type":"text","text":"第二答"}]},
          {"k":"turn_end","seq":37,"time":7,"turn":2,"reason":"completed"},
          {"k":"turn_start","seq":39,"time":8,"turn":3},
          {"k":"user","seq":42,"time":8,"id":"u4","text":"第三问","images":0,"files":[],"source":"user"},
          {"k":"assistant","seq":43,"time":9,"id":"a4","turn":3,"step":1,"blocks":[{"type":"text","text":"半截"}]},
          {"k":"turn_end","seq":45,"time":9,"turn":3,"reason":"error","message":"rate limited"}
        ]
        """,
    )

    @Test
    fun branchCutsAtTurnEndOfTheClosingReplyOnly() {
        val points = DesktopChatProjector.branchPoints(transcript)
        assertEquals(mapOf("a-16-0" to 18L, "a-35-0" to 37L), points.branchAt)
        // 调工具之前的过渡回复、出错轮次的回复都不能分支（桌面也不给）。
        assertNull(points.branchAt["a-28-0"])
        assertNull(points.branchAt["a-43-0"])
    }

    @Test
    fun reaskRewindsToBeforeTheTurnAndResendsItsOpeningPrompt() {
        val points = DesktopChatProjector.branchPoints(transcript)
        val first = points.reask.getValue("a-16-0")
        assertEquals(4L, first.atSeq)
        assertEquals("第一问", first.prompt.text)
        assertEquals(listOf("img1"), first.prompt.imageRefs.map { it["attachmentId"].toString().trim('"') })
        assertEquals(listOf("a.txt"), first.prompt.files)
        // 中途补充的消息不算开启这一轮的消息。
        val second = points.reask.getValue("a-35-0")
        assertEquals(20L, second.atSeq)
        assertEquals("第二问", second.prompt.text)
        assertEquals(second, points.reask.getValue("a-28-0"))
    }

    @Test
    fun editCutsBeforeTheTurnOrBeforeAMidTurnMessage() {
        val points = DesktopChatProjector.branchPoints(transcript)
        assertEquals(4L, points.edit.getValue("u-9").atSeq)
        assertEquals(20L, points.edit.getValue("u-24").atSeq)
        assertEquals(31L, points.edit.getValue("u-32").atSeq)
        assertEquals("补充一句", points.edit.getValue("u-32").prompt.text)
    }

    @Test
    fun failedLastTurnOffersItsPromptForRetry() {
        val points = DesktopChatProjector.branchPoints(transcript)
        assertEquals("第三问", points.failedPrompt?.text)
        val recovered = DesktopChatProjector.branchPoints(transcript + items(
            """[{"k":"user","seq":50,"time":10,"id":"u5","text":"再来","images":0,"files":[],"source":"user"}]""",
        ))
        assertNull(recovered.failedPrompt)
    }

    @Test
    fun firstTurnAtTheStartOfTheLogFallsBackToANewSession() {
        val points = DesktopChatProjector.branchPoints(items(
            """
            [
              {"k":"turn_start","seq":0,"time":1,"turn":1},
              {"k":"user","seq":1,"time":1,"id":"u","text":"hi","images":0,"files":[],"source":"user"},
              {"k":"assistant","seq":2,"time":2,"id":"a","turn":1,"step":1,"blocks":[{"type":"text","text":"hello"}]},
              {"k":"turn_end","seq":3,"time":3,"turn":1,"reason":"completed"}
            ]
            """,
        ))
        assertNull(points.reask.getValue("a-2-0").atSeq)
        assertNull(points.edit.getValue("u-1").atSeq)
        assertEquals(3L, points.branchAt["a-2-0"])
    }

    @Test
    fun slashCommandLineAndResultMergeIntoOneRow() {
        val rows = DesktopChatProjector.committed(items(
            """
            [
              {"k":"notice","seq":21,"time":1,"code":"command","id":"cmd-1","text":"/compact"},
              {"k":"notice","seq":22,"time":1,"code":"compaction_start","text":""},
              {"k":"notice","seq":24,"time":2,"code":"command_done","id":"cmd-1","kind":"error","text":"no summary"},
              {"k":"notice","seq":25,"time":3,"code":"command","id":"cmd-2","text":"/goal ship"}
            ]
            """,
        ))
        assertEquals(listOf("cmd-cmd-1", "cmd-cmd-2"), rows.map { it.id })
        val done = rows[0] as SystemNoticeMessageUi
        val detail = Json.parseToJsonElement(done.detail!!).jsonObject
        assertEquals("\"/compact · 失败\"", detail["title"].toString())
        assertEquals("\"no summary\"", detail["details"].toString())
        assertTrue((rows[1] as SystemNoticeMessageUi).detail!!.contains("执行中"))
    }
}
