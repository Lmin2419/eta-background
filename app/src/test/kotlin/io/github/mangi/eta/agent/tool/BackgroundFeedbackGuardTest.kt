package io.github.mangi.eta.agent.tool

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackgroundFeedbackGuardTest {
    private val dump = """{"ok":true,"displayId":7,"geometry":{"displayId":7,"width":1000,"height":2000},"nodes":[{"pkg":"app","text":"添加设备","vid":"button","click":true,"enabled":true,"bounds":[200,500,800,650]}]}"""
    private val click = JSONObject("""{"text":"添加设备"}""")
    private val tap = JSONObject("""{"x":500,"y":570,"coordinate_space":"display"}""")
    private val unknown = JSONObject("""{"ok":true,"operationMayHaveRun":true,"outcome":"UNKNOWN"}""")

    @Test fun changingTheClickApiCannotBypassTheBudget() {
        val guard = BackgroundFeedbackGuard()
        guard.record("screen_dump", JSONObject(dump))
        assertNull(guard.before("screen_click", click)); guard.record("screen_click", unknown)
        guard.record("screen_dump", JSONObject(dump)) // a fresh observation isn't progress
        assertNull(guard.before("screen_tap", tap)); guard.record("screen_tap", unknown)
        assertEquals("NO_PROGRESS", guard.before("screen_press", tap)?.getString("code"))
        guard.record("screen_finish", JSONObject().put("ok", false))
        assertEquals("NO_PROGRESS", guard.before("screen_click", click)?.getString("code"))
        guard.record("screen_open", JSONObject().put("ok", true).put("displayId", 7))
        assertEquals("NO_PROGRESS", guard.before("screen_click", click)?.getString("code"))
        // Real form state changes allow a new attempt.
        guard.record("screen_dump", JSONObject(dump.replace("添加设备", "正在添加")))
        assertNull(guard.before("screen_tap", tap))
    }

    @Test fun changedScreensAndSeparateBindingsHaveIndependentBudgets() {
        val a = BackgroundFeedbackGuard(); val b = BackgroundFeedbackGuard()
        for (g in listOf(a, b)) g.record("screen_dump", JSONObject(dump))
        repeat(2) { a.before("screen_click", click); a.record("screen_click", unknown) }
        assertNotNull(a.before("screen_click", click))
        assertNull(b.before("screen_click", click))
        b.record("screen_click", JSONObject("""{"ok":true,"outcome":"CHANGED"}"""))
        assertNull(b.before("screen_click", click))
        a.clear(); a.record("screen_dump", JSONObject(dump))
        assertNull(a.before("screen_click", click))
    }

    @Test fun rejectionCannotClearEarlierUncertainSubmissions() {
        val guard = ActionRepeatGuard { 10L }
        repeat(2) { guard.record("button", "state", dispatched = true, changed = false) }
        guard.record("button", "state", dispatched = false, changed = false)
        assertTrue(guard.blocked("button", "state"))
        assertFalse(guard.blocked("button", "different input"))
    }
}
