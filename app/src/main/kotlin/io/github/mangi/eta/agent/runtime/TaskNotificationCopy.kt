// runtime/TaskNotificationCopy.kt
package io.github.mangi.eta.agent.runtime

import android.content.res.Resources
import io.github.mangi.eta.R
import java.util.Locale
import io.github.mangi.eta.agent.overlay.localizedText
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.model.toTaskVisualStatus

internal data class TaskNotificationCopy(val title: String, val body: String)

private const val TITLE_MAX = 60
private const val PREVIEW_MAX = 100
private const val SEP = " · "

internal fun taskNotificationCopy(
    resources: Resources,
    task: TaskNotificationState,
    appName: String?,
    step: Int? = null,
    elapsedMs: Long? = null,
): TaskNotificationCopy = TaskNotificationCopy(
    title = buildTitle(task, appName),
    body = buildBody(resources, task, step, elapsedMs),
)

private fun buildTitle(task: TaskNotificationState, appName: String?): String {
    return listOfNotNull(appName?.trim()?.takeIf(String::isNotEmpty), task.title.trim().takeIf(String::isNotEmpty))
        .joinToString(SEP).truncateForTitle()
}

private fun String.truncateForTitle(): String =
    if (length <= TITLE_MAX) this else take(TITLE_MAX - 1).trimEnd { it.isHighSurrogate() } + "…"

private fun buildBody(
    resources: Resources,
    task: TaskNotificationState,
    step: Int?,
    elapsedMs: Long?,
): String {
    val status = task.toTaskVisualStatus()
    val reason = if (task.streamPreview.isNotBlank()) task.streamPreview.notificationFirstSentence() else ""
    val parts = mutableListOf<String>()

    when (status) {
        TaskVisualStatus.Running -> {
            // 保留 localizedText 的自然语法（可能已含"正在执行：X"），不叠加"正在"
            parts += resources.getString(
                R.string.task_notice_running,
                task.status.localizedText(resources),
            )
            if (step != null && step > 0) {
                parts += resources.getString(R.string.task_notice_step, step)
            }
        }
        TaskVisualStatus.Attention -> {
            parts += resources.getString(R.string.task_notice_attention)
            if (reason.isNotEmpty()) parts += reason
        }
        TaskVisualStatus.Paused -> {
            // Paused 的"原因"没有独立来源；streamPreview 不用于 Paused，避免编造
            parts += resources.getString(R.string.task_notice_paused)
            val pauseReason = task.status.localizedText(resources)
            if (pauseReason != resources.getString(R.string.overlay_paused)) parts += pauseReason
        }
        TaskVisualStatus.Success -> {
            parts += resources.getString(R.string.task_notice_success)
            if (elapsedMs != null && elapsedMs >= 0) {
                parts += resources.getString(
                    R.string.task_notice_duration,
                    formatDuration(elapsedMs),
                )
            }
            if (reason.isNotEmpty()) parts += reason
        }
        TaskVisualStatus.Failed -> {
            parts += resources.getString(R.string.task_notice_failed)
            if (reason.isNotEmpty()) parts += reason
        }
        TaskVisualStatus.Queued -> {
            parts += resources.getString(R.string.task_notice_queued)
        }
    }
    if (task.replyFailed) parts.add(1.coerceAtMost(parts.size), resources.getString(R.string.task_notice_reply_failed))
    return parts.joinToString(SEP)
}

private fun formatDuration(elapsedMs: Long): String {
    val totalSec = elapsedMs / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.ROOT, "%d:%02d", m, s)
}

internal fun String.notificationFirstSentence(): String {
    val firstLine = trim().substringBefore('\n').replace('\r', ' ').trim()
    if (firstLine.isEmpty()) return ""
    var endIdx = -1
    for (i in firstLine.indices) {
        if (isTerminator(firstLine[i]) && isReliableBreak(firstLine, i)) {
            endIdx = i + 1
            break
        }
    }
    val candidate = if (endIdx > 0) firstLine.substring(0, endIdx).trim() else firstLine
    return candidate.takePreview(PREVIEW_MAX)
}

private fun isTerminator(c: Char): Boolean =
    c == '。' || c == '！' || c == '？' || c == '；' || c == '…' ||
        c == '.' || c == '!' || c == '?' || c == ';'

private fun isReliableBreak(s: String, i: Int): Boolean = when (val c = s[i]) {
    '。', '！', '？', '；', '…' -> true
    '.', '!', '?', ';' -> {
        val next = s.getOrNull(i + 1)
        val prev = s.getOrNull(i - 1)
        val nextOk = next == null || next.isWhitespace()
        // 不截小数、不截域名/URL：. 后非空白即视为非句末
        val decimal = c == '.' && prev?.isDigit() == true && next?.isDigit() == true
        nextOk && !decimal
    }
    else -> false
}

private fun String.takePreview(max: Int): String =
    if (length <= max) this else take(max - 1).trimEnd() + "…"
