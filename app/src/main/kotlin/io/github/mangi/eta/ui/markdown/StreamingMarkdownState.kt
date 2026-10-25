package io.github.mangi.eta.ui.markdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext

/** 列表按消息身份保留会话，条目重新挂载时接用已有解析和显现进度。 */
internal class StreamingMarkdownState {
    private var attachedContentCount by mutableIntStateOf(0)
    val isAttachedToContent: Boolean get() = attachedContentCount > 0
    var revealedContent by mutableStateOf<String?>(null)
    private val parserSession = StreamingGfmParserSession()
    val revealCoordinator = SmoothTextRevealCoordinator().apply { pauseAnimationsAndCatchUp() }
    val restoreState = StreamingMarkdownRestoreState()
    val parseTargets = Channel<StreamingMarkdownTarget>(Channel.CONFLATED)
    var snapshot by mutableStateOf<StreamingGfmSnapshot?>(null)

    fun attachContent() {
        attachedContentCount += 1
    }

    fun detachContent() {
        check(attachedContentCount > 0)
        attachedContentCount -= 1
    }

    suspend fun parseUpdates() = consumeStreamingMarkdownTargets(
        targets = parseTargets,
        parse = { target ->
            withContext(Dispatchers.Default) {
                parserSession.parse(target.content, isComplete = !target.isStreaming, style = target.style)
            }
        },
        publish = { snapshot = it },
    )
}

internal data class StreamingMarkdownTarget(
    val content: String,
    val isStreaming: Boolean,
    val style: MarkdownInlineStyle = MarkdownInlineStyle.Default,
)

internal suspend fun consumeStreamingMarkdownTargets(
    targets: ReceiveChannel<StreamingMarkdownTarget>,
    parse: suspend (StreamingMarkdownTarget) -> StreamingGfmSnapshot,
    publish: (StreamingGfmSnapshot) -> Unit,
) {
    var target = targets.receiveCatching().getOrNull() ?: return
    while (true) {
        while (true) {
            target = targets.tryReceive().getOrNull() ?: break
        }
        val parsed = parse(target)
        val newerTarget = targets.tryReceive().getOrNull()
        // 追加分片不会使已解析的前缀失效。若每次有新目标就丢弃结果，持续高速流会
        // 饿死显示端；只有上游纠正全文或重新打开终态时，才跳过不再适用的快照。
        if (newerTarget == null ||
            (newerTarget.content.startsWith(parsed.originalSource) &&
                (!parsed.isComplete || newerTarget == target))
        ) {
            publish(parsed)
        }
        target = newerTarget ?: targets.receiveCatching().getOrNull() ?: return
    }
}
