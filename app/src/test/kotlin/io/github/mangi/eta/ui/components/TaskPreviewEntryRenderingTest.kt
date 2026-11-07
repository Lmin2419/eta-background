package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.*
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.*
import io.github.mangi.eta.data.datastore.SettingsDataStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskPreviewEntryRenderingTest {
    @org.junit.Before @org.junit.After fun clearRetainedCardsBetweenTests() {
        dismissRetainedTaskCardsForTest(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun lightEntryStaysTappableAfterPauseAndStopThenAnotherInstruction() = verify(false)
    @Test fun darkEntryStaysTappableAfterPauseAndStopThenAnotherInstruction() = verify(true)

    private fun verify(dark: Boolean) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val context = activity.get()
        SettingsDataStore.init(context)
        val runId = "background-$dark"
        val task = TaskNotificationState(runId, "chat", "后台页面", AgentOverlayStatus.PreviewPaused,
            paused = true, targetPackage = "fixture.background", runtimeAuthoritative = true)
        AgentExecutionService.acquire(context, "run:$runId") {}
        val service = Robolectric.buildService(AgentExecutionService::class.java).create()
        AgentExecutionService.updateTasks(context, listOf(task))
        val panel = Stage3TestPanel(context, dark) {
            top.yukonga.miuix.kmp.basic.Scaffold { Column { ConversationLiveTasks("chat", {}) } }
        }
        fun title() = panel.nodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == task.title } == true
        }
        fun openEntry() {
            val center = requireNotNull(title()).boundsInRoot.center
            panel.tapFinger(center.x, center.y)
            val intent = shadowOf(context).nextStartedActivity
            assertNotNull("Finger tap must still open the preview", intent)
            assertEquals(TaskPreviewActivity::class.java.name, intent.component?.className)
            assertEquals(task.runId, intent.getStringExtra("run_id"))
        }
        try {
            panel.advance(25)
            AgentExecutionService.updateTasks(context, listOf(task.copy(streamPreview = "新指令等待执行")))
            panel.advance(20)
            assertTrue(AgentExecutionService.task(runId)!!.paused)
            openEntry()
            panel.capture("preview-entry-paused-${if (dark) "dark" else "light"}")
            AgentExecutionService.completeTask(context, task.copy(status = AgentOverlayStatus.Stopped))
            AgentExecutionService.release("run:$runId"); panel.advance(20)
            AgentExecutionService.acquire(context, "run:next") {}
            AgentExecutionService.updateTasks(context, listOf(TaskNotificationState("next", "chat", "下一条指令", AgentOverlayStatus.Reasoning)))
            panel.advance(25)
            openEntry()
            panel.capture("preview-entry-stopped-${if (dark) "dark" else "light"}")
            val close = panel.nodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(context.getString(R.string.action_close)) == true
            }.boundsInRoot.center
            panel.tapFinger(close.x, close.y)
            assertNull("Explicitly dismissing the strip still works", title())
        } finally {
            panel.close()
            AgentExecutionService.release("run:$runId"); AgentExecutionService.release("run:next")
            service.destroy(); AgentExecutionService.updateTasks(context, emptyList())
            activity.pause().stop().destroy()
        }
    }
}
