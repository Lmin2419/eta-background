package io.github.mangi.eta.ui.markdown

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StaticMarkdownDocumentCacheTest {
    private val style = MarkdownInlineStyle.Default

    @Test
    fun smallDocumentsRespectTheEntryBudgetAndLeastRecentlyUsedOrder() {
        val cache = cache()
        val first = cache.parse("第一条", style)
        cache.parse("第二条", style)
        cache.parse("第三条", style)
        cache.parse("第四条", style)
        assertSame(first, cache.get("第一条", style))

        cache.parse("第五条", style)

        assertSame(first, cache.get("第一条", style))
        assertNull(cache.get("第二条", style))
        assertNotNull(cache.get("第三条", style))
        assertNotNull(cache.get("第四条", style))
        assertNotNull(cache.get("第五条", style))
    }

    @Test
    fun totalSourceCharactersEvictBeforeTheEntryLimitIsReached() {
        val cache = cache()
        val first = "甲".repeat(40)
        val second = "乙".repeat(40)
        val third = "丙".repeat(40)
        cache.parse(first, style)
        val retained = cache.parse(second, style)

        val latest = cache.parse(third, style)

        assertNull(cache.get(first, style))
        assertSame(retained, cache.get(second, style))
        assertSame(latest, cache.get(third, style))
    }

    @Test
    fun oversizedStaticDocumentIsParsedWithoutDisplacingSmallerDocuments() {
        val cache = cache()
        val small = cache.parse("保留的短回复", style)
        val oversized = "长".repeat(61)

        val rendered = cache.parse(oversized, style)

        assertEquals(oversized, (rendered.blocks.single() as MarkdownParagraph).text.text)
        assertNull(cache.get(oversized, style))
        assertSame(small, cache.get("保留的短回复", style))
    }

    @Test
    fun streamingHandoffUsesTheSameSingleDocumentAdmissionLimit() {
        val cache = cache()
        val small = cache.parse("流式交接前的小文档", style)
        val oversized = StreamingGfmParserSession().parse("大".repeat(61), isComplete = true)

        cache.put(oversized)

        assertNull(cache.get(oversized.originalSource, style))
        assertSame(small, cache.get("流式交接前的小文档", style))

        val accepted = StreamingGfmParserSession().parse("界".repeat(60), isComplete = true)
        cache.put(accepted)
        assertSame(accepted.document, cache.get(accepted.originalSource, style))
    }

    private fun cache() = StaticMarkdownDocumentCache(
        sourceCharBudget = 100,
        maxEntries = 4,
        maxDocumentSourceChars = 60,
    )
}
