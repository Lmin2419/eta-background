package io.github.mangi.eta.agent.context

import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalContextQueryServiceTest {
    @Test fun listingAllSourcesDoesNotIssueAnyRootQuery() {
        val transport = DmpQueryTransport { throw AssertionError("Static inventory attempted a query") }
        val result = service(transport).execute(JSONObject().put("action", "sources"))
        assertTrue(result.toString(), result.getBoolean("ok"))
        val sources = result.getJSONArray("sources")
        assertEquals(16, sources.length())
        for (index in 0 until sources.length()) assertEquals("not_checked", sources.getJSONObject(index).getString("availability"))
    }

    @Test fun sourceInspectionOnlyUsesFalseAggregateProbesForThatResource() {
        val transport = FakeTransport()
        val result = service(transport).execute(request("sources", "photos"))
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertEquals("available", result.getJSONObject("source_info").getString("availability"))
        assertTrue(transport.calls.isNotEmpty())
        assertTrue(transport.calls.all { it.resource == "gallery" && it.selection == "0" && it.limit == 1 })
        assertTrue(transport.calls.flatMap { it.projection }.all { it.expression.matches(Regex("COUNT\\([A-Za-z_][A-Za-z0-9_]*\\)")) })
        assertTrue(transport.dataCalls.isEmpty())
    }

    @Test fun everyDataActionRetainsValidityAndSourceProtectionFilters() {
        val sourceActions = mapOf(
            "notes" to listOf("search", "read", "count"),
            "memories" to listOf("search", "read", "count"),
            "bills" to listOf("search", "read", "count", "bill_summary"),
            "photos" to listOf("search", "read", "count"),
        )
        for ((source, actions) in sourceActions) {
            for (action in actions) {
                val transport = FakeTransport()
                val arguments = request(action, source).also { if (action == "read") it.put("id", "1") }
                val result = service(transport).execute(arguments)
                assertTrue(result.toString(), result.getBoolean("ok"))
                val selection = transport.dataCalls.single().selection.orEmpty()
                assertTrue(selection, selection.contains("(valid = 1)"))
                when (source) {
                    "notes" -> {
                        assertTrue(selection, selection.contains("(recycle_timeCom = 0)"))
                        assertTrue(selection, selection.contains("(encryptedCom = 0)"))
                    }
                    "memories", "bills" -> assertTrue(selection, selection.contains("(recycleTimeCom IS NULL OR recycleTimeCom = 0)"))
                    "photos" -> assertTrue(selection, selection.contains("(typeCom = 1)"))
                }
            }
        }
    }

    @Test fun captionAliasResolutionIsUsedByActualProjectionAndSearch() {
        val transport = FakeTransport(mutableSetOf("caption_content0Com"))
        transport.onData = { query ->
            DmpQueryResult.Success(listOf(record(query, mapOf("id" to "7", "caption" to "海边照片", "origin_id" to "42"))))
        }
        val result = service(transport).execute(request("search", "photos").put("query", "海边"))
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertEquals(2, transport.probes.size)
        val data = transport.dataCalls.single()
        assertEquals("caption_contentCom", data.projection.single { it.name == "caption" }.expression)
        assertTrue(data.selection.orEmpty().contains("caption_contentCom"))
        assertFalse(data.selection.orEmpty().contains("caption_content0Com"))
        assertEquals("海边照片", result.getJSONArray("items").getJSONObject(0).getString("caption"))
    }

    @Test fun keywordQuotesCommentsAndWildcardsStayInsideLiteralLikePatterns() {
        val transport = FakeTransport()
        val keyword = "O'Brien%_\\'; DROP TABLE metainfo; --"
        val result = service(transport).execute(request("search", "notes").put("query", keyword))
        assertTrue(result.toString(), result.getBoolean("ok"))
        val parsed = sqlStrings(transport.dataCalls.single().selection.orEmpty())
        assertFalse(parsed.outside.contains("DROP", ignoreCase = true))
        assertFalse(parsed.outside.contains("--"))
        assertTrue(parsed.outside.contains("valid = 1"))
        assertTrue(parsed.outside.contains("encryptedCom = 0"))
        val patterns = parsed.literals.filter { it.startsWith('%') && it.endsWith('%') }
        assertTrue(patterns.isNotEmpty())
        patterns.forEach { assertEquals(keyword, unescapeLike(it.substring(1, it.length - 1))) }
    }

    @Test fun timeFiltersUseSourceUnitsAndCeilBothHalfOpenBounds() {
        for ((source, field, lower, upper) in listOf(
            listOf("photos", "date_modifiedCom", "1", "3"),
            listOf("notes", "update_timeCom", "1", "2001"),
            listOf("calendar_todos", "start_timeCom", "1", "2001"),
        )) {
            val transport = FakeTransport()
            val result = service(transport).execute(request("search", source)
                .put("start_time", "1970-01-01T00:00:00.000000001Z")
                .put("end_time", "1970-01-01T00:00:02.000000001Z"))
            assertTrue(result.toString(), result.getBoolean("ok"))
            val query = transport.dataCalls.single()
            assertTrue(query.selection.orEmpty().contains("CAST($field AS INTEGER) >= $lower"))
            assertTrue(query.selection.orEmpty().contains("CAST($field AS INTEGER) < $upper"))
            assertFalse(query.selection.orEmpty().contains("<= $upper"))
            assertEquals("CAST($field AS INTEGER) DESC, docID DESC", query.sortOrder)
        }
    }

    @Test fun recordIdStaysExactTextAndPhotoUriOnlyUsesPositiveMediaId() {
        val transport = FakeTransport()
        var mediaId: String? = "42"
        transport.onData = { query -> DmpQueryResult.Success(listOf(record(query, mapOf(
            "id" to Long.MAX_VALUE.toString(), "origin_id" to mediaId,
            "uri" to "content://untrusted.example/arbitrary", "title" to "not-a-path.jpg",
        )))) }
        val service = service(transport)
        val result = service.execute(request("search", "photos"))
        val item = result.getJSONArray("items").getJSONObject(0)
        assertTrue(item.get("id") is String)
        assertEquals(Long.MAX_VALUE.toString(), item.getString("id"))
        assertEquals("content://media/external/images/media/42", item.getString("image_tool_input"))
        for (invalid in listOf(null, "0", "-2", "not-a-media-id")) {
            mediaId = invalid
            val invalidItem = service.execute(request("search", "photos")).getJSONArray("items").getJSONObject(0)
            assertFalse(invalidItem.has("image_tool_input"))
        }
    }

    @Test fun missingOrInvalidStableIdCannotBecomeASuccessfulRecord() {
        for (id in listOf(null, "-1", "not-an-id", "9223372036854775808")) {
            val transport = FakeTransport()
            transport.onData = { query -> DmpQueryResult.Success(listOf(record(query, mapOf("id" to id)))) }
            val result = service(transport).execute(request("search", "notes"))
            assertFailure("DMP_OUTPUT_INVALID", result)
        }
    }

    @Test fun lookaheadPaginationDoesNotConsumeTheUnreturnedRecord() {
        val transport = FakeTransport()
        val ids = (1..5).map(Int::toString)
        transport.onData = { query -> DmpQueryResult.Success(ids.drop(query.offset).take(query.limit).map { record(query, mapOf("id" to it)) }) }
        val service = service(transport)
        val received = mutableListOf<String>()
        var offset = 0
        do {
            val result = service.execute(request("search", "notes").put("limit", 2).put("offset", offset))
            assertTrue(result.toString(), result.getBoolean("ok"))
            val items = result.getJSONArray("items")
            for (index in 0 until items.length()) received += items.getJSONObject(index).getString("id")
            if (result.getBoolean("has_more")) {
                assertEquals(offset + items.length(), result.getInt("next_offset"))
                offset = result.getInt("next_offset")
            } else assertTrue(result.isNull("next_offset"))
        } while (result.getBoolean("has_more"))
        assertEquals(ids, received)
        assertEquals(listOf(0, 2, 4), transport.dataCalls.map { it.offset })
        assertTrue(transport.dataCalls.all { it.limit == 3 })
        assertEquals(1, transport.probes.size)
    }

    @Test fun payloadLimitedPageResumesFromTheFirstUnreturnedRow() {
        val transport = FakeTransport()
        val ids = (1..8).map(Int::toString)
        transport.onData = { query ->
            DmpQueryResult.Success(ids.drop(query.offset).take(query.limit).map { id ->
                query.projection.associate { projection ->
                    projection.name to when (projection.name) {
                        "id" -> id
                        "origin_id" -> "origin-$id"
                        "time", "updated_time", "indexed_time" -> "1"
                        else -> "\"".repeat(512)
                    }
                }
            })
        }
        val service = service(transport)
        val first = service.execute(request("search", "memories").put("limit", 8))
        assertTrue(first.toString(), first.getBoolean("ok"))
        assertTrue(first.getBoolean("truncated"))
        assertTrue(first.getBoolean("has_more"))
        val firstItems = first.getJSONArray("items")
        assertEquals(firstItems.length(), first.getInt("next_offset"))
        val second = service.execute(request("search", "memories").put("limit", 8).put("offset", first.getInt("next_offset")))
        assertTrue(second.toString(), second.getBoolean("ok"))
        val secondItems = second.getJSONArray("items")
        val received = (0 until firstItems.length()).map { firstItems.getJSONObject(it).getString("id") } +
            (0 until secondItems.length()).map { secondItems.getJSONObject(it).getString("id") }
        assertEquals(ids, received)
        assertFalse(second.getBoolean("has_more"))
    }

    @Test fun readUsesOriginalDocumentIdAndEmptyResultIsNotFound() {
        val transport = FakeTransport()
        transport.onData = { query -> DmpQueryResult.Success(listOf(record(query, mapOf("id" to "12", "title" to "周期日程")))) }
        val result = service(transport).execute(request("read", "calendar").put("id", "00012"))
        assertTrue(result.toString(), result.getBoolean("ok"))
        val query = transport.dataCalls.single()
        assertTrue(query.selection.orEmpty().contains("docID = 12"))
        assertFalse(query.selection.orEmpty().contains("startTimeCom"))
        assertNull(query.sortOrder)
        assertEquals("indexed_record", result.getJSONArray("items").getJSONObject(0).getString("record_kind"))
        val empty = FakeTransport().also { it.onData = { DmpQueryResult.Success(emptyList()) } }
        assertFailure("PERSONAL_CONTEXT_NOT_FOUND", service(empty).execute(request("read", "notes").put("id", "12")))
    }

    @Test fun calendarCountAddsBothUnionPartsAndSearchPreservesOccurrenceIdentity() {
        for (source in listOf("calendar", "calendar_todos")) {
            val transport = FakeTransport()
            transport.onData = { DmpQueryResult.Success(listOf(mapOf("count" to "2"), mapOf("count" to "3"))) }
            val result = service(transport).execute(request("count", source)
                .put("start_time", "2026-10-05T00:00:00Z").put("end_time", "2026-10-06T00:00:00Z"))
            assertTrue(result.toString(), result.getBoolean("ok"))
            assertEquals(5, result.getInt("count"))
            assertTrue(result.getBoolean("complete_for_query"))
            assertEquals(2, transport.dataCalls.single().limit)
        }
        val transport = FakeTransport()
        transport.onData = { query -> DmpQueryResult.Success(listOf(record(query, mapOf("id" to "5")) + ("instance_id" to "99"))) }
        val item = service(transport).execute(request("search", "calendar")).getJSONArray("items").getJSONObject(0)
        assertEquals("5", item.getString("id"))
        assertEquals("99", item.getString("instance_id"))
        assertEquals("occurrence", item.getString("record_kind"))
    }

    @Test fun countRejectsMalformedOrTruncatedTotals() {
        for (result in listOf(
            DmpQueryResult.Success(emptyList()),
            DmpQueryResult.Success(listOf(mapOf("count" to "-1"))),
            DmpQueryResult.Success(listOf(mapOf("count" to Long.MAX_VALUE.toString()), mapOf("count" to "1"))),
            DmpQueryResult.Success(listOf(mapOf("count" to "2")), setOf("count")),
        )) {
            val transport = FakeTransport().also { it.onData = { result } }
            assertFailure("DMP_OUTPUT_INVALID", service(transport).execute(request("count", "calendar")))
        }
    }

    @Test fun calendarTodoDoesNotClaimToDistinguishOccurrencesWithoutInstanceIds() {
        val transport = FakeTransport()
        transport.onData = { query -> DmpQueryResult.Success(listOf(record(query, mapOf("id" to "12", "text" to "周期待办")))) }
        val service = service(transport)
        val result = service.execute(request("search", "calendar_todos").put("start_time", "2026-10-05T00:00:00Z"))
        assertEquals("indexed_record_or_occurrence", result.getJSONArray("items").getJSONObject(0).getString("record_kind"))
        assertFalse(result.getJSONArray("items").getJSONObject(0).has("instance_id"))
        val original = service.execute(request("read", "calendar_todos").put("id", "12"))
        assertEquals("indexed_record", original.getJSONArray("items").getJSONObject(0).getString("record_kind"))
        assertEquals("original_indexed_document", original.getString("read_scope"))
    }

    @Test fun billSummaryIsDecimalExactAndSeparatesTransfersAndUnknownTypes() {
        val transport = FakeTransport()
        transport.onData = { DmpQueryResult.Success(listOf(
            bill("0.10", "expenses", "meals"), bill("0.20", "expenses", "meals"),
            bill("10.00", "income", "salary"), bill("4.50", "income", "transfer"), bill("2.00", "", "other"),
        )) }
        val result = service(transport).execute(request("bill_summary", "bills"))
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertEquals("CNY", result.getString("currency"))
        assertEquals("0.3", result.getJSONObject("totals").getJSONObject("expenses").getString("amount"))
        assertEquals("10", result.getJSONObject("totals").getJSONObject("income").getString("amount"))
        assertEquals("4.5", result.getJSONObject("totals").getJSONObject("transfers").getString("amount"))
        assertEquals("2", result.getJSONObject("totals").getJSONObject("unclassified").getString("amount"))
        assertTrue(result.getBoolean("complete_for_query"))
        assertEquals(1001, transport.dataCalls.single().limit)
        assertEquals(setOf("amount", "transaction_type", "category"), transport.dataCalls.single().projection.map { it.name }.toSet())
    }

    @Test fun billSummaryNeverReturnsPartialTotalsAtTheRowOrFieldLimit() {
        for ((rows, fields, code) in listOf(
            Triple(List(1001) { bill("1.00", "expenses", "meals") }, emptySet(), "PERSONAL_CONTEXT_AGGREGATE_LIMIT"),
            Triple(listOf(bill("1.00", "expenses", "meals")), setOf("amount"), "PERSONAL_CONTEXT_AGGREGATE_INCOMPLETE"),
            Triple(listOf(bill("unknown", "expenses", "meals")), emptySet(), "PERSONAL_CONTEXT_INVALID_AMOUNT"),
        )) {
            val transport = FakeTransport().also { it.onData = { DmpQueryResult.Success(rows, fields) } }
            val result = service(transport).execute(request("bill_summary", "bills"))
            assertFailure(code, result)
            assertFalse(result.has("totals"))
            assertFalse(result.has("complete_for_query"))
        }
    }

    @Test fun missingProtectionTimeOrSearchColumnsCannotWidenTheDataQuery() {
        for ((source, missing) in listOf("notes" to "encryptedCom", "memories" to "recycleTimeCom", "bills" to "recycleTimeCom", "photos" to "typeCom", "notes" to "valid", "notes" to "docID")) {
            val transport = FakeTransport(mutableSetOf(missing))
            assertFailure("PERSONAL_CONTEXT_SCHEMA_UNSUPPORTED", service(transport).execute(request("search", source)))
            assertTrue(transport.dataCalls.isEmpty())
        }
        val time = FakeTransport(mutableSetOf("update_timeCom"))
        assertFailure("PERSONAL_CONTEXT_FIELD_UNAVAILABLE", service(time).execute(request("count", "notes").put("start_time", "2026-01-01T00:00:00Z")))
        assertTrue(time.dataCalls.isEmpty())
        val search = FakeTransport(mutableSetOf("titleCom", "textCom", "folder_nameCom"))
        assertFailure("PERSONAL_CONTEXT_FIELD_UNAVAILABLE", service(search).execute(request("search", "notes").put("query", "内容")))
        assertTrue(search.dataCalls.isEmpty())
    }

    @Test fun schemaDriftRefreshesOnceAndProviderDiagnosticsAreNotReturned() {
        val transport = FakeTransport()
        var dataAttempts = 0
        transport.onData = { query ->
            if (++dataAttempts == 1) {
                transport.missingColumns += "caption_content0Com"
                DmpQueryResult.Failure("DMP_COLUMN_MISSING", "caption_content0Com")
            } else DmpQueryResult.Success(listOf(record(query, mapOf("id" to "8", "caption" to "兼容描述"))))
        }
        val result = service(transport).execute(request("search", "photos"))
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertEquals(2, dataAttempts)
        assertEquals("caption_contentCom", transport.dataCalls.last().projection.single { it.name == "caption" }.expression)
        val rejected = FakeTransport().also {
            it.onData = { DmpQueryResult.Failure("DMP_PROVIDER_ERROR", "PRIVATE_PROVIDER_BODY") }
        }
        val failure = service(rejected).execute(request("search", "notes"))
        assertFailure("DMP_PROVIDER_ERROR", failure)
        assertFalse(failure.toString().contains("PRIVATE_PROVIDER_BODY"))
        assertFalse(failure.has("items"))
        assertFalse(failure.has("count"))
    }

    private fun service(transport: DmpQueryTransport) = PersonalContextQueryService(transport) { Instant.parse("2026-10-05T00:00:00Z") }
    private fun request(action: String, source: String) = JSONObject().put("action", action).put("source", source)
    private fun bill(amount: String, type: String, category: String) = mapOf("amount" to amount, "transaction_type" to type, "category" to category)
    private fun assertFailure(code: String, result: JSONObject) {
        assertFalse(result.toString(), result.getBoolean("ok"))
        assertEquals(code, result.getString("code"))
    }

    private class FakeTransport(val missingColumns: MutableSet<String> = mutableSetOf()) : DmpQueryTransport {
        val calls = mutableListOf<DmpQuery>()
        val probes: List<DmpQuery> get() = calls.filter { it.selection == "0" }
        val dataCalls: List<DmpQuery> get() = calls.filter { it.selection != "0" }
        var onData: (DmpQuery) -> DmpQueryResult = { query ->
            when {
                query.projection.singleOrNull()?.expression == "COUNT(*)" -> DmpQueryResult.Success(listOf(mapOf("count" to "0")))
                query.projection.map { it.name }.toSet() == setOf("amount", "transaction_type", "category") -> DmpQueryResult.Success(emptyList())
                else -> DmpQueryResult.Success(listOf(record(query, mapOf("id" to "1"))))
            }
        }

        override fun query(query: DmpQuery): DmpQueryResult {
            calls += query
            if (query.selection != "0") return onData(query)
            val missing = missingColumns.firstOrNull { column -> query.projection.any { it.expression == "COUNT($column)" } }
            if (missing != null) return DmpQueryResult.Failure("DMP_COLUMN_MISSING", missing)
            return DmpQueryResult.Success(listOf(query.projection.associate { it.name to "0" }))
        }
    }

    private data class SqlStrings(val outside: String, val literals: List<String>)
    private fun sqlStrings(sql: String): SqlStrings {
        val outside = StringBuilder()
        val literals = mutableListOf<String>()
        var index = 0
        while (index < sql.length) {
            if (sql[index] != '\'') { outside.append(sql[index++]); continue }
            index++
            val value = StringBuilder()
            var closed = false
            while (index < sql.length) {
                val character = sql[index++]
                if (character != '\'') value.append(character)
                else if (index < sql.length && sql[index] == '\'') { value.append('\''); index++ }
                else { closed = true; break }
            }
            assertTrue("Unterminated SQL string", closed)
            literals += value.toString()
            outside.append('?')
        }
        return SqlStrings(outside.toString(), literals)
    }

    private fun unescapeLike(pattern: String): String = buildString {
        var index = 0
        while (index < pattern.length) {
            val character = pattern[index++]
            if (character == '\\') {
                assertTrue("Dangling LIKE escape", index < pattern.length)
                append(pattern[index++])
            } else {
                assertFalse("Unescaped LIKE wildcard", character == '%' || character == '_')
                append(character)
            }
        }
    }

    private companion object {
        fun record(query: DmpQuery, values: Map<String, String?>): Map<String, String?> =
            query.projection.associate { it.name to values[it.name] }
    }
}
