package io.github.mangi.eta.agent.subagent

import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.agent.skill.SkillContext
import io.github.mangi.eta.agent.memory.AgentMemoryContext
import io.github.mangi.eta.agent.tool.AgentToolCapabilities
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage9SubagentDispatchTest {
    @Test fun dispatchAndResumeResolveTheActualRoleBeforePublishingItsModel() {
        val seen = mutableListOf<String>()
        fun config(name: String) = AgentModelClient.ModelConfig(baseUrl = "invalid://fixture", apiKey = "", model = name,
            modelDisplayName = "Model-$name", systemPrompt = "", terminalTools = false, browserTools = false)
        // Both interfaces allow this identical assertion to reproduce the old no-role provider.
        val provider = object : Function0<AgentModelClient.ModelConfig>, Function1<SubagentProfile, AgentModelClient.ModelConfig> {
            override fun invoke() = config("default")
            override fun invoke(profile: SubagentProfile): AgentModelClient.ModelConfig {
                seen += profile.name
                return config(profile.name)
            }
        }
        val snapshots = mutableListOf<SubagentActivitySnapshot>()
        val executor = SubagentExecutor("stage9-dispatch", AgentRunController(), provider,
            { AgentToolCapabilities(rootAvailable = false) }, AgentModelClient.ToolExecutor { error("No tools") },
            SkillContext.EMPTY, AgentMemoryContext.DISABLED, JSONArray(), null,
            { event -> SubagentActivitySnapshot.decode(event.snapshotJson)?.let { snapshots += it } })
        for (name in listOf("explore", "coder", "plan")) {
            val args = JSONObject().put("subagent_type", name).put("prompt", "Fixture").put("description", "Fixture")
            val result = JSONObject(executor.execute(1, AgentModelClient.ToolCall("call-$name", "subagent", args.toString())).content)
            assertEquals("Model-$name", result.getString("model"))
            assertTrue(snapshots.any { it.subagentType == name && it.model == "Model-$name" })
            args.remove("subagent_type"); args.put("resume", result.getString("subagent_id"))
            val resumed = JSONObject(executor.execute(2, AgentModelClient.ToolCall("resume-$name", "subagent", args.toString())).content)
            assertEquals(name, resumed.getString("subagent_type")); assertEquals("Model-$name", resumed.getString("model"))
        }
        assertEquals(listOf("explore", "explore", "coder", "coder", "plan", "plan"), seen)
    }
}
