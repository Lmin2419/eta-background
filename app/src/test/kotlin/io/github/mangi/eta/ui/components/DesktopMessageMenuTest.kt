package io.github.mangi.eta.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.screens.desktop.DesktopChatProjector
import io.github.mangi.eta.agent.remote.DesktopSessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** 电脑会话的回复菜单：只在桌面能分支的回复上有「分支」，没有「删除」。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h896dp-xhdpi")
class DesktopMessageMenuTest {
    private lateinit var activity: ComponentActivity
    private lateinit var controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
    private var panel: Stage3TestPanel? = null

    @Before
    fun setUp() {
        io.github.mangi.eta.data.datastore.SettingsDataStore.init(org.robolectric.RuntimeEnvironment.getApplication())
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity = controller.get()
    }

    @After
    fun tearDown() {
        panel?.close()
        panel = null
        controller.pause().stop().destroy()
    }

    private fun Stage3TestPanel.find(text: String): SemanticsNode? = nodes().firstOrNull { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }

    @Test
    fun desktopReplyMenuOffersBranchButNotDelete() {
        val branched = mutableListOf<String>()
        val messages = listOf(UserMessageUi(id = "u-9", content = "第一问"), AgentMessageUi(id = "a-16-0", content = "第一答"))
        panel = Stage3TestPanel(activity) {
            CompositionLocalProvider(
                LocalRemoteComposer provides RemoteComposer("电脑正在执行…"),
                LocalBranchableMessageIds provides setOf("a-16-0"),
                LocalMessageDeleteSupported provides false,
            ) {
                AgentChatBody(
                    messages = messages,
                    modelPickerState = DesktopChatProjector.modelPicker(null, DesktopSessionState("s")),
                    isCompacting = false, input = "", isStreaming = false,
                    reasoningEffort = ReasoningEffort.DEFAULT, availableReasoningEfforts = emptyList(),
                    pendingImages = emptyList(), pendingFileReferences = emptyList(), messageEdit = null,
                    onReasoningEffortChange = {}, onCompactContext = {}, canCompactContext = false, onModelSelected = {},
                    onSubmit = {}, onStop = {}, onAttachImage = {}, onRemoveImage = {}, onAttachFiles = {},
                    onAttachFolder = {}, onAttachFilePath = {}, onRemoveFileReference = {}, onEditMessage = {},
                    onCancelMessageEdit = {}, onDeleteMessage = {}, onRegenerateMessage = {},
                    onSelectReplyCandidate = { _, _ -> }, onSuggestionClick = {}, onRunTraceClick = {}, onOpenBrowser = {},
                    onBranchMessage = { branched += it },
                )
            }
        }
        val p = panel!!
        p.advance(60)
        var reply: SemanticsNode? = p.find("第一答") ?: error("no reply")
        while (reply?.config?.getOrNull(SemanticsActions.OnLongClick) == null) reply = reply?.parent ?: error("no long click")
        reply.config[SemanticsActions.OnLongClick].action!!.invoke()
        p.advance(40)
        assertNotNull(p.find(activity.getString(R.string.conversation_branch)))
        assertNull(p.find(activity.getString(R.string.ui_delete_3755f5)))
        p.click(p.find(activity.getString(R.string.conversation_branch))!!)
        p.advance(20)
        assertEquals(listOf("a-16-0"), branched)
    }
}
