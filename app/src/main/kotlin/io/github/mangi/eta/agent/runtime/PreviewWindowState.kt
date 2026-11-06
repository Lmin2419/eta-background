package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.model.toTaskVisualStatus

internal fun previewVisualStatus(card: TaskNotificationState?, controllerPaused: Boolean): TaskVisualStatus {
    val status = card?.toTaskVisualStatus() ?: TaskVisualStatus.Running
    return if (card?.completed == true || status == TaskVisualStatus.Success || status == TaskVisualStatus.Failed) status
        else if (controllerPaused) TaskVisualStatus.Paused else status
}

internal enum class PreviewMode { Running, Paused, Manual, NeedsAttention, Handoff, Completed, Incomplete }

internal enum class PreviewAction { TakeOver, Pause, Resume, ReturnToAi, Back, OpenApp, Close, OpenConversation, Wait }

internal data class PreviewWindowState(
    val mode: PreviewMode,
    val primary: PreviewAction,
    val secondary: List<PreviewAction>,
    val primaryEnabled: Boolean,
    val secondaryEnabled: Boolean,
    val zoomEnabled: Boolean
)

internal data class PreviewSnapshot(
    val visualStatus: TaskVisualStatus,
    val manual: Boolean,
    val busy: Boolean,
    val handoffPending: Boolean,
    val closing: Boolean,
    val resizing: Boolean,
    val frameAvailable: Boolean,
    val frameUnavailable: Boolean,
    val hasConversation: Boolean
)

internal object PreviewWindowMapper {
    fun map(s: PreviewSnapshot): PreviewWindowState {
        val canInteract = !s.busy && !s.resizing && !s.closing
        val frameInteractive = canInteract && s.frameAvailable

        if (s.handoffPending) return PreviewWindowState(
            PreviewMode.Handoff, PreviewAction.Wait, emptyList(), false, false, false
        )

        if (s.manual) return PreviewWindowState(
            PreviewMode.Manual, PreviewAction.ReturnToAi, listOf(PreviewAction.Back),
            primaryEnabled = canInteract, secondaryEnabled = frameInteractive, zoomEnabled = canInteract
        )

        if (s.visualStatus == TaskVisualStatus.Success && !s.frameUnavailable) return PreviewWindowState(
            PreviewMode.Completed, PreviewAction.OpenApp, listOf(PreviewAction.Close),
            primaryEnabled = canInteract, secondaryEnabled = canInteract, zoomEnabled = canInteract
        )

        if (s.visualStatus == TaskVisualStatus.Failed || s.frameUnavailable) {
            val sec = if (s.hasConversation) listOf(PreviewAction.OpenConversation) else emptyList()
            return PreviewWindowState(
                PreviewMode.Incomplete, PreviewAction.Close, sec,
                primaryEnabled = canInteract,
                secondaryEnabled = canInteract && sec.isNotEmpty(),
                zoomEnabled = canInteract
            )
        }

        if (s.visualStatus == TaskVisualStatus.Attention) return PreviewWindowState(
            PreviewMode.NeedsAttention, PreviewAction.TakeOver, listOf(PreviewAction.Pause),
            primaryEnabled = frameInteractive, secondaryEnabled = canInteract, zoomEnabled = canInteract
        )

        if (s.visualStatus == TaskVisualStatus.Paused) return PreviewWindowState(
            PreviewMode.Paused, PreviewAction.Resume, listOf(PreviewAction.TakeOver),
            primaryEnabled = canInteract, secondaryEnabled = frameInteractive, zoomEnabled = canInteract
        )

        // Running / Queued
        return PreviewWindowState(
            PreviewMode.Running, PreviewAction.TakeOver, listOf(PreviewAction.Pause),
            primaryEnabled = frameInteractive, secondaryEnabled = canInteract, zoomEnabled = canInteract
        )
    }
}
