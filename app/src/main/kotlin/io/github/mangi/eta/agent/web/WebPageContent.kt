package io.github.mangi.eta.agent.web

import java.util.Locale

internal data class WebPageLink(val id: Int, val text: String, val url: String)

internal data class WebPageDocument(
    val url: String,
    val title: String?,
    val content: String,
    val contentType: String,
    val format: String,
    val links: List<WebPageLink>,
    val truncated: Boolean,
    val truncationReasons: List<String>,
    val linksTruncated: Boolean = false,
    val titleTruncated: Boolean = false,
)

internal data class WebPageContentPage(
    val offsetChars: Int,
    val nextOffsetChars: Int,
    val totalChars: Int,
    val content: String,
    val hasMore: Boolean,
    val sourceTruncated: Boolean,
)

internal class WebPageContentException(val code: String, message: String) : IllegalArgumentException(message)

/** 只处理调用方已经取得的文本；HTML 解析不发起请求或执行脚本。 */
internal object WebPageContent {
    const val MAX_INPUT_CHARS = 512_000
    const val MAX_CONTENT_CHARS = 200_000
    const val MAX_PAGE_CHARS = 16_000
    const val MAX_LINKS = 200
    const val MAX_LINK_URL_CHARS = 4_096
    const val MAX_LINK_TEXT_CHARS = 200
    const val MAX_LINK_TOTAL_CHARS = 24_000
    const val MAX_TITLE_CHARS = 512

    fun extract(
        text: String,
        contentType: String,
        finalUrl: String,
        sourceTruncated: Boolean = false,
        ensureActive: () -> Unit = {},
    ): WebPageDocument {
        ensureActive()
        val mime = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
        val format = when {
            mime == "text/html" || mime == "application/xhtml+xml" -> "html"
            mime == "text/markdown" || mime == "text/x-markdown" -> "markdown"
            mime == "application/json" || mime.startsWith("application/") && mime.endsWith("+json") -> "json"
            mime.startsWith("text/") -> "text"
            else -> throw WebPageContentException("UNSUPPORTED_CONTENT_TYPE", "此内容类型不支持文本正文解析")
        }
        val reasons = mutableListOf<String>()
        if (sourceTruncated) reasons += "source_limit"
        val input = takeCompleteCharacters(text, MAX_INPUT_CHARS)
        if (input.length < text.length) reasons += "input_limit"
        if (format == "html") {
            val html = HtmlPageContent.extract(input, finalUrl, ensureActive)
            if (html.contentTruncated) reasons += "content_limit"
            return WebPageDocument(finalUrl, html.title, html.content, mime, format, html.links,
                reasons.isNotEmpty(), reasons, html.linksTruncated, html.titleTruncated)
        }
        val content = takeCompleteCharacters(input, MAX_CONTENT_CHARS)
        if (content.length < input.length) reasons += "content_limit"
        val links = WebPageLinks()
        links.addPlainTextLinks(content, ensureActive)
        ensureActive()
        return WebPageDocument(finalUrl, null, content, mime, format, links.items,
            reasons.isNotEmpty(), reasons, links.truncated)
    }

    fun page(document: WebPageDocument, offsetChars: Int = 0, maxChars: Int = MAX_PAGE_CHARS): WebPageContentPage {
        if (offsetChars !in 0..document.content.length || maxChars <= 0) {
            throw WebPageContentException("INVALID_CONTENT_RANGE", "正文分页范围无效")
        }
        if (offsetChars > 0 && offsetChars < document.content.length &&
            document.content[offsetChars].isLowSurrogate() && document.content[offsetChars - 1].isHighSurrogate()
        ) throw WebPageContentException("INVALID_CONTENT_OFFSET", "正文游标位于 Unicode 字符中间，请使用上次 next_offset_chars")
        val end = completeCharacterEnd(document.content, (offsetChars.toLong() + maxChars.coerceAtMost(MAX_PAGE_CHARS)).coerceAtMost(document.content.length.toLong()).toInt())
        if (end == offsetChars && offsetChars < document.content.length) {
            throw WebPageContentException("CONTENT_PAGE_TOO_SMALL", "分页上限不足以返回一个完整 Unicode 字符")
        }
        return WebPageContentPage(offsetChars, end, document.content.length, document.content.substring(offsetChars, end),
            end < document.content.length, document.truncated)
    }
}

internal fun takeCompleteCharacters(text: String, maxChars: Int): String =
    text.substring(0, completeCharacterEnd(text, maxChars.coerceAtMost(text.length)))

private fun completeCharacterEnd(text: String, end: Int): Int =
    if (end > 0 && end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end - 1 else end
