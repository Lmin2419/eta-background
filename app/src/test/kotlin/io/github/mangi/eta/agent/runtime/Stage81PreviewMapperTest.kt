package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.ui.model.TaskVisualStatus
import org.junit.Assert.*
import org.junit.Test

class Stage81PreviewMapperTest {
    private fun snapshot(status: TaskVisualStatus, frame: Boolean) = PreviewSnapshot(
        status, false, false, false, false, false, frame, !frame, true)

    private fun terminal(status: TaskVisualStatus, frame: Boolean) {
        val state = PreviewWindowMapper.map(snapshot(status, frame))
        assertEquals(if (status == TaskVisualStatus.Success) PreviewMode.Completed else PreviewMode.Incomplete, state.mode)
        assertEquals(PreviewAction.OpenConversation, state.primary)
        assertEquals(listOf(PreviewAction.OpenApp) + if (frame) listOf(PreviewAction.TakeOver) else emptyList(), state.secondary)
        assertTrue(state.primaryEnabled); assertTrue(state.secondaryEnabled)
    }

    @Test fun successWithPage() = terminal(TaskVisualStatus.Success, true)
    @Test fun successWithoutPage() = terminal(TaskVisualStatus.Success, false)
    @Test fun failureWithPage() = terminal(TaskVisualStatus.Failed, true)
    @Test fun failureWithoutPage() = terminal(TaskVisualStatus.Failed, false)
    @Test fun runningWithoutFrameKeepsItsStatusAndPauseAction() {
        val state = PreviewWindowMapper.map(snapshot(TaskVisualStatus.Running, false))
        assertEquals(PreviewMode.Running, state.mode)
        assertEquals(PreviewAction.TakeOver, state.primary)
        assertFalse(state.primaryEnabled)
        assertEquals(listOf(PreviewAction.Pause), state.secondary)
        assertTrue(state.secondaryEnabled)
    }

    @Test fun missingFramesPreservePausedAndAttentionStates() {
        val paused = PreviewWindowMapper.map(snapshot(TaskVisualStatus.Paused, false))
        assertEquals(PreviewMode.Paused, paused.mode); assertTrue(paused.primaryEnabled); assertFalse(paused.secondaryEnabled)
        val attention = PreviewWindowMapper.map(snapshot(TaskVisualStatus.Attention, false))
        assertEquals(PreviewMode.NeedsAttention, attention.mode); assertFalse(attention.primaryEnabled)
    }

    @Test fun noConversationKeepsOpenAppAndEveryBusyGateDisablesTerminalActions() {
        for (status in listOf(TaskVisualStatus.Success, TaskVisualStatus.Failed)) for (frame in listOf(false, true)) {
            val base = snapshot(status, frame).copy(hasConversation = false)
            val state = PreviewWindowMapper.map(base)
            assertEquals(PreviewAction.OpenApp, state.primary)
            assertEquals(if (frame) listOf(PreviewAction.TakeOver) else emptyList<PreviewAction>(), state.secondary)
            for (blocked in listOf(base.copy(busy = true), base.copy(resizing = true), base.copy(closing = true))) {
                val disabled = PreviewWindowMapper.map(blocked)
                assertFalse(disabled.primaryEnabled); assertFalse(disabled.secondaryEnabled); assertFalse(disabled.zoomEnabled)
            }
        }
    }
}
