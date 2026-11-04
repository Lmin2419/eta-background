package io.github.mangi.eta.agent.web

import org.junit.Assert.*
import org.junit.Test

class SearchRelevanceTest {
    @Test fun modelSuffixesCannotMatchDifferentProducts() {
        val page = page("Intel Core i5-14500HX", "Intel Core i5-14500H benchmarks")
        val result = SearchRelevance.filter("i5-14500H Cinebench", page)
        assertEquals(listOf("Intel Core i5-14500H benchmarks"), result.results.map { it.title })
        assertTrue(result.limited)
        assertEquals(1, result.skippedResults)
    }

    @Test fun punctuationVariantsAreAcceptedWithoutSemanticGuessing() {
        assertEquals(1, SearchRelevance.filter("E3-1231v3 PassMark", page("Xeon E3 1231 v3 benchmark")).results.size)
        val generic = page("Meaningful answer phrased differently")
        assertSame(generic, SearchRelevance.filter("安卓 系统推荐", generic))
        assertSame(generic, SearchRelevance.filter("site:example2026.com android", generic))
        assertTrue(SearchRelevance.filter("i5-14500H", page()).results.isEmpty())
    }

    private fun page(vararg titles: String) = SearchPage("fixture", "https://search.test", titles.mapIndexed { index, title ->
        SearchResult(title, "https://site.test/result/$index", "")
    }, false)
}
