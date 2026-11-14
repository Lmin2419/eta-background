package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.repository.CommonTask
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.theme.EtaTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Scaffold

internal val Stage3Models = AgentModelPickerUiState(selectedModel = AgentModelOptionUi("model-a", "provider", "Provider", "openai", "gpt-test", "Example Model", 128000, true, true, true),
    providerGroups = listOf(AgentModelProviderGroupUi("provider", "Provider", "openai", listOf(
        AgentModelOptionUi("model-a", "provider", "Provider", "openai", "gpt-test", "Example Model", 128000, true, true, true),
        AgentModelOptionUi("model-b", "provider", "Provider", "openai", "gpt-small", "Small Model", 32000)))))

@Composable
internal fun Stage3ChatFixture(messages: List<AgentChatMessageUi> = emptyList(), streaming: Boolean = false,
    shortcuts: ChatHomeShortcuts = ChatHomeShortcuts(), onSubmit: (String) -> Unit = {}, onToast: (String) -> Unit = {}) {
    CompositionLocalProvider(LocalChatHomeShortcuts provides shortcuts, LocalChatToast provides onToast) {
        Scaffold(contentWindowInsets = WindowInsets(0.dp)) {
            Column(Modifier.fillMaxSize().background(EtaTheme.colors.canvas)) {
                HomeConversationTopBar(Stage3Models, streaming, false, AgentContextUsageUi(12500, 128000), true, messages.isNotEmpty(), null, 0,
                    {}, {}, {}, {}, {}, {}, {}, {})
                AgentChatBody(messages, Stage3Models, false, "", streaming, ReasoningEffort.OFF, listOf(ReasoningEffort.OFF, ReasoningEffort.HIGH),
                    emptyList(), emptyList(), messageEdit = null, onReasoningEffortChange = {}, onCompactContext = {}, canCompactContext = true,
                    onModelSelected = {}, onSubmit = onSubmit, onStop = {}, onAttachImage = {}, onRemoveImage = {}, onAttachFiles = {}, onAttachFolder = {},
                    onAttachFilePath = {}, onRemoveFileReference = {}, onEditMessage = {}, onCancelMessageEdit = {}, onDeleteMessage = {},
                    onRegenerateMessage = {}, onSelectReplyCandidate = { _, _ -> }, onSuggestionClick = onSubmit, onRunTraceClick = {}, onOpenBrowser = {},
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeConversationPresentationTest {
    @org.junit.Before fun initializeSettings() {
        io.github.mangi.eta.data.datastore.SettingsDataStore.init(org.robolectric.RuntimeEnvironment.getApplication())
    }
    @Test fun exampleFillsComposerWithoutSendingAndSavedTaskUsesItsExecutionCallback() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val submitted = mutableListOf<String>(); val run = mutableListOf<String>()
        val task = CommonTask("saved", "Saved task", "Read my agenda", "synthetic")
        val panel = Stage3TestPanel(controller.get()) {
            Stage3ChatFixture(shortcuts = ChatHomeShortcuts(tasks = listOf(task), onRunTask = { run += it.id }), onSubmit = { submitted += it })
        }
        try {
            panel.advance(30)
            val title = controller.get().getString(R.string.eta_example_coffee)
            panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == title } == true })
            panel.advance(10)
            val input = panel.nodes().first { it.config.getOrNull(SemanticsProperties.EditableText) != null }
            assertEquals(controller.get().getString(R.string.eta_prompt_coffee), input.config[SemanticsProperties.EditableText].text)
            assertTrue(submitted.isEmpty())
            panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == task.name } == true })
            assertEquals(listOf(task.id), run); assertTrue(submitted.isEmpty())
        } finally { panel.close(); controller.pause().stop().destroy() }
    }

    @Test fun busyModelPillReportsLockWithoutOpeningSettings() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val notices = mutableListOf<String>()
        val panel = Stage3TestPanel(controller.get()) { Stage3ChatFixture(streaming = true, onToast = { notices += it }) }
        try {
            panel.advance(20)
            panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Example Model") == true })
            assertEquals(listOf(controller.get().getString(R.string.eta_model_locked)), notices)
            assertFalse(panel.nodes().any { it.config.getOrNull(SemanticsProperties.PaneTitle) == controller.get().getString(R.string.eta_chat_settings) })
        } finally { panel.close(); controller.pause().stop().destroy() }
    }

    @Test fun settingsModelSearchSelectionAndExistingCommandsStayReachable() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val actions = mutableListOf<String>()
        var selected by mutableStateOf(Stage3Models.selectedModel!!)
        val models = (1..9).map { selected.copy(id = "choice-$it", displayName = "Choice $it") }
        val panel = Stage3TestPanel(controller.get()) {
            EtaAppOverlayHost {
                HomeConversationTopBar(Stage3Models.copy(selectedModel = selected,
                    providerGroups = listOf(Stage3Models.providerGroups.single().copy(models = models))),
                    false, false, AgentContextUsageUi(12500, 128000), true, true, null, 0,
                    {}, {}, {}, {}, { id -> actions += id; selected = models.first { it.id == id } },
                    { actions += "compact" }, { actions += "export" }, { actions += "manage" })
            }
        }
        fun open() {
            panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(selected.displayName) == true })
            panel.advance(35)
        }
        fun search(query: String) {
            panel.nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }
                .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(query))
            panel.advance(8)
        }
        fun clickText(text: String) = panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true })
        try {
            panel.advance(25); open(); search("Choice 9")
            assertFalse(panel.nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "Choice 1" } == true })
            panel.capture("model-settings-search")
            clickText("Choice 9"); panel.advance(35)
            assertEquals(listOf("choice-9"), actions)
            // 「压缩」挪到上下文圆环、「导出」挪到侧栏长按；模型小窗里保留「管理模型…」。
            for ((id, action) in listOf(R.string.eta_manage_models_ellipsis to "manage")) {
                open(); search("no matching model")
                clickText(controller.get().getString(id)); panel.advance(35)
                assertEquals(action, actions.last())
            }
        } finally { panel.close(); controller.pause().stop().destroy() }
    }

    @Test fun assistantLongPressExposesCopyRegenerateAndDelete() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get()) {
            EtaAppOverlayHost { Box(Modifier.padding(top = 64.dp)) { ChatMessageItem(AgentMessageUi("answer", "A completed answer"), {}, {}, {}, false, showMessageActions = true) } }
        }
        try {
            panel.advance(25)
            val node = panel.nodes().first { it.config.getOrNull(SemanticsActions.OnLongClick) != null }
            val point = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "A completed answer" } == true }.boundsInRoot.center
            val down = android.os.SystemClock.uptimeMillis()
            panel.touch(android.view.MotionEvent.ACTION_DOWN, point.x, point.y, down)
            panel.advance(45)
            panel.touch(android.view.MotionEvent.ACTION_UP, point.x, point.y, down)
            panel.advance(35)
            // 阶段 11：长按菜单是贴着回答弹出的气泡（独立弹窗窗口），在所有窗口里找。
            val texts = popupTexts()
            for (id in listOf(R.string.copy_answer, R.string.ui_regenerate_reply_84a7d9, R.string.ui_delete_3755f5))
                assertTrue("Missing assistant action $id in $texts", controller.get().getString(id) in texts)
        } finally { panel.close(); controller.pause().stop().destroy() }
    }

    @Test fun inlineEditorSendsOnlyTheEditedTextThroughTheExistingCallback() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val submitted = mutableListOf<String>()
        val panel = Stage3TestPanel(controller.get()) {
            CompositionLocalProvider(LocalChatInlineEditor provides ChatInlineEditor("user", "Original", false, { submitted += it }, {})) {
                ChatMessageItem(UserMessageUi("user", "Original"), {}, {}, {}, false, isEditing = true, messageActionsEnabled = false)
            }
        }
        try {
            panel.advance(20)
            panel.nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Edited"))
            panel.advance(2)
            assertTrue(submitted.isEmpty())
            panel.click(panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == controller.get().getString(R.string.eta_edit_resend) } == true })
            assertEquals(listOf("Edited"), submitted)
        } finally { panel.close(); controller.pause().stop().destroy() }
    }

    @Test fun captureHomeLightDarkAndLargeType() {
        for ((name, dark, scale) in listOf(Triple("light", false, 1f), Triple("dark", true, 1f), Triple("font-200", false, 2f))) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val panel = Stage3TestPanel(controller.get(), dark, scale) { Stage3ChatFixture() }
            try { panel.advance(40); panel.capture("home-$name") }
            finally { panel.close(); controller.pause().stop().destroy() }
        }
    }

    @Test fun captureConversationAndSidebar() {
        for (dark in listOf(false, true)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val panel = Stage3TestPanel(controller.get(), dark) {
                Stage3ChatFixture(listOf(UserMessageUi("u", "请比较这两种方案，并给出示例代码。"),
                    ThinkingMessageUi("t", "检查了两个方案的执行步骤。", false, 3),
                    AgentMessageUi("a", "### 方案比较\n\n两种方案都支持离线处理。\n\n| 方案 | 优点 |\n| --- | --- |\n| A | 简单易用 |\n| B | 灵活配置 |\n\n```kotlin\nval result = tasks.map { it.title }\n```")))
            }
            try { panel.advance(50); panel.capture("conversation-${if (dark) "dark" else "light"}") }
            finally { panel.close(); controller.pause().stop().destroy() }
        }
    }

    /** 所有窗口（含弹窗）里 Compose 语义树上的文字。 */
    private fun popupTexts(): List<String> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val roots = global.javaClass.getMethod("getWindowViews").invoke(global) as List<android.view.View>
        val out = mutableListOf<String>()
        fun walk(view: android.view.View) {
            if (view is androidx.compose.ui.platform.ViewRootForTest) {
                view.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).forEach { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
                }
            }
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        roots.forEach(::walk)
        return out
    }
}
