package io.github.mangi.eta.ui.markdown

import android.util.LruCache

internal val staticMarkdownDocumentCache = StaticMarkdownDocumentCache()

/**
 * 缓存权重使用 UTF-16 源码字符数，并为短文档计入最低权重来限制条目数。
 * 这不是精确堆字节预算：语法树和富文本对象的放大率随内容结构变化。
 */
internal class StaticMarkdownDocumentCache(
    sourceCharBudget: Int = 512 * 1024,
    maxEntries: Int = 96,
    private val maxDocumentSourceChars: Int = (sourceCharBudget / 4).coerceAtLeast(1),
) {
    init {
        require(sourceCharBudget > 0 && maxEntries > 0)
        require(maxDocumentSourceChars in 1..sourceCharBudget)
    }

    private val minimumEntryWeight =
        ((sourceCharBudget.toLong() + maxEntries - 1) / maxEntries).toInt()
    private val cache = object : LruCache<Pair<String, MarkdownInlineStyle>, MarkdownDocument>(sourceCharBudget) {
        override fun sizeOf(key: Pair<String, MarkdownInlineStyle>, value: MarkdownDocument): Int =
            key.first.length.coerceAtLeast(minimumEntryWeight)
    }

    fun get(content: String, style: MarkdownInlineStyle): MarkdownDocument? = cache.get(content to style)

    fun put(snapshot: StreamingGfmSnapshot) {
        if (snapshot.isComplete) admit(snapshot.originalSource, snapshot.document)
    }

    fun parse(content: String, style: MarkdownInlineStyle): MarkdownDocument =
        StreamingGfmParserSession().parse(content, isComplete = true, style = style).document
            .also { admit(content, it) }

    private fun admit(content: String, document: MarkdownDocument) {
        // 巨型回复仍完整显示，但不挤出整批小文档；流式交接和静态解析共用准入边界。
        if (content.length > maxDocumentSourceChars) return
        cache.put(content to document.inlineStyle, document)
    }
}
