package io.github.mangi.eta.ui.app

import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.ui.AppearanceSettingsScreen
import io.github.mangi.eta.ui.SettingsScreen
import io.github.mangi.eta.ui.components.AgentChatBody
import io.github.mangi.eta.ui.components.EngineReadinessProbe
import io.github.mangi.eta.ui.components.EtaAppOverlayHost
import io.github.mangi.eta.ui.components.EtaCouiSupport
import io.github.mangi.eta.ui.components.HomeConversationTopBar
import io.github.mangi.eta.ui.components.LocalEngineReadinessProbe
import io.github.mangi.eta.ui.components.Stage3Models
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.navigation.AppRoute
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * 真机模式截图（etax/design/06 · 下一版第一期）：[EtaCouiSupport.shaders] = true 走真机那套布局，
 * 用硬件渲染（RenderNode + PixelCopy）截整个窗口，存到 build/reports/device-mode/，CI 上传。
 *
 * 本机渲染器编译不了 Miuix 的模糊着色器，所以截图时关掉模糊：截图看的是布局、深色、大字体、宽屏，
 * 不是玻璃效果本身。
 */
internal object DeviceModeScreens {
    class Shot(val bitmap: Bitmap, val ink: Int, val result: Int)

    /**
     * [deviceMode] 为假时走测试骨架：COUI 顶栏的着色器（`layout(color)`）本机编译不了，
     * 设置类页面只能这样截；页面内容（行、开关、卡片）仍是真机那套。
     */
    fun capture(name: String, dark: Boolean = false, fontScale: Float = 1f, deviceMode: Boolean = true,
        content: @Composable () -> Unit): Shot {
        val previous = EtaCouiSupport.shaders
        EtaCouiSupport.shaders = deviceMode
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get(), dark = dark, fontScale = fontScale, blur = false, content = content)
        try {
            panel.advance(45)
            val window = controller.get().window
            val decor = window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            var result = -1
            PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
            shadowOf(Looper.getMainLooper()).idle()
            val out = File("build/reports/device-mode/$name.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return Shot(bitmap, contrastPixels(bitmap, dark), result)
        } finally {
            EtaCouiSupport.shaders = previous
            System.clearProperty("robolectric.pixelCopyRenderMode")
            runCatching { panel.close() }
            runCatching { controller.pause().stop().destroy() }
        }
    }

    /** 测试窗口的标题栏（约 112px）以下，和页面底色反差明显的像素数：0 表示整页空白。 */
    private fun contrastPixels(bitmap: Bitmap, dark: Boolean): Int {
        var count = 0
        for (y in 240 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
            val p = bitmap.getPixel(x, y)
            val sum = ((p shr 16) and 0xff) + ((p shr 8) and 0xff) + (p and 0xff)
            if (if (dark) sum > 450 else sum < 300) count++
        }
        return count
    }

    fun initEnvironment() {
        val context = RuntimeEnvironment.getApplication() as Application
        SettingsDataStore.init(context)
        Prefs.initLocal(context)
        ProviderRepository.init(context)
    }

    @Composable
    fun SettingsProviders(content: @Composable () -> Unit) {
        val appearance by AppearanceSettingsRepository.settingsFlow().collectAsState(initial = AppearanceSettings())
        val readiness = remember { EngineReadinessProbe { true } }
        CompositionLocalProvider(
            LocalAppearanceSettings provides appearance,
            LocalEngineReadinessProbe provides readiness,
        ) { EtaAppOverlayHost { content() } }
    }

    val sampleMessages: List<AgentChatMessageUi> = listOf(
        UserMessageUi("u1", "帮我整理本周的工作进展，按项目列出来。"),
        AgentMessageUi("a1", "好的，本周三个项目的进展如下：\n\n1. **手机端改版**：设置页换上新的页面骨架，侧栏改成推开式。\n2. **电脑同步**：会话列表自动刷新，审批可以在手机上处理。\n3. **排错改进**：命令输出保留结尾，改文件找不到原文时给出最相近的一段。"),
        UserMessageUi("u2", "第二项再展开说说。"),
        AgentMessageUi("a2", "电脑同步这周主要做了两件事：会话列表在电脑上有变化时自动推送到手机；审批和提问可以直接在手机对话里回答。"),
    )

    /** 最后一条回答像是放弃了：消息下方应出现「复核报错再试」（第三期）。 */
    val gaveUpMessages: List<AgentChatMessageUi> = listOf(
        UserMessageUi("u1", "帮我在终端里装一下 requests。"),
        AgentMessageUi("a1", "试了两次都报错：pip 提示 externally-managed-environment，这个环境里暂时无法完成安装。"),
    )

    val sampleConversations: List<ConversationSummaryUi> = listOf(
        conversation("c1", "整理本周工作进展", minutesAgo = 5),
        conversation("c2", "查一下明天的天气", minutesAgo = 90),
        conversation("c3", "把会议纪要改成待办清单", minutesAgo = 60 * 26),
        conversation("c4", "旅行行程规划", minutesAgo = 60 * 24 * 4),
    )

    private fun conversation(id: String, title: String, minutesAgo: Long): ConversationSummaryUi {
        val updated = System.currentTimeMillis() - minutesAgo * 60_000L
        return ConversationSummaryUi(id = id, title = title, preview = title, timeLabel = "",
            updatedAtMillis = updated, mode = io.github.mangi.eta.ui.model.ConversationModeUi.Chat)
    }

    @Composable
    fun Home(messages: List<AgentChatMessageUi>, title: String?, paneOpen: Boolean = false,
        models: io.github.mangi.eta.ui.model.AgentModelPickerUiState = Stage3Models, streaming: Boolean = false) {
        AgentAppShell(
            currentRoute = AppRoute.Home, isCurrentRoute = true,
            conversationPaneState = ConversationPaneUiState(sampleConversations, if (title != null) "c1" else null, ""),
            isConversationPaneOpen = paneOpen, onBack = {}, onOpenConversationPane = {}, onDismissConversationPane = {},
            onSearchConversations = {}, onNewConversation = {}, onOpenCommonTasks = {}, onOpenTerminal = {},
            onLaunchKimiWeb = {}, kimiWebLabel = "", canStopKimiWeb = false, onStopKimiWeb = {}, onRefreshKimiWeb = {},
            onOpenBrowser = {}, onSelectConversation = {}, onConversationRename = {}, onConversationExport = {},
            onConversationDelete = {}, onOpenTools = {}, onOpenSkills = {}, onOpenCharacters = {},
            onOpenPermissions = {}, onOpenSettings = {}, onOpenModelProviders = {},
            homeTopBar = {
                HomeConversationTopBar(models, streaming, false, AgentContextUsageUi(12500, 128000), true, messages.isNotEmpty(), null, 0,
                    {}, {}, {}, {}, {}, {}, {}, {}, conversationTitle = title, currentConversationId = if (title != null) "c1" else null)
            },
        ) { padding ->
            AgentChatBody(messages, Stage3Models, false, "", false, ReasoningEffort.OFF, listOf(ReasoningEffort.OFF, ReasoningEffort.HIGH),
                emptyList(), emptyList(), messageEdit = null, onReasoningEffortChange = {}, onCompactContext = {}, canCompactContext = true,
                onModelSelected = {}, onSubmit = {}, onStop = {}, onAttachImage = {}, onRemoveImage = {}, onAttachFiles = {}, onAttachFolder = {},
                onAttachFilePath = {}, onRemoveFileReference = {}, onEditMessage = {}, onCancelMessageEdit = {}, onDeleteMessage = {},
                onRegenerateMessage = {}, onSelectReplyCandidate = { _, _ -> }, onSuggestionClick = {}, onRunTraceClick = {}, onOpenBrowser = {},
                modifier = Modifier.fillMaxSize().padding(padding))
        }
    }

    /** 电脑会话页：与首页同一套——顶部玻璃控件浮在消息上（2026111603）。 */
    @Composable
    fun DesktopChat() {
        io.github.mangi.eta.ui.screens.desktop.DesktopChatFrame(
            title = "整理本周工作进展", subtitle = "电脑 · eta-background", onBack = {}, onTitle = {}, underTopBar = true,
            trailing = {
                io.github.mangi.eta.ui.components.CircleIconButton(androidx.compose.material.icons.Icons.Outlined.NotificationsActive,
                    "提醒", onClick = {}, tint = io.github.mangi.eta.ui.theme.EtaTheme.colors.accent)
            },
        ) {
            AgentChatBody(sampleMessages + sampleMessages.map {
                    when (it) { is UserMessageUi -> it.copy(id = it.id + "b"); is AgentMessageUi -> it.copy(id = it.id + "b"); else -> it }
                }, Stage3Models, false, "", false, ReasoningEffort.OFF, listOf(ReasoningEffort.OFF),
                emptyList(), emptyList(), messageEdit = null, onReasoningEffortChange = {}, onCompactContext = {}, canCompactContext = false,
                onModelSelected = {}, onSubmit = {}, onStop = {}, onAttachImage = {}, onRemoveImage = {}, onAttachFiles = {}, onAttachFolder = {},
                onAttachFilePath = {}, onRemoveFileReference = {}, onEditMessage = {}, onCancelMessageEdit = {}, onDeleteMessage = {},
                onRegenerateMessage = {}, onSelectReplyCandidate = { _, _ -> }, onSuggestionClick = {}, onRunTraceClick = {}, onOpenBrowser = {},
                modifier = Modifier.weight(1f))
        }
    }

    /** 终端页外壳：顶部是与对话页同一套玻璃控件（2026111602）；终端本体用一块深色占位。 */
    @Composable
    fun TerminalShell() {
        AgentAppShell(
            currentRoute = AppRoute.Terminal, isCurrentRoute = true,
            conversationPaneState = ConversationPaneUiState(emptyList(), null, ""),
            isConversationPaneOpen = false, onBack = {}, onOpenConversationPane = {}, onDismissConversationPane = {},
            onSearchConversations = {}, onNewConversation = {}, onOpenCommonTasks = {}, onOpenTerminal = {},
            onLaunchKimiWeb = {}, kimiWebLabel = "", canStopKimiWeb = false, onStopKimiWeb = {}, onRefreshKimiWeb = {},
            onOpenBrowser = {}, onSelectConversation = {}, onConversationRename = {}, onConversationExport = {},
            onConversationDelete = {}, onOpenTools = {}, onOpenSkills = {}, onOpenCharacters = {},
            onOpenPermissions = {}, onOpenSettings = {}, onOpenModelProviders = {},
        ) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding)
                .background(androidx.compose.ui.graphics.Color(0xFF1E1E1E))) {
                androidx.compose.foundation.text.BasicText("~ $ ls\nREADME.md  app  etax",
                    style = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color(0xFFE0E0E0)),
                    modifier = Modifier.padding(16.dp))
            }
        }
    }

    @Composable
    fun Settings() {
        val context = RuntimeEnvironment.getApplication()
        SettingsProviders { SettingsScreen(context, onNavigate = {}, onBack = {}) }
    }

    @Composable
    fun Appearance() {
        SettingsProviders { AppearanceSettingsScreen(onBack = {}) }
    }
}
