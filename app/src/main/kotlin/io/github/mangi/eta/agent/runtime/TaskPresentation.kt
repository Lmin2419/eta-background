package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.overlay.AgentOverlayPhase

internal fun RuntimeRunRegistry.Run.presentationPhase(): AgentOverlayPhase = when {
    session.isTerminal -> if (session.result?.ok == true) AgentOverlayPhase.FINISHED else AgentOverlayPhase.FAILED
    session.isCommitting -> AgentOverlayPhase.RUNNING
    session.controller.isPaused -> AgentOverlayPhase.PAUSED
    else -> AgentOverlayPhase.RUNNING
}

/** Read-only projection of the existing runtime authority; never a second task lifecycle. */
internal fun RuntimeRunRegistry.Run.presentationStatus(): AgentOverlayStatus {
    session.result?.let { result ->
        return when {
            result.ok -> AgentOverlayStatus.ResultReady
            result.error == "已停止" -> AgentOverlayStatus.Stopped
            else -> AgentOverlayStatus.RunFailed
        }
    }
    return when (snapshot().phase) {
        AgentRuntimeWire.RunPhase.Committing -> AgentOverlayStatus.Finishing
        AgentRuntimeWire.RunPhase.Stopping -> AgentOverlayStatus.Stopping
        AgentRuntimeWire.RunPhase.Paused -> when {
            session.controller.pausedForPreview -> AgentOverlayStatus.PreviewPaused
            session.controller.pausedForAppTakeover -> AgentOverlayStatus.AppTakeoverPaused
            else -> AgentOverlayStatus.Paused
        }
        AgentRuntimeWire.RunPhase.Queued -> AgentOverlayStatus.Queued
        AgentRuntimeWire.RunPhase.Preparing -> AgentOverlayStatus.Preparing
        AgentRuntimeWire.RunPhase.WaitingForGui -> AgentOverlayStatus.WaitingForGui
        AgentRuntimeWire.RunPhase.Running -> when {
            backgroundScreenPhase != null -> AgentOverlayStatus.BackgroundScreen(backgroundScreenPhase!!)
            // Model completion is not the persistence/terminal commit boundary.
            overlayState.status in setOf(AgentOverlayStatus.ResultReady, AgentOverlayStatus.RunFailed,
                AgentOverlayStatus.Stopped) -> AgentOverlayStatus.Finishing
            else -> overlayState.status
        }
    }
}
