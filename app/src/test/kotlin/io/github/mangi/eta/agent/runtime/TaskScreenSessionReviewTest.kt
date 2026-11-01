package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.model.AgentModelClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Review-only regressions against the actual Boot.dump / VDriveWorker.shot wire formats. */
class TaskScreenSessionReviewTest {
    private class Backend : ScreenToolBackend {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        var rotation = 0
        var display = 7
        var mutationResult = """{"ok":true}"""
        override fun execute(name: String, arguments: String): String {
            calls += name to JSONObject(arguments)
            return when (name) {
                "screen_open" -> """{"ok":true,"mode":"hidden","displayId":7}"""
                "screen_dump" -> """{"ok":true,"displayId":$display,"count":1,
                    "geometry":{"displayId":$display,"width":1080,"height":2400,"rotation":$rotation,"density":420},
                    "nodes":[{"cls":"android.widget.EditText","pkg":"app.one","text":"same","desc":"",
                    "vid":"app.one:id/input","edit":true,"click":true,"scrollable":false,
                    "actions":[16,2097152],"enabled":true,"bounds":[0,200,500,300]}]}"""
                "screen_shot" -> """{"ok":true,"displayId":7,"path":"/tmp/review.png","frameId":"frame-1",
                    "dims":"540x1200","geometry":{"displayId":7,"width":1080,"height":2400,"rotation":0,"density":420},
                    "scaleToDisplayX":2.0,"scaleToDisplayY":2.0}"""
                else -> mutationResult
            }
        }
    }

    private fun setup(backend: Backend): TaskScreenSession {
        val session = TaskScreenSession("review", AgentRunController(), backend, {
            AgentModelClient.ToolResult("{}", images = listOf(AgentModelClient.ModelImage(
                reference = "data:image/png;base64,AA==", mimeType = "image/png", bytes = 2,
                width = 540, height = 1200,
            )))
        })
        call(session, "launch_app", """{"package_name":"app.one","execution_location":"background"}""")
        return session
    }

    private fun call(session: TaskScreenSession, name: String, args: String = "{}"): JSONObject =
        JSONObject(session.execute(AgentModelClient.ToolCall(name, name, args))!!.content)

    @Test fun actualDumpKeepsEditableAndIdentity() {
        val session = setup(Backend())
        val observed = call(session, "observe_screen")
        val node = observed.getJSONArray("ui_nodes").getJSONObject(0)
        assertTrue("Boot.dump edit=true must remain editable", node.getBoolean("editable"))
        assertTrue(node.getBoolean("clickable"))
        assertEquals("app.one:id/input", node.getString("view_id"))
        assertEquals("app.one", node.getString("package"))
    }

    @Test fun realEditableNodeAcceptsIndexedTypeText() {
        val backend = Backend()
        val session = setup(backend)
        val id = call(session, "observe_screen").getString("observation_id")
        val result = call(session, "type_text", """{"text":"hello","index":0,"observation_id":"$id"}""")
        assertTrue("Real editable node rejected: $result", result.getBoolean("ok"))
        assertEquals("app.one:id/input", backend.calls.last().second.getString("view_id"))
    }

    @Test fun screenshotContractUsesBitmapDimensions() {
        val session = setup(Backend())
        val result = call(session, "observe_screen", """{"include_screenshot":true}""")
        val dimensions = result.getJSONObject("coordinate_contract").getJSONObject("screenshot")
        assertEquals("Screenshot is half display width", 540, dimensions.getInt("width"))
        assertEquals(1200, dimensions.getInt("height"))
    }

    @Test fun invalidScreenshotPointNeverReachesBackend() {
        val backend = Backend()
        val session = setup(backend)
        val id = call(session, "observe_screen", """{"include_screenshot":true}""").getString("observation_id")
        val result = call(session, "tap", """{"x":600,"y":10,"coordinate_space":"screenshot","frame_id":"frame-1","observation_id":"$id"}""")
        assertFalse("600 is outside the 540px bitmap: $result", backend.calls.any { it.first == "screen_tap" })
    }

    @Test fun screenshotOnlyObservationCanActOnUnchangedScreen() {
        val backend = Backend()
        val session = setup(backend)
        val id = call(session, "observe_screen", """{"include_screenshot":true,"include_ui_tree":false}""").getString("observation_id")
        val result = call(session, "tap", """{"x":10,"y":20,"coordinate_space":"screenshot","frame_id":"frame-1","observation_id":"$id"}""")
        assertTrue("Screen never changed: $result", result.getBoolean("ok"))
    }

    @Test fun halfTurnRotationRejectsOldObservation() {
        val backend = Backend()
        val session = setup(backend)
        val id = call(session, "observe_screen").getString("observation_id")
        backend.rotation = 2
        call(session, "tap", """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$id"}""")
        assertFalse("Rotation changed despite identical width/height", backend.calls.any { it.first == "screen_tap" })
    }

    @Test fun changedDisplayRejectsOldObservation() {
        val backend = Backend()
        val session = setup(backend)
        val id = call(session, "observe_screen").getString("observation_id")
        backend.display = 8
        call(session, "tap", """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$id"}""")
        assertFalse("Display identity changed", backend.calls.any { it.first == "screen_tap" })
    }

    @Test fun uncertainGeometryFailureInvalidatesObservation() {
        val backend = Backend()
        val session = setup(backend)
        val id = call(session, "observe_screen").getString("observation_id")
        backend.mutationResult = """{"ok":false,"code":"GEOMETRY_CHANGED_DURING_OPERATION","operationMayHaveRun":true}"""
        val args = """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$id"}"""
        call(session, "tap", args)
        call(session, "tap", args)
        assertEquals("An uncertain operation cannot be retried using the old observation", 1,
            backend.calls.count { it.first == "screen_tap" })
    }
}
