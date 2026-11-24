package io.github.mangi.eta.agent.runtime

import androidx.compose.ui.graphics.toArgb
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.app.Service
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.overlay.localizedText
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val runtimeAuthoritative: Boolean = false,
    val replyFailed: Boolean = false,
    // Presentation timestamps populated only in taskSnapshots; never stored in cards or sent to runtime.
    val firstPublishedAtElapsedMs: Long? = null,
    val completedAtElapsedMs: Long? = null,
    val completedAtMillis: Long? = null,
    val step: Int? = null, // Read-only display step; never drives runtime progress.
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
            if (task != null) checkLiveUpdate(this, task, id)
            // Refresh the detached card in place with its final state.
            if (oldId != id) {
                val oldPosted = posted.remove(oldId)
                val oldOwner = cards.values.firstOrNull { notificationIds[it.runId] == oldId }
                // 没有卡片认领的旧编号直接撤掉；认领它的卡片已经完成而旧通知还是实况（流体云）时也先撤掉，
                // 由下面的 publishCards 重新发一条普通的「已完成」（见 [mustRepostFresh]）。
                if (oldId != 0 && (oldOwner == null || mustRepostFresh(oldPosted, oldOwner))) {
                    getSystemService(NotificationManager::class.java).cancel(oldId)
                }
            }
        }
        publishCards(this, exceptId = id)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REPLY) {
            receiveReply(intent)
        } else if (intent?.action in RUN_ACTIONS) {
            intent?.getStringExtra(EXTRA_RUN_ID)?.let { runId ->
                // 运行早已结束（或运行时服务已退出）却还挂着的卡片：暂停、停止都不会有人处理，
                // 以前就一直卡在「运行中」。这种情况直接收掉这张卡片。
                if (AgentRuntimeService.hasActiveRun(runId)) {
                    AgentRuntimeService.dispatchNotificationAction(intent.action.orEmpty(), runId)
                } else retireStaleCard(this, runId, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
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
        // A run acquired after stopSelf belongs to the next service, just like its surviving lease.
        if (current) cards.values.filter { !it.completed && !leases.contains("run:${it.runId}") }
            .map { it.runId }.forEach { removeCard(this, it) }
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
        cards[runId] = original.copy(replyFailed = false)
        publishTaskSnapshots()
        if (runId !in notificationIds) notificationIds[runId] = intent.getIntExtra(EXTRA_NOTIFICATION_ID, idFor(runId))
        val leaseId = "notification-reply:$runId"
        val connection = NotificationReplyConnection(this, runId, conversationId, text) { accepted ->
            replyConnections.remove(runId)
            if (accepted) {
                repliedRuns.add(runId)
                while (repliedRuns.size > 32) repliedRuns.remove(repliedRuns.first())
                removeCard(this, runId)
            } else {
                // Reply admission can fail independently of the completed task. Use the latest
                // result and leave a dismissed or reclaimed notification dismissed.
                cards[runId]?.takeIf { it.completed && it.conversationId == conversationId }?.let {
                    cards[runId] = it.copy(replyFailed = true)
                    publishTaskSnapshots()
                    if (instance == null) publishCards(this, completedOnly = true)
                }
            }
            release(leaseId)
        }
        replyConnections[runId] = connection
        // A notification reply is a user gesture. Hold the existing FGS only until Runtime admits it.
        leases.acquire(leaseId, allowBoundFallback = true, onStop = { mainHandler.post { connection.close() } })
        if (original.replyFailed) refreshNotification()
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
                // 前台这条通知现在属于一张已完成的卡就留着；没有卡片认领它（任务已结束、卡片被撤掉），
                // 留下来就成了永远停在「生成回答中」的陈旧卡片，所以连通知一起撤掉。
                // 认领它的卡片已完成、但前台这条还是实况（流体云）时也撤掉，随后 publishCards 重新发「已完成」：
                // ColorOS 不会收起被原地改回普通的实况通知，旧的「生成回答中」会一直挂着，要手动点停止。
                val owner = cards.values.firstOrNull { notificationIds[it.runId] == foregroundId }
                val keep = owner?.completed == true && !mustRepostFresh(posted[foregroundId], owner)
                stopForeground(if (keep) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
                posted.remove(foregroundId)
            }
            publishCards(this, completedOnly = true)
            stopSelf()
        } else postForegroundNotification()
    }

    companion object {
        private const val CHANNEL = "eta_execution_tasks_v2"
        internal const val COMPLETION_CHANNEL = "eta_task_completion_v1"
        private const val NOTIFICATION_ID = 1107
        private const val ACTION_STOP = "io.github.mangi.eta.action.STOP_USER_EXECUTION"
        private const val ACTION_DISMISS_COMPLETED = "io.github.mangi.etax.action.DISMISS_COMPLETED_TASK"
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
        private val mutableTaskSnapshots = MutableStateFlow<List<TaskNotificationState>>(emptyList())
        /** Immutable, main-thread publication. UI observes; notification/runtime ownership is unchanged. */
        internal val taskSnapshots: StateFlow<List<TaskNotificationState>> = mutableTaskSnapshots.asStateFlow()
        private fun publishTaskSnapshots() {
            val previous = mutableTaskSnapshots.value.associateBy { it.runId }
            val elapsed = SystemClock.elapsedRealtime()
            val wallTime = System.currentTimeMillis()
            mutableTaskSnapshots.value = cards.values.map { card ->
                val old = previous[card.runId]
                card.copy(
                    firstPublishedAtElapsedMs = old?.firstPublishedAtElapsedMs ?: elapsed.takeUnless { card.completed },
                    completedAtElapsedMs = if (card.completed) old?.completedAtElapsedMs ?: elapsed else null,
                    completedAtMillis = if (card.completed) old?.completedAtMillis ?: wallTime else null,
                )
            }
        }
        private val notificationIds = mutableMapOf<String, Int>()
        /** 运行卡片被撤掉时的编号，留给紧随其后的「已完成」原地更新（见 [idFor]）。 */
        private val tombstones = linkedMapOf<String, Int>()
        private val posted = mutableMapOf<Int, TaskNotificationState>()
        private var phoneIcon: Icon? = null

        /** Send transparent pixels, not a vendor-reinterpreted layered vector. */
        @Synchronized internal fun taskPhoneIcon(context: Context): Icon = phoneIcon ?: run {
            val bitmap = Bitmap.createBitmap(96, 144, Bitmap.Config.ARGB_8888)
            val drawable = context.getDrawable(R.drawable.ic_task_phone)!!
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            Icon.createWithBitmap(bitmap).also { phoneIcon = it }
        }
        private val repliedRuns = linkedSetOf<String>()
        // Finite jobs may outlive the FGS: its last lease is normally released at completion.
        private val completionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val completionJobs = mutableMapOf<String, Job>()
        private val completionAlertCards = mutableSetOf<String>()
        private val completedRuns = linkedSetOf<String>()
        private var nextId = 0x45540000
        private var refreshPending = false
        private val refresh = Runnable {
            refreshPending = false
            instance?.takeIf { it.foregroundActive }?.refreshNotification()
        }

        private fun onMain(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
        }

        /**
         * 任务卡片的编号。任务结束时会有先后顺序的竞态：任务列表先把卡片撤掉，「已完成」随后才到。
         * 撤掉时记下编号（[tombstones]），「已完成」回来就落在同一条通知上原地更新，
         * 不会另开一条、把旧的「生成回答中」留在通知栏里。
         */
        private fun idFor(runId: String): Int = notificationIds.getOrPut(runId) {
            tombstones.remove(runId)
                ?: if (notificationIds.isEmpty() && NOTIFICATION_ID !in tombstones.values) NOTIFICATION_ID else nextId++
        }

        /** 每个任务只查一次实况提升结果，避免诊断记录刷屏。 */
        private val liveUpdateChecked = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        /**
         * 运行中的卡片发出约 1 秒后看一眼系统有没有把它提升成实况通知（流体云），记进诊断：
         * 申请了没有、卡片本身合不合格（Android 16 的 hasPromotableCharacteristics）、系统实际提升了没有、
         * 系统设置里是否允许本应用发实况通知。只在 Android 16 起、每个任务第一次运行中时记一次。
         */
        private fun checkLiveUpdate(context: Context, task: TaskNotificationState, id: Int) {
            if (!usesSystemTaskSurface() || !wasLive(task) || !liveUpdateChecked.add(task.runId)) return
            val app = context.applicationContext
            mainHandler.postDelayed({
                runCatching {
                    val info = liveUpdateState(app, id) ?: "id=$id not_active"
                    RuntimeDiagnostics.record("live_update", info)
                    io.github.mangi.eta.core.AndroidAgentLogger.info("Live update check: $info")
                }
            }, 1_000L)
        }

        /** 当前这条通知的实况状态（诊断用）；通知已不在时返回 null。 */
        internal fun liveUpdateState(context: Context, id: Int): String? {
            if (Build.VERSION.SDK_INT < 36) return null
            val manager = context.getSystemService(NotificationManager::class.java)
            val active = manager.activeNotifications.firstOrNull { it.id == id } ?: return null
            val n = active.notification
            val requested = n.extras.getBoolean("android.requestPromotedOngoing", false)
            val promotable = runCatching { n.hasPromotableCharacteristics() }.getOrNull()
            val promoted = n.flags and Notification.FLAG_PROMOTED_ONGOING != 0
            val allowed = runCatching { manager.canPostPromotedNotifications() }.getOrNull()
            val importance = manager.getNotificationChannel(n.channelId)?.importance
            return "id=$id requested=$requested promotable=$promotable promoted=$promoted allowed=$allowed " +
                "ongoing=${n.flags and Notification.FLAG_ONGOING_EVENT != 0} fgs=${n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0} " +
                "channel=${n.channelId} importance=$importance"
        }

        /** 这条通知上次发出时是不是实况卡片（Android 16 起请求提升为流体云 / 实况通知）。 */
        internal fun wasLive(posted: TaskNotificationState?): Boolean =
            posted != null && !posted.completed && !posted.paused && posted.status != AgentOverlayStatus.Queued

        /**
         * 同一条通知从实况（运行中）变成已完成时，要先撤掉再重新发，而不是原地更新：
         * ColorOS 不会收起被原地改回普通的实况通知，流体云和通知栏里会一直留着旧的「运行中」，
         * 只有手动点「停止」才消失（2026111620 修过一次，后来为了让「已完成」落回同一条通知改回了原地更新）。
         * 编号不变，所以一个任务仍然只有一条通知。
         */
        internal fun mustRepostFresh(previous: TaskNotificationState?, next: TaskNotificationState): Boolean =
            next.completed && wasLive(previous)

        /** 通知上点了暂停 / 停止，但运行时已经没有这个任务：收掉它的卡片和那条通知。 */
        private fun retireStaleCard(context: Context, runId: String, notificationId: Int) {
            if (cards[runId]?.completed == false) removeCard(context, runId)
            val service = instance
            if (notificationId != 0 && notificationId !in notificationIds.values &&
                (service == null || !service.foregroundActive || service.foregroundId != notificationId)) {
                context.getSystemService(NotificationManager::class.java).cancel(notificationId)
                posted.remove(notificationId)
            }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.execution_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null); enableVibration(false); setShowBadge(false)
                })
            // Android does not allow raising an existing channel's importance on upgrade.
            // A separate channel changes alert policy, never the task's notification identity.
            manager.createNotificationChannel(
                NotificationChannel(COMPLETION_CHANNEL, context.getString(R.string.task_completion_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false); setShowBadge(false)
                })
        }

        private fun removeCard(context: Context, runId: String, tombstone: Boolean = false) {
            completionJobs.remove(runId)?.cancel()
            completionAlertCards.remove(runId)
            cards.remove(runId)
            publishTaskSnapshots()
            notificationIds.remove(runId)?.let { id ->
                context.getSystemService(NotificationManager::class.java).cancel(id)
                posted.remove(id)
                if (tombstone) {
                    tombstones[runId] = id
                    while (tombstones.size > 16) tombstones.remove(tombstones.keys.first())
                }
            }
        }

        /** Ordinary streaming updates share one throttle. Completed cards are retained unchanged. */
        fun updateTasks(context: Context, snapshot: List<TaskNotificationState>) = onMain {
            val liveIds = snapshot.map { it.runId }.toSet()
            val removed = cards.values.filter { !it.completed && it.runId !in liveIds }.map { it.runId }
            // 「已完成」可能紧跟着到：记下编号，让它回到同一条通知上。
            removed.forEach { removeCard(context, it, tombstone = true) }
            var changed = removed.isNotEmpty()
            snapshot.forEach { task ->
                if (task.runId !in completedRuns && cards[task.runId]?.completed != true && cards[task.runId] != task) {
                    cards[task.runId] = task
                    idFor(task.runId)
                    changed = true
                }
            }
            if (changed) publishTaskSnapshots()
            if (changed || snapshot.isEmpty()) scheduleRefresh()
        }

        /** Terminal events bypass streaming throttles and update the original task card immediately. */
        fun completeTask(context: Context, task: TaskNotificationState) = onMain {
            val previous = cards[task.runId]
            // Late terminal callbacks must not recreate a dismissed, replied-to or reclaimed card.
            if (previous == null && task.runId in completedRuns) return@onMain
            if (previous?.completed == true && previous.status == task.status && previous.targetPackage == task.targetPackage &&
                previous.streamPreview == task.streamPreview) return@onMain
            val firstCompletion = completedRuns.add(task.runId)
            while (completedRuns.size > 64) completedRuns.remove(completedRuns.first())
            val replyFailed = previous?.takeIf { it.conversationId == task.conversationId }?.replyFailed == true
            cards[task.runId] = task.copy(completed = true, paused = false, replyFailed = replyFailed)
            idFor(task.runId)
            cards.values.filter { it.completed }.dropLast(3).map { it.runId }.forEach { removeCard(context, it) }
            publishTaskSnapshots()
            mainHandler.removeCallbacks(refresh)
            refreshPending = false
            val service = instance
            if (service != null) service.refreshNotification() else publishCards(context, completedOnly = true)
            if (firstCompletion) {
                // 侧栏与首页的「完成未读」圆点（提示条只停 6 秒，错过了靠它找回来）。不受「完成提示」开关影响。
                cards[task.runId]?.let(FinishedUnseen::record)
                scheduleCompletionAlert(context.applicationContext, task.runId)
            }
        }

        internal fun onNotificationDismissed(context: Context, intent: Intent) = onMain {
            if (intent.action != ACTION_DISMISS_COMPLETED) return@onMain
            val runId = intent.getStringExtra(EXTRA_RUN_ID) ?: return@onMain
            val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            if (cards[runId]?.completed == true && notificationIds[runId] == id) removeCard(context, runId)
        }

        /** Request one native heads-up update, leaving animation, duration and dismissal to System UI. */
        private fun scheduleCompletionAlert(context: Context, runId: String) {
            val requestedAt = SystemClock.uptimeMillis()
            val job = completionScope.launch(start = CoroutineStart.LAZY) {
                try {
                    // Coalesce RunFinished and its final archive/result update without delaying the card itself.
                    delay(120)
                    val enabled = withTimeoutOrNull(1_500) {
                        AppearanceSettingsRepository.settings().taskCompletionAlertsEnabled
                    } == true
                    if (!enabled || SystemClock.uptimeMillis() - requestedAt > 2_000) return@launch
                    if (!context.getSystemService(PowerManager::class.java).isInteractive ||
                        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.state != Display.STATE_ON ||
                        context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@launch
                    val task = cards[runId]?.takeIf { it.completed } ?: return@launch
                    // 用户正停在这个对话上：回复就在眼前，不再弹完成横幅（卡片照常留在状态栏）。
                    if (ForegroundConversation.isShowing(task.conversationId)) return@launch
                    // 停在首页：首页顶部已经弹出「已完成 · 查看」提示条（HomeDoneNotice），不重复弹横幅。
                    if (ForegroundConversation.homeVisible && task.conversationId != null) return@launch
                    val id = notificationIds[runId] ?: return@launch
                    val manager = context.getSystemService(NotificationManager::class.java)
                    if (!taskNotificationsEnabled(context) ||
                        (manager.getNotificationChannel(COMPLETION_CHANNEL)?.importance ?: 0) < NotificationManager.IMPORTANCE_HIGH) return@launch
                    // Do not resurrect a card the user dismissed while the preference was loading.
                    if (manager.activeNotifications.none { it.id == id && it.tag == null }) return@launch
                    completionAlertCards.add(runId)
                    manager.notify(id, notification(context, task, id, alert = true))
                    posted[id] = task
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    // The original completed card is already present; alert failure must not affect it.
                    completionAlertCards.remove(runId)
                    AndroidAgentLogger.warn("Completion alert unavailable: type=${failure.safeLogType()}")
                }
            }
            completionJobs[runId] = job
            job.invokeOnCompletion { onMain { if (completionJobs[runId] === job) completionJobs.remove(runId) } }
            job.start()
        }

        @JvmStatic fun publishRunCompletion(context: Context, runId: String, conversationId: String, status: String, text: String) = onMain {
            val previous = cards[runId] ?: return@onMain
            if (previous.completed || previous.runtimeAuthoritative) return@onMain // Only the session terminal commit completes runtime cards.
            completeTask(context, previous.copy(conversationId = conversationId,
                status = if (status == "已完成") AgentOverlayStatus.ResultReady else AgentOverlayStatus.RunFailed,
                streamPreview = text.notificationFirstSentence()))
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
                    if (mustRepostFresh(posted[id], task)) manager.cancel(id)
                    manager.notify(id, notification(context, task, id))
                    posted[id] = task
                    checkLiveUpdate(context, task, id)
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

        internal fun task(runId: String): TaskNotificationState? = cards[runId]

        private fun notification(context: Context, task: TaskNotificationState?, id: Int, alert: Boolean = false): Notification {
            val status = task?.status
            val completed = task?.completed == true
            val progressing = !completed && task?.paused != true && status != AgentOverlayStatus.Queued
            val label = if (completed && status == AgentOverlayStatus.ResultReady) context.getString(R.string.background_task_completed)
                else status?.localizedText(context.resources)
                    ?: context.resources.getQuantityString(R.plurals.execution_summary, leases.count(), leases.count())
            val hasCompletionAlert = completed && task.runId in completionAlertCards
            val completionLabel = if (!hasCompletionAlert) null else when (status) {
                AgentOverlayStatus.ResultReady -> context.getString(R.string.task_completion_notice_success)
                AgentOverlayStatus.Stopped -> context.getString(R.string.task_completion_notice_stopped)
                else -> context.getString(R.string.task_completion_notice_failed)
            }
            val snapshot = task?.let { card -> taskSnapshots.value.firstOrNull { it.runId == card.runId } }
            val elapsed = snapshot?.completedAtElapsedMs?.let { end ->
                snapshot.firstPublishedAtElapsedMs?.let { start -> (end - start).coerceAtLeast(0L) }
            }
            val appName = task?.targetPackage?.let { pkg -> runCatching {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull() }
            val copy = task?.let { taskNotificationCopy(context.resources, it, appName, it.step, elapsed) }
            val builder = Notification.Builder(context, if (hasCompletionAlert) COMPLETION_CHANNEL else CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(io.github.mangi.eta.ui.theme.lightEtaColors().accent.toArgb())
                .setContentTitle(copy?.title?.takeIf(String::isNotBlank) ?: context.getString(R.string.execution_title))
                .setContentText(copy?.body ?: label)
                .setContentIntent(when {
                    completed && !task.conversationId.isNullOrBlank() -> conversationIntent(context, task.conversationId)
                    task?.targetPackage.isNullOrBlank() -> destination(context, task, id)
                    else -> TaskPreviewActivity.pendingIntent(context, task, id)
                })
                // Keep the continue action available, including after an unsuccessful App handoff.
                .setOngoing(!completed).setAutoCancel(completed && task.targetPackage.isNullOrBlank()).setOnlyAlertOnce(!alert)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
            if (!task?.targetPackage.isNullOrBlank()) builder.setLargeIcon(taskPhoneIcon(context))
            completionLabel?.let(builder::setSubText)
            if (completed) {
                val dismissed = Intent(context, TaskNotificationDismissReceiver::class.java).setAction(ACTION_DISMISS_COMPLETED)
                    .setData(Uri.Builder().scheme("eta-task").authority("dismiss").appendPath(task.runId).build())
                    .putExtra(EXTRA_RUN_ID, task.runId).putExtra(EXTRA_NOTIFICATION_ID, id)
                builder.setDeleteIntent(PendingIntent.getBroadcast(context, 0, dismissed,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            if (task != null && !completed) {
                // 「打开会话」始终回到胖小胖的会话页；后台虚拟屏预览仍可点通知正文打开。
                val openConversation = !task.conversationId.isNullOrBlank()
                builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification),
                    context.getString(if (openConversation) R.string.overlay_open_conversation else R.string.task_notice_view),
                    when {
                        openConversation -> conversationIntent(context, task.conversationId)
                        task.targetPackage.isNullOrBlank() -> destination(context, task, id)
                        else -> TaskPreviewActivity.pendingIntent(context, task, id)
                    }).build())
                builder.addAction(runAction(context, task.runId, id,
                    if (task.paused) ACTION_RESUME_RUN else ACTION_PAUSE_RUN,
                    if (task.paused) R.string.overlay_resume else R.string.overlay_pause,
                    if (task.paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause))
                builder.addAction(runAction(context, task.runId, id, ACTION_STOP_RUN, R.string.overlay_stop_task, R.drawable.ic_assistant_stop))
            } else if (completed) {
                if (!task.conversationId.isNullOrBlank()) {
                    builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification),
                        context.getString(R.string.overlay_open_conversation), conversationIntent(context, task.conversationId)).build())
                }
                if (!task.targetPackage.isNullOrBlank()) {
                    builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_view),
                        context.getString(R.string.background_open_app), destination(context, task, id)).build())
                }
                if (!task.conversationId.isNullOrBlank()) {
                    val replyIntent = Intent(context, AgentExecutionService::class.java).setAction(ACTION_REPLY)
                        .setData(Uri.Builder().scheme("eta-task").authority("reply").appendPath(task.runId).build())
                        .putExtra(EXTRA_RUN_ID, task.runId).putExtra(EXTRA_CONVERSATION_ID, task.conversationId)
                        .putExtra(EXTRA_NOTIFICATION_ID, id)
                    val reply = PendingIntent.getForegroundService(context, 0, replyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_edit),
                        context.getString(R.string.background_continue_instruction), reply)
                        .addRemoteInput(RemoteInput.Builder(REPLY_TEXT).setLabel(context.getString(R.string.background_continue_instruction)).build())
                        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).setAllowGeneratedReplies(false)
                        .setAuthenticationRequired(true).build())
                }
            } else if (task == null) {
                val stop = PendingIntent.getService(context, 1, Intent(context, AgentExecutionService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(Notification.Action.Builder(null, context.getString(R.string.execution_stop), stop).build())
            }
            if (task != null && usesSystemTaskSurface()) {
                builder.setStyle(Notification.ProgressStyle().setProgressIndeterminate(progressing)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(maxOf(100, (task.step ?: 0) + 1))))
                    .setProgress(if (completed) maxOf(100, (task.step ?: 0) + 1) else task.step?.coerceAtLeast(0) ?: 0))
                    .setShortCriticalText(shortText(context, task.status, completed))
                if (progressing) builder.extras.putBoolean("android.requestPromotedOngoing", true)
            }
            return builder.build()
        }

        private fun runAction(context: Context, runId: String, id: Int, action: String, label: Int, icon: Int): Notification.Action {
            val intent = Intent(context, AgentExecutionService::class.java).setAction(action)
                .setData(Uri.Builder().scheme("eta-task").authority("control").appendPath(runId).appendPath(action).build())
                .putExtra(EXTRA_RUN_ID, runId).putExtra(EXTRA_NOTIFICATION_ID, id)
            val pending = PendingIntent.getService(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return Notification.Action.Builder(Icon.createWithResource(context, icon), context.getString(label), pending).build()
        }

        private fun shortText(context: Context, status: AgentOverlayStatus, completed: Boolean): String = context.getString(
            if (completed) when (status) {
                AgentOverlayStatus.ResultReady -> R.string.task_short_done
                AgentOverlayStatus.Stopped -> R.string.task_short_stopped
                else -> R.string.task_short_failed
            } else when (status) {
                AgentOverlayStatus.Queued, AgentOverlayStatus.WaitingForGui -> R.string.task_short_queued
                AgentOverlayStatus.Preparing, AgentOverlayStatus.SupplementReceived, AgentOverlayStatus.Continuing,
                is AgentOverlayStatus.BackgroundScreen, is AgentOverlayStatus.RunningTool,
                is AgentOverlayStatus.HostedToolRunning -> R.string.task_short_running
                AgentOverlayStatus.Reasoning, AgentOverlayStatus.WaitingForModel -> R.string.task_short_thinking
                AgentOverlayStatus.GeneratingAnswer -> R.string.task_short_answering
                AgentOverlayStatus.Paused -> R.string.task_short_paused
                AgentOverlayStatus.PreviewPaused, AgentOverlayStatus.AppTakeoverPaused -> R.string.task_short_in_control
                AgentOverlayStatus.ContinuationUnavailable -> R.string.task_short_needs_you
                AgentOverlayStatus.ResultReady, AgentOverlayStatus.Stopping, AgentOverlayStatus.Finishing -> R.string.task_short_finishing
                AgentOverlayStatus.Stopped -> R.string.task_short_stopped
                AgentOverlayStatus.RunFailed -> R.string.task_short_failed
            })

        fun acquire(context: Context, id: String, allowBoundFallback: Boolean = false, onStop: () -> Unit): Boolean {
            if (instance?.startRejected == true) return false
            val newBatch = leases.count() == 0
            if (!leases.acquire(id, allowBoundFallback, onStop)) return true
            if (newBatch) onMain {
                // A stopped/finished background page still needs its in-chat preview entry.
                // Completed cards remain bounded by completeTask and can be explicitly dismissed.
                cards.values.filter { it.completed && it.targetPackage.isNullOrBlank() }
                    .map { it.runId }.forEach { removeCard(context, it) }
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
