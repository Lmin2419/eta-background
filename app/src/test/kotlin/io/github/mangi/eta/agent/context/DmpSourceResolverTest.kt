package io.github.mangi.eta.agent.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DmpSourceResolverTest {
    @Test
    fun triesCaptionAliasWithoutDroppingTheLogicalField() {
        val calls = mutableListOf<DmpQuery>()
        val resolver = DmpSourceResolver { query ->
            calls += query
            if (query.projection.any { it.expression == "COUNT(caption_content0Com)" }) {
                DmpQueryResult.Failure("DMP_COLUMN_MISSING", "caption_content0Com")
            } else zeros(query)
        }
        val resolved = available(resolver.resolve(source("photos")))

        assertEquals(2, calls.size)
        assertEquals("caption_contentCom", resolved.columns["caption"])
        assertFalse("caption" in resolved.unavailableFields)
        assertTrue(calls.last().projection.any { it.name == "caption" && it.expression == "COUNT(caption_contentCom)" })
        calls.forEach(::assertContentFreeProbe)
    }

    @Test
    fun missingOptionalFieldIsReportedAndExcludedFromLaterProjection() {
        val calls = mutableListOf<DmpQuery>()
        val resolver = DmpSourceResolver { query ->
            calls += query
            if (query.projection.any { it.expression == "COUNT(titleCom)" }) {
                DmpQueryResult.Failure("DMP_COLUMN_MISSING", "titleCom")
            } else zeros(query)
        }
        val resolved = available(resolver.resolve(source("notes")))

        assertEquals(2, calls.size)
        assertEquals(setOf("title"), resolved.unavailableFields)
        assertFalse(resolved.columns.containsKey("title"))
        assertTrue(calls.last().projection.none { it.name == "title" })
        assertTrue(resolved.columns.keys.containsAll(listOf("_valid", "_recycled", "_encrypted")))
    }

    @Test
    fun missingProtectionColumnFailsWithoutRemovingItsCondition() {
        listOf("valid", "recycle_timeCom", "encryptedCom").forEach { protectedColumn ->
            val calls = mutableListOf<DmpQuery>()
            val resolver = DmpSourceResolver { query ->
                calls += query
                DmpQueryResult.Failure("DMP_COLUMN_MISSING", protectedColumn)
            }
            val result = resolver.resolve(source("notes"))

            assertEquals(protectedColumn, DmpSourceResolution.Unavailable("PERSONAL_CONTEXT_SCHEMA_UNSUPPORTED"), result)
            assertEquals(protectedColumn, 1, calls.size)
            assertTrue(calls.single().projection.any { it.expression == "COUNT($protectedColumn)" })
            assertContentFreeProbe(calls.single())
        }
    }

    @Test
    fun probeMustReturnExactlyOneCompleteRowOfZeros() {
        val invalidResults: List<(DmpQuery) -> DmpQueryResult.Success> = listOf(
            { DmpQueryResult.Success(emptyList()) },
            { query -> zeros(query).let { it.copy(rows = it.rows + it.rows) } },
            { query -> zeros(query).let { it.copy(rows = listOf(it.rows.single() + ("id" to "1"))) } },
            { query -> zeros(query).let { it.copy(rows = listOf(it.rows.single() - "id")) } },
            { query -> zeros(query).let { it.copy(rows = listOf(it.rows.single() + ("id" to null))) } },
            { query -> zeros(query).copy(truncatedFields = setOf("id")) },
        )
        invalidResults.forEachIndexed { index, response ->
            val resolver = DmpSourceResolver { query ->
                assertContentFreeProbe(query)
                response(query)
            }
            assertEquals("invalid probe $index", DmpSourceResolution.Unavailable("DMP_OUTPUT_INVALID"), resolver.resolve(source("notes")))
        }
    }

    @Test
    fun successfulResolutionIsCachedAndRefreshRechecksTheSource() {
        var missingTitle = false
        var calls = 0
        val resolver = DmpSourceResolver { query ->
            calls++
            if (missingTitle && query.projection.any { it.name == "title" }) {
                DmpQueryResult.Failure("DMP_COLUMN_MISSING", "titleCom")
            } else zeros(query)
        }
        val notes = source("notes")
        assertTrue(available(resolver.resolve(notes)).columns.containsKey("title"))
        assertEquals(1, calls)

        missingTitle = true
        assertTrue(available(resolver.resolve(notes)).columns.containsKey("title"))
        assertEquals(1, calls)

        val refreshed = available(resolver.resolve(notes, refresh = true))
        assertFalse(refreshed.columns.containsKey("title"))
        assertEquals(setOf("title"), refreshed.unavailableFields)
        assertEquals(3, calls)
        assertEquals(refreshed, available(resolver.resolve(notes)))
        assertEquals(3, calls)
    }

    @Test
    fun failedRefreshDoesNotLeaveAnOldAvailabilityClaimCached() {
        var denied = false
        var calls = 0
        val resolver = DmpSourceResolver { query ->
            calls++
            if (denied) DmpQueryResult.Failure("ROOT_REQUIRED") else zeros(query)
        }
        val notes = source("notes")
        available(resolver.resolve(notes))
        denied = true
        assertEquals(DmpSourceResolution.Unavailable("ROOT_REQUIRED"), resolver.resolve(notes, refresh = true))
        assertEquals(DmpSourceResolution.Unavailable("ROOT_REQUIRED"), resolver.resolve(notes))
        assertEquals(3, calls)
        denied = false
        assertTrue(resolver.resolve(notes) is DmpSourceResolution.Available)
        assertEquals(4, calls)
    }

    @Test
    fun unknownMissingColumnDoesNotSilentlyReduceTheSchema() {
        var calls = 0
        val resolver = DmpSourceResolver {
            calls++
            DmpQueryResult.Failure("DMP_COLUMN_MISSING", "not_in_projection")
        }
        assertEquals(DmpSourceResolution.Unavailable("PERSONAL_CONTEXT_SCHEMA_UNSUPPORTED"), resolver.resolve(source("notes")))
        assertEquals(1, calls)
    }

    private fun source(id: String): PersonalContextSource = requireNotNull(PersonalContextSources.find(id))

    private fun available(result: DmpSourceResolution): ResolvedPersonalContextSource {
        assertTrue(result.toString(), result is DmpSourceResolution.Available)
        return (result as DmpSourceResolution.Available).value
    }

    private fun zeros(query: DmpQuery): DmpQueryResult.Success =
        DmpQueryResult.Success(listOf(query.projection.associate { it.name to "0" }))

    private fun assertContentFreeProbe(query: DmpQuery) {
        assertEquals("0", query.selection)
        assertEquals(1, query.limit)
        assertEquals(0, query.offset)
        assertTrue(query.projection.all { it.expression.startsWith("COUNT(") && it.expression.endsWith(')') })
    }
}
