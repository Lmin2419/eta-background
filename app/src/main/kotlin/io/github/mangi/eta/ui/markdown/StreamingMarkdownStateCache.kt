package io.github.mangi.eta.ui.markdown

import androidx.compose.runtime.mutableStateMapOf

/** 流式状态只跨条目挂载保留；已结束且脱离组合的文档交给有界静态缓存。 */
internal class StreamingMarkdownStateCache {
    private val retainedStates = mutableStateMapOf<String, StreamingMarkdownState>()
    val states: Map<String, StreamingMarkdownState> get() = retainedStates

    fun getOrCreate(messageId: String): StreamingMarkdownState =
        retainedStates.getOrPut(messageId) { StreamingMarkdownState() }

    fun unusedMessageIds(streamingMessageIds: Set<String>): List<String> =
        retainedStates.entries.mapNotNull { (id, state) ->
            id.takeIf { it !in streamingMessageIds && !state.isAttachedToContent }
        }

    fun discardUnused(streamingMessageIds: Set<String>) {
        unusedMessageIds(streamingMessageIds).forEach { id ->
            retainedStates.remove(id)?.snapshot?.let(staticMarkdownDocumentCache::put)
        }
    }
}
