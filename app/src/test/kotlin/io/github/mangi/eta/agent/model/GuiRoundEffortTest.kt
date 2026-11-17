package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.data.model.ModelReasoningCapabilities
import io.github.mangi.eta.data.model.ReasoningEffort
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 11 号方案实验：紧跟纯界面操作的那一轮降低思考强度，只降档、不关思考，默认关。 */
class GuiRoundEffortTest {
    private val tiered = ModelReasoningCapabilities(
        supportedEfforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
    )

    private fun config(effort: ReasoningEffort, caps: ModelReasoningCapabilities? = tiered) = AgentModelClient.ModelConfig(
        baseUrl = "https://example.invalid/v1", apiKey = "k", model = "m", contextWindow = 128_000, systemPrompt = "",
        browserTools = false, thinkingEnabled = effort.enablesReasoning, reasoningEffort = effort, reasoningCapabilities = caps,
    )

    @Test fun lowersOnlyWithinRealTiersAndNeverTurnsThinkingOff() {
        assertEquals(ReasoningEffort.LOW, GuiRoundEffort.lower(config(ReasoningEffort.HIGH)).reasoningEffort)
        assertEquals(ReasoningEffort.LOW, GuiRoundEffort.lower(config(ReasoningEffort.DEFAULT)).reasoningEffort)
        // 已是低档、没开思考、模型只有开/关两档、不知道档位时都原样返回。
        val low = config(ReasoningEffort.LOW)
        assertSame(low, GuiRoundEffort.lower(low))
        val off = config(ReasoningEffort.OFF)
        assertSame(off, GuiRoundEffort.lower(off))
        val toggle = config(ReasoningEffort.DEFAULT, ModelReasoningCapabilities(canDisable = true))
        assertSame(toggle, GuiRoundEffort.lower(toggle))
        val unknown = config(ReasoningEffort.HIGH, caps = null)
        assertSame(unknown, GuiRoundEffort.lower(unknown))
        // 只有 minimal 时退到 minimal。
        val minimalOnly = config(ReasoningEffort.HIGH, ModelReasoningCapabilities(
            supportedEfforts = listOf(ReasoningEffort.MINIMAL, ReasoningEffort.HIGH)))
        assertEquals(ReasoningEffort.MINIMAL, GuiRoundEffort.lower(minimalOnly).reasoningEffort)
    }

    @Test fun onlyTheRoundRightAfterScreenOnlyToolsIsLowered() {
        assertEquals(
            listOf(ReasoningEffort.HIGH, ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.HIGH),
            run(lightGuiRounds = true),
        )
        // 开关关着时每轮都按原强度。
        assertEquals(List(4) { ReasoningEffort.HIGH }, run(lightGuiRounds = false))
    }

    /** 第 1 轮点屏幕 → 第 2 轮（降档）读文件 → 第 3 轮看屏幕加读文件 → 第 4 轮交卷。 */
    private fun run(lightGuiRounds: Boolean): List<ReasoningEffort?> {
        val replies = listOf(
            listOf("tap_element"), listOf("read_file"), listOf("observe_screen", "read_file"), emptyList(),
        )
        val seen = mutableListOf<ReasoningEffort?>()
        val provider = object : AgentProviderClient {
            override val id = "scripted"
            override val capabilities = ProviderCapabilities(
                endpoint = EndpointKind.CHAT_COMPLETIONS, streamingText = true, streamingToolCalls = true,
                imageInput = true, toolResultImages = false, strictTools = false, parallelToolCalls = false,
            )
            override fun complete(request: ProviderRequest, runController: AgentRunController, onEvent: (ProviderEvent) -> Unit): ProviderResponse {
                val names = replies[seen.size]
                seen += request.config.reasoningEffort
                val message = JSONObject().put("role", "assistant").put("content", if (names.isEmpty()) "完成" else "")
                    .put("finish_reason", if (names.isEmpty()) "stop" else "tool_calls")
                if (names.isNotEmpty()) message.put("tool_calls", JSONArray(names.mapIndexed { i, name ->
                    JSONObject().put("id", "c${seen.size}-$i").put("type", "function")
                        .put("function", JSONObject().put("name", name).put("arguments", "{}"))
                }))
                return ProviderResponse(message)
            }
        }
        AgentLoop(
            config = config(ReasoningEffort.HIGH), messages = JSONArray().put(AgentConversationCodec.userTextMessage("开始")),
            tools = AgentToolCatalog.build(terminalTools = false, browserTools = false), provider = provider,
            toolExecutor = AgentModelClient.ToolExecutor { AgentModelClient.ToolResult(JSONObject().put("ok", true).toString()) },
            runController = AgentRunController(), traceFormatter = AgentTraceFormatter(), onEvent = {},
            completionReview = false, lightGuiRounds = lightGuiRounds,
        ).run()
        return seen
    }
}
