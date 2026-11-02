package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.memory.AgentMemoryContext
import io.github.mangi.eta.agent.skill.SkillContext
import io.github.mangi.eta.agent.subagent.SubagentProfile
import io.github.mangi.eta.agent.tool.AgentToolCapabilities
import io.github.mangi.eta.data.repository.CommonTaskRepository
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AgentCommonTaskPromptTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun taskWritesDoNotChangeModelPrefixOrPublishLibraryBodies() {
        val config = AgentModelClient.ModelConfig(baseUrl = "https://example.invalid", apiKey = "",
            model = "test", systemPrompt = "Custom provider instructions", terminalTools = false, browserTools = false)
        fun prompt() = AgentPromptBuilder.buildSystemMessages(config, SkillContext.EMPTY, AgentMemoryContext.DISABLED, false)
        fun catalog() = AgentToolCatalog.build(false, false, capabilities = AgentToolCapabilities(rootAvailable = false))
        val before = prompt().toString()
        val toolsBefore = catalog().toString()
        val repository = CommonTaskRepository(File(folder.root, "tasks.json"))
        repeat(64) { repository.save(null, "Private title $it", "Private workflow $it " + "x".repeat(7800)) }
        assertEquals(before, prompt().toString())
        assertEquals(toolsBefore, catalog().toString())
        assertFalse(before.contains("Private title"))
        assertFalse(before.contains("Private workflow"))
        assertEquals("Custom provider instructions", prompt().getJSONObject(0).getString("content"))
        assertTrue(before.contains("内置常用任务"))
        assertTrue(before.contains("读取或保存不等于执行或定时"))
        assertTrue(before.contains("当前用户要求和权限优先"))
        assertTrue(before.contains("不逐个加载无关任务"))
    }

    @Test fun roleplaySchemaIsReadOnlyAndSubagentsCannotMutateTheLibrary() {
        val tools = JSONArray().also { AgentCommonTaskToolCatalog.appendTo(it, writable = false) }
        val parameters = tools.getJSONObject(0).getJSONObject("function").getJSONObject("parameters")
        val actions = parameters.getJSONObject("properties").getJSONObject("action").getJSONArray("enum")
        assertEquals(listOf("list", "get"), (0 until actions.length()).map(actions::getString))
        assertFalse(parameters.getJSONObject("properties").has("instruction"))
        assertTrue("common_task" in SubagentProfile.FORBIDDEN_TOOLS)
    }

    @Test fun fullBuiltInPrefixStaysWithinTheReviewedInputBudget() {
        // No user prompt/history/memory/images: those are not eligible for silent truncation.
        val config = AgentModelClient.ModelConfig(baseUrl = "https://example.invalid", apiKey = "",
            model = "test-model", systemPrompt = "", terminalTools = true, browserTools = true, subagentTools = true)
        val messages = AgentPromptBuilder.buildSystemMessages(config, SkillContext.EMPTY,
            AgentMemoryContext.DISABLED, rootAvailable = true)
        val tools = AgentToolCatalog.build(true, true, capabilities = AgentToolCapabilities(rootAvailable = true))
        val chars = (0 until messages.length()).sumOf { messages.getJSONObject(it).getString("content").length }
        val bytes = JSONObject().put("messages", messages).put("tools", tools).toString().toByteArray().size
        assertTrue("Built-in system prompt grew to $chars characters", chars <= 6800)
        assertTrue("Built-in prefix and base schemas grew to $bytes bytes", bytes <= 78000)
    }
}
