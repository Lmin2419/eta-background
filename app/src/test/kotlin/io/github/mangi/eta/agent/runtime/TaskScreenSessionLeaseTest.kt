package io.github.mangi.eta.agent.runtime

import android.app.Application
import io.github.mangi.eta.agent.mcp.McpRunSnapshot
import io.github.mangi.eta.agent.mcp.McpToolExecutor
import io.github.mangi.eta.agent.mcp.RoutingToolExecutor
import io.github.mangi.eta.agent.model.AgentModelClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class TaskScreenSessionLeaseTest {
    private class Connection(
        private val app: String,
        private val tapEntered: CountDownLatch? = null,
        private val tapRelease: CountDownLatch? = null,
    ) : ScreenToolConnection {
        val calls = CopyOnWriteArrayList<String>()
        @Volatile var releaseAllowed = true

        override fun execute(name: String, arguments: String): String {
            calls += name
            return when (name) {
                "screen_open" -> """{"ok":true,"mode":"hidden","displayId":1,"display":1,"package":"$app"}"""
                "screen_dump" -> """{"ok":true,"displayId":1,"geometry":{"displayId":1,"width":1080,"height":2400,"rotation":0,"density":420},"nodes":[{"cls":"android.widget.Button","pkg":"$app","text":"ok","vid":"","edit":false,"click":true,"scrollable":false,"actions":[16],"enabled":true,"bounds":[0,0,100,100]}]}"""
                "screen_shot" -> """{"ok":true,"path":"/tmp/lease-shot.png","frameId":"lease-frame","displayId":1,"width":540,"height":1200,"dims":"540x1200","geometry":{"width":1080,"height":2400,"rotation":0,"displayId":1,"coordinateSpace":"display"},"scaleToDisplayX":2.0,"scaleToDisplayY":2.0}"""
                "screen_tap" -> {
                    tapEntered?.countDown()
                    tapRelease?.await(2, TimeUnit.SECONDS)
                    """{"ok":true,"accepted":true}"""
                }
                "screen_finish" -> """{"ok":true}"""
                else -> """{"ok":true}"""
            }
        }

        override fun release(keepFinished: Boolean): Boolean = releaseAllowed
        override fun keepAlive() = Unit
    }

    @Test
    fun twoDifferentBackgroundTasksDoNotShareTheMainScreenGuiLock() {
        val firstEntered = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val releaseTaps = CountDownLatch(1)
        val firstConnection = Connection("app.one", firstEntered, releaseTaps)
        val secondConnection = Connection("app.two", secondEntered, releaseTaps)
        val firstController = AgentRunController()
        val secondController = AgentRunController()
        val registry = ScreenLeaseRegistry(2)
        val firstShadow = BackgroundScreenTools(
            "first", firstController, registry = registry, connectionFactory = { firstConnection },
        )
        val secondShadow = BackgroundScreenTools(
            "second", secondController, registry = registry, connectionFactory = { secondConnection },
        )
        val first = TaskScreenSession(
            runId = "first", controller = firstController, backend = firstShadow,
            readScreenshot = { AgentModelClient.ToolResult("{}") },
        )
        val second = TaskScreenSession(
            runId = "second", controller = secondController, backend = secondShadow,
            readScreenshot = { AgentModelClient.ToolResult("{}") },
        )
        try {
            assertTrue(ok(first.execute(call(
                "launch_app", """{"package_name":"app.one","execution_location":"background"}""",
            ))))
            assertTrue(ok(first.execute(call("observe_screen", "{}"))))
            assertTrue(ok(second.execute(call(
                "launch_app", """{"package_name":"app.two","execution_location":"background"}""",
            ))))
            assertTrue(ok(second.execute(call("observe_screen", "{}"))))
            val firstObservation = observationId(first.execute(call("observe_screen", "{}")))
            val secondObservation = observationId(second.execute(call("observe_screen", "{}")))
            val workerA = thread {
                first.execute(call(
                    "tap", """{"x":1,"y":1,"coordinate_space":"display","observation_id":"$firstObservation"}""",
                ))
            }
            val workerB = thread {
                second.execute(call(
                    "tap", """{"x":2,"y":2,"coordinate_space":"display","observation_id":"$secondObservation"}""",
                ))
            }
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS))
            releaseTaps.countDown()
            workerA.join(2_000)
            workerB.join(2_000)
            assertFalse(workerA.isAlive)
            assertFalse(workerB.isAlive)
            assertTrue(firstConnection.calls.contains("screen_tap"))
            assertTrue(secondConnection.calls.contains("screen_tap"))
        } finally {
            releaseTaps.countDown()
            firstShadow.close()
            secondShadow.close()
        }
    }

    @Test
    fun legacyScreenToolsStillUseTheOldExecutionPath() {
        val connection = Connection("app.one")
        val controller = AgentRunController()
        val shadow = BackgroundScreenTools(
            "legacy", controller, registry = ScreenLeaseRegistry(1), connectionFactory = { connection },
        )
        val task = TaskScreenSession(
            runId = "legacy", controller = controller, backend = shadow,
            readScreenshot = { AgentModelClient.ToolResult("{}") },
        )
        var localCalls = 0
        val local = AgentModelClient.ToolExecutor {
            localCalls++
            AgentModelClient.ToolResult("""{"ok":true,"path":"foreground"}""")
        }
        val routing = RoutingToolExecutor(
            local = local,
            mcp = McpToolExecutor(McpRunSnapshot.EMPTY),
            shadow = shadow,
            taskScreen = task,
        )
        try {
            assertTrue(ok(routing.execute(call(
                "screen_open", """{"package":"app.one"}""",
            ))))
            assertEquals(TaskExecutionLocation.BACKGROUND, task.currentLocation())
            assertTrue(ok(routing.execute(call("screen_dump", "{}"))))
            assertTrue(ok(routing.execute(call(
                "screen_tap", """{"x":1,"y":1,"coordinate_space":"display"}""",
            ))))
            assertEquals(0, localCalls)
            assertTrue(connection.calls.contains("screen_open"))
            assertTrue(connection.calls.contains("screen_dump"))
            assertTrue(connection.calls.contains("screen_tap"))
        } finally {
            routing.close()
        }
    }

    private fun ok(result: AgentModelClient.ToolResult?): Boolean =
        result != null && JSONObject(result.content).optBoolean("ok", false)

    private fun observationId(result: AgentModelClient.ToolResult?): String =
        JSONObject(result!!.content).getString("observation_id")

    private fun call(name: String, arguments: String): AgentModelClient.ToolCall =
        AgentModelClient.ToolCall(id = name, name = name, argumentsJson = arguments)
}
