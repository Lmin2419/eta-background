package io.github.mangi.eta.agent.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.overlay.localizedText
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.ui.MainActivity
import java.util.concurrent.atomic.AtomicLong

/** A task keeps its notification identity from admission through completion. */
internal data class TaskNotificationState(
    val runId: String,
    val conversationId: String?,
    val title: String,
    val status: AgentOverlayStatus,
    val streamPreview: String = "",
    val paused: Boolean = false,
    val completed: Boolean = false,
    val targetPackage: String? = null,
)

/** One execution service; each task owns one card, including its final state. */
internal class AgentExecutionService : Service() {
    private val stopQueue = ExecutionStopQueue { failure ->
        AndroidAgentLogger.warn("Execution task stop failed: type=${failure.safeLogType()}")
    }
    private val owner = ownerSequence.incrementAndGet()
    private var foregroundActive = false
    private var foregroundId = 0
    @Volatile private var startRejected = false
    private val replyConnections = mutableMapOf<String, NotificationReplyConnection>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        leases.attachOwner(owner)
        ensureChannel(this)
        ensureForeground()
    }

    private fun ensureForeground() {
        if (foregroundActive || startRejected) return
        leases.attachOwner(owner)
        try { postForegroundNotification() }
        catch (failure: RuntimeException) {
            startRejected = true
            AndroidAgentLogger.warn("Execution service foreground failed: type=${failure.safeLogType()}")
            stopTasks(startFailed = true)
        }
    }

    private fun postForegroundNotification() {
        val task = cards.values.firstOrNull { !it.completed && !it.paused && it.status != AgentOverlayStatus.Queued }
            ?: cards.values.firstOrNull { !it.completed } ?: cards.values.lastOrNull()
        val id = task?.let { idFor(it.runId) } ?: NOTIFICATION_ID
        if (!foregroundActive || foregroundId != id || task == null || posted[id] != task) {
            val card = notification(this, task, id)
            val oldId = foregroundId
            if (foregroundActive && oldId != id) {
                // Detach first so Android cannot cancel the old card during ownership transfer.
                stopForeground(STOP_FOREGROUND_DETACH)
                foregroundActive = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(id, card, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(id, card)
            foregroundActive = true
            foregroundId = id
            if (task != null) posted[id] = task
            // Refresh the detached card in place with its final state.
            if (oldId != id) {
                posted.remove(oldId)
                if (oldId != 0 && oldId !in notificationIds.values) getSystemService(NotificationManager::class.java).cancel(oldId)
            }
        }
        publishCards(this, exceptId = id)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REPLY) {
            receiveReply(intent)
        } else if (intent?.action in RUN_ACTIONS) {
            intent?.getStringExtra(EXTRA_RUN_ID)?.let {
                AgentRuntimeService.dispatchNotificationAction(intent.action.orEmpty(), it)
            }
            refreshNotification()
        } else if (intent?.action == ACTION_STOP) stopTasks()
        else { ensureForeground(); refreshNotification() }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val current = instance === this
        if (current) instance = null
        replyConnections.values.toList().forEach { it.close() }
        stopQueue.close(leases.drainOwner(owner))
        if (current) cards.values.filterNot { it.completed }.map { it.runId }.forEach { removeCard(this, it) }
        super.onDestroy()
    }

    private fun receiveReply(intent: Intent) {
        val runId = intent.getStringExtra(EXTRA_RUN_ID).orEmpty()
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY_TEXT)?.toString()?.trim().orEmpty()
        if (runId.isBlank() || conversationId.isBlank() || text.isBlank() || runId in replyConnections || runId in repliedRuns) {
            refreshNotification(); return
        }
        val original = cards[runId] ?: TaskNotificationState(runId, conversationId,
            getString(R.string.background_continue_instruction), AgentOverlayStatus.ResultReady, completed = true)
        if (!original.completed || original.conversationId != conversationId) { refreshNotification(); return }
        cards[runId] = original
        if (runId !in notificationIds) notificationIds[runId] = intent.getIntExtra(EXTRA_NOTIFICATION_ID, idFor(runId))
        val leaseId = "notification-reply:$runId"
        val connection = NotificationReplyConnection(this, runId, conversationId, text) { accepted ->
            replyConnections.remove(runId)
            if (accepted) {
                repliedRuns.add(runId)
                while (repliedRuns.size > 32) repliedRuns.remove(repliedRuns.first())
                removeCard(this, runId)
            } else {
                completeTask(this, original.copy(status = AgentOverlayStatus.RunFailed,
                    streamPreview = getString(R.string.background_reply_failed)))
            }
            release(leaseId)
        }
        replyConnections[runId] = connection
        // A notification reply is a user gesture. Hold the existing FGS only until Runtime admits it.
        leases.acquire(leaseId, allowBoundFallback = true, onStop = { mainHandler.post { connection.close() } })
        connection.start()
    }

    private fun stopTasks(startFailed: Boolean = false) {
        stopQueue.submit(leases.drain(startFailed)) {
            mainHandler.post { if (instance === this) refreshNotification() }
        }
    }

    private fun refreshNotification() {
        if (leases.closeOwnerIfIdle(owner)) {
            if (foregroundActive) {
                foregroundActive = false
                stopForeground(STOP_FOREGROUND_DETACH)
                posted.remove(foregroundId)
            }
            publishCards(this, completedOnly = true)
            stopSelf()
        } else postForegroundNotification()
    }

    companion object {
        private const val CHANNEL = "eta_execution_tasks_v2"
        private const val NOTIFICATION_ID = 1107
        private const val ACTION_STOP = "io.github.mangi.eta.action.STOP_USER_EXECUTION"
        // Accepted only for PendingIntents left by older builds; new cards have no switch action.
        internal const val ACTION_NEXT_TASK = "io.github.mangi.etax.action.NEXT_TASK"
        internal const val ACTION_PAUSE_RUN = "io.github.mangi.etax.action.PAUSE_RUN"
        internal const val ACTION_RESUME_RUN = "io.github.mangi.etax.action.RESUME_RUN"
        internal const val ACTION_STOP_RUN = "io.github.mangi.etax.action.STOP_RUN"
        internal const val ACTION_REPLY = "io.github.mangi.etax.action.REPLY"
        internal const val REPLY_TEXT = "eta_reply_text"
        internal const val EXTRA_CONVERSATION_ID = "reply_conversation_id"
        private const val EXTRA_NOTIFICATION_ID = "reply_notification_id"
        private const val EXTRA_RUN_ID = "run_id"
        private val RUN_ACTIONS = setOf(ACTION_NEXT_TASK, ACTION_PAUSE_RUN, ACTION_RESUME_RUN, ACTION_STOP_RUN)
        private val leases = ExecutionLeaseRegistry()
        private val ownerSequence = AtomicLong()
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile private var instance: AgentExecutionService? = null
        private val cards = linkedMapOf<String, TaskNotificationState>()
        private val notificationIds = mutableMapOf<String, Int>()
        private val posted = mutableMapOf<Int, TaskNotificationState>()
        private val repliedRuns = linkedSetOf<String>()
        private var nextId = 0x45540000
        private var refreshPending = false
        private val refresh = Runnable {
            refreshPending = false
            instance?.takeIf { it.foregroundActive }?.refreshNotification()
        }

        private fun onMain(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
        }

        private fun idFor(runId: String): Int = notificationIds.getOrPut(runId) {
            if (notificationIds.isEmpty()) NOTIFICATION_ID else nextId++
        }

        private fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.execution_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null); enableVibration(false); setShowBadge(false)
                })
        }

        private fun removeCard(context: Context, runId: String) {
            cards.remove(runId)
            notificationIds.remove(runId)?.let { id ->
                context.getSystemService(NotificationManager::class.java).cancel(id)
                posted.remove(id)
            }
        }

        /** Ordinary streaming updates share one throttle. Completed cards are retained unchanged. */
        fun updateTasks(context: Context, snapshot: List<TaskNotificationState>) = onMain {
            val liveIds = snapshot.map { it.runId }.toSet()
            val removed = cards.values.filter { !it.completed && it.runId !in liveIds }.map { it.runId }
            removed.forEach { removeCard(context, it) }
            var changed = removed.isNotEmpty()
            snapshot.forEach { task ->
                if (cards[task.runId]?.completed != true && cards[task.runId] != task) {
                    cards[task.runId] = task
                    idFor(task.runId)
                    changed = true
                }
            }
            if (changed || snapshot.isEmpty()) scheduleRefresh()
        }

        /** Terminal events bypass streaming throttles and update the original task card immediately. */
        fun completeTask(context: Context, task: TaskNotificationState) = onMain {
            val previous = cards[task.runId]
            if (previous?.completed == true && previous.status == task.status && previous.targetPackage == task.targetPackage &&
                previous.streamPreview == task.streamPreview) return@onMain
            cards[task.runId] = task.copy(completed = true, paused = false)
            idFor(task.runId)
            cards.values.filter { it.completed }.dropLast(3).map { it.runId }.forEach { removeCard(context, it) }
            mainHandler.removeCallbacks(refresh)
            refreshPending = false
            val service = instance
            if (service != null) service.refreshNotification() else publishCards(context, completedOnly = true)
        }

        @JvmStatic fun publishRunCompletion(context: Context, runId: String, conversationId: String, status: String, text: String) = onMain {
            val previous = cards[runId] ?: return@onMain
            if (previous.completed) return@onMain // Runtime's terminal event already updated the original card.
            completeTask(context, previous.copy(conversationId = conversationId,
                status = if (status == "已完成") AgentOverlayStatus.ResultReady else AgentOverlayStatus.RunFailed,
                streamPreview = text.takeLast(240)))
        }

        private fun scheduleRefresh() {
            if (instance?.foregroundActive != true || refreshPending) return
            refreshPending = true
            mainHandler.postDelayed(refresh, 750)
        }

        private fun publishCards(context: Context, exceptId: Int = 0, completedOnly: Boolean = false) {
            ensureChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            cards.values.forEach { task ->
                val id = idFor(task.runId)
                if (id != exceptId && (!completedOnly || task.completed) && posted[id] != task) {
                    manager.notify(id, notification(context, task, id))
                    posted[id] = task
                }
            }
        }

        internal fun usesSystemTaskSurface(): Boolean = Build.VERSION.SDK_INT >= 36
        internal fun taskNotificationsEnabled(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() &&
                manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        }

        private fun conversationIntent(context: Context, conversationId: String?, compose: Boolean = false): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            conversationId?.takeIf(String::isNotBlank)?.let {
                intent.action = MainActivity.ACTION_OPEN_CONVERSATION_ID
                intent.data = Uri.Builder().scheme("eta-task").authority(if (compose) "continue" else "conversation").appendPath(it).build()
                intent.putExtra(MainActivity.EXTRA_CONVERSATION_ID, it)
                intent.putExtra(MainActivity.EXTRA_COMPOSE_REPLY, compose)
            }
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun destination(context: Context, task: TaskNotificationState?, id: Int): PendingIntent {
            if (task?.completed == true) {
                if (!task.targetPackage.isNullOrBlank()) return PendingIntent.getActivity(context, id,
                    Intent(context, CompletedAppActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .setData(Uri.Builder().scheme("eta-task").authority("open").appendPath(task.runId).build())
                        .putExtra(CompletedAppActivity.EXTRA_RUN_ID, task.runId)
                        .putExtra(CompletedAppActivity.EXTRA_PACKAGE, task.targetPackage),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            return conversationIntent(context, task?.conversationId)
        }

        private fun notification(context: Context, task: TaskNotificationState?, id: Int): Notification {
            val status = task?.status
            val completed = task?.completed == true
            val progressing = !completed && task?.paused != true && status != AgentOverlayStatus.Queued
            val label = if (completed && status == AgentOverlayStatus.ResultReady) context.getString(R.string.background_task_completed)
                else status?.localizedText(context.resources)
                    ?: context.resources.getQuantityString(R.plurals.execution_summary, leases.count(), leases.count())
            val builder = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(task?.title?.take(60)?.takeIf(String::isNotBlank) ?: context.getString(R.string.execution_title))
                .setContentText(label + task?.streamPreview?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty())
                .setContentIntent(destination(context, task, id))
                // Keep the continue action available, including after an unsuccessful App handoff.
                .setOngoing(!completed).setAutoCancel(completed && task?.targetPackage.isNullOrBlank()).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
            if (task != null && !completed) {
                builder.addAction(runAction(context, task.runId,
                    if (task.paused) ACTION_RESUME_RUN else ACTION_PAUSE_RUN,
                    if (task.paused) R.string.overlay_resume else R.string.overlay_pause,
                    if (task.paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause))
                builder.addAction(runAction(context, task.runId, ACTION_STOP_RUN, R.string.overlay_stop_task, R.drawable.ic_assistant_stop))
            } else if (completed && !task?.conversationId.isNullOrBlank()) {
                val replyIntent = Intent(context, AgentExecutionService::class.java).setAction(ACTION_REPLY)
                    .setData(Uri.Builder().scheme("eta-task").authority("reply").appendPath(task!!.runId).build())
                    .putExtra(EXTRA_RUN_ID, task.runId).putExtra(EXTRA_CONVERSATION_ID, task.conversationId)
                    .putExtra(EXTRA_NOTIFICATION_ID, id)
                val reply = PendingIntent.getForegroundService(context, 0, replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_edit),
                    context.getString(R.string.background_continue_instruction), reply)
                    .addRemoteInput(RemoteInput.Builder(REPLY_TEXT).setLabel(context.getString(R.string.background_continue_instruction)).build())
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).setAllowGeneratedReplies(false)
                    .setAuthenticationRequired(true).build())
                builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification),
                    context.getString(R.string.overlay_open_conversation), conversationIntent(context, task?.conversationId)).build())
            } else if (task == null) {
                val stop = PendingIntent.getService(context, 1, Intent(context, AgentExecutionService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(Notification.Action.Builder(null, context.getString(R.string.execution_stop), stop).build())
            }
            if (task != null && usesSystemTaskSurface()) {
                builder.setStyle(Notification.ProgressStyle().setProgressIndeterminate(progressing)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100))).setProgress(if (completed) 100 else 0))
                    .setShortCriticalText(if (completed) label.take(7) else shortText(context, task.status))
                if (progressing) builder.extras.putBoolean("android.requestPromotedOngoing", true)
            }
            return builder.build()
        }

        private fun runAction(context: Context, runId: String, action: String, label: Int, icon: Int): Notification.Action {
            val intent = Intent(context, AgentExecutionService::class.java).setAction(action)
                .setData(Uri.Builder().scheme("eta-task").authority("control").appendPath(runId).appendPath(action).build())
                .putExtra(EXTRA_RUN_ID, runId)
            val pending = PendingIntent.getService(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return Notification.Action.Builder(Icon.createWithResource(context, icon), context.getString(label), pending).build()
        }

        private fun shortText(context: Context, status: AgentOverlayStatus): String = when (status) {
            AgentOverlayStatus.Queued -> context.getString(R.string.background_task_queued).take(7)
            AgentOverlayStatus.ScreenOffPaused -> context.getString(R.string.overlay_paused)
            is AgentOverlayStatus.BackgroundScreen, AgentOverlayStatus.WaitingForModel -> status.localizedText(context.resources).take(7)
            is AgentOverlayStatus.RunningTool, is AgentOverlayStatus.HostedToolRunning -> context.getString(R.string.execution_chip_running)
            AgentOverlayStatus.Paused -> context.getString(R.string.overlay_paused)
            AgentOverlayStatus.Stopping -> context.getString(R.string.overlay_stopping)
            AgentOverlayStatus.GeneratingAnswer -> context.getString(R.string.execution_chip_answering)
            else -> context.getString(R.string.overlay_reasoning)
        }

        fun acquire(context: Context, id: String, allowBoundFallback: Boolean = false, onStop: () -> Unit): Boolean {
            if (instance?.startRejected == true) return false
            val newBatch = leases.count() == 0
            if (!leases.acquire(id, allowBoundFallback, onStop)) return true
            if (newBatch) onMain {
                cards.values.filter { it.completed }.map { it.runId }.forEach { removeCard(context, it) }
            }
            return try {
                context.applicationContext.startForegroundService(Intent(context, AgentExecutionService::class.java))
                true
            } catch (failure: RuntimeException) {
                leases.release(id)
                AndroidAgentLogger.warn("Execution service start rejected: type=${failure.safeLogType()}")
                false
            }
        }

        fun release(id: String) {
            leases.release(id)
            mainHandler.post { instance?.refreshNotification() }
        }

        @JvmStatic fun pokeStreaming() { mainHandler.post { scheduleRefresh() } }
    }
}
