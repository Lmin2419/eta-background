package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 10 号方案第一期：交卷前自检什么时候让模型先复核一次。 */
class CompletionGateTest {
    private fun ok(name: String) = ToolOutcomeSummary(name, ok = true)
    private fun failed(name: String, code: String = "EXIT_1", message: String = "boom") = ToolOutcomeSummary(name, false, code, message)

    @Test fun lastToolFailedTriggersOnceAndListsTheFailure() {
        val outcomes = listOf(ok("read_file"), failed("terminal", "EXIT_2", "SyntaxError: unexpected EOF"))
        val request = CompletionGate.review(outcomes, "运行报错了。", alreadyReviewed = false)!!
        assertEquals(CompletionGate.Reason.LastToolFailed, request.reason)
        assertEquals(listOf("terminal"), request.failures.map { it.name })
        assertTrue(request.prompt().contains("SyntaxError: unexpected EOF"))
        assertTrue(request.prompt().contains("EXIT_2"))
        // 每轮最多一次。
        assertNull(CompletionGate.review(outcomes, "运行报错了。", alreadyReviewed = true))
    }

    @Test fun aLaterSuccessOfTheSameToolCountsAsFixed() {
        val outcomes = listOf(failed("terminal"), ok("terminal"))
        assertNull(CompletionGate.review(outcomes, "修好了，测试全部通过。", alreadyReviewed = false))
        assertNull(CompletionGate.review(outcomes, "这个办法行不通。", alreadyReviewed = false))
    }

    @Test fun givingUpAfterAnUnresolvedFailureTriggers() {
        val outcomes = listOf(failed("web_fetch", "HTTP_403"), ok("read_file"))
        assertEquals(CompletionGate.Reason.GaveUpAfterFailure,
            CompletionGate.review(outcomes, "网页打不开，无法完成这个任务。", alreadyReviewed = false)?.reason)
        // 没放弃、最后一步也成功了：不打扰。
        assertNull(CompletionGate.review(outcomes, "这是从本地文件里整理出来的结果。", alreadyReviewed = false))
    }

    @Test fun claimingDoneAfterAnUnconfirmedActionAsksToVerify() {
        val outcomes = listOf(ok("observe_screen"), ToolOutcomeSummary("tap_element", ok = true, unconfirmed = true))
        val request = CompletionGate.review(outcomes, "已发送。", alreadyReviewed = false)!!
        assertEquals(CompletionGate.Reason.UnconfirmedAction, request.reason)
        assertTrue(request.prompt().contains("不要重复执行"))
        assertNull(CompletionGate.review(outcomes, "我点了发送按钮，请你看一下有没有发出去。", alreadyReviewed = false))
    }

    @Test fun noToolsOrStopCodesDoNotTrigger() {
        assertNull(CompletionGate.review(emptyList(), "做不到。", alreadyReviewed = false))
        assertNull(CompletionGate.review(listOf(failed("tap", "NO_PROGRESS")), "界面没有反应，停止了。", alreadyReviewed = false))
        assertNull(CompletionGate.review(listOf(failed("terminal", "TOOL_DISABLED")), "终端工具被关掉了。", alreadyReviewed = false))
    }

    @Test fun summariesReadToolResultShapes() {
        val terminal = ToolOutcomeSummary.of("terminal", """{"exit_code":127,"stderr":"python: command not found"}""")
        assertEquals(false, terminal.ok); assertEquals("EXIT_127", terminal.code); assertEquals("python: command not found", terminal.message)
        val tool = ToolOutcomeSummary.of("read_file", """{"ok":false,"code":"FILE_NOT_FOUND","message":"no such file"}""")
        assertEquals("FILE_NOT_FOUND", tool.code)
        assertTrue(ToolOutcomeSummary.of("tap", """{"ok":true,"outcome":"UNKNOWN"}""").unconfirmed)
        assertTrue(ToolOutcomeSummary.of("web_search", "plain text result").ok)
        assertEquals(300, ToolOutcomeSummary.of("x", """{"ok":false,"message":"${"a".repeat(900)}"}""").message.length)
    }
}
