package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.ui.app.AgentAppShell
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.navigation.AppRoute
import org.junit.Assert.*
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
class ComposerTransitionTest {
    @Test fun longMessagesPassUnderComposerWithRealFingerInThreeAppearances() {
        SettingsDataStore.init(RuntimeEnvironment.getApplication())
        val messages = List<AgentChatMessageUi>(30) { index ->
            if (index % 2 == 0) UserMessageUi("user-$index", "请整理这次讨论的重点，并说明具体的检查步骤。")
            else AgentMessageUi("answer-$index", (1..20).joinToString("\n\n") { paragraph ->
                "第 $paragraph 项：检查消息区域与输入卡片的衔接。上下滚动时文字应当逐渐淡出，背景保持一致。键盘、任务条和排队条变化后，也应保留原来的阅读位置。"
            })
        }
        for ((name, dark, scale) in listOf(Triple("light", false, 1f), Triple("dark", true, 1f), Triple("font200", false, 2f))) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val panel = Stage3TestPanel(controller.get(), dark, scale) { FullHome(messages) }
            try {
                panel.advance(60)
                fun offset(): Float = panel.nodes().mapNotNull {
                    it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                }.filter { it.maxValue() > 5f }.maxOf { it.value() }
                val before = offset()
                panel.dragFinger(fromX = 400f, fromY = 450f, toY = 1200f, settleFrames = 60)
                assertTrue("$name must scroll back through real pointer dispatch", offset() < before)
                assertTrue("composer remains visible", panel.nodes().any {
                    it.config.getOrNull(SemanticsProperties.EditableText) != null && it.boundsInRoot.height > 0
                })
                panel.capture("stage55a-$name")
                val afterBack = offset()
                panel.dragFinger(fromX = 400f, fromY = 1200f, toY = 450f, settleFrames = 60)
                assertTrue("$name must still scroll forward", offset() > afterBack)
            } finally { panel.close(); controller.pause().stop().destroy() }
        }
    }

    /** Use the real shell; a fixture-painted canvas would hide the original surface/canvas mismatch. */
    @Composable private fun FullHome(messages: List<AgentChatMessageUi>) {
        AgentAppShell(
            currentRoute = AppRoute.Home, isCurrentRoute = true, conversationPaneState = null,
            isConversationPaneOpen = false, onBack = {}, onOpenConversationPane = {}, onDismissConversationPane = {},
            onSearchConversations = {}, onNewConversation = {}, onOpenCommonTasks = {}, onOpenTerminal = {},
            onLaunchKimiWeb = {}, kimiWebLabel = "", canStopKimiWeb = false, onStopKimiWeb = {}, onRefreshKimiWeb = {},
            onOpenBrowser = {}, onSelectConversation = {}, onConversationRename = {}, onConversationExport = {},
            onConversationDelete = {}, onOpenTools = {}, onOpenSkills = {}, onOpenCharacters = {},
            onOpenPermissions = {}, onOpenSettings = {}, onOpenModelProviders = {},
            homeTopBar = {
                HomeConversationTopBar(Stage3Models, false, false, AgentContextUsageUi(12500, 128000), true, true, null, 0,
                    {}, {}, {}, {}, {}, {}, {}, {})
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
}
