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
        ),
        workspaces = listOf(DesktopWorkspace("w1", "web", "/repo/web")),
        loaded = true,
    )

    private fun chatState(): DesktopSessionState {
        val snapshot = Json.parseToJsonElement(
            """
            {"t":"snapshot","sessionId":"s1","cursor":5,"hasMore":false,"title":"修复登录页的空指针","queue":[],"items":[
              {"k":"user","seq":1,"time":1,"id":"u","text":"登录页点提交会崩，帮我查一下","source":"user"},
              {"k":"assistant","seq":2,"time":2,"id":"a","turn":1,"step":1,"blocks":[
                {"type":"reasoning","text":"先看看提交按钮的处理函数和最近的修改。"},
                {"type":"tool","id":"c1","name":"bash","args":"{\"command\":\"git log -3 --oneline -- src/login\",\"description\":\"查看登录模块最近的提交\"}"}]},
              {"k":"tool_result","seq":3,"time":3,"callId":"c1","isError":false,"text":"a1b2c3 refactor(login): 提取表单校验\n9f8e7d fix: 记住账号"},
              {"k":"assistant","seq":4,"time":4,"id":"b","turn":1,"step":2,"blocks":[{"type":"text","text":"问题出在 `validate()` 返回 `null` 时没有判空，最近一次重构引入的。"}],"usage":{"input":12000,"output":180,"cacheRead":9000}}
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
            .addEcho(DesktopEcho("r9", "顺便看看注册页有没有同样的问题", queued = true))
    }

    private fun renderSessions(name: String, dark: Boolean) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get(), dark = dark) {
            DesktopSessionsContent(
                configured = true,
                connection = DesktopBridgeClient.State.Connected("my-mac", "0.1.0", 38),
                sessions = sessions,
                approvals = listOf(DesktopApproval("p1", "s1", "bash", "c1", "允许本次操作使用 danger-full-access 权限", 0)),
                creating = false,
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
            assertTrue(labels.toString(), labels.any { "修复登录页的空指针" in it })
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
        assertTrue((messages.last() as AgentMessageUi).isStreaming)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get(), dark = dark) {
            AgentChatBody(
                messages = messages,
                modelPickerState = DesktopChatProjector.modelPicker(null, state),
                isCompacting = false,
                input = "",
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
        try {
            panel.advance(40)
            panel.capture(name)
        } finally {
            panel.close()
            controller.pause().stop().destroy()
        }
    }

    @Test fun sessionsLight() = renderSessions("desktop-sessions-light", dark = false)

    @Test fun sessionsDark() = renderSessions("desktop-sessions-dark", dark = true)

    @Test fun chatLight() = renderChat("desktop-chat-light", dark = false)

    @Test fun chatDark() = renderChat("desktop-chat-dark", dark = true)
}
