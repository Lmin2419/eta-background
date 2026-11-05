package io.github.mangi.eta.ui.screens.tasks

import android.content.Context
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.*
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.model.toTaskVisualStatus

/** Read-only projection; never invent a runtime start time or a successful result. */
internal data class CompletedTaskUi(
    val runId: String, val conversationId: String?, val title: String,
    val status: TaskVisualStatus, val completedAt: Long? = null,
    val durationMs: Long? = null, val targetPackage: String? = null,
    val archiveSource: String? = null, val archiveConversationKey: String? = null,
)

internal fun completedTaskRows(cards: List<TaskNotificationState>, archived: List<CompletedTaskUi>): List<CompletedTaskUi> {
    val archiveById = archived.associateBy { it.runId }
    val live = cards.filter { it.completed }.asReversed().map { card ->
        val history = archiveById[card.runId]
        CompletedTaskUi(card.runId, card.conversationId ?: history?.conversationId, card.title,
            card.toTaskVisualStatus(), history?.completedAt ?: card.completedAtMillis,
            history?.durationMs ?: card.firstPublishedAtElapsedMs?.let { start -> card.completedAtElapsedMs?.minus(start)?.coerceAtLeast(0) },
            card.targetPackage, history?.archiveSource, history?.archiveConversationKey)
    }
    return (live + archived).distinctBy { it.runId }.sortedByDescending { it.completedAt }.take(20)
}

internal fun orderedActiveTasks(cards: List<TaskNotificationState>): List<TaskNotificationState> =
    cards.filterNot { it.completed }.sortedBy { if (it.toTaskVisualStatus() == TaskVisualStatus.Attention) 0 else 1 }

internal suspend fun loadCompletedTasks(context: Context): List<CompletedTaskUi> =
    io.github.mangi.eta.data.db.EtaDatabase.get(context.applicationContext).runtimeRunDao().recentTaskArchiveRows(20).map { stored ->
        val run = stored.run
        val payload = AgentExternalArchivePayload.from(run.handoffPayload)
        CompletedTaskUi(run.runId, null, payload?.title?.takeIf { it.isNotBlank() } ?: payload?.userText?.take(80) ?: context.getString(io.github.mangi.eta.R.string.eta_tasks),
            if (run.ok) TaskVisualStatus.Success else TaskVisualStatus.Failed, run.createdAt,
            stored.events.asReversed().firstNotNullOfOrNull { (AgentEventJsonCodec.decode(it.eventJson) as? AgentEvent.RunPerformance)?.summary?.elapsedMs },
            archiveSource = run.handoffSource, archiveConversationKey = payload?.conversationKey)
    }

internal fun taskAction(task: TaskNotificationState, action: String) {
    // Same admission and ownership checks as the existing notification buttons.
    if (AgentExecutionService.task(task.runId)?.completed == false)
        AgentRuntimeService.dispatchNotificationAction(action, task.runId)
}

internal fun openTaskPreview(context: Context, task: TaskNotificationState) {
    TaskPreviewActivity.pendingIntent(context, task, task.runId.hashCode()).send()
}

internal fun taskPreviewPollingAllowed(pageVisible: Boolean, started: Boolean, inViewport: Boolean,
    background: Boolean, completed: Boolean): Boolean = pageVisible && started && inViewport && background && !completed
