package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.os.Looper
import androidx.compose.ui.platform.ComposeView
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.overlay.AgentOverlayPhase
import io.github.mangi.eta.agent.overlay.AgentOverlayState
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import androidx.compose.runtime.MutableState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AgentRuntimeScreenOnTest {
    @Test
    fun returningToEtaSuppressesAmbientWindowsButRetainsBackgroundTaskCard() {
        val controller = Robolectric.buildService(AgentRuntimeService::class.java).create()
        val service = controller.get()
        val foreground = io.github.mangi.eta.ui.MainActivity::class.java.getDeclaredField("isInForeground").apply {
            isAccessible = true
        }
        val previous = foreground.getBoolean(null)
        try {
            service.registerRun("eta-ui-background", ownsGui = false, handoffSource = AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE)
            service.invoke("publishExecutionState")
            service.setField("capsuleView", ComposeView(service))
            service.setField("glowView", ComposeView(service))
            foreground.setBoolean(null, true)
            AgentRuntimeService.onEtaUiVisibilityChanged()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(service.field<ComposeView?>("capsuleView"))
            assertNull(service.field<ComposeView?>("glowView"))
            assertEquals(listOf("eta-ui-background"), AgentRuntimeService.tasks.value.map { it.runId })
            assertFalse(AgentRuntimeService.keepScreenOn.value)
        } finally {
            foreground.setBoolean(null, previous)
            controller.destroy()
        }
    }

    @Test
    fun physicalScreenOffPausesRunAndScreenOnRequiresManualResume() {
        val controller = Robolectric.buildService(AgentRuntimeService::class.java).create()
        val service = controller.get()
        val display = service.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(0)
        try {
            val run = service.registerRun("screen-off", ownsGui = false)
            // Another display may keep global power interactive while the phone's screen is off.
            shadowOf(service.getSystemService(android.os.PowerManager::class.java)).setIsInteractive(true)
            shadowOf(display).setState(android.view.Display.STATE_OFF)
            org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
            service.field<android.content.BroadcastReceiver>("screenReceiver").onReceive(service, android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF))
            assertFalse(run.session.controller.isCancelled)
            assertTrue(run.session.controller.isPaused)
            org.junit.Assert.assertNull(service.field<ComposeView?>("glowView"))
            org.junit.Assert.assertNull(service.field<ComposeView?>("capsuleView"))
            shadowOf(display).setState(android.view.Display.STATE_ON)
            service.field<android.content.BroadcastReceiver>("screenReceiver").onReceive(service, android.content.Intent(android.content.Intent.ACTION_SCREEN_ON))
            assertTrue(run.session.controller.isPaused)
            service.invoke("requestResume")
            assertFalse(run.session.controller.isPaused)
        } finally {
            shadowOf(display).setState(android.view.Display.STATE_ON)
            org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
            controller.destroy()
        }
    }

    @Test
    @Config(sdk = [37])
    fun systemTaskSurfaceKeepsOnlyTheGlowAndNotificationControlsTargetTheirOriginalRun() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
        val serviceController = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = serviceController.get()
            val first = service.registerRun("native-one", ownsGui = false)
            val second = service.registerRun("native-two", ownsGui = false)
            service.invoke("publishExecutionState")
            // Existing windows are represented without starting an infinite Compose animation
            // on Robolectric's virtual frame clock. Verify the runtime removes only the capsule.
            val glow = ComposeView(service)
            service.setField("glowView", glow)
            service.setField("capsuleView", ComposeView(service))
            org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
            service.invoke("publishExecutionState")
            org.junit.Assert.assertNull(service.field<ComposeView?>("capsuleView"))
            service.invoke("publishExecutionState")
            org.junit.Assert.assertSame(glow, service.field<ComposeView?>("glowView"))
            org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
            AgentRuntimeService.dispatchNotificationAction(AgentExecutionService.ACTION_PAUSE_RUN, first.runId)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(first.session.controller.isPaused)
            assertFalse(second.session.controller.isPaused)
            AgentRuntimeService.dispatchNotificationAction(AgentExecutionService.ACTION_NEXT_TASK, first.runId)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(second.runId, service.field<RuntimeRunRegistry.Run>("displayedRun").runId)
            first.session.complete(AgentRuntimeWire.RunResult(first.runId, true, "完成"))
            AgentRuntimeService.dispatchNotificationAction(AgentExecutionService.ACTION_STOP_RUN, first.runId)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(second.session.controller.isCancelled)
        } finally {
            serviceController.destroy()
            org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
        }
    }

    @Test
    fun backgroundCapsuleSwitchesAndControlsOnlySelectedTaskWithoutKeepingScreenOn() {
        val serviceController = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = serviceController.get()
            val first = service.registerRun("background-one", ownsGui = false)
            val second = service.registerRun("background-two", ownsGui = false)
            service.invoke("publishExecutionState")
            assertEquals(2, service.field<MutableState<List<io.github.mangi.eta.agent.overlay.OverlayTaskItem>>>("taskItems").value.size)
            service.invokeForRun("selectOverlayTask", first.runId)
            service.invokeForRun("requestPause", first.runId)
            assertTrue(first.session.controller.isPaused)
            assertFalse(second.session.controller.isPaused)
            assertEquals(AgentOverlayPhase.PAUSED, service.field<MutableState<AgentOverlayState>>("state").value.phase)
            assertEquals(AgentOverlayPhase.RUNNING, service.field<MutableState<AgentOverlayState>>("ambientState").value.phase)
            service.invoke("requestResume")
            assertFalse(first.session.controller.isPaused)
            service.invoke("updateKeepScreenOn")
            assertFalse(AgentRuntimeService.keepScreenOn.value)
        } finally {
            serviceController.destroy()
        }
    }

    @Test
    fun pauseResumeAndCancellationUpdateBothAppAndGuiWindowState() {
        val serviceController = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = serviceController.get()
            val session = service.registerRun("active-run").session
            val capsule = ComposeView(service)
            service.setField("capsuleView", capsule)

            service.invoke("updateKeepScreenOn")
            assertTrue(AgentRuntimeService.keepScreenOn.value)
            assertTrue(capsule.keepScreenOn)

            service.invokeForRun("requestPause", session.runId)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(AgentRuntimeService.keepScreenOn.value)
            assertFalse(capsule.keepScreenOn)

            service.invoke("requestResume")
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(AgentRuntimeService.keepScreenOn.value)
            assertTrue(capsule.keepScreenOn)

            session.controller.cancel()
            service.invoke("updateKeepScreenOn")
            assertFalse(AgentRuntimeService.keepScreenOn.value)
            assertFalse(capsule.keepScreenOn)

            service.invoke("requestResume")
            assertFalse(AgentRuntimeService.keepScreenOn.value)
            assertFalse(capsule.keepScreenOn)
        } finally {
            serviceController.destroy()
        }
    }

    @Test
    fun completedRunStopsKeepingScreenOnBeforeServiceDestruction() {
        val serviceController = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = serviceController.get()
            val session = service.registerRun("completed-run").session
            service.invoke("updateKeepScreenOn")
            assertTrue(AgentRuntimeService.keepScreenOn.value)

            session.complete(AgentRuntimeWire.RunResult(session.runId, true, "完成"))
            service.invoke("updateKeepScreenOn")
            assertFalse(AgentRuntimeService.keepScreenOn.value)

            service.invoke("requestResume")
            assertFalse(AgentRuntimeService.keepScreenOn.value)
        } finally {
            serviceController.destroy()
        }
    }

    @Test
    fun destroyingServiceClearsActiveRunScreenRequest() {
        val serviceController = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = serviceController.get()
            service.registerRun("destroyed-run")
            service.invoke("updateKeepScreenOn")
            assertTrue(AgentRuntimeService.keepScreenOn.value)
        } finally {
            serviceController.destroy()
        }
        assertFalse(AgentRuntimeService.keepScreenOn.value)
    }

    @Test
    fun backgroundCompletionDoesNotClearGuiOwnersScreenRequest() {
        val serviceController = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = serviceController.get()
            service.registerRun("gui-run")
            val background = service.registerRun("background-run", ownsGui = false)
            background.session.complete(AgentRuntimeWire.RunResult(background.runId, true, "完成"))
            service.invoke("updateKeepScreenOn")
            assertTrue(AgentRuntimeService.keepScreenOn.value)
        } finally {
            serviceController.destroy()
        }
    }

    @Test
    fun cancelledPausedOwnerKeepsTheResumeControlAvailable() {
        val serviceController = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = serviceController.get()
            val run = service.registerRun("paused-run")
            service.invokeForRun("requestPause", run.runId)
            run.session.controller.cancel()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(service.field<GuiControlCoordinator>("guiControl").state.value.userPaused)
            assertEquals(AgentOverlayPhase.PAUSED, service.field<MutableState<AgentOverlayState>>("state").value.phase)
            service.invoke("requestResume")
            assertFalse(service.field<GuiControlCoordinator>("guiControl").state.value.userPaused)
        } finally {
            serviceController.destroy()
        }
    }

    @Test
    fun executionStatusFollowsGuiOwnershipWithoutGivingBackgroundRunsScreenControl() {
        val serviceController = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = serviceController.get()
            val background = service.registerRun("background-run", ownsGui = false).apply {
                overlayState = overlayState.copy(status = AgentOverlayStatus.RunningTool("terminal_job"))
            }
            val guiCoordinator = service.field<GuiControlCoordinator>("guiControl")
            service.assertTaskStatuses(mapOf(background.runId to background.overlayState.status))
            assertNull(guiCoordinator.state.value.ownerRunId)
            assertFalse(AgentRuntimeService.keepScreenOn.value)

            val guiRun = service.registerRun("gui-run", ownsGui = false).apply {
                overlayState = overlayState.copy(status = AgentOverlayStatus.GeneratingAnswer)
            }
            val guiControl = guiCoordinator.openRun(guiRun.runId, guiRun.session.controller)
            guiControl.withAction { }
            service.assertTaskStatuses(mapOf(background.runId to background.overlayState.status, guiRun.runId to AgentOverlayStatus.GeneratingAnswer))
            assertEquals(guiRun.runId, guiCoordinator.state.value.ownerRunId)
            assertTrue(AgentRuntimeService.keepScreenOn.value)

            guiControl.release()
            service.assertTaskStatuses(mapOf(background.runId to background.overlayState.status, guiRun.runId to guiRun.overlayState.status))
            assertNull(guiCoordinator.state.value.ownerRunId)
            assertFalse(AgentRuntimeService.keepScreenOn.value)
        } finally {
            serviceController.destroy()
        }
    }

    @Test
    fun retainedGuiPauseDoesNotPauseBackgroundNotificationOrKeepFinishedRunsPromoted() {
        val serviceController = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = serviceController.get()
            val guiRun = service.registerRun("paused-run")
            service.invokeForRun("requestPause", guiRun.runId)
            val background = service.registerRun("background-run", ownsGui = false).apply {
                overlayState = overlayState.copy(status = AgentOverlayStatus.RunningTool("terminal_job"))
            }
            service.assertTaskStatuses(mapOf(guiRun.runId to AgentOverlayStatus.Paused, background.runId to background.overlayState.status))

            guiRun.session.controller.cancel()
            guiRun.session.complete(AgentRuntimeWire.RunResult(guiRun.runId, true, "完成"))
            service.assertTaskStatuses(mapOf(background.runId to background.overlayState.status))
            val guiCoordinator = service.field<GuiControlCoordinator>("guiControl")
            assertTrue(guiCoordinator.state.value.userPaused)
            assertNull(guiCoordinator.state.value.ownerRunId)
            assertFalse(background.session.controller.isPaused)
            assertFalse(AgentRuntimeService.keepScreenOn.value)

            background.session.complete(AgentRuntimeWire.RunResult(background.runId, true, "完成"))
            service.assertTaskStatuses(emptyMap())
            assertTrue(guiCoordinator.state.value.userPaused)
        } finally {
            serviceController.destroy()
        }
    }

    private fun AgentRuntimeService.assertTaskStatuses(expected: Map<String, AgentOverlayStatus>) {
        invoke("publishExecutionState")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(expected, AgentRuntimeService.tasks.value.associate { it.runId to it.status })
    }

    private fun AgentRuntimeService.registerRun(runId: String, ownsGui: Boolean = true, handoffSource: String? = null): RuntimeRunRegistry.Run {
        val registry = field<RuntimeRunRegistry>("runs")
        lateinit var session: AgentRuntimeSession
        session = AgentRuntimeSession(runId, beforeTerminalPublish = { registry.finish(runId, session) })
        val request = AgentRuntimeWire.RunRequest(
            runId, "测试", AgentModelClient.ModelConfig(baseUrl = "https://example.invalid", apiKey = "test-key", model = "test-model", systemPrompt = ""),
            emptyList(),
            handoff = handoffSource?.let { AgentRuntimeWire.EntryHandoff(runId, it, "{}") },
        )
        val run = (registry.admit(request) { RuntimeRunRegistry.Run(request, session) } as RuntimeRunRegistry.Admission.Accepted).run
        registry.claimNext(setOf(runId))
        run.prepared(request)
        if (ownsGui) field<GuiControlCoordinator>("guiControl").openRun(runId, session.controller).withAction { }
        shadowOf(Looper.getMainLooper()).idle()
        return run
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> AgentRuntimeService.field(name: String): T = AgentRuntimeService::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }.get(this) as T

    private fun AgentRuntimeService.invokeForRun(name: String, runId: String) {
        AgentRuntimeService::class.java.getDeclaredMethod(name, String::class.java).apply {
            isAccessible = true
        }.invoke(this, runId)
    }

    private fun AgentRuntimeService.setField(name: String, value: Any) {
        AgentRuntimeService::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(this, value)
    }

    private fun AgentRuntimeService.invoke(name: String) {
        AgentRuntimeService::class.java.getDeclaredMethod(name).apply {
            isAccessible = true
        }.invoke(this)
    }
}
