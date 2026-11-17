package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.model.AgentModelClient
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 11 号方案：后台动作后自动回看、只核对目标元素、少走弯路、程序自己决定截图。 */
class TaskScreenSessionSpeedTest {
    private class Backend : ScreenToolBackend {
        val calls = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        @Volatile var nodes: List<JSONObject> = listOf(button("确定", 0, 0, 200, 100), label("12:00", 0, 300, 200, 360))
        @Volatile var pkg = "app.one"
        /** 不为空时，screen_tap 直接回这个（模拟防重试拦下）。 */
        @Volatile var tapReply: String? = null
        /** 每次读树前调用；参数是第几次读树（从 1 开始）。 */
        @Volatile var beforeDump: ((Int) -> Unit)? = null
        private var dumps = 0
        override fun execute(name: String, arguments: String): String {
            calls += name to JSONObject(arguments)
            if (name == "screen_tap") tapReply?.let { return it }
            if (name == "screen_dump") { dumps++; beforeDump?.invoke(dumps) }
            return when (name) {
                "screen_open" -> """{"ok":true,"mode":"hidden","displayId":7,"display":7}"""
                "screen_dump" -> JSONObject().put("ok", true).put("displayId", 7).put("package", pkg)
                    .put("geometry", JSONObject().put("displayId", 7).put("width", 1080).put("height", 2400).put("rotation", 0))
                    .put("count", nodes.size).put("nodes", JSONArray(nodes)).toString()
                "screen_shot" -> """{"ok":true,"path":"/tmp/s.png","frameId":"f1","displayId":7,"width":540,"height":1200,
                    "dims":"540x1200","geometry":{"width":1080,"height":2400,"rotation":0,"displayId":7},
                    "scaleToDisplayX":2.0,"scaleToDisplayY":2.0}"""
                else -> """{"ok":true,"accepted":true}"""
            }
        }
        fun names() = calls.map { it.first }
        fun last(name: String) = calls.last { it.first == name }.second
    }

    private fun session(backend: Backend) = TaskScreenSession(
        runId = "speed-${System.nanoTime()}",
        controller = AgentRunController(),
        backend = backend,
        readScreenshot = {
            AgentModelClient.ToolResult("{}", images = listOf(AgentModelClient.ModelImage(
                reference = "data:image/png;base64,AA==", mimeType = "image/png", bytes = 2, width = 540, height = 1200)))
        },
    ).also { it.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}""")) }

    private fun TaskScreenSession.run(name: String, args: JSONObject = JSONObject()): JSONObject =
        JSONObject(execute(call(name, args.toString()))!!.content)

    @Test fun actionResultCarriesAFreshObservationTheNextStepCanUse() {
        val backend = Backend()
        val tools = session(backend)
        val first = tools.run("observe_screen").getString("observation_id")
        val tapped = tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", first))
        assertTrue(tapped.toString(), tapped.getBoolean("ok"))
        val after = tapped.getJSONObject("after")
        val next = after.getString("observation_id")
        assertNotEquals(first, next)
        assertTrue(after.getJSONArray("ui_nodes").length() > 0)
        // 下一步直接用 after 里的编号，中间不用再 observe_screen。
        val observeCalls = backend.names().count { it == "screen_dump" }
        val again = tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", next))
        assertTrue(again.toString(), again.getBoolean("ok"))
        assertEquals(2, backend.names().count { it == "screen_tap" })
        assertEquals(observeCalls + 2, backend.names().count { it == "screen_dump" }) // 动作前核对 + 动作后回看
    }

    @Test fun aChangingClockElsewhereNoLongerBlocksTheTap() {
        val backend = Backend()
        val tools = session(backend)
        val id = tools.run("observe_screen").getString("observation_id")
        backend.nodes = listOf(button("确定", 0, 0, 200, 100), label("12:01", 0, 300, 200, 360))
        assertTrue(tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", id)).getBoolean("ok"))
    }

    @Test fun aSlightlyMovedTargetIsTappedWhereItIsNow() {
        val backend = Backend()
        val tools = session(backend)
        val id = tools.run("observe_screen").getString("observation_id")
        backend.nodes = listOf(button("确定", 0, 20, 200, 120), label("12:00", 0, 300, 200, 360))
        assertTrue(tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", id)).getBoolean("ok"))
        assertEquals(70, backend.last("screen_tap").getInt("y"))
    }

    @Test fun aVanishedOrAmbiguousTargetIsRefusedWithoutTapping() {
        for (changed in listOf(
            listOf(label("12:00", 0, 300, 200, 360), label("加载中", 0, 0, 200, 100)),
            listOf(button("确定", 0, 0, 200, 100), button("确定", 0, 10, 200, 110), label("12:00", 0, 300, 200, 360)),
        )) {
            val backend = Backend()
            val tools = session(backend)
            val id = tools.run("observe_screen").getString("observation_id")
            backend.nodes = changed
            val result = tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", id))
            assertEquals("OBSERVATION_STALE", result.getString("code"))
            assertFalse(backend.names().contains("screen_tap"))
        }
    }

    @Test fun aDialogOverTheTappedPointStillBlocksACoordinateTap() {
        val backend = Backend()
        val tools = session(backend)
        val id = tools.run("observe_screen").getString("observation_id")
        backend.nodes = listOf(label("要离开吗？", 0, 0, 1080, 2400), button("确定", 0, 0, 200, 100))
        val result = tools.run("tap", JSONObject().put("x", 100).put("y", 50).put("coordinate_space", "display").put("observation_id", id))
        assertTrue(result.toString(), result.getBoolean("ok")) // 点到的最小元素仍是同一个按钮
        backend.nodes = listOf(label("要离开吗？", 0, 0, 1080, 2400))
        val id2 = tools.run("observe_screen").getString("observation_id")
        backend.nodes = listOf(button("确定", 0, 0, 200, 100), label("对话框", 0, 0, 300, 150))
        val blocked = tools.run("tap", JSONObject().put("x", 100).put("y", 50).put("coordinate_space", "display").put("observation_id", id2))
        assertEquals("OBSERVATION_STALE", blocked.getString("code"))
    }

    @Test fun keysAndFocusedTypingNeedNoObservationIdAndSubmitPressesEnter() {
        val backend = Backend().apply { nodes = listOf(input("", focused = true), label("搜索", 0, 0, 200, 80)) }
        val tools = session(backend)
        tools.run("observe_screen")
        val typed = tools.run("type_text", JSONObject().put("text", "高德").put("submit", true))
        assertTrue(typed.toString(), typed.getBoolean("ok"))
        assertEquals(listOf("screen_text", "screen_key"), backend.names().filter { it == "screen_text" || it == "screen_key" })
        assertEquals(66, backend.last("screen_key").getInt("code"))
        assertTrue(tools.run("press_key", JSONObject().put("button", "BACK")).getBoolean("ok"))
        assertTrue(tools.run("press_key", JSONObject().put("button", "BACK")).getBoolean("ok"))
    }

    @Test fun appendReadsTheCurrentTextFirst() {
        val backend = Backend().apply { nodes = listOf(input("你好", focused = true), label("聊天", 0, 0, 200, 80)) }
        val tools = session(backend)
        tools.run("observe_screen")
        assertTrue(tools.run("type_text", JSONObject().put("text", "，在吗").put("mode", "append")).getBoolean("ok"))
        assertEquals("你好，在吗", backend.last("screen_text").getString("text"))
    }

    @Test fun anEmptyTreeGetsAScreenshotWithoutTheModelAsking() {
        val backend = Backend().apply { nodes = listOf(view("android.view.SurfaceView", 0, 0, 1080, 2400)) }
        val tools = session(backend)
        val observed = tools.execute(call("observe_screen", "{}"))!!
        assertEquals(1, observed.images.size)
        assertEquals("sparse_ui_tree", JSONObject(observed.content).getString("screenshot_reason"))
        // 树读得到内容时只给树。
        val normal = Backend()
        assertTrue(session(normal).execute(call("observe_screen", "{}"))!!.images.isEmpty())
        assertFalse(normal.names().contains("screen_shot"))
    }

    @Test fun waitReturnsAsSoonAsThePageSettlesAndShowsTheScreen() {
        val backend = Backend()
        val tools = session(backend)
        tools.run("observe_screen")
        val waited = tools.run("wait", JSONObject().put("duration_ms", 8000))
        assertTrue(waited.getBoolean("ok"))
        assertTrue(backend.last("screen_wait").getInt("timeout_ms") <= 1000)
        assertEquals(2, backend.names().count { it == "screen_wait" }) // 启动后回看一次 + wait 一次：页面一静就返回，不再等满 8 秒
        assertTrue(waited.getJSONObject("after").has("observation_id"))
    }

    @Test fun waitForPackageWorksOnTheBackgroundScreen() {
        val backend = Backend()
        val tools = session(backend)
        val result = tools.run("wait_for_package", JSONObject().put("package_name", "app.one").put("timeout_ms", 1000))
        assertTrue(result.toString(), result.getBoolean("ok"))
    }

    /** 2026111623 真机反馈：点按被防重试拦下（没执行）后，刚才的观察仍然有效，接着按返回不用再观察。 */
    @Test fun aBlockedTapKeepsTheObservationSoTheNextStepNeedsNoNewLook() {
        val backend = Backend()
        val tools = session(backend)
        val id = tools.run("observe_screen").getString("observation_id")
        backend.tapReply = """{"ok":false,"code":"NO_PROGRESS","operationMayHaveRun":false}"""
        assertEquals("NO_PROGRESS", tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", id)).getString("code"))
        val back = tools.run("press_key", JSONObject().put("button", "BACK").put("observation_id", id))
        assertTrue(back.toString(), back.getBoolean("ok"))
        // 说不清有没有执行的仍然作废，要先重新观察。
        val fresh = tools.run("observe_screen").getString("observation_id")
        backend.tapReply = """{"ok":false,"code":"BACKGROUND_TOOL_UNAVAILABLE"}"""
        tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", fresh))
        assertEquals("BACKGROUND_OBSERVATION_REQUIRED",
            tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", fresh)).getString("code"))
    }

    @Test fun launchLooksOnceAndBackWithoutAnyObservationLooksByItself() {
        val backend = Backend()
        val tools = TaskScreenSession(runId = "speed-launch", controller = AgentRunController(), backend = backend,
            readScreenshot = { AgentModelClient.ToolResult("{}") })
        // 启动后自动看一眼：after 里的编号下一步直接能用，不用先 observe_screen。
        val launched = tools.run("launch_app", JSONObject().put("package_name", "app.one").put("execution_location", "background"))
        val id = launched.getJSONObject("after").getString("observation_id")
        assertFalse(launched.has("observation_required"))
        assertTrue(tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", id)).getBoolean("ok"))
        // 手上没有观察（读树失败后）时按返回：程序自己读一次再按（读树 + 动作前核对 + 按键）。
        backend.tapReply = """{"ok":false,"code":"BACKGROUND_TOOL_UNAVAILABLE"}"""
        tools.run("tap_element", JSONObject().put("index", 0).put("observation_id",
            tools.run("observe_screen").getString("observation_id")))
        val start = backend.calls.size
        val back = tools.run("press_key", JSONObject().put("button", "BACK"))
        assertTrue(back.toString(), back.getBoolean("ok"))
        assertEquals(listOf("screen_dump", "screen_dump", "screen_key"), backend.names().drop(start).take(3))
    }

    @Test fun aSlowPageAfterATapIsWaitedForBeforeSayingNothingChanged() {
        val page = List(6) { label("第 $it 项", 0, 120 * it, 600, 120 * it + 100) } + button("确定", 0, 900, 200, 1000)
        val backend = Backend().apply { nodes = page }
        val tools = session(backend)
        val id = tools.run("observe_screen").getString("observation_id")
        val base = backend.names().count { it == "screen_dump" }
        // 点按读两次树（动作前核对、动作后回看）：回看时还是旧页面，再看一次才读到详情页。
        backend.beforeDump = { n -> if (n == base + 3) backend.nodes = listOf(label("详情", 0, 0, 600, 100)) + page.take(5) }
        val tapped = tools.run("tap_element", JSONObject().put("index", 6).put("observation_id", id))
        assertTrue(tapped.toString(), tapped.getJSONObject("after").getBoolean("screen_changed"))
        assertEquals(1, tapped.getInt("late_change_checks"))
        // 一直没变：最多再看两次，然后照实报告没变化。
        backend.beforeDump = null
        val next = tapped.getJSONObject("after").getString("observation_id")
        val again = tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", next))
        assertFalse(again.getJSONObject("after").getBoolean("screen_changed"))
        assertEquals(2, again.getInt("late_change_checks"))
    }

    /** 动作后的回看第一次没读成（页面切换中）：隔一下再读一次，after 照常可用；两次都失败就写明下一步先观察。 */
    @Test fun aFailedLookAfterAnActionIsRetriedOnce() {
        for (failures in listOf(setOf(4), setOf(4, 5))) {
            val backend = Backend()
            var dumps = 0
            val flaky = object : ScreenToolBackend {
                override fun execute(name: String, arguments: String): String {
                    if (name == "screen_dump" && ++dumps in failures) return """{"ok":false,"code":"WINDOW_NOT_READY"}"""
                    return backend.execute(name, arguments)
                }
            }
            val tools = TaskScreenSession(runId = "speed-retry-${failures.size}", controller = AgentRunController(), backend = flaky,
                readScreenshot = { AgentModelClient.ToolResult("{}") })
            tools.run("launch_app", JSONObject().put("package_name", "app.one").put("execution_location", "background")) // 第 1 次读树
            val id = tools.run("observe_screen").getString("observation_id") // 第 2 次
            // 第 3 次是动作前核对，第 4 次是动作后回看。
            val tapped = tools.run("tap_element", JSONObject().put("index", 0).put("observation_id", id))
            assertTrue(tapped.toString(), tapped.getBoolean("ok"))
            if (failures.size == 1) {
                assertTrue(tapped.toString(), tapped.getJSONObject("after").has("observation_id"))
                assertFalse(tapped.has("after_unavailable"))
            } else {
                assertFalse(tapped.has("after"))
                assertTrue(tapped.getBoolean("after_unavailable"))
                assertEquals("WINDOW_NOT_READY", tapped.getString("after_error"))
            }
        }
    }

    @Test fun relocationAndSparsenessRules() {
        val target = NodeRelocation.Identity("Button", "", "确定", "", NodeRelocation.Box(0, 0, 200, 100))
        assertEquals(NodeRelocation.Result.At(1), NodeRelocation.find(target,
            listOf(target.copy(text = "取消"), target.copy(box = NodeRelocation.Box(0, 30, 200, 130))), 1080))
        assertEquals(NodeRelocation.Result.Missing, NodeRelocation.find(target,
            listOf(target.copy(box = NodeRelocation.Box(0, 900, 200, 1000))), 1080))
        assertTrue(ScreenSparseness.needsScreenshot(listOf(ScreenSparseness.View("TextView", true, 100)), 1080, 2400))
        assertFalse(ScreenSparseness.needsScreenshot(List(3) { ScreenSparseness.View("TextView", true, 100) }, 1080, 2400))
        assertTrue(ScreenSparseness.needsScreenshot(List(3) { ScreenSparseness.View("TextView", true, 100) } +
            ScreenSparseness.View("android.webkit.WebView", false, 1080L * 2000), 1080, 2400))
    }

    private fun call(name: String, args: String) = AgentModelClient.ToolCall(name, name, args)

    private companion object {
        fun node(cls: String, text: String, desc: String, l: Int, t: Int, r: Int, b: Int, edit: Boolean = false,
            click: Boolean = false, focused: Boolean = false): JSONObject = JSONObject()
            .put("cls", cls).put("pkg", "app.one").put("text", text).put("desc", desc).put("vid", if (edit) "app.one:id/input" else "")
            .put("edit", edit).put("click", click).put("scrollable", false).put("actions", JSONArray(listOf(16)))
            .put("enabled", true).put("focused", focused).put("bounds", JSONArray(listOf(l, t, r, b)))
        fun button(text: String, l: Int, t: Int, r: Int, b: Int) = node("android.widget.Button", text, "", l, t, r, b, click = true)
        fun label(text: String, l: Int, t: Int, r: Int, b: Int) = node("android.widget.TextView", text, "", l, t, r, b)
        fun input(text: String, focused: Boolean) = node("android.widget.EditText", text, "", 0, 200, 1000, 300, edit = true, click = true, focused = focused)
        fun view(cls: String, l: Int, t: Int, r: Int, b: Int) = node(cls, "", "", l, t, r, b)
    }
}
