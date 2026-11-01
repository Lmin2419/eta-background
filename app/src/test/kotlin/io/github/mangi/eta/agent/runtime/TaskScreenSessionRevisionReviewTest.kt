package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.model.AgentModelClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Independent acceptance probes. Wire geometry is added by Boot.handle(), not dump(). */
class TaskScreenSessionRevisionReviewTest {
    private class Backend : ScreenToolBackend {
        val calls = mutableListOf<String>()
        var frame = "frame-1"
        var captureAvailable = true
        override fun execute(name: String, arguments: String): String {
            calls += name
            return when (name) {
                "screen_open" -> """{"ok":true,"mode":"hidden","displayId":7}"""
                "screen_dump" -> """{"ok":true,"displayId":7,"count":1,
                    "geometry":{"displayId":7,"width":1080,"height":2400,"rotation":0,"density":420},
                    "nodes":[{"cls":"android.widget.EditText","pkg":"app.one","text":"same","desc":"",
                    "vid":"app.one:id/input","edit":true,"click":true,"scrollable":false,
                    "actions":[16,2097152],"enabled":true,"bounds":[0,200,500,300]}]}"""
                "screen_shot" -> if (!captureAvailable) """{"ok":false,"code":"NO_FRAME"}""" else
                    """{"ok":true,"displayId":7,"path":"/tmp/review.png","frameId":"$frame",
                    "dims":"540x1200","geometry":{"displayId":7,"width":1080,"height":2400,"rotation":0,"density":420},
                    "scaleToDisplayX":2.0,"scaleToDisplayY":2.0}"""
                else -> """{"ok":true}"""
            }
        }
    }

    private fun setup(backend: Backend): TaskScreenSession {
        val session = TaskScreenSession("revision-review", AgentRunController(), backend, {
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

    @Test fun defaultTreeObservationDoesNotEncodeAnUnrequestedImage() {
        val backend = Backend()
        val session = setup(backend)
        val result = call(session, "observe_screen")
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertFalse("Boot.handle provides authoritative geometry; a tree observation needs no image", backend.calls.contains("screen_shot"))
    }

    @Test fun nodeObservationWorksWhenCaptureIsUnavailable() {
        val backend = Backend().apply { captureAvailable = false }
        val session = setup(backend)
        val result = call(session, "observe_screen")
        assertTrue("Valid nodes and authoritative geometry are available: $result", result.getBoolean("ok"))
        assertTrue(result.getJSONArray("ui_nodes").length() > 0)
    }

    @Test fun indexedTextInputDoesNotRequireAnotherScreenshot() {
        val backend = Backend()
        val session = setup(backend)
        val id = call(session, "observe_screen").getString("observation_id")
        backend.calls.clear()
        val result = call(session, "type_text", """{"index":0,"text":"hello","observation_id":"$id"}""")
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertFalse("Geometry verification must not encode and write a screenshot for a node input", backend.calls.contains("screen_shot"))
    }

    @Test fun aNewBufferFrameDoesNotRewriteThePublishedScreenshotContract() {
        val backend = Backend()
        val session = setup(backend)
        val observed = call(session, "observe_screen", """{"include_screenshot":true}""")
        val id = observed.getString("observation_id")
        val publishedFrame = observed.getString("frame_id")
        backend.frame = "frame-2" // New buffer, same geometry, scale and node content.
        val result = call(session, "tap", """{"x":10,"y":20,"coordinate_space":"screenshot","frame_id":"$publishedFrame","observation_id":"$id"}""")
        assertTrue("A valid published frame was changed inside validation: $result", result.getBoolean("ok"))
        assertEquals(1, backend.calls.count { it == "screen_tap" })
    }
}
