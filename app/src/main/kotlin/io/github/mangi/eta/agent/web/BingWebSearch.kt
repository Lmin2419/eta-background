package io.github.mangi.eta.agent.web

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** Parse Bing's public RSS results, never HTML challenges or arbitrary redirect pages. */
internal object BingWebSearch {
    const val PROVIDER = "bing_rss"

    fun parse(xml: String, finalUrl: String, limit: Int, ensureActive: () -> Unit = {}): SearchPage {
        if (limit !in 1..10) throw WebSearchException("INVALID_ARGUMENT", "max_results 必须为 1 到 10")
        val base = finalUrl.toHttpUrlOrNull()
        if (base == null || !(base.host == "bing.com" || base.host.endsWith(".bing.com"))) {
            throw WebSearchException("SEARCH_PARSE_FAILED", "搜索响应没有来自预期站点")
        }
        ensureActive()
        if (xml.length > PublicWebSearch.MAX_HTML_CHARS) throw WebSearchException("SEARCH_RESPONSE_TOO_LARGE", "搜索响应超过大小限制")
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) {
            throw WebSearchException("SEARCH_PARSE_FAILED", "搜索响应不是可读取的 RSS")
        }
        if (!xml.trimEnd().endsWith("</rss>")) {
            throw WebSearchException("SEARCH_PARSE_FAILED", "搜索响应不完整，未取得可靠结果")
        }
        // Jsoup does not resolve external entities; do not accept feeds with reported parsing errors.
        val parser = Parser.xmlParser().setTrackErrors(10)
        val document = Jsoup.parse(xml, base.toString(), parser)
        val rss = document.children().singleOrNull()?.takeIf { it.tagName() == "rss" }
        val channel = rss?.children()?.singleOrNull { it.tagName() == "channel" }
        if (parser.errors.isNotEmpty() || channel == null || channel.children().none { it.tagName() == "title" }) {
            throw WebSearchException("SEARCH_PARSE_FAILED", "未识别到有效搜索结果，可能需要网页验证或站点结构已变化")
        }
        val items = channel.children().filter { it.tagName() == "item" }
        val reasons = linkedSetOf<String>()
        if (items.size > 100) reasons += "scan_limit"
        val results = mutableListOf<SearchResult>()
        val seen = hashSetOf<String>()
        var skipped = 0
        var chars = 0
        for (item in items.take(100)) {
            ensureActive()
            fun field(name: String) = item.children().firstOrNull { it.tagName() == name }?.text().orEmpty().trim()
            val rawUrl = field("link")
            val url = rawUrl.takeIf { it.length <= PublicWebSearch.MAX_URL_CHARS && it.none(Char::isISOControl) }
                ?.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
            val rawTitle = field("title")
            if (url == null || rawTitle.isBlank()) {
                skipped++
                reasons += "invalid_result"
                continue
            }
            if (!seen.add(url)) continue
            if (results.size >= limit) { reasons += "result_limit"; continue }
            val rawSnippet = Jsoup.parseBodyFragment(field("description")).text()
            val title = takeCompleteCharacters(rawTitle, 512)
            val snippet = takeCompleteCharacters(rawSnippet, 1200)
            if (title.length != rawTitle.length || snippet.length != rawSnippet.length) reasons += "text_limit"
            val cost = title.length + snippet.length + url.length
            if (chars + cost > 24_000) { reasons += "output_limit"; continue }
            results += SearchResult(title, url, snippet)
            chars += cost
        }
        if (items.isNotEmpty() && results.isEmpty()) throw WebSearchException("SEARCH_PARSE_FAILED", "搜索结果缺少有效标题或链接")
        ensureActive()
        return SearchPage(PROVIDER, base.toString(), results, reasons.isNotEmpty(),
            limitReasons = reasons.toList(), skippedResults = skipped)
    }
}
