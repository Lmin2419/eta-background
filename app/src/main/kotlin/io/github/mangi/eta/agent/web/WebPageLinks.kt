package io.github.mangi.eta.agent.web

import java.net.URI
import java.net.URISyntaxException

/** 链接只作为来源数据返回，不访问链接目标。 */
internal class WebPageLinks {
    private val byUrl = linkedMapOf<String, WebPageLink>()
    private var retainedChars = 0
    var truncated: Boolean = false
        private set
    val items: List<WebPageLink> get() = byUrl.values.toList()

    fun add(url: String, label: String): WebPageLink? {
        if (url.isBlank() || url.any { it.isWhitespace() || it.isISOControl() }) return null
        val parsed = try { URI(url) } catch (_: URISyntaxException) { return null }
        if ((!parsed.scheme.equals("http", true) && !parsed.scheme.equals("https", true)) || parsed.rawAuthority.isNullOrBlank()) return null
        byUrl[url]?.let { return it }
        val text = takeCompleteCharacters(label.replace(WHITESPACE, " ").trim().ifBlank { url }, WebPageContent.MAX_LINK_TEXT_CHARS)
        if (url.length > WebPageContent.MAX_LINK_URL_CHARS || byUrl.size >= WebPageContent.MAX_LINKS ||
            retainedChars + url.length + text.length > WebPageContent.MAX_LINK_TOTAL_CHARS
        ) {
            truncated = true
            return null
        }
        return WebPageLink(byUrl.size + 1, text, url).also {
            byUrl[url] = it
            retainedChars += url.length + text.length
        }
    }

    fun addPlainTextLinks(text: String, ensureActive: () -> Unit) {
        for (match in HTTP_URL.findAll(text)) {
            ensureActive()
            var url = match.value.trimEnd('.', ',', ';', '!', '。', '，', '；', '！')
            var end = url.length
            for ((closing, opening) in listOf(')' to '(', ']' to '[', '}' to '{')) {
                var extra = url.count { it == closing } - url.count { it == opening }
                while (end > 0 && url[end - 1] == closing && extra > 0) { end--; extra-- }
            }
            url = url.substring(0, end)
            add(url, url)
            if (truncated) break
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val HTTP_URL = Regex("https?://[^\\s<>\"，。；！、\\u0000-\\u001f]+", RegexOption.IGNORE_CASE)
    }
}
