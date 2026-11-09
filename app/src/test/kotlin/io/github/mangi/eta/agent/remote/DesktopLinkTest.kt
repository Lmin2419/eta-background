package io.github.mangi.eta.agent.remote

import io.github.mangi.eta.ui.screens.desktop.listed
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** dsh-bridge 0.3.0 的新协议面：归档、提问、轮次结束、诊断信息，以及提醒范围。 */
class DesktopLinkTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun row(id: String, blank: Boolean = false, archived: Boolean = false, subagent: Boolean = false) =
        DesktopSessionRow(id, id, 1, false, blank, false, null, null, subagent, null, false, archived)

    @Test
    fun blankDraftsArchivedAndSubagentSessionsStayOutOfTheMainList() {
        assertTrue(row("a").listed)
        assertFalse(row("b", blank = true).listed)
        assertFalse(row("c", archived = true).listed)
        assertFalse(row("d", subagent = true).listed)
        val parsed = DesktopBridgeProtocol.parseSessionRow(json("""{"id":"x","title":"t","updatedAt":3,"running":false,"blank":false,"live":false,"archived":true}"""))
        assertEquals(true, parsed?.archived)
    }

    @Test
    fun helloCarriesRuntimeAndFeatures() {
        val info = DesktopBridgeProtocol.parseServerInfo(json("""{"version":"0.3.0","runtime":"desktop","host":"pc","features":["archive","questions"]}"""))
        assertEquals("0.3.0", info.version)
        assertEquals("desktop", info.runtime)
        assertEquals(setOf("archive", "questions"), info.features)
        // 旧插件没有这些字段。
        assertNull(DesktopBridgeProtocol.parseServerInfo(json("""{"version":"0.2.0"}""")).runtime)
    }

    @Test
    fun questionsParseOptionsAndPlanReviewIntent() {
        val question = DesktopBridgeProtocol.parseQuestion(json("""
            {"id":"q","sessionId":"s","callId":"c","timed":true,"createdAt":5,"questions":[
              {"id":"pick","question":"Which?","options":[{"label":"A","description":"first"},{"label":"B"}],"multiSelect":true},
              {"id":"plan","question":"Approve?","detail":"# Plan","options":[{"label":"Approve"},{"label":"Revise"}],"multiSelect":false,
               "intent":{"kind":"plan-review","approve":"Approve"}}
            ]}
        """.trimIndent()))!!
        assertEquals("c", question.callId)
        assertTrue(question.timed)
        assertTrue(question.isPlanReview)
        assertEquals(listOf("A", "B"), question.items[0].options.map { it.label })
        assertEquals("first", question.items[0].options[0].description)
        assertTrue(question.items[0].multiSelect)
        assertEquals("Approve", question.items[1].intent?.approve)
        assertNull(DesktopBridgeProtocol.parseQuestion(json("""{"id":"empty","questions":[]}""")))
    }

    @Test
    fun answersSerialiseSelectedAndOptionalCustomText() {
        val array = DesktopBridgeProtocol.answersJson(listOf(
            DesktopQuestionAnswer("a", listOf("x", "y")),
            DesktopQuestionAnswer("b", emptyList(), "  mine  "),
            DesktopQuestionAnswer("c", emptyList(), " "),
        ))
        val first = array[0].jsonObject
        assertEquals("a", first["id"]!!.jsonPrimitive.content)
        assertEquals(2, (first["selected"] as JsonArray).size)
        assertFalse(first.containsKey("custom"))
        assertEquals("  mine  ", array[1].jsonObject["custom"]!!.jsonPrimitive.content)
        assertFalse(array[2].jsonObject.containsKey("custom"))
    }

    @Test
    fun continuedQuestionsComeFromTheQuestionsProjection() {
        val snapshot = json("""
            {"t":"snapshot","sessionId":"s","cursor":1,"hasMore":false,"resumed":false,"items":[],"queue":[],
             "questions":{"active":[{"callId":"c1","state":"continued","questions":[{"id":"q","question":"?","options":[]}]},
                                    {"callId":"c2","state":"open","questions":[{"id":"q","question":"?","options":[]}]}]}}
        """.trimIndent())
        val state = DesktopSessionState("s").reduce(snapshot)
        assertEquals(listOf("c1" to true, "c2" to false), state.pendingQuestions.map { it.callId to it.continued })
        val answered = state.reduce(json("""{"t":"projection","sessionId":"s","seq":2,"questions":{"active":[]}}"""))
        assertTrue(answered.pendingQuestions.isEmpty())
        // 没带 questions 的投影帧不清空已知提问。
        assertEquals(2, state.reduce(json("""{"t":"projection","sessionId":"s","seq":2,"title":"x"}""")).pendingQuestions.size)
    }

    @Test
    fun turnEventsCarryReasonAndLastText() {
        val turn = DesktopBridgeProtocol.parseTurn(json("""{"t":"turn","sessionId":"s","turn":3,"reason":"completed","text":"done","time":9}"""))!!
        assertEquals(3, turn.turn)
        assertEquals("completed", turn.reason)
        assertEquals("done", turn.text)
        assertEquals(9L, turn.time)
        assertNull(DesktopBridgeProtocol.parseTurn(json("""{"t":"turn"}""")))
    }

    @Test
    fun remindersFollowScopeWatchedAndMutedSessions() {
        val prefs = DesktopLinkPrefs(watched = setOf("mine"), muted = setOf("quiet"))
        assertTrue(prefs.reminds("mine"))
        assertFalse(prefs.reminds("other"))
        val all = prefs.copy(reminderScope = DesktopReminderScope.All)
        assertTrue(all.reminds("other"))
        assertFalse(all.reminds("quiet"))
    }

    @Test
    fun defaultsKeepTheComputerQuietUnlikeLocalTasks() {
        val defaults = DesktopLinkPrefs()
        assertTrue(defaults.enabled)
        assertTrue(defaults.ongoingLowPriority)
        assertFalse(defaults.glow)
        assertFalse(defaults.fluidCloud)
        assertFalse(defaults.reminderSound)
        assertEquals(DesktopReminderScope.Watched, defaults.reminderScope)
    }

    @Test
    fun reminderIdsAreStablePerKindAndKey() {
        assertEquals(DesktopReminders.notificationId("turn", "s1"), DesktopReminders.notificationId("turn", "s1"))
        assertTrue(DesktopReminders.notificationId("turn", "s1") != DesktopReminders.notificationId("approval", "s1"))
        assertTrue(DesktopReminders.notificationId("turn", "s1") > 0)
    }
}
