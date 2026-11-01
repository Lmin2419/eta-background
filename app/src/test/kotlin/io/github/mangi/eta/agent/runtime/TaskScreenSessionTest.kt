package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.model.AgentModelClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskScreenSessionTest {
    private data class Call(val name: String, val arguments: JSONObject)

    private class FakeBackend : ScreenToolBackend {
        val calls = CopyOnWriteArrayList<Call>()
        @Volatile var openResult = """{"ok":true,"mode":"hidden","displayId":7,"display":7}"""
        @Volatile var dumpResult = dumpJson()
        @Volatile var shotResult = shotJson()
        @Volatile var tapResult = """{"ok":true,"accepted":true,"outcome":"CHANGED"}"""
        @Volatile var pressResult = """{"ok":true,"accepted":true}"""
        @Volatile var textResult = """{"ok":true,"accepted":true}"""
        @Volatile var keyResult = """{"ok":true}"""
        @Volatile var waitResult = """{"ok":true,"found":true}"""
        @Volatile var onExecute: ((String) -> Unit)? = null

        override fun execute(name: String, arguments: String): String? {
            val json = runCatching { JSONObject(arguments) }.getOrDefault(JSONObject())
            calls += Call(name, json)
            onExecute?.invoke(name)
            return when (name) {
                "screen_open" -> openResult
                "screen_dump" -> dumpResult
                "screen_shot" -> shotResult
                "screen_tap" -> tapResult
                "screen_press" -> pressResult
                "screen_text" -> textResult
                "screen_key" -> keyResult
                "screen_wait" -> waitResult
                "screen_finish" -> """{"ok":true}"""
                else -> """{"ok":false,"code":"UNKNOWN"}"""
            }
        }

        fun names(): List<String> = calls.map { it.name }
    }

    private fun session(
        backend: FakeBackend,
        controller: AgentRunController = AgentRunController(),
        rootAvailable: Boolean = true,
    ): TaskScreenSession = TaskScreenSession(
        runId = "run-${System.nanoTime()}",
        controller = controller,
        backend = backend,
        readScreenshot = {
            AgentModelClient.ToolResult(
                content = """{"ok":true,"image_attached":true}""",
                images = listOf(AgentModelClient.ModelImage(
                    reference = "data:image/png;base64,AA==",
                    mimeType = "image/png",
                    bytes = 2,
                    width = 540,
                    height = 1200,
                )),
            )
        },
        rootAvailable = { rootAvailable },
    )

    @Test
    fun ordinaryForegroundCallReturnsNullAndDoesNotTouchBackground() {
        val backend = FakeBackend()
        val tools = session(backend)
        assertNull(tools.execute(call("tap", """{"x":1,"y":2,"coordinate_space":"screen"}""")))
        assertNull(tools.execute(call("observe_screen", "{}")))
        assertEquals(TaskExecutionLocation.FOREGROUND, tools.currentLocation())
        assertTrue(backend.calls.isEmpty())
    }

    @Test
    fun backgroundLaunchObserveAndTapKeepOneTaskLocalObservation() {
        val backend = FakeBackend()
        val tools = session(backend)
        val launched = tools.execute(call(
            "launch_app",
            """{"package_name":"app.one","execution_location":"background"}""",
        ))!!
        assertTrue(JSONObject(launched.content).getBoolean("ok"))
        assertEquals(TaskExecutionLocation.BACKGROUND, tools.currentLocation())

        val observed = tools.execute(call(
            "observe_screen",
            """{"include_screenshot":true,"max_nodes":10}""",
        ))!!
        val observedJson = JSONObject(observed.content)
        assertTrue(observedJson.getBoolean("ok"))
        assertEquals("background", observedJson.getString("execution_location"))
        assertTrue(observedJson.getJSONObject("screenshot").getBoolean("attached"))
        assertEquals(1, observed.images.size)
        val observationId = observedJson.getString("observation_id")
        assertTrue(observationId.startsWith("bg-run-"))

        val tapped = tools.execute(call(
            "tap",
            """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$observationId"}""",
        ))!!
        val tappedJson = JSONObject(tapped.content)
        assertTrue(tappedJson.getBoolean("ok"))
        assertEquals("background", tappedJson.getString("execution_location"))
        val tapCall = backend.calls.last { it.name == "screen_tap" }
        assertEquals(10, tapCall.arguments.getInt("x"))
        assertEquals(20, tapCall.arguments.getInt("y"))
        assertEquals("display", tapCall.arguments.getString("coordinate_space"))

        val repeated = tools.execute(call(
            "tap",
            """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$observationId"}""",
        ))!!
        assertEquals("BACKGROUND_OBSERVATION_REQUIRED", JSONObject(repeated.content).getString("code"))
        assertFalse(backend.calls.count { it.name == "screen_tap" } > 1)
    }

    @Test
    fun staticObservationRemainsValidWithoutTimeExpiry() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = tools.execute(call("observe_screen", "{}"))!!
        val id = JSONObject(observed.content).getString("observation_id")
        Thread.sleep(80)
        val tapped = tools.execute(call(
            "tap",
            """{"x":1,"y":1,"coordinate_space":"display","observation_id":"$id"}""",
        ))!!
        assertTrue(JSONObject(tapped.content).getBoolean("ok"))
    }

    @Test
    fun changedTreeInvalidatesTheOldObservationBeforeInput() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = tools.execute(call("observe_screen", "{}"))!!
        val id = JSONObject(observed.content).getString("observation_id")
        backend.dumpResult = dumpJson(text = "changed")

        val tapped = tools.execute(call(
            "tap_element",
            """{"index":0,"observation_id":"$id"}""",
        ))!!
        assertEquals("OBSERVATION_STALE", JSONObject(tapped.content).getString("code"))
        assertFalse(backend.calls.any { it.name == "screen_tap" })
    }

    @Test
    fun pauseResumeInvalidatesOldObservationWithoutReplayingInput() {
        val controller = AgentRunController()
        val backend = FakeBackend()
        val tools = session(backend, controller)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = tools.execute(call("observe_screen", "{}"))!!
        val id = JSONObject(observed.content).getString("observation_id")
        controller.pause()
        controller.resume()

        val tapped = tools.execute(call(
            "tap",
            """{"x":1,"y":1,"coordinate_space":"display","observation_id":"$id"}""",
        ))!!
        assertEquals("OBSERVE_AFTER_RESUME", JSONObject(tapped.content).getString("code"))
        assertFalse(backend.calls.any { it.name == "screen_tap" })
    }

    @Test
    fun uncertainTimeoutIsReturnedOnceAndNeverReplayed() {
        val backend = FakeBackend().apply {
            tapResult = """{"ok":false,"code":"TIMEOUT","operationMayHaveRun":true,"error":"timeout"}"""
        }
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = tools.execute(call("observe_screen", "{}"))!!
        val id = JSONObject(observed.content).getString("observation_id")

        val first = tools.execute(call(
            "tap",
            """{"x":1,"y":1,"coordinate_space":"display","observation_id":"$id"}""",
        ))!!
        assertEquals("TIMEOUT", JSONObject(first.content).getString("code"))
        assertEquals(1, backend.calls.count { it.name == "screen_tap" })

        val second = tools.execute(call(
            "tap",
            """{"x":1,"y":1,"coordinate_space":"display","observation_id":"$id"}""",
        ))!!
        assertEquals("BACKGROUND_OBSERVATION_REQUIRED", JSONObject(second.content).getString("code"))
        assertEquals(1, backend.calls.count { it.name == "screen_tap" })
    }

    @Test
    fun invalidDisplayCoordinateReturnsAStructuredError() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = tools.execute(call("observe_screen", "{}"))!!
        val id = JSONObject(observed.content).getString("observation_id")
        val result = tools.execute(call(
            "tap",
            """{"x":99999,"y":1,"coordinate_space":"display","observation_id":"$id"}""",
        ))!!
        assertEquals("INVALID_COORDINATE", JSONObject(result.content).getString("code"))
        assertFalse(backend.calls.any { it.name == "screen_tap" })
    }

    @Test
    fun explicitForegroundCannotSilentlyReplaceALiveBackgroundTask() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val result = tools.execute(call(
            "launch_app",
            """{"package_name":"app.two","execution_location":"foreground"}""",
        ))!!
        assertEquals("BACKGROUND_SESSION_ACTIVE", JSONObject(result.content).getString("code"))
        assertEquals(TaskExecutionLocation.BACKGROUND, tools.currentLocation())
        assertFalse(backend.calls.any { it.name == "screen_open" && it.arguments.optString("package") == "app.two" })
    }

    @Test
    fun backgroundLaunchFailureNeverFallsBackToMainScreen() {
        val backend = FakeBackend().apply { openResult = """{"ok":false,"code":"ENGINE_DOWN","error":"engine"}""" }
        val tools = session(backend)
        val result = tools.execute(call(
            "launch_app",
            """{"package_name":"app.one","execution_location":"background"}""",
        ))!!
        assertEquals("ENGINE_DOWN", JSONObject(result.content).getString("code"))
        assertEquals(TaskExecutionLocation.BACKGROUND, tools.currentLocation())
        assertEquals(listOf("screen_open"), backend.names())
        val followUp = tools.execute(call("tap", """{"x":1,"y":1,"coordinate_space":"display"}"""))!!
        assertEquals("BACKGROUND_OBSERVATION_REQUIRED", JSONObject(followUp.content).getString("code"))
        assertFalse(backend.calls.any { it.name == "screen_tap" })
    }

    @Test
    fun onscreenAdoptionIsRejectedAsBackgroundUnavailable() {
        val backend = FakeBackend().apply {
            openResult = """{"ok":true,"mode":"onscreen","displayId":0,"display":0}"""
        }
        val tools = session(backend)
        val result = tools.execute(call(
            "launch_app",
            """{"package_name":"app.one","execution_location":"background"}""",
        ))!!
        assertEquals("BACKGROUND_UNAVAILABLE", JSONObject(result.content).getString("code"))
        assertEquals(TaskExecutionLocation.BACKGROUND, tools.currentLocation())
    }

    @Test
    fun backgroundBackKeyUsesTheExistingEngineAndPasteStaysExplicitlyUnsupported() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = tools.execute(call("observe_screen", "{}"))!!
        val id = JSONObject(observed.content).getString("observation_id")

        val back = tools.execute(call(
            "press_key",
            """{"button":"BACK","observation_id":"$id"}""",
        ))!!
        assertTrue(JSONObject(back.content).getBoolean("ok"))
        val keyCall = backend.calls.last { it.name == "screen_key" }
        assertEquals(4, keyCall.arguments.getInt("code"))

        val paste = tools.execute(call("press_key", """{"button":"PASTE"}"""))!!
        assertEquals("BACKGROUND_UNSUPPORTED_OPERATION", JSONObject(paste.content).getString("code"))
        assertEquals("background", JSONObject(paste.content).getString("execution_location"))
        assertFalse(backend.calls.count { it.name == "screen_key" } > 1)
    }

    @Test
    fun screenshotAndNodesFromDifferentDisplayAreRejected() {
        val backend = FakeBackend().apply {
            dumpResult = dumpJson().replace("\"displayId\": 7", "\"displayId\": 8")
        }
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = tools.execute(call("observe_screen", """{"include_screenshot":true}"""))!!
        assertEquals("BACKGROUND_OBSERVATION_CHANGED", JSONObject(observed.content).getString("code"))
        assertFalse(backend.calls.any { it.name == "screen_tap" })
    }

    @Test
    fun twoTaskSessionsKeepDifferentAppsAndObservations() {
        val firstBackend = FakeBackend().apply { dumpResult = dumpJson(text = "first") }
        val secondBackend = FakeBackend().apply { dumpResult = dumpJson(text = "second") }
        val first = session(firstBackend)
        val second = session(secondBackend)
        first.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        second.execute(call("launch_app", """{"package_name":"app.two","execution_location":"background"}"""))
        val firstId = JSONObject(first.execute(call("observe_screen", "{}"))!!.content).getString("observation_id")
        val secondId = JSONObject(second.execute(call("observe_screen", "{}"))!!.content).getString("observation_id")

        assertTrue(JSONObject(first.execute(call(
            "tap_element",
            """{"index":0,"observation_id":"$firstId"}""",
        ))!!.content).getBoolean("ok"))
        assertEquals("app.two", secondBackend.calls.first { it.name == "screen_open" }.arguments.getString("package"))
        assertTrue(JSONObject(second.execute(call(
            "tap_element",
            """{"index":0,"observation_id":"$secondId"}""",
        ))!!.content).getBoolean("ok"))
        assertEquals("app.one", firstBackend.calls.first { it.name == "screen_open" }.arguments.getString("package"))
    }

    @Test
    fun cancellationOfOneTaskDoesNotTerminateAnother() {
        val firstController = AgentRunController()
        val secondController = AgentRunController()
        val first = session(FakeBackend(), firstController)
        val second = session(FakeBackend(), secondController)
        first.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val entered = CountDownLatch(1)
        var error: Throwable? = null
        val worker = Thread {
            try {
                entered.countDown()
                first.execute(call("wait", """{"duration_ms":5000}"""))
            } catch (failure: Throwable) {
                error = failure
            }
        }
        worker.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        firstController.cancel("test")
        worker.join(2_000)
        assertFalse(worker.isAlive)
        assertTrue(error is AgentRunCancelledException)
        assertFalse(secondController.isCancelled)
        assertNotNull(second.execute(call("launch_app", """{"package_name":"app.two","execution_location":"background"}""")))
    }

    @Test
    fun realDumpFieldsSurviveTheNativeObservationConversion() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = JSONObject(
            tools.execute(call("observe_screen", """{"include_screenshot":true}"""))!!.content,
        )
        val nodes = observed.getJSONArray("ui_nodes")
        val button = nodes.getJSONObject(0)
        val edit = nodes.getJSONObject(1)
        assertEquals("android.widget.Button", button.getString("class"))
        assertEquals("app.one", button.getString("package"))
        assertTrue(button.getBoolean("clickable"))
        assertEquals("android.widget.EditText", edit.getString("class"))
        assertEquals("app.one", edit.getString("package"))
        assertEquals("app.one:id/input", edit.getString("view_id"))
        assertTrue(edit.getBoolean("editable"))
        assertTrue(edit.getBoolean("enabled"))
        assertEquals(16, edit.getJSONArray("actions").getInt(0))
    }

    @Test
    fun realEditableIndexTargetsTheInputByViewId() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = JSONObject(
            tools.execute(call("observe_screen", """{"include_screenshot":true}"""))!!.content,
        )
        val id = observed.getString("observation_id")
        val result = tools.execute(call(
            "type_text",
            """{"index":1,"text":"hello","observation_id":"$id"}""",
        ))!!
        assertTrue(JSONObject(result.content).getBoolean("ok"))
        val textCall = backend.calls.last { it.name == "screen_text" }
        assertEquals("app.one:id/input", textCall.arguments.getString("view_id"))
        assertFalse(textCall.arguments.has("index"))
    }

    @Test
    fun screenshotContractUsesScreenshotPixelsNotDisplayPixels() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = JSONObject(
            tools.execute(call("observe_screen", """{"include_screenshot":true}"""))!!.content,
        )
        val screenshot = observed.getJSONObject("coordinate_contract").getJSONObject("screenshot")
        assertEquals(540, screenshot.getInt("width"))
        assertEquals(1200, screenshot.getInt("height"))
        assertEquals(540, observed.getJSONObject("screenshot").getInt("width"))
        assertEquals(1200, observed.getJSONObject("screenshot").getInt("height"))
    }

    @Test
    fun screenshotCoordinatesAreCheckedAndScaledBeforeTheBackend() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = JSONObject(
            tools.execute(call("observe_screen", """{"include_screenshot":true}"""))!!.content,
        )
        val id = observed.getString("observation_id")
        val outside = tools.execute(call(
            "tap",
            """{"x":600,"y":10,"coordinate_space":"screenshot","frame_id":"frame-1","observation_id":"$id"}""",
        ))!!
        assertEquals("INVALID_COORDINATE", JSONObject(outside.content).getString("code"))
        assertFalse(backend.calls.any { it.name == "screen_tap" })

        val legal = tools.execute(call(
            "tap",
            """{"x":500,"y":1000,"coordinate_space":"screenshot","frame_id":"frame-1","observation_id":"$id"}""",
        ))!!
        assertTrue(JSONObject(legal.content).getBoolean("ok"))
        val tapCall = backend.calls.last { it.name == "screen_tap" }
        assertEquals(1000, tapCall.arguments.getInt("x"))
        assertEquals(2000, tapCall.arguments.getInt("y"))
    }

    @Test
    fun screenshotOnlyObservationRemainsUsableOnAnUnchangedScreen() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = JSONObject(
            tools.execute(call(
                "observe_screen",
                """{"include_ui_tree":false,"include_screenshot":true}""",
            ))!!.content,
        )
        assertTrue(observed.getBoolean("ok"))
        assertFalse(observed.getBoolean("ui_tree_read"))
        val id = observed.getString("observation_id")
        val tapped = tools.execute(call(
            "tap",
            """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$id"}""",
        ))!!
        assertTrue(JSONObject(tapped.content).getBoolean("ok"))
        assertTrue(backend.calls.any { it.name == "screen_tap" })
    }

    @Test
    fun nodeAndScreenshotGeometryMismatchIsRejected() {
        val backend = FakeBackend().apply {
            dumpResult = dumpJson(rotation = 2)
            shotResult = shotJson(rotation = 0)
        }
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = tools.execute(call("observe_screen", """{"include_screenshot":true}"""))!!
        assertEquals("BACKGROUND_OBSERVATION_CHANGED", JSONObject(observed.content).getString("code"))
        assertFalse(backend.calls.any { it.name == "screen_tap" })
    }

    @Test
    fun rotationChangeInvalidatesTheOldObservationEvenWhenWidthAndHeightMatch() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = JSONObject(
            tools.execute(call("observe_screen", """{"include_screenshot":true}"""))!!.content,
        )
        val id = observed.getString("observation_id")
        backend.dumpResult = dumpJson(rotation = 2)
        val result = tools.execute(call(
            "tap",
            """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$id"}""",
        ))!!
        assertEquals("OBSERVATION_STALE", JSONObject(result.content).getString("code"))
        assertFalse(backend.calls.any { it.name == "screen_tap" })
    }

    @Test
    fun displayIdChangeInvalidatesTheOldObservation() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = JSONObject(
            tools.execute(call("observe_screen", """{"include_screenshot":true}"""))!!.content,
        )
        val id = observed.getString("observation_id")
        backend.dumpResult = dumpJson(displayId = 8)
        val result = tools.execute(call(
            "tap",
            """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$id"}""",
        ))!!
        assertEquals("OBSERVATION_STALE", JSONObject(result.content).getString("code"))
        assertFalse(backend.calls.any { it.name == "screen_tap" })
    }

    @Test
    fun uncertainOperationMayHaveRunAlwaysInvalidatesTheObservation() {
        val backend = FakeBackend()
        val tools = session(backend)
        tools.execute(call("launch_app", """{"package_name":"app.one","execution_location":"background"}"""))
        val observed = JSONObject(
            tools.execute(call("observe_screen", """{"include_screenshot":true}"""))!!.content,
        )
        val id = observed.getString("observation_id")
        backend.tapResult = """{"ok":false,"code":"GEOMETRY_CHANGED_DURING_OPERATION","operationMayHaveRun":true}"""
        tools.execute(call(
            "tap",
            """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$id"}""",
        ))
        val retry = tools.execute(call(
            "tap",
            """{"x":10,"y":20,"coordinate_space":"display","observation_id":"$id"}""",
        ))!!
        assertEquals("BACKGROUND_OBSERVATION_REQUIRED", JSONObject(retry.content).getString("code"))
        assertEquals(1, backend.calls.count { it.name == "screen_tap" })
    }

    private fun call(name: String, arguments: String): AgentModelClient.ToolCall =
        AgentModelClient.ToolCall(id = name, name = name, argumentsJson = arguments)

    private companion object {
        fun dumpJson(text: String = "same", rotation: Int = 0, displayId: Int = 7): String = """
            {
              "ok": true,
              "displayId": $displayId,
              "geometry": {"displayId": $displayId, "width": 1080, "height": 2400, "rotation": $rotation, "density": 420},
              "count": 2,
              "nodes": [
                {"cls": "android.widget.Button", "pkg": "app.one", "text": "$text", "desc": "", "vid": "",
                 "edit": false, "click": true, "scrollable": false, "actions": [16], "enabled": true, "bounds": [0,0,100,100]},
                {"cls": "android.widget.EditText", "pkg": "app.one", "text": "", "desc": "input",
                 "vid": "app.one:id/input", "edit": true, "click": true, "scrollable": false, "actions": [16],
                 "enabled": true, "bounds": [0,200,500,300]}
              ]
            }
        """.trimIndent()

        fun shotJson(rotation: Int = 0, displayId: Int = 7): String = """
            {"ok":true,"path":"/tmp/bg-shot.png","frameId":"frame-1","displayId":$displayId,
             "width":540,"height":1200,"dims":"540x1200",
             "geometry":{"width":1080,"height":2400,"rotation":$rotation,"displayId":$displayId,
             "coordinateSpace":"display"},"scaleToDisplayX":2.0,"scaleToDisplayY":2.0}
        """.trimIndent()
    }
}
