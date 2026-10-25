package io.github.mangi.eta.agent.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

internal data class HtmlPageExtraction(
    val title: String?,
    val content: String,
    val links: List<WebPageLink>,
    val contentTruncated: Boolean,
    val linksTruncated: Boolean,
    val titleTruncated: Boolean,
)

internal object HtmlPageContent {
    fun extract(html: String, baseUrl: String, ensureActive: () -> Unit): HtmlPageExtraction {
        ensureActive()
        val document = Jsoup.parse(html, baseUrl)
        ensureActive()
        val titleText = document.title().ifBlank {
            document.select("meta[property=og:title]").firstOrNull()?.attr("content").orEmpty()
        }.trim()
        document.select(NOISE_SELECTORS).remove()
        val body = document.body()
        val primary = body.select("main, [role=main]").firstOrNull()?.takeIf { it.text().isNotBlank() }
            ?: body.select("article").firstOrNull()?.takeIf { it.text().isNotBlank() }
            ?: body
        val title = titleText.ifBlank { primary.select("h1").firstOrNull()?.text().orEmpty() }
        val links = WebPageLinks()
        val output = HtmlTextBuffer(WebPageContent.MAX_CONTENT_CHARS)
        var preformattedDepth = 0
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                ensureActive()
                when (node) {
                    is TextNode -> output.appendText(node.wholeText, preformattedDepth > 0)
                    is Element -> {
                        val tag = node.normalName()
                        if (tag in PARAGRAPH_TAGS) output.lineBreak(2)
                        else if (tag in LINE_TAGS) output.lineBreak(1)
                        when (tag) {
                            "pre" -> preformattedDepth++
                            "br" -> output.lineBreak(1)
                            "li" -> output.appendToken("- ")
                            "img" -> node.attr("alt").takeIf(String::isNotBlank)?.let {
                                output.appendText("[图片：$it]", false)
                            }
                        }
                    }
                }
            }

            override fun tail(node: Node, depth: Int) {
                ensureActive()
                if (node !is Element) return
                val tag = node.normalName()
                when (tag) {
                    "a" -> {
                        val label = node.text().ifBlank { node.attr("aria-label").ifBlank { node.attr("title") } }
                        links.add(node.absUrl("href"), label)?.let { link -> output.appendToken(" [${link.id}]") }
                    }
                    "pre" -> preformattedDepth--
                    "td", "th" -> output.appendToken(" | ")
                }
                if (tag in PARAGRAPH_TAGS) output.lineBreak(2)
                else if (tag in LINE_TAGS) output.lineBreak(1)
            }
        }, primary)
        ensureActive()
        return HtmlPageExtraction(
            title = takeCompleteCharacters(title, WebPageContent.MAX_TITLE_CHARS).takeIf(String::isNotEmpty),
            content = output.content(),
            links = links.items,
            contentTruncated = output.truncated,
            linksTruncated = links.truncated,
            titleTruncated = title.length > WebPageContent.MAX_TITLE_CHARS,
        )
    }

    private val PARAGRAPH_TAGS = setOf("p", "div", "section", "article", "main", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "figure", "table")
    private val LINE_TAGS = setOf("li", "ul", "ol", "tr", "dl", "dt", "dd", "figcaption", "hr")
    private const val NOISE_SELECTORS = "script, style, noscript, template, iframe, object, embed, svg, canvas, " +
        "nav, aside, form, button, input, select, textarea, [hidden], [aria-hidden=true], " +
        "[role=navigation], [role=banner], [role=complementary], body > header, body > footer"
}

private class HtmlTextBuffer(private val limit: Int) {
    private val text = StringBuilder()
    private var pendingSpace = false
    var truncated: Boolean = false
        private set

    fun appendText(value: String, preformatted: Boolean) {
        if (truncated) return
        if (preformatted) { appendToken(value); return }
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character.isWhitespace() || character == '\u00a0') {
                pendingSpace = true
                index++
                continue
            }
            if (pendingSpace && text.isNotEmpty() && !text.last().isWhitespace()) appendToken(" ")
            pendingSpace = false
            val width = if (character.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate()) 2 else 1
            if (text.length + width > limit) { truncated = true; return }
            text.append(value, index, index + width)
            index += width
        }
    }

    fun appendToken(value: String) {
        if (truncated) return
        pendingSpace = false
        val retained = takeCompleteCharacters(value, (limit - text.length).coerceAtLeast(0))
        text.append(retained)
        if (retained.length < value.length) truncated = true
    }

    fun lineBreak(count: Int) {
        if (truncated) return
        pendingSpace = false
        while (text.isNotEmpty() && (text.last() == ' ' || text.last() == '\t')) text.setLength(text.length - 1)
        if (text.isEmpty()) return
        var existing = 0
        var index = text.lastIndex
        while (index >= 0 && text[index] == '\n') { existing++; index-- }
        repeat((count - existing).coerceAtLeast(0)) { if (text.length < limit) text.append('\n') }
    }

    fun content(): String = text.toString().trim('\n', '\r')
}
