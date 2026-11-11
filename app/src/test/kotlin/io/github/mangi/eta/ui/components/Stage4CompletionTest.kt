package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.*
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.ui.app.CommonTasksStore
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.screens.tasks.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Text

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Stage4CompletionTest {
    @Before fun init() { SettingsDataStore.init(RuntimeEnvironment.getApplication()) }

    @Test fun sidebarAndTaskCenterAgreeWhenOneOfTwoTasksNeedsAttention() {
        val a = TaskNotificationState("a", "chat", "A", AgentOverlayStatus.Reasoning)
        val b = a.copy(runId = "b", status = AgentOverlayStatus.ContinuationUnavailable)
        assertEquals(TaskVisualStatus.Attention, conversationTaskStatuses(listOf(a, b))["chat"])
        assertEquals("b", orderedActiveTasks(listOf(a, b)).first().runId)
        assertEquals(TaskVisualStatus.Running, conversationTaskStatuses(listOf(a, b.copy(completed = true)))["chat"])
        assertTrue(conversationTaskStatuses(listOf(a.copy(completed = true))).isEmpty())
    }

    @Test fun sidebarOpensScrollsAndClosesWithRealFingerDrags() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var open by mutableStateOf(false)
        val conversations = List(40) { ConversationSummaryUi("chat-$it", "Conversation $it", "", "Today", mode = ConversationModeUi.Chat) }
        val panel = Stage3TestPanel(activity.get()) {
            EtaAppOverlayHost {
                ConversationSidePaneScaffold(ConversationPaneUiState(conversations, "chat-0", ""), open, true,
                    { open = true }, { open = false }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        fun has(text: String) = panel.nodes().any { (it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true ||
            it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(text)) && it.boundsInRoot.height > 0 }
        try {
            panel.advance(20)
            panel.dragFinger(fromX = 20f, fromY = 800f, toX = 740f, toY = 800f)
            assertTrue("horizontal finger opens sidebar", open)
            assertTrue(has("Conversation 0"))
            panel.dragFinger(fromX = 300f, fromY = 1350f, toY = 500f)
            assertFalse("vertical drag moves the sidebar conversation list", has("Conversation 0"))
            assertTrue(has(activity.get().getString(R.string.route_settings)))
            panel.dragFinger(fromX = 620f, fromY = 900f, toX = 20f, toY = 900f)
            assertFalse("horizontal finger closes sidebar", open)
        } finally { panel.close(); activity.pause().stop().destroy() }
    }

    @Test fun thumbnailStopsOffscreenAndResumesAfterFingerScrollBack() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var reads = 0
        lateinit var list: LazyListState
        val source = TaskFrameSource { _, _, _, _ -> reads++; JSONObject().put("ok", false) }
        val panel = Stage3TestPanel(activity.get()) {
            list = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                item { TaskThumbnail(TaskNotificationState("gesture-preview", "chat", "Task", AgentOverlayStatus.Reasoning), true,
                    Modifier.size(72.dp, 156.dp), background = true, frameSource = source) }
                items(30) { Text("Task $it", Modifier.fillMaxWidth().height(100.dp)) }
            }
        }
        try {
            panel.advance(20); assertTrue(reads > 0)
            panel.dragFinger(); assertTrue(list.firstVisibleItemIndex > 0)
            val offscreenReads = reads
            panel.advance(350); assertEquals("offscreen item must stop reads", offscreenReads, reads)
            panel.dragFinger(fromY = 400f, toY = 1500f)
            panel.dragFinger(fromY = 400f, toY = 1500f)
            assertEquals(0, list.firstVisibleItemIndex)
            panel.advance(160); assertTrue("returning into view resumes reads", reads > offscreenReads)
        } finally { panel.close(); activity.pause().stop().destroy() }
    }

    @Test fun savedTaskCreateButtonBecomesCircleAfterFingerScrollAndStillOpensEditor() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        // Seed the existing file format once; this test exercises touch/UI, not AtomicFile replacement.
        val savedTasks = JSONArray()
        repeat(12) { savedTasks.put(JSONObject().put("id", "saved-$it").put("name", "Saved task $it")
            .put("instruction", "Read the notes for task $it").put("shortcutToken", UUID.randomUUID().toString())) }
        File(activity.get().applicationContext.filesDir, "common_tasks.json")
            .writeText(JSONObject().put("version", 1).put("tasks", savedTasks).toString())
        val store = CommonTasksStore(activity.get(), scope)
        store.refresh()
        val panel = Stage3TestPanel(activity.get()) { EtaAppOverlayHost { CommonTasksScreen(store, {}, {}, embedded = true) } }
        val label = activity.get().getString(R.string.common_tasks_create)
        fun button() = panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
        try {
            repeat(100) { if (store.busy) { Thread.sleep(5); panel.advance() } }
            panel.advance(20); assertEquals(12, store.tasks.size)
            val expanded = button().boundsInRoot
            assertTrue(expanded.width > expanded.height)
            panel.dragFinger()
            val collapsed = button().boundsInRoot
            assertEquals("collapsed create is circular", collapsed.height, collapsed.width, 1f)
            assertEquals(52f, collapsed.width / activity.get().resources.displayMetrics.density, 1f)
            panel.capture("stage4-saved-collapsed")
            panel.tapFinger(collapsed.center.x, collapsed.center.y)
            assertTrue(panel.nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == activity.get().getString(R.string.common_tasks_name) } == true })
        } finally { panel.close(); scope.cancel(); activity.pause().stop().destroy() }
    }
}
