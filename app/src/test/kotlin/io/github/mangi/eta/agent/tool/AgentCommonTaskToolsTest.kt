package io.github.mangi.eta.agent.tool

import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.AgentSensitiveToolPolicy
import io.github.mangi.eta.agent.model.AgentToolCatalog
import io.github.mangi.eta.agent.model.AgentTraceFormatter
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.agent.runtime.GuiControlCoordinator
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.repository.CommonTaskRepository
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36])
class AgentCommonTaskToolsTest {
    @get:Rule val folder = TemporaryFolder()
    private val file get() = File(folder.root, "tasks.json")
    private val repository get() = CommonTaskRepository(file)
    private fun call(args: JSONObject, tools: AgentCommonTaskTools = AgentCommonTaskTools(repository)) =
        JSONObject(tools.execute(args))
    private fun args(action: String) = JSONObject().put("action", action)

    @Test fun agentAndUiUseOneLibraryWithPrivateShortcutsAndExplicitExecution() {
        val ui = repository.save(null, "早间统计", "只计数，不提交消息")
        val listed = call(args("list"))
        assertEquals(1, listed.getInt("total"))
        assertFalse(listed.toString().contains(ui.instruction))
        assertFalse(listed.toString().contains(ui.shortcutToken))
        val read = call(args("get").put("id", ui.id))
        assertEquals(ui.instruction, read.getJSONObject("task").getString("instruction"))
        assertFalse(read.getBoolean("started"))
        assertFalse(read.toString().contains(ui.shortcutToken))
        val saved = call(args("save").put("name", "每日路线").put("instruction", "查询上班路线"))
        assertTrue(saved.getBoolean("ok"))
        assertFalse(saved.getBoolean("started"))
        assertFalse(saved.getJSONObject("task").has("instruction"))
        assertEquals(2, CommonTaskRepository(file).load().size)
    }

    @Test fun staleEditsAndDeletesCannotOverwriteNewUserContent() {
        val original = repository.save(null, "Morning", "Old")
        val edited = repository.save(original.id, "Morning", "New user instruction")
        for (action in listOf("save", "delete")) {
            val request = args(action).put("id", original.id).put("revision", original.revision)
                .put("name", "Replace").put("instruction", "Lost update")
            assertEquals("COMMON_TASK_CONFLICT", call(request).getString("code"))
        }
        val updated = call(args("save").put("id", edited.id).put("revision", edited.revision)
            .put("name", "Evening").put("instruction", "Agreed change"))
        assertTrue(updated.getBoolean("ok"))
        assertEquals(original.shortcutToken, repository.load().single().shortcutToken)
        val deleted = call(args("delete").put("id", edited.id)
            .put("revision", updated.getJSONObject("task").getString("revision")))
        assertTrue(deleted.getBoolean("deleted"))
        assertNull(repository.resolveShortcut(original.id, original.shortcutToken))
    }

    @Test fun concurrentCreatesAreDeduplicatedAndDifferentSameNameCannotOverwrite() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll(List(8) { Callable {
                call(args("save").put("name", "Same").put("instruction", "Same workflow"))
            } }).map { it.get() }
            assertTrue(results.all { it.getBoolean("ok") })
            assertEquals(1, results.map { it.getJSONObject("task").getString("id") }.distinct().size)
            assertEquals(1, repository.load().size)
            assertEquals("COMMON_TASK_NAME_EXISTS",
                call(args("save").put("name", "Same").put("instruction", "Different")).getString("code"))
            assertEquals("Same workflow", repository.load().single().instruction)
        } finally { pool.shutdownNow() }
    }

    @Test fun paginationAndNameSearchDoNotLoadUnrelatedInstructions() {
        repeat(24) { repository.save(null, "Task $it", "private-$it") }
        val first = call(args("list"))
        assertEquals(20, first.getJSONArray("tasks").length())
        assertEquals(20, first.getInt("next_offset"))
        val last = call(args("list").put("offset", 20))
        assertEquals(4, last.getJSONArray("tasks").length())
        assertFalse(last.has("next_offset"))
        val found = call(args("list").put("name", "Task 23"))
        assertEquals(1, found.getInt("total"))
        assertFalse(found.toString().contains("private-"))
    }

    @Test fun malformedArgumentsAndMissingRevisionsNeverWrite() {
        val existing = repository.save(null, "Task", "Keep")
        val before = file.readBytes()
        val requests = listOf(
            args("save").put("name", "").put("instruction", "test"),
            args("save").put("name", "x").put("instruction", "a".repeat(8001)),
            args("save").put("name", 4).put("instruction", "test"),
            args("delete").put("id", existing.id),
            args("save").put("id", existing.id).put("name", "x").put("instruction", "test"),
            args("list").put("limit", 2.5), args("list").put("offset", -1),
            args("run").put("id", existing.id),
        )
        requests.forEach { assertEquals(it.toString(), "INVALID_ARGUMENT", call(it).getString("code")) }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun corruptStorageIsNotReplacedAndShortcutFailureDoesNotUndoSave() {
        file.writeText("{broken")
        assertEquals("COMMON_TASK_STORAGE_ERROR", call(args("save").put("name", "N").put("instruction", "I")).getString("code"))
        assertEquals("{broken", file.readText())
        file.delete()
        val result = call(args("save").put("name", "N").put("instruction", "I"),
            AgentCommonTaskTools(repository, onSaved = { error("Launcher unavailable") }))
        assertTrue(result.getBoolean("ok"))
        assertFalse(result.getBoolean("shortcut_updated"))
        assertEquals(1, repository.load().size)
    }

    @Test fun roleplayReadOnlyIsEnforcedAtExecutionNotJustInSchema() {
        val existing = repository.save(null, "Task", "User workflow")
        val tools = AgentCommonTaskTools(repository, writable = false)
        assertTrue(call(args("get").put("id", existing.id), tools).getBoolean("ok"))
        for (action in listOf("save", "delete"))
            assertEquals("COMMON_TASK_READ_ONLY", call(args(action), tools).getString("code"))
        assertEquals(listOf(existing), repository.load())
    }

    @Test fun publishedWithoutRootTerminalMemoryOrAccessibilityAndRoutesWithoutTakingGui() {
        val capabilities = AgentToolCapabilities(rootAvailable = false, accessibilityAvailable = false)
        val catalog = AgentToolCatalog.build(false, false, memoryTools = false, capabilities = capabilities)
        assertTrue((0 until catalog.length()).any {
            catalog.getJSONObject(it).getJSONObject("function").getString("name") == "common_task"
        })
        assertFalse(AgentToolRequirements.requiresGuiControl("common_task"))
        assertTrue(AgentSensitiveToolPolicy.isSensitive("common_task"))
        val context = RuntimeEnvironment.getApplication()
        val coordinator = GuiControlCoordinator()
        val owner = coordinator.openRun("owner", AgentRunController())
        val requester = coordinator.openRun("library", AgentRunController())
        owner.withAction { }
        try {
            AgentLocalTools(context, AndroidAgentLogger, rootAvailable = { false }, guiControl = requester,
                memoryToolsEnabled = { false }, terminalToolsEnabled = { false }).use { tools ->
                val call = AgentModelClient.ToolCall("save", "common_task",
                    args("save").put("name", "Integrated").put("instruction", "Private body").toString())
                val result = tools.execute(call)
                assertTrue(JSONObject(result.content).getBoolean("ok"))
                assertTrue(result.sensitive)
                assertEquals("owner", coordinator.state.value.ownerRunId)
                assertFalse(AgentTraceFormatter().summarizeArguments(call).contains("Private body"))
                assertTrue(CommonTaskRepository(context).load().any { it.name == "Integrated" })
            }
        } finally { requester.close(); owner.close() }
    }
}
