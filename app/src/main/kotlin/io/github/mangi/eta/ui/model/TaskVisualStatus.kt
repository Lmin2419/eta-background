package io.github.mangi.eta.ui.model

import io.github.mangi.eta.agent.overlay.AgentOverlayPhase
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.TaskNotificationState

/** Presentation only. Never feeds back into task admission, pause or completion. */
enum class TaskVisualStatus { Running, Attention, Paused, Success, Failed, Queued }

internal fun AgentOverlayPhase.toTaskVisualStatus(): TaskVisualStatus = when (this) {
    AgentOverlayPhase.RUNNING -> TaskVisualStatus.Running
    AgentOverlayPhase.PAUSED -> TaskVisualStatus.Paused
    AgentOverlayPhase.FINISHED -> TaskVisualStatus.Success
    AgentOverlayPhase.FAILED -> TaskVisualStatus.Failed
}

fun RunStatusUi.toTaskVisualStatus(): TaskVisualStatus = when (this) {
    RunStatusUi.Running -> TaskVisualStatus.Running
    RunStatusUi.Success -> TaskVisualStatus.Success
    RunStatusUi.Failed, RunStatusUi.Cancelled -> TaskVisualStatus.Failed
}

fun ToolActivityStatusUi.toTaskVisualStatus(): TaskVisualStatus = when (this) {
    ToolActivityStatusUi.Running -> TaskVisualStatus.Running
    ToolActivityStatusUi.Success -> TaskVisualStatus.Success
    ToolActivityStatusUi.Failed -> TaskVisualStatus.Failed
    // No outcome is known: ask for inspection, never invent success or an active animation.
    ToolActivityStatusUi.Unknown -> TaskVisualStatus.Attention
}

internal fun AgentOverlayStatus.toTaskVisualStatus(): TaskVisualStatus = when (this) {
    AgentOverlayStatus.ResultReady -> TaskVisualStatus.Success
    AgentOverlayStatus.RunFailed, AgentOverlayStatus.Stopped -> TaskVisualStatus.Failed
    AgentOverlayStatus.Paused, AgentOverlayStatus.PreviewPaused,
    AgentOverlayStatus.AppTakeoverPaused -> TaskVisualStatus.Paused
    AgentOverlayStatus.Queued, AgentOverlayStatus.WaitingForGui -> TaskVisualStatus.Queued
    AgentOverlayStatus.ContinuationUnavailable -> TaskVisualStatus.Attention
    AgentOverlayStatus.Preparing, AgentOverlayStatus.Reasoning,
    is AgentOverlayStatus.BackgroundScreen, AgentOverlayStatus.WaitingForModel,
    AgentOverlayStatus.SupplementReceived, is AgentOverlayStatus.RunningTool,
    is AgentOverlayStatus.HostedToolRunning, AgentOverlayStatus.GeneratingAnswer,
    AgentOverlayStatus.Stopping, AgentOverlayStatus.Continuing, AgentOverlayStatus.Finishing -> TaskVisualStatus.Running
}

internal fun TaskNotificationState.toTaskVisualStatus(): TaskVisualStatus = when {
    // A terminal result outranks stale flags. Reply delivery failure is deliberately independent.
    status == AgentOverlayStatus.RunFailed || status == AgentOverlayStatus.Stopped -> TaskVisualStatus.Failed
    status == AgentOverlayStatus.ResultReady -> TaskVisualStatus.Success
    // "Completed" alone does not tell us whether a task succeeded. Never animate a terminal card.
    completed -> TaskVisualStatus.Attention
    paused -> TaskVisualStatus.Paused
    else -> status.toTaskVisualStatus()
}
