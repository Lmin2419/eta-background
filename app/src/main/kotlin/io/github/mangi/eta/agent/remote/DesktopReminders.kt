package io.github.mangi.eta.agent.remote

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 电脑会话的提醒：一轮完成、需要审批、Agent 提问、出错。
 *
 * 只提醒 [DesktopLinkPrefs.reminds] 的会话（默认是在手机上发过消息的），正在手机上看的会话不提醒。
 * 用自己的通知类别，和胖小胖本机任务的完成横幅互不影响。
 */
internal class DesktopReminders(private val context: Context, private val scope: CoroutineScope) {
    private val jobs = ArrayList<Job>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val shownApprovals = HashSet<String>()
    private val shownQuestions = HashSet<String>()

    fun start() {
        jobs += scope.launch { DesktopBridge.turns.collect(::onTurn) }
        jobs += scope.launch { DesktopBridge.errors.collect { (sessionId, message) -> onError(sessionId, message) } }
        jobs += scope.launch { DesktopBridge.approvals.collect(::onApprovals) }
        jobs += scope.launch { DesktopBridge.questions.collect(::onQuestions) }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private val prefs get() = DesktopLinkSettings.state.value

    /** 正在手机上看着的会话不打扰。 */
    private fun watching(sessionId: String): Boolean = MainActivity.isInForeground && DesktopBridge.isFollowing(sessionId)

    private fun wants(sessionId: String): Boolean = prefs.enabled && prefs.reminds(sessionId) && !watching(sessionId)

    private fun titleOf(sessionId: String): String =
        DesktopBridge.sessions.value.rows.firstOrNull { it.id == sessionId }?.title?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.desktop_untitled)

    private fun onTurn(turn: DesktopTurnEvent) {
        if (!wants(turn.sessionId)) return
        when (turn.reason) {
            "completed" -> if (prefs.remindCompleted) post(
                id = notificationId("turn", turn.sessionId),
                sessionId = turn.sessionId,
                title = context.getString(R.string.desktop_remind_done, titleOf(turn.sessionId)),
                text = turn.text.ifBlank { context.getString(R.string.desktop_remind_done_empty) },
                reply = true,
            )
            "aborted", "cancelled" -> Unit
            else -> if (prefs.remindError) post(
                id = notificationId("turn", turn.sessionId),
                sessionId = turn.sessionId,
                title = context.getString(R.string.desktop_remind_failed, titleOf(turn.sessionId)),
                text = turn.message ?: turn.text.ifBlank { turn.reason },
                reply = true,
            )
        }
    }

    private fun onError(sessionId: String, message: String) {
        if (!prefs.remindError || !wants(sessionId)) return
        post(notificationId("error", sessionId), sessionId, context.getString(R.string.desktop_remind_failed, titleOf(sessionId)), message, reply = false)
    }

    private fun onApprovals(approvals: List<DesktopApproval>) {
        val current = approvals.mapTo(HashSet()) { it.id }
        for (id in shownApprovals - current) manager.cancel(notificationId("approval", id))
        shownApprovals.retainAll(current)
        if (!prefs.remindApproval) return
        for (approval in approvals) {
            if (approval.id in shownApprovals || !wants(approval.sessionId)) continue
            shownApprovals += approval.id
            val id = notificationId("approval", approval.id)
            val builder = base(approval.sessionId, id)
                .setContentTitle(context.getString(R.string.desktop_remind_approval, titleOf(approval.sessionId)))
                .setContentText(listOfNotNull(approval.toolName, approval.reason).joinToString(" · "))
                .setStyle(Notification.BigTextStyle().bigText(listOfNotNull(approval.toolName, approval.reason).joinToString("\n")))
                .addAction(action(R.string.desktop_approval_allow, DesktopReminderReceiver.ACTION_ALLOW, approval.sessionId, id) {
                    putExtra(DesktopReminderReceiver.EXTRA_APPROVAL_ID, approval.id)
                })
                .addAction(action(R.string.desktop_approval_reject, DesktopReminderReceiver.ACTION_REJECT, approval.sessionId, id) {
                    putExtra(DesktopReminderReceiver.EXTRA_APPROVAL_ID, approval.id)
                })
            manager.notify(id, builder.build())
        }
    }

    private fun onQuestions(questions: List<DesktopQuestion>) {
        val current = questions.mapTo(HashSet()) { it.id }
        for (id in shownQuestions - current) manager.cancel(notificationId("question", id))
        shownQuestions.retainAll(current)
        if (!prefs.remindQuestion) return
        for (question in questions) {
            if (question.id in shownQuestions || !wants(question.sessionId)) continue
            shownQuestions += question.id
            val id = notificationId("question", question.id)
            val title = context.getString(
                if (question.isPlanReview) R.string.desktop_remind_plan else R.string.desktop_remind_question,
                titleOf(question.sessionId),
            )
            val text = question.items.joinToString("\n") { it.question }
            manager.notify(id, base(question.sessionId, id).setContentTitle(title).setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text)).build())
        }
    }

    private fun post(id: Int, sessionId: String, title: String, text: String, reply: Boolean) {
        val builder = base(sessionId, id)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
        if (reply) {
            val intent = receiverIntent(DesktopReminderReceiver.ACTION_REPLY, sessionId, id)
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_edit),
                context.getString(R.string.desktop_remind_reply), pending)
                .addRemoteInput(RemoteInput.Builder(DesktopReminderReceiver.REPLY_TEXT).setLabel(context.getString(R.string.desktop_remind_reply_hint)).build())
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                .setAllowGeneratedReplies(false)
                .setAuthenticationRequired(true)
                .build())
        }
        manager.notify(id, builder.build())
    }

    private fun base(sessionId: String, id: Int): Notification.Builder {
        val channel = if (prefs.reminderSound) DesktopLinkService.CHANNEL_REMINDERS else DesktopLinkService.CHANNEL_REMINDERS_SILENT
        return Notification.Builder(context, channel)
            .setSmallIcon(io.github.mangi.eta.R.drawable.ic_notification)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setGroup(GROUP)
            .setContentIntent(DesktopLinkNav.openIntent(context, sessionId, id))
            .setSubText(context.getString(R.string.desktop_link_subtext))
    }

    private fun receiverIntent(action: String, sessionId: String, notificationId: Int): Intent =
        Intent(context, DesktopReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.Builder().scheme("eta-desktop").authority(action.substringAfterLast('.')).appendPath(notificationId.toString()).build())
            .putExtra(DesktopReminderReceiver.EXTRA_SESSION_ID, sessionId)
            .putExtra(DesktopReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)

    private fun action(label: Int, action: String, sessionId: String, notificationId: Int, extras: Intent.() -> Unit): Notification.Action {
        val intent = receiverIntent(action, sessionId, notificationId).apply(extras)
        val pending = PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(null, context.getString(label), pending).build()
    }

    companion object {
        private const val GROUP = "desktop_reminders"

        /** 同一会话的完成提醒覆盖上一条，审批和提问各占一条。 */
        fun notificationId(kind: String, key: String): Int = ("desktop:$kind:$key").hashCode() and 0x3FFFFFFF or 0x40000000
    }
}

/** 通知上的回复、允许、拒绝：经常驻连接直接发给电脑。 */
internal class DesktopReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val manager = context.getSystemService(NotificationManager::class.java)
        val pending = goAsync()
        DesktopBridge.acquire(context)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { DesktopBridge.client.awaitConnected(CONNECT_TIMEOUT_MS) } == true
                if (!connected) {
                    notifyResult(context, manager, notificationId, sessionId, context.getString(R.string.desktop_remind_offline))
                    return@launch
                }
                when (intent.action) {
                    ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY_TEXT)?.toString()?.trim().orEmpty()
                        if (text.isEmpty()) return@launch
                        DesktopBridge.prompt(sessionId, text)
                        notifyResult(context, manager, notificationId, sessionId, context.getString(R.string.desktop_remind_sent, text))
                    }
                    ACTION_ALLOW, ACTION_REJECT -> {
                        val approvalId = intent.getStringExtra(EXTRA_APPROVAL_ID) ?: return@launch
                        DesktopBridge.answerApproval(approvalId, allow = intent.action == ACTION_ALLOW)
                        manager.cancel(notificationId)
                    }
                }
            } catch (error: DesktopBridgeException) {
                notifyResult(context, manager, notificationId, sessionId, error.message ?: error.code)
            } finally {
                DesktopBridge.release()
                pending.finish()
            }
        }
    }

    private fun notifyResult(context: Context, manager: NotificationManager, id: Int, sessionId: String, text: String) {
        val channel = DesktopLinkService.CHANNEL_REMINDERS_SILENT
        manager.notify(id, Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_TIMEOUT_MS)
            .setContentIntent(DesktopLinkNav.openIntent(context, sessionId, id))
            .build())
    }

    companion object {
        const val ACTION_REPLY = "io.github.mangi.eta.desktop.REPLY"
        const val ACTION_ALLOW = "io.github.mangi.eta.desktop.ALLOW"
        const val ACTION_REJECT = "io.github.mangi.eta.desktop.REJECT"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_APPROVAL_ID = "approval_id"
        const val REPLY_TEXT = "desktop_reply"
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val RESULT_TIMEOUT_MS = 6_000L
    }
}
