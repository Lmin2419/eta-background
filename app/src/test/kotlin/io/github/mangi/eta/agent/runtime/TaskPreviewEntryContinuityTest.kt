package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.os.Looper
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36], application = Application::class)
class TaskPreviewEntryContinuityTest {
    @org.junit.Before @org.junit.After fun clearRetainedCardsBetweenTests() {
        dismissRetainedTaskCardsForTest(org.robolectric.RuntimeEnvironment.getApplication())
    }

    private val context get() = RuntimeEnvironment.getApplication()
    private fun card(id: String, status: AgentOverlayStatus = AgentOverlayStatus.Reasoning) =
        TaskNotificationState(id, "chat", "后台页面 $id", status, targetPackage = "fixture.background", runtimeAuthoritative = true)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun pausedBackgroundEntrySurvivesAnotherInstructionWithoutResumingOrLosingItsPackage() {
        AgentExecutionService.acquire(context, "run:paused") {}
        val service = Robolectric.buildService(AgentExecutionService::class.java).create()
        try {
            val paused = card("paused", AgentOverlayStatus.Paused).copy(paused = true)
            AgentExecutionService.updateTasks(context, listOf(paused)); idle()
            // A message queued for this conversation does not replace the active runtime snapshot.
            AgentExecutionService.updateTasks(context, listOf(paused.copy(streamPreview = "新指令等待执行"))); idle()
            val visible = AgentExecutionService.taskSnapshots.value.single { it.runId == "paused" }
            assertTrue(visible.paused); assertFalse(visible.completed)
            assertEquals("fixture.background", visible.targetPackage)
        } finally { AgentExecutionService.release("run:paused"); idle(); service.destroy() }
    }

    @Test fun stoppingThenStartingAnotherInstructionDoesNotDeleteThePreviousBackgroundPreviewEntry() {
        AgentExecutionService.acquire(context, "run:stopped") {}
        val service = Robolectric.buildService(AgentExecutionService::class.java).create()
        try {
            AgentExecutionService.updateTasks(context, listOf(card("stopped", AgentOverlayStatus.Paused).copy(paused = true)))
            AgentExecutionService.completeTask(context, card("stopped", AgentOverlayStatus.Stopped).copy(completed = true))
            AgentExecutionService.release("run:stopped"); idle()
            assertNotNull(AgentExecutionService.task("stopped"))
            AgentExecutionService.acquire(context, "run:next") {}
            AgentExecutionService.updateTasks(context, listOf(card("next").copy(targetPackage = null))); idle()
            assertNotNull("Starting another instruction must not erase the stopped page's only in-chat preview entry", AgentExecutionService.task("stopped"))
            assertEquals(AgentOverlayStatus.Stopped, AgentExecutionService.task("stopped")?.status)
        } finally { AgentExecutionService.release("run:stopped"); AgentExecutionService.release("run:next"); idle(); service.destroy() }
    }

    @Test fun lateNotificationServiceDestructionCannotEraseTheNewBackgroundTaskWhileItsLeaseSurvives() {
        var nextCancelled = false
        AgentExecutionService.acquire(context, "run:old") {}
        val old = Robolectric.buildService(AgentExecutionService::class.java).create()
        var destroyed = false
        try {
            AgentExecutionService.updateTasks(context, listOf(card("old")))
            AgentExecutionService.release("run:old"); idle() // stopSelf requested; the old owner is detached.
            AgentExecutionService.acquire(context, "run:new") { nextCancelled = true }
            AgentExecutionService.updateTasks(context, listOf(card("new"))); idle()
            assertNotNull(AgentExecutionService.task("new"))
            old.destroy(); destroyed = true; idle()
            assertFalse("The previous FGS must not cancel the new lease", nextCancelled)
            assertNotNull("A surviving new task must retain the card consumed by ConversationLiveTasks", AgentExecutionService.task("new"))
            val next = Robolectric.buildService(AgentExecutionService::class.java).create()
            try {
                assertEquals("fixture.background", AgentExecutionService.taskSnapshots.value.single { it.runId == "new" }.targetPackage)
            } finally { AgentExecutionService.release("run:new"); idle(); next.destroy() }
        } finally {
            AgentExecutionService.release("run:old"); AgentExecutionService.release("run:new"); idle()
            if (!destroyed) old.destroy()
        }
    }

    @Test fun destroyingTheActualOwnerStillRemovesItsActiveCard() {
        AgentExecutionService.acquire(context, "run:owned") {}
        val service = Robolectric.buildService(AgentExecutionService::class.java).create()
        AgentExecutionService.updateTasks(context, listOf(card("owned"))); idle()
        assertNotNull(AgentExecutionService.task("owned"))
        service.destroy(); idle()
        assertNull(AgentExecutionService.task("owned"))
    }

    @Test fun newBatchStillClearsNonBackgroundCompletionsAndBackgroundHistoryStaysBounded() {
        AgentExecutionService.completeTask(context, card("text-only", AgentOverlayStatus.Stopped).copy(targetPackage = null))
        AgentExecutionService.acquire(context, "run:new-batch") {}; idle()
        try {
            assertNull(AgentExecutionService.task("text-only"))
            repeat(4) { AgentExecutionService.completeTask(context, card("finished-$it", AgentOverlayStatus.Stopped)) }
            idle()
            assertNull(AgentExecutionService.task("finished-0"))
            assertEquals(listOf("finished-1", "finished-2", "finished-3"),
                AgentExecutionService.taskSnapshots.value.filter { it.completed }.map { it.runId })
        } finally { AgentExecutionService.release("run:new-batch"); idle() }
    }

    @Test fun replacementOwnerCanPublishBeforeThePreviousServiceIsDestroyed() {
        AgentExecutionService.acquire(context, "run:previous") {}
        val old = Robolectric.buildService(AgentExecutionService::class.java).create()
        AgentExecutionService.release("run:previous"); idle()
        AgentExecutionService.acquire(context, "run:replacement") {}
        val next = Robolectric.buildService(AgentExecutionService::class.java).create()
        try {
            AgentExecutionService.updateTasks(context, listOf(card("replacement"))); idle()
            old.destroy(); idle()
            assertNotNull(AgentExecutionService.task("replacement"))
        } finally { AgentExecutionService.release("run:replacement"); idle(); next.destroy() }
    }
}
