package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.ui.model.TaskVisualStatus
import org.junit.Assert.*
import org.junit.Test

class PreviewWindowMapperTest {
    private val base = PreviewSnapshot(TaskVisualStatus.Running, false, false, false, false, false, true, false, true)

    @Test fun everyRowHasItsSpecifiedActions() {
        val cases = listOf(
            base to Triple(PreviewMode.Running, PreviewAction.TakeOver, PreviewAction.Pause),
            base.copy(visualStatus = TaskVisualStatus.Paused) to Triple(PreviewMode.Paused, PreviewAction.Resume, PreviewAction.TakeOver),
            base.copy(manual = true) to Triple(PreviewMode.Manual, PreviewAction.ReturnToAi, PreviewAction.Back),
            base.copy(visualStatus = TaskVisualStatus.Attention) to Triple(PreviewMode.NeedsAttention, PreviewAction.TakeOver, PreviewAction.Pause),
            base.copy(visualStatus = TaskVisualStatus.Success) to Triple(PreviewMode.Completed, PreviewAction.OpenApp, PreviewAction.Close),
            base.copy(visualStatus = TaskVisualStatus.Failed) to Triple(PreviewMode.Incomplete, PreviewAction.Close, PreviewAction.OpenConversation),
            base.copy(frameUnavailable = true, frameAvailable = false) to Triple(PreviewMode.Incomplete, PreviewAction.Close, PreviewAction.OpenConversation),
        )
        cases.forEach { (input, expected) ->
            val actual = PreviewWindowMapper.map(input)
            assertEquals(expected.first, actual.mode)
            assertEquals(expected.second, actual.primary)
            assertEquals(listOf(expected.third), actual.secondary)
            assertTrue(actual.primaryEnabled)
            assertTrue(actual.secondaryEnabled)
        }
        val waiting = PreviewWindowMapper.map(base.copy(handoffPending = true, manual = true))
        assertEquals(PreviewMode.Handoff, waiting.mode)
        assertEquals(PreviewAction.Wait, waiting.primary)
        assertTrue(waiting.secondary.isEmpty())
        assertFalse(waiting.primaryEnabled); assertFalse(waiting.zoomEnabled)
    }

    @Test fun missingFrameDoesNotStrandManualOrPausedTask() {
        val manual = PreviewWindowMapper.map(base.copy(manual = true, frameAvailable = false, frameUnavailable = true))
        assertEquals(PreviewMode.Manual, manual.mode)
        assertTrue(manual.primaryEnabled); assertFalse(manual.secondaryEnabled)
        val paused = PreviewWindowMapper.map(base.copy(visualStatus = TaskVisualStatus.Paused, frameAvailable = false))
        assertEquals(PreviewAction.Resume, paused.primary)
        assertTrue(paused.primaryEnabled); assertFalse(paused.secondaryEnabled)
        val connecting = PreviewWindowMapper.map(base.copy(frameAvailable = false))
        assertEquals(PreviewMode.Running, connecting.mode)
        assertFalse(connecting.primaryEnabled); assertTrue(connecting.zoomEnabled)
    }

    @Test fun manualWinsOverTerminalAndEachBusyGateDisablesInput() {
        assertEquals(PreviewMode.Manual, PreviewWindowMapper.map(base.copy(manual = true, visualStatus = TaskVisualStatus.Success)).mode)
        listOf(base.copy(busy = true), base.copy(resizing = true), base.copy(closing = true)).forEach { input ->
            val controls = PreviewWindowMapper.map(input.copy(manual = true))
            assertFalse(controls.primaryEnabled); assertFalse(controls.secondaryEnabled); assertFalse(controls.zoomEnabled)
        }
        val noConversation = PreviewWindowMapper.map(base.copy(visualStatus = TaskVisualStatus.Failed, hasConversation = false))
        assertTrue(noConversation.secondary.isEmpty()); assertFalse(noConversation.secondaryEnabled)
    }

    @Test fun pausedControllerOverridesNonterminalAttentionButNeverATerminalResult() {
        val card = TaskNotificationState("run", "chat", "Task", io.github.mangi.eta.agent.overlay.AgentOverlayStatus.ContinuationUnavailable)
        assertEquals(TaskVisualStatus.Paused, previewVisualStatus(card, true))
        assertEquals(TaskVisualStatus.Attention, previewVisualStatus(card.copy(completed = true), true))
        assertEquals(TaskVisualStatus.Success, previewVisualStatus(card.copy(status = io.github.mangi.eta.agent.overlay.AgentOverlayStatus.ResultReady), true))
        assertEquals(PreviewMode.Incomplete, PreviewWindowMapper.map(base.copy(visualStatus = TaskVisualStatus.Success,
            frameUnavailable = true, frameAvailable = false)).mode)
    }
}
