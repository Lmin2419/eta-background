package io.github.mangi.eta.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.delay

/**
 * 发出消息到模型吐出第一个字之间（连接、排队、长上下文预填充）列表尾部原本一片空白。
 * 这一行只在该轮还没有任何助手输出时出现，第一个思考、正文或工具事件到达即被替换。
 */
internal enum class PendingReplyPhase { Waiting, Counting, SlowStart }

internal data class PendingReplyLabel(val phase: PendingReplyPhase, val seconds: Long)

/** 3 秒内只说「正在等待回复」；之后显示秒数；20 秒起明确告诉用户是模型首字慢，而不是卡死。 */
internal fun pendingReplyLabel(elapsedMillis: Long): PendingReplyLabel {
    val seconds = (elapsedMillis / 1000).coerceAtLeast(0)
    val phase = when {
        seconds < PendingReplyShowSecondsAfter -> PendingReplyPhase.Waiting
        seconds < PendingReplySlowStartSeconds -> PendingReplyPhase.Counting
        else -> PendingReplyPhase.SlowStart
    }
    return PendingReplyLabel(phase, seconds)
}

/** 计时按用户消息 id 记在进程内：切会话、侧栏开合或列表回收后回来，秒数接着走而不是归零。 */
internal object PendingReplyClock {
    private const val MaxEntries = 32
    private val startedAt = LinkedHashMap<String, Long>()

    @Synchronized
    fun startedAt(messageId: String, now: Long): Long {
        startedAt[messageId]?.let { return it }
        startedAt[messageId] = now
        while (startedAt.size > MaxEntries) startedAt.remove(startedAt.keys.first())
        return now
    }
}

@Composable
internal fun PendingReplyIndicator(
    pendingMessageId: String,
    modifier: Modifier = Modifier,
    assistantOverlay: Boolean = false,
    nowElapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    val start = remember(pendingMessageId) { PendingReplyClock.startedAt(pendingMessageId, nowElapsedRealtime()) }
    var elapsed by remember(pendingMessageId) { mutableLongStateOf(nowElapsedRealtime() - start) }
    LaunchedEffect(pendingMessageId) {
        while (true) {
            elapsed = nowElapsedRealtime() - start
            // 对齐到下一个整秒，避免秒数在两帧之间跳两格。
            delay(1000 - elapsed.coerceAtLeast(0) % 1000)
        }
    }
    val label = pendingReplyLabel(elapsed)
    val announcement = stringResource(R.string.chat_waiting_reply)
    val text = when (label.phase) {
        PendingReplyPhase.Waiting -> announcement
        PendingReplyPhase.Counting -> stringResource(R.string.chat_waiting_reply_seconds, label.seconds)
        PendingReplyPhase.SlowStart -> stringResource(R.string.chat_waiting_reply_slow, label.seconds)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 与「工作过程」行同一套边距，状态圈和下一轮的工作过程上下对齐。
            .padding(horizontal = if (assistantOverlay) 12.dp else 16.dp, vertical = if (assistantOverlay) 2.dp else 0.dp)
            .heightIn(min = WorkRowHeight)
            .testTag(PendingReplyTestTag)
            // 读屏只在出现时读一次固定文案，不随秒数每秒播报。
            .clearAndSetSemantics {
                contentDescription = announcement
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(WorkGutterWidth), contentAlignment = Alignment.Center) {
            EtaThinkingMark(size = 18.dp)
        }
        Spacer(modifier = Modifier.width(WorkGutterGap))
        ShimmerText(
            text = text,
            active = true,
            style = EtaTheme.type.footnote,
            color = EtaTheme.colors.textSecondary,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

internal const val PendingReplyTestTag = "pending_reply"
internal const val PendingReplyShowSecondsAfter = 3L
internal const val PendingReplySlowStartSeconds = 20L

internal const val ChatPendingReplyKey = "agent-chat-pending-reply"

/**
 * 正在运行、且时间线最后一项是用户消息（本轮还没有思考、正文或工具输出）时返回该消息 id。
 * 工具调用之后的下一轮等待由「工作过程」行的运行态承担，不重复显示。
 */
internal fun pendingReplyMessageId(entries: List<AgentTimelineEntry>, isStreaming: Boolean): String? {
    if (!isStreaming) return null
    val tail = (entries.lastOrNull() as? AgentTimelineEntry.Message)?.message
    return (tail as? io.github.mangi.eta.ui.model.UserMessageUi)?.id
}
