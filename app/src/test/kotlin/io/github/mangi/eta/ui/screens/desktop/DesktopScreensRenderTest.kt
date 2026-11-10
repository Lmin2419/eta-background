package io.github.mangi.eta.ui.screens.desktop

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.agent.remote.DesktopApproval
import io.github.mangi.eta.agent.remote.DesktopBridgeClient
import io.github.mangi.eta.agent.remote.DesktopEcho
import io.github.mangi.eta.agent.remote.DesktopSessionRow
import io.github.mangi.eta.agent.remote.DesktopSessionState
import io.github.mangi.eta.agent.remote.DesktopSessionsState
import io.github.mangi.eta.agent.remote.DesktopWorkspace
import io.github.mangi.eta.ui.components.AgentChatBody
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import io.github.mangi.eta.data.datastore.SettingsDataStore
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesktopScreensRenderTest {
    @Before fun init() { SettingsDataStore.init(RuntimeEnvironment.getApplication()) }

    private val now = System.currentTimeMillis()

    private val sessions = DesktopSessionsState(
        rows = listOf(
            DesktopSessionRow("s1", "修复登录页的空指针", now - 60_000, running = true, blank = false, live = true, cwd = "/repo/web", parentId = null, subagent = false, workspaceId = "w1", pinned = false),
            DesktopSessionRow("s2", "整理 CI 缓存", now - 3_600_000, running = false, blank = false, live = false, cwd = "/repo/web", parentId = null, subagent = false, workspaceId = "w1", pinned = true),
            DesktopSessionRow("s3", null, now - 86_400_000, running = false, blank = true, live = false, cwd = "/tmp", parentId = null, subagent = false, workspaceId = null, pinned = false),
            DesktopSessionRow("s4", "生成周报", now - 7_200_000, running = false, blank = false, live = false, cwd = "/repo/docs", parentId = null, subagent = false, workspaceId = null, pinned = false),
            DesktopSessionRow("s5", "迁移旧数据库", now - 172_800_000, running = false, blank = false, live = false, cwd = "/repo/web", parentId = null, subagent = false, workspaceId = "w1", pinned = false, archived = true),
            DesktopSessionRow("s6", "设计新首页", now - 1_800_000, running = false, blank = false, live = false, cwd = "/repo/web", parentId = null, subagent = false, workspaceId = "w1", pinned = false),
        ),
        workspaces = listOf(DesktopWorkspace("w1", "web", "/repo/web")),
        loaded = true,
    )

    private fun chatState(): DesktopSessionState {
        val snapshot = Json.parseToJsonElement(
            """
            {"t":"snapshot","sessionId":"s1","cursor":9,"hasMore":false,"title":"修复登录页的空指针",
             "queue":[{"id":"q1","text":"顺便看看注册页有没有同样的问题","placement":"steering"}],
             "stats":{"turns":1,"steps":2,"llmMs":6200,"toolMs":800,"ttftMs":1400,"ttftSteps":2,"decodeMs":3000,"decodeTokens":180},
             "usage":{"uncachedInputTokens":3200,"outputTokens":180,"cacheReadTokens":18000,"cacheWriteTokens":0},
             "pressure":{"pressureTokens":21200,"contextWindow":1000000},
             "subagents":[{"id":"c9","mode":"continuable","label":"检查注册页","createdAt":1}],
             "items":[
              {"k":"turn_start","seq":1,"time":1794200000000,"turn":1},
              {"k":"user","seq":2,"time":1794200000000,"id":"u","text":"登录页点提交会崩，帮我查一下","source":"user"},
              {"k":"step_start","seq":3,"time":1794200000100,"turn":1,"step":1},
              {"k":"assistant","seq":4,"time":1794200002000,"id":"a","turn":1,"step":1,"firstTokenAt":1794200000800,"model":{"provider":"deepseek","model":"deepseek-v4-pro"},"blocks":[
                {"type":"reasoning","text":"先看看提交按钮的处理函数和最近的修改。"},
                {"type":"tool","id":"c1","name":"bash","args":"{\"command\":\"git log -3 --oneline -- src/login\",\"description\":\"查看登录模块最近的提交\"}"}],"usage":{"input":1200,"output":60,"cacheRead":9000}},
              {"k":"tool_call","seq":5,"time":1794200002100,"callId":"c1","name":"bash"},
              {"k":"tool_result","seq":6,"time":1794200002900,"callId":"c1","isError":false,"text":"a1b2c3 refactor(login): 提取表单校验\n9f8e7d fix: 记住账号"},
              {"k":"step_start","seq":7,"time":1794200003000,"turn":1,"step":2},
              {"k":"assistant","seq":8,"time":1794200006000,"id":"b","turn":1,"step":2,"firstTokenAt":1794200003700,"model":{"provider":"deepseek","model":"deepseek-v4-pro"},"blocks":[{"type":"text","text":"问题出在 `validate()` 返回 `null` 时没有判空，最近一次重构引入的。"}],"usage":{"input":2000,"output":120,"cacheRead":9000}},
              {"k":"turn_end","seq":9,"time":1794200006200,"turn":1,"reason":"completed"},
              {"k":"trigger","seq":10,"time":1794200007000,"id":"t1","kind":"subagent-settled","text":"Background subagent c9 finished.","summary":"检查注册页 已完成"}
            ]}
            """,
        ).jsonObject
        val live = Json.parseToJsonElement(
            """{"t":"live","op":"delta","sessionId":"s1","attemptId":"x","deltas":[{"index":0,"kind":"text","text":"我先补上判空并加一个单测，"}]}""",
        ).jsonObject
        return DesktopSessionState("s1")
            .reduce(snapshot)
            .reduce(Json.parseToJsonElement("""{"t":"live","op":"start","sessionId":"s1","attemptId":"x","turn":2,"step":1}""").jsonObject)
            .reduce(live)
            .addEcho(DesktopEcho("r9", "再补一个单测", queued = true))
    }

    private val question = io.github.mangi.eta.agent.remote.DesktopBridgeProtocol.parseQuestion(Json.parseToJsonElement(
        """{"id":"q1","sessionId":"s4","callId":"call","timed":false,"questions":[
          {"id":"fmt","question":"周报用什么格式？","options":[{"label":"Markdown","description":"直接贴进飞书"},{"label":"PDF"}],"multiSelect":false},
          {"id":"parts","question":"要包含哪些部分？","options":[{"label":"进度"},{"label":"风险"},{"label":"下周计划"}],"multiSelect":true}]}""",
    ).jsonObject)!!

    private fun renderSessions(name: String, dark: Boolean, query: String = "", collapsed: Set<String> = emptySet()) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get(), dark = dark) {
            DesktopSessionsContent(
                configured = true,
                connection = DesktopBridgeClient.State.Connected("my-mac", "0.3.0", 38),
                sessions = sessions,
                approvals = listOf(DesktopApproval("p1", "s1", "bash", "c1", "允许本次操作使用 danger-full-access 权限", 0)),
                questions = listOf(question),
                unread = setOf("s6"),
                collapsed = collapsed,
                creating = false,
                query = query,
                contentHits = if (query.isEmpty()) emptyMap() else mapOf("s5" to "…把旧数据库的用户表迁移到…"),
                onBack = {},
                onOpenSession = {},
                onOpenSettings = {},
                onCreate = {},
                onRetry = {},
            )
        }
        try {
            panel.advance(30)
            val labels = panel.nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() }
            if (query.isEmpty() && collapsed.isEmpty()) {
                assertTrue(labels.toString(), labels.any { "修复登录页的空指针" in it })
                assertTrue(labels.toString(), labels.any { it == "Recent" || it == "最近任务" })
                // 桌面预建的空白「新对话」和归档会话都不在主列表里。
                assertTrue(labels.toString(), labels.none { "迁移旧数据库" in it })
            } else if (query.isEmpty()) {
                // 收起的分组只剩标题、会话数和运行状态；归档在底部。
                assertTrue(labels.toString(), labels.none { "修复登录页的空指针" in it })
                assertTrue(labels.toString(), labels.any { "1 running" in it || "1 个运行中" in it || "need you" in it || "等你处理" in it })
                assertTrue(labels.toString(), labels.any { it == "Archived" || it == "已归档" })
            } else {
                assertTrue(labels.toString(), labels.any { "迁移旧数据库" in it })
                assertTrue(labels.toString(), labels.none { "修复登录页的空指针" in it })
            }
            panel.capture(name)
        } finally {
            panel.close()
            controller.pause().stop().destroy()
        }
    }

    private fun renderChat(name: String, dark: Boolean) {
        val state = chatState()
        val messages: List<AgentChatMessageUi> = DesktopChatProjector.messages(state)
        assertTrue(messages.first() is UserMessageUi)
        assertTrue(messages.any { it is ToolActivityMessageUi })
        assertTrue(messages.filterIsInstance<AgentMessageUi>().last().isStreaming)
        assertTrue((messages.last() as UserMessageUi).id == "steer-q1")
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get(), dark = dark) {
          androidx.compose.foundation.layout.Column {
            DesktopStatsLine(state, runningForMs = 72_000) {}
            androidx.compose.runtime.CompositionLocalProvider(
                io.github.mangi.eta.ui.components.LocalRemoteComposer provides io.github.mangi.eta.ui.components.RemoteComposer("电脑正在执行…", "立即插队发送", {}),
            ) {
            AgentChatBody(
                messages = messages,
                modelPickerState = DesktopChatProjector.modelPicker(null, state),
                isCompacting = false,
                input = "也检查一下忘记密码页",
                isStreaming = state.running,
                reasoningEffort = io.github.mangi.eta.data.model.ReasoningEffort.DEFAULT,
                availableReasoningEfforts = emptyList(),
                pendingImages = emptyList(),
                pendingFileReferences = emptyList(),
                queuedMessages = DesktopChatProjector.queued(state),
                messageEdit = null,
                onReasoningEffortChange = {},
                onCompactContext = {},
                canCompactContext = false,
                onModelSelected = {},
                onSubmit = {},
                onStop = {},
                onAttachImage = {},
                onRemoveImage = {},
                onAttachFiles = {},
                onAttachFolder = {},
                onAttachFilePath = {},
                onRemoveFileReference = {},
                onEditMessage = {},
                onCancelMessageEdit = {},
                onDeleteMessage = {},
                onRegenerateMessage = {},
                onSelectReplyCandidate = { _, _ -> },
                onSuggestionClick = {},
                onRunTraceClick = {},
                onOpenBrowser = {},
            )
            }
          }
        }
        try {
            panel.advance(40)
            panel.capture(name)
        } finally {
            panel.close()
            controller.pause().stop().destroy()
        }
    }

    @Test fun settingsLight() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get()) { DesktopBridgeSettingsScreen(onBack = {}) }
        try {
            panel.advance(30)
            val labels = panel.nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() }
            assertTrue(labels.toString(), labels.any { "8.163.249.159" in it })
            panel.capture("desktop-settings-light")
        } finally {
            panel.close()
            controller.pause().stop().destroy()
        }
    }

    private fun renderSheet(name: String, content: @androidx.compose.runtime.Composable (Boolean) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val show = androidx.compose.runtime.mutableStateOf(false)
        val panel = Stage3TestPanel(controller.get()) {
            top.yukonga.miuix.kmp.basic.Scaffold { content(show.value) }
        }
        try {
            panel.advance(10)
            show.value = true
            panel.advance(60)
            panel.capture(name)
        } finally {
            panel.close()
            controller.pause().stop().destroy()
        }
    }

    @Test fun sessionSheetLight() {
        val state = chatState()
        val catalog = io.github.mangi.eta.agent.remote.DesktopBridgeProtocol.parseCatalog(Json.parseToJsonElement(
            """{"default":{"provider":"deepseek","model":"deepseek-v4-pro"},"groups":[{"id":"deepseek","name":"DeepSeek","models":[
              {"id":"deepseek-flash","name":"DeepSeek Flash","reasoning":{"efforts":[{"id":"off"}],"defaultEffort":"off"}},
              {"id":"deepseek-v4-pro","name":"DeepSeek V4 Pro","reasoning":{"efforts":[{"id":"off"},{"id":"low"},{"id":"high"},{"id":"max"}],"defaultEffort":"high"}}]}]}""",
        ).jsonObject)
        val picker = DesktopChatProjector.modelPicker(catalog, state)
        val (efforts, effort) = DesktopChatProjector.reasoningEfforts(catalog, state)
        assertTrue(efforts.size == 4)
        renderSheet("desktop-session-sheet-light") { show ->
            DesktopSessionSheet(show, state, picker, efforts, effort, allowModelChange = true, busy = false, onDismiss = {}, onModel = {}, onEffort = {})
        }
    }

    @Test fun subagentSheetLight() = renderSheet("desktop-subagent-sheet-light") { show ->
        DesktopSubagentSheet(show, listOf(
            io.github.mangi.eta.agent.remote.DesktopSubagent("c9", "continuable", "检查注册页", "checker", 1, running = true,
                usage = io.github.mangi.eta.agent.remote.DesktopTokenTotals(1200, 300, 8000, 0)),
            io.github.mangi.eta.agent.remote.DesktopSubagent("c8", "one-shot", "整理依赖列表", null, 1,
                timing = io.github.mangi.eta.agent.remote.DesktopSubagentTiming(42_000, null, null, true)),
            io.github.mangi.eta.agent.remote.DesktopSubagent("x1", "external", "Codex 任务", null, 1),
        ), onDismiss = {}, onOpen = {})
    }

    @Test fun linkSettingsLight() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get()) {
            io.github.mangi.eta.ui.components.MiuixScaffoldPage(title = "电脑", onBack = {}) {
                desktopLinkSections(io.github.mangi.eta.agent.remote.DesktopLinkPrefs())
            }
        }
        try {
            panel.advance(30)
            val labels = panel.nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() }
            assertTrue(labels.toString(), labels.any { "流体云" in it || "fluid" in it })
            panel.capture("desktop-link-settings-light")
        } finally {
            panel.close()
            controller.pause().stop().destroy()
        }
    }

    @Test fun sessionsLight() = renderSessions("desktop-sessions-light", dark = false)

    @Test fun sessionsCollapsedLight() = renderSessions("desktop-sessions-collapsed-light", dark = false, collapsed = setOf(RECENT_GROUP, "w1", OTHER_GROUP))

    @Test fun sessionsSearchLight() = renderSessions("desktop-sessions-search-light", dark = false, query = "数据库")

    @Test fun questionSheetLight() = renderSheet("desktop-question-sheet-light") { show ->
        DesktopQuestionSheet(if (show) DesktopAsk("q:q1", "s4", question.items, questionId = "q1") else null, onDismiss = {})
    }

    @Test fun planReviewSheetLight() {
        val plan = io.github.mangi.eta.agent.remote.DesktopBridgeProtocol.parseQuestionItems(Json.parseToJsonElement(
            """[{"id":"plan","question":"批准这个计划吗？","detail":"## 计划\n1. 给 `validate()` 加判空\n2. 补充登录表单单测\n3. 跑一遍回归","options":[{"label":"批准"},{"label":"修改"}],"intent":{"kind":"plan-review","approve":"批准"}}]""",
        ))
        renderSheet("desktop-plan-sheet-light") { show ->
            DesktopQuestionSheet(if (show) DesktopAsk("q:p", "s1", plan, questionId = "p") else null, onDismiss = {})
        }
    }

    @Test fun sessionsDark() = renderSessions("desktop-sessions-dark", dark = true)

    @Test fun chatLight() = renderChat("desktop-chat-light", dark = false)

    @Test fun chatDark() = renderChat("desktop-chat-dark", dark = true)
}
