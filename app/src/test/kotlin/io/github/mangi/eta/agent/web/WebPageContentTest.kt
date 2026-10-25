package io.github.mangi.eta.agent.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPageContentTest {
    private val url = "https://example.com/news/story"

    @Test fun htmlSelectsMainContentAndPreservesArticleHeadingAndAuthor() {
        val html = """
            <html><head><title>报道 &amp; 分析</title><style>.x{color:red}</style></head><body>
            <header>站点导航</header><nav>登录 注册</nav>
            <main><article><header><h1>报道标题</h1><p>作者甲</p></header>
            <p>第一段正文 <a href="../source">原始资料</a>。</p>
            <aside>广告推荐</aside><p hidden>不可见内容</p><p aria-hidden="true">隐藏说明</p>
            <script>window.secret = "不要输出";</script><p>第二段正文。</p></article></main>
            <footer>站点版权导航</footer></body></html>
        """.trimIndent()
        val document = WebPageContent.extract(html, "text/html; charset=utf-8", url)
        assertEquals("报道 & 分析", document.title)
        assertTrue(document.content.contains("报道标题"))
        assertTrue(document.content.contains("作者甲"))
        assertTrue(document.content.contains("原始资料 [1]"))
        assertTrue(document.content.contains("第二段正文。"))
        for (noise in listOf("站点导航", "登录", "广告推荐", "不可见内容", "隐藏说明", "不要输出", "color:red", "站点版权导航")) {
            assertFalse(noise, document.content.contains(noise))
        }
        assertEquals("https://example.com/source", document.links.single().url)
        assertFalse(document.truncated)
    }

    @Test fun mainKeepsMultipleArticlesAndArticleIsUsedWithoutMain() {
        val main = WebPageContent.extract("<main><article>第一篇</article><article>第二篇</article></main>", "text/html", url)
        assertTrue(main.content.contains("第一篇"))
        assertTrue(main.content.contains("第二篇"))
        val article = WebPageContent.extract("<div>页面外侧内容</div><article>主体文章</article>", "text/html", url)
        assertEquals("主体文章", article.content)
    }

    @Test fun bodyFallbackRetainsParagraphsEntitiesAndPreformattedCode() {
        val html = "<p>甲 &lt; 乙 &amp; 丙</p><p>下一段<br>换行</p><pre>  first\n    second</pre><table><tr><td>列甲</td><td>列乙</td></tr></table>"
        val document = WebPageContent.extract(html, "text/html", url)
        assertTrue(document.content.contains("甲 < 乙 & 丙\n\n下一段\n换行"))
        assertTrue(document.content.contains("  first\n    second"))
        assertTrue(document.content.contains("列甲 | 列乙"))
    }

    @Test fun htmlLinksResolveAgainstBaseAndDeduplicateWithoutExecutableSchemes() {
        val html = """
            <head><base href="https://reference.example/docs/"></head><main>
            <a href="../spec?q=1&amp;x=2">资料</a><a href="../spec?q=1&amp;x=2">再次引用</a>
            <a href="javascript:alert(1)">脚本入口</a><a href="data:text/plain,test">内联数据</a>
            <a href="mailto:test@example.com">邮箱</a><a href="/other#part">章节</a></main>
        """.trimIndent()
        val document = WebPageContent.extract(html, "application/xhtml+xml", url)
        assertEquals(listOf("https://reference.example/spec?q=1&x=2", "https://reference.example/other#part"), document.links.map { it.url })
        assertTrue(document.content.contains("资料 [1]"))
        assertTrue(document.content.contains("再次引用 [1]"))
        assertTrue(document.content.contains("章节 [2]"))
    }

    @Test fun textMarkdownAndJsonAreNotReformattedOrParsedAsHtml() {
        val fixtures = mapOf(
            "text/plain" to "  原始行\n\t保留缩进 <tag>\n",
            "text/markdown" to "# 标题\n\n```html\n<b>源码</b>\n```\n[文档](https://example.com/spec)\n",
            "application/problem+json; charset=UTF-8" to " {\n  \"value\": \"<p>不是 HTML</p>\"\n}\n",
        )
        for ((type, text) in fixtures) {
            val document = WebPageContent.extract(text, type, url)
            assertEquals(text, document.content)
            assertFalse(document.truncated)
        }
        assertEquals("json", WebPageContent.extract("null", "application/json", url).format)
    }

    @Test fun textLinkExtractionPreservesBalancedParenthesesAndSourceText() {
        val text = "[资料](https://example.com/a_(b))，另见 https://example.com/c."
        val document = WebPageContent.extract(text, "text/markdown", url)
        assertEquals(text, document.content)
        assertEquals(listOf("https://example.com/a_(b)", "https://example.com/c"), document.links.map { it.url })
    }

    @Test fun pageCursorNeverSplitsSupplementaryCharacters() {
        val document = WebPageContent.extract("ab😀cd\n", "text/plain", url)
        val first = WebPageContent.page(document, maxChars = 3)
        val second = WebPageContent.page(document, first.nextOffsetChars, 3)
        val third = WebPageContent.page(document, second.nextOffsetChars, 3)
        assertEquals("ab", first.content)
        assertEquals("😀c", second.content)
        assertEquals(document.content, first.content + second.content + third.content)
        assertEquals(document.content.length, third.nextOffsetChars)
        assertFalse(third.hasMore)
        assertFailure("INVALID_CONTENT_OFFSET") { WebPageContent.page(document, 3) }
        assertFailure("CONTENT_PAGE_TOO_SMALL") { WebPageContent.page(document, 2, 1) }
    }

    @Test fun sourceAndContentLimitsRemainVisibleAfterLastRetainedPage() {
        val text = "x".repeat(WebPageContent.MAX_INPUT_CHARS + 1)
        val document = WebPageContent.extract(text, "text/plain", url, sourceTruncated = true)
        assertEquals(WebPageContent.MAX_CONTENT_CHARS, document.content.length)
        assertEquals(setOf("source_limit", "input_limit", "content_limit"), document.truncationReasons.toSet())
        val last = WebPageContent.page(document, document.content.length - 3)
        assertFalse(last.hasMore)
        assertTrue(last.sourceTruncated)
    }

    @Test fun contentLimitKeepsAnIntactUnicodePrefix() {
        val prefix = "a".repeat(WebPageContent.MAX_CONTENT_CHARS - 1)
        val document = WebPageContent.extract(prefix + "😀之后", "text/plain", url)
        assertEquals(prefix, document.content)
        assertTrue(document.truncated)
        val html = WebPageContent.extract("<article>$prefix😀之后<p>后续</p></article>", "text/html", url)
        assertEquals(prefix, html.content)
        assertTrue(html.truncated)
    }

    @Test fun linksHaveIndependentCountAndAggregateSizeBudgets() {
        val many = (0 until 220).joinToString("") { "<a href='https://example.com/$it'>link$it</a> " }
        val document = WebPageContent.extract("<main>$many</main>", "text/html", url)
        assertEquals(WebPageContent.MAX_LINKS, document.links.size)
        assertTrue(document.linksTruncated)
        val long = (0 until 100).joinToString("") { "<a href='https://example.com/${"x".repeat(500)}/$it'>链接$it</a> " }
        val longLinks = WebPageContent.extract("<main>$long</main>", "text/html", url)
        assertTrue(longLinks.linksTruncated)
        assertTrue(longLinks.links.sumOf { it.url.length + it.text.length } <= WebPageContent.MAX_LINK_TOTAL_CHARS)
        assertTrue(longLinks.content.contains("链接99"))
    }

    @Test fun htmlTitleHasAnIndependentBudgetAndCanUseHeadingFallback() {
        val title = "x".repeat(WebPageContent.MAX_TITLE_CHARS + 10)
        val document = WebPageContent.extract("<title>$title</title><main>正文</main>", "text/html", url)
        assertEquals(WebPageContent.MAX_TITLE_CHARS, document.title!!.length)
        assertTrue(document.titleTruncated)
        assertFalse(document.truncated)
        val heading = WebPageContent.extract("<article><h1>正文标题</h1>内容</article>", "text/html", url)
        assertEquals("正文标题", heading.title)
    }

    @Test fun malformedHtmlIsToleratedWithoutRunningScriptContent() {
        val document = WebPageContent.extract("<main><p>第一段<p>第二段<a href='/source'>来源<script>throw Error('秘密')", "text/html", url)
        assertTrue(document.content.contains("第一段"))
        assertTrue(document.content.contains("第二段"))
        assertFalse(document.content.contains("秘密"))
        assertEquals("https://example.com/source", document.links.single().url)
    }

    @Test fun unsupportedBinaryMimeAndInvalidRangesHaveExplicitErrors() {
        assertFailure("UNSUPPORTED_CONTENT_TYPE") { WebPageContent.extract("%PDF-1.7", "application/pdf", url) }
        val document = WebPageContent.extract("text", "text/plain", url)
        assertFailure("INVALID_CONTENT_RANGE") { WebPageContent.page(document, -1) }
        assertFailure("INVALID_CONTENT_RANGE") { WebPageContent.page(document, 5) }
    }

    @Test fun cancellationFromCallerPropagatesDuringHtmlTraversal() {
        var checks = 0
        try {
            WebPageContent.extract("<main>" + "<p>正文</p>".repeat(30) + "</main>", "text/html", url, ensureActive = {
                if (++checks == 8) throw InterruptedException("cancelled")
            })
            throw AssertionError("cancelled extraction completed")
        } catch (_: InterruptedException) { assertEquals(8, checks) }
    }

    private fun assertFailure(code: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected $code")
        } catch (error: WebPageContentException) { assertEquals(code, error.code) }
    }
}
