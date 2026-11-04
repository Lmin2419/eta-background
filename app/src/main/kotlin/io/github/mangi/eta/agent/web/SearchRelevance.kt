package io.github.mangi.eta.agent.web

import java.util.Locale

/** Reject only strong entity mismatches, not general semantic queries or incomplete snippets. */
internal object SearchRelevance {
    fun filter(query: String, page: SearchPage): SearchPage {
        if (page.results.isEmpty()) return page
        // Product identifiers are high-signal. Whole-token boundaries keep 14500H distinct from 14500HX.
        val identifiers = Regex("(?i)(?<![\\p{L}\\p{N}])(?=[a-z0-9-]*[a-z])(?=[a-z0-9-]*[0-9])[a-z0-9]+(?:-[a-z0-9]+)*(?![\\p{L}\\p{N}])")
            .findAll(query.replace(Regex("-?site:\\S+"), ""))
            .map { it.value.lowercase(Locale.ROOT) }.filter { it.length >= 4 }.distinct().take(8).toList()
        if (identifiers.isEmpty()) return page
        val patterns = identifiers.map { identifier ->
            val parts = identifier.split(Regex("-+|(?<=[a-z])(?=[0-9])|(?<=[0-9])(?=[a-z])"))
                .joinToString("[\\s_-]*") { Regex.escape(it) }
            Regex("(?<![\\p{L}\\p{N}])$parts(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        }
        val relevant = page.results.filter { item ->
            val evidence = "${item.title} ${item.snippet} ${item.url}"
            patterns.any { it.containsMatchIn(evidence) }
        }
        if (relevant.isEmpty()) throw WebSearchException("SEARCH_LOW_RELEVANCE", "搜索结果没有匹配查询中的产品标识，已拒绝将无关结果作为答案依据")
        if (relevant.size == page.results.size) return page
        return page.copy(results = relevant, limited = true,
            skippedResults = page.skippedResults + page.results.size - relevant.size,
            limitReasons = (page.limitReasons + "entity_mismatch").distinct())
    }
}
