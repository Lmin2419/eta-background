package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.memory.AgentMemoryContext
import io.github.mangi.eta.agent.skill.SkillContext
import java.io.File
import org.junit.Test

/** 11 号方案第四期：系统提示的篇幅（每段字数写进 build/reports/prompt-budget.txt，供前后对比）。 */
class PromptBudgetTest {
    private fun config(terminal: Boolean, browser: Boolean) = AgentModelClient.ModelConfig(
        baseUrl = "https://example.invalid/v1", apiKey = "test-key", model = "test-model",
        systemPrompt = "", terminalTools = terminal, browserTools = browser,
    )

    @Test fun reportSystemPromptSize() {
        val report = StringBuilder()
        for ((label, terminal, browser) in listOf(Triple("chat-only", false, false), Triple("full", true, true))) {
            val messages = AgentPromptBuilder.buildSystemMessages(config(terminal, browser), SkillContext.EMPTY,
                AgentMemoryContext.DISABLED, rootAvailable = true)
            val contents = (0 until messages.length()).map(messages::getJSONObject)
                .filter { it.getString("role") == "system" }.map { it.getString("content") }
            val cjk = contents.sumOf { part -> part.count { it.code >= 0x2E80 } }
            report.appendLine("$label total=${contents.sumOf { it.length }} cjk=$cjk parts=${contents.map { it.length }}")
            File("build/reports").mkdirs()
            File("build/reports/prompt-$label.txt").writeText(contents.joinToString("\n"))
        }
        for (root in listOf(true, false)) {
            val messages = AgentPromptBuilder.buildSystemMessages(config(true, true), SkillContext.EMPTY,
                AgentMemoryContext.DISABLED, rootAvailable = root)
            File("build/reports/prompt-root-$root.txt").writeText((0 until messages.length()).map(messages::getJSONObject)
                .filter { it.getString("role") == "system" }.joinToString("\n") { it.getString("content") })
        }
        File("build/reports").mkdirs()
        File("build/reports/prompt-budget.txt").writeText(report.toString())
        println(report)
    }
}
