package io.github.mangi.eta.agent.context

import io.github.mangi.eta.agent.device.BoundedRootCommandExecutor
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmpQueryTransportTest {
    @Test
    fun decodesCommasNewlinesEmptyNullLiteralNullAndUnicodeExactly() {
        val values = listOf("逗号, 等号=\n下一行\r\n😀", "", null, "NULL", "null")
        val query = DmpQuery("com.example#records", values.indices.map { DmpProjection("field$it", "source$it") })
        val result = transport(row(0, values)).query(query) as DmpQueryResult.Success
        assertEquals(1, result.rows.size)
        assertEquals(values.indices.map { "field$it" }.toSet(), result.rows[0].keys)
        values.forEachIndexed { index, value -> assertEquals(value, result.rows[0]["field$index"]) }
        assertTrue(result.truncatedFields.isEmpty())
    }

    @Test
    fun fieldTruncationUsesUnicodeCharactersAndIsReportedByLogicalName() {
        val query = DmpQuery("persona/records", listOf(DmpProjection("text", "bodyCom", 3)))
        val output = row(0, listOf("你好😀"), setOf(0)) + "\n" + row(1, listOf(null))
        val result = transport(output).query(query) as DmpQueryResult.Success
        assertEquals("你好😀", result.rows[0]["text"])
        assertNull(result.rows[1]["text"])
        assertEquals(setOf("text"), result.truncatedFields)
    }

    @Test
    fun buildsFixedAuthorityEncodedResourceScopedUserAndQuotedClauses() {
        var command = ""
        val executor = RootDmpQueryTransport(userId = 12) { script, timeout, budget ->
            command = script
            assertEquals(15_000L, timeout)
            assertEquals(2 * 1024 * 1024, budget)
            ok(row(0, listOf("example")))
        }
        val selection = "titleCom = 'O''Brien \$(printf ignored) `printf ignored`\nvalue'"
        val query = DmpQuery("com.example#records", listOf(DmpProjection("title", "titleCom", 7)), selection, "dateCom DESC", 3, 4)
        assertTrue(executor.query(query) is DmpQueryResult.Success)
        assertTrue(command.startsWith("content query --user 12 --uri 'content://com.oplus.oss.provider.PersonalizationProvider/com.example%23records?report=true&limit=3&offset=4' --projection '"))
        assertTrue(command.contains("hex(substr(CAST((titleCom) AS TEXT),1,7))"))
        assertTrue(command.contains("instr(CAST((titleCom) AS TEXT),char(0))>0"))
        assertTrue(command.contains("length(CAST((titleCom) AS TEXT))>7"))
        assertTrue(command.contains("END AS eta_v0:"))
        assertTrue(command.contains("END AS eta_t0'"))
        assertTrue(command.contains(" --where 'titleCom = '\\''O'\\'''\\''Brien \$(printf ignored) `printf ignored`\nvalue'\\'''"))
        assertTrue(command.endsWith(" --sort 'dateCom DESC'"))
        val uriText = command.substringAfter("--uri '").substringBefore('\'')
        val uri = URI(uriText)
        assertNull(uri.fragment)
        assertEquals("/com.example#records", uri.path)
        assertFalse(uriText.contains("%2523"))
    }

    @Test
    fun supportsControlledAggregateExpressionsAndSingleQueryLargeBatch() {
        var command = ""
        val executor = RootDmpQueryTransport(0) { script, _, _ -> command = script; ok(row(0, listOf("0", "0", "0"))) }
        val result = executor.query(DmpQuery("persona/bills", listOf(
            DmpProjection("count", "COUNT(*)", 128),
            DmpProjection("known", "COUNT(amountCom)", 128),
            DmpProjection("amount", "CAST(amountCom AS TEXT)", 128),
        ), selection = "0", limit = 1001))
        assertTrue(result is DmpQueryResult.Success)
        assertTrue(command.contains("limit=1001"))
        assertTrue(command.contains("COUNT(*)"))
        assertTrue(command.endsWith(" --where '0'"))
    }

    @Test
    fun rejectsInvalidResourceQueryNamesAndBudgetsBeforeExecuting() {
        var calls = 0
        val executor = RootDmpQueryTransport(0) { _, _, _ -> calls++; error("Unexpected execution") }
        val base = query()
        val invalid = listOf(
            base.copy(resource = "../records"), base.copy(resource = "/records"), base.copy(resource = "a//b"),
            base.copy(resource = "content://different/records"), base.copy(resource = "a?limit=999999"),
            base.copy(resource = "a%23b"), base.copy(resource = "a#b#c"), base.copy(resource = "a'\$(id)"),
            base.copy(limit = 0), base.copy(limit = 1002), base.copy(offset = -1),
            base.copy(projection = emptyList()), base.copy(projection = List(17) { DmpProjection("f$it", "field$it") }),
            base.copy(projection = listOf(DmpProjection("same", "one"), DmpProjection("same", "two"))),
            base.copy(projection = listOf(DmpProjection("bad,name", "one"))),
            base.copy(projection = listOf(DmpProjection("text", "source", 0))),
            base.copy(projection = listOf(DmpProjection("text", "source", 2049))),
            base.copy(projection = listOf(DmpProjection("text", "'a:b'"))),
            base.copy(selection = "bad\u0000value"),
        )
        invalid.forEach { assertEquals(DmpQueryResult.Failure("DMP_INVALID_QUERY"), executor.query(it)) }
        assertEquals(0, calls)
        assertEquals(DmpQueryResult.Failure("DMP_INVALID_QUERY"), RootDmpQueryTransport(-1) { _, _, _ -> error("Unexpected execution") }.query(base))
    }

    @Test
    fun missingColumnIsReportedOnlyWhenItIsAValidatedReference() {
        val request = DmpQuery("persona/records", listOf(DmpProjection("summary", "COALESCE(optionalCom, '')")), selection = "titleCom = 'private_literal'")
        listOf(
            "Row: 0 error_message=no such column: optionalCom",
            "android.database.sqlite.SQLiteException: no such column: table.optionalCom (code 1)",
            "java.lang.IllegalArgumentException: Invalid column optionalCom",
        ).forEach { output ->
            assertEquals(DmpQueryResult.Failure("DMP_COLUMN_MISSING", "optionalCom"), transport(output).query(request))
        }
        listOf("private_literal", "notReferenced", "optionalCom-bad", "CAST").forEach { column ->
            val result = transport("no such column: $column").query(request)
            assertEquals(DmpQueryResult.Failure("DMP_COLUMN_MISSING"), result)
        }
        assertEquals(
            DmpQueryResult.Failure("DMP_COLUMN_MISSING", "caption_contentCom"),
            transport("Row: 0 error=no such column: caption_contentCom: , while compiling: SELECT private_query")
                .query(DmpQuery("persona/records", listOf(DmpProjection("caption", "caption_contentCom")))),
        )
    }

    @Test
    fun errorCursorsAndProviderExceptionsCannotBecomeSuccessfulRows() {
        assertEquals(DmpQueryResult.Failure("DMP_PROVIDER_ERROR"), transport("Row: 0 error=unavailable").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_PROVIDER_ERROR"), transport(row(0, listOf("ok")) + "\nRow: 1 error_code=broken").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_ACCESS_DENIED"), transport("java.lang.SecurityException: Permission Denial").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_RESOURCE_UNAVAILABLE"), transport("Unknown URL content://example/unknown").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_RESOURCE_UNAVAILABLE"), transport("Row: 0 error=invalid args: example is not a legal resource.").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_RESOURCE_UNAVAILABLE"), transport("android.database.sqlite.SQLiteException: no such table: unknown").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_PROVIDER_ERROR"), transport("Error while accessing provider: example").query(query()))
    }

    @Test
    fun noResultRequiresAnActualAggregateCursorBeforeReturningEmpty() {
        val commands = mutableListOf<String>()
        val executor = RootDmpQueryTransport(10) { command, _, _ ->
            commands += command
            if (commands.size == 1) ok("No result found.\n") else ok(row(0, listOf("0")))
        }
        val request = query().copy(selection = "titleCom = 'not found'", offset = 30)
        assertEquals(DmpQueryResult.Success(emptyList()), executor.query(request))
        assertEquals(2, commands.size)
        assertTrue(commands[1].contains("COUNT(*)"))
        assertTrue(commands[1].contains("limit=1&offset=0"))
        assertTrue(commands[1].endsWith(" --where '0'"))
        assertEquals(DmpQueryResult.Failure("DMP_RESOURCE_UNAVAILABLE"), transport("No result found.").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_OUTPUT_INVALID"), transport("").query(query()))
    }

    @Test
    fun rejectsMalformedRowsHexUtf8FlagsAndUnrequestedColumns() {
        val invalid = listOf(
            "Row: 1 eta_v0=V61, eta_t0=0", "Row: 00 eta_v0=V61, eta_t0=0",
            "Row: 0 eta_v0=V6, eta_t0=0", "Row: 0 eta_v0=VGG, eta_t0=0",
            "Row: 0 eta_v0=N, eta_t0=1", "Row: 0 eta_v0=V61, eta_t0=2",
            "Row: 0 eta_v0=V61, eta_t1=0", "Row: 0 eta_v0=V61",
            "Row: 0 eta_v0=V61, eta_t0=0, extra=secret", "Row: 0 eta_v0=NULL, eta_t0=0",
            "unexpected line\n" + row(0, listOf("a")),
        )
        invalid.forEach { assertEquals(it, DmpQueryResult.Failure("DMP_OUTPUT_INVALID"), transport(it).query(query())) }
        assertEquals(DmpQueryResult.Failure("DMP_INVALID_UTF8"), transport("Row: 0 eta_v0=VC328, eta_t0=0").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_OUTPUT_INVALID"), transport(row(0, listOf("long"))).query(query().copy(projection = listOf(DmpProjection("text", "bodyCom", 3)))))
    }

    @Test
    fun rejectsRawOutputTruncationAndMoreRowsThanRequested() {
        val request = query().copy(limit = 1)
        assertEquals(DmpQueryResult.Failure("DMP_OUTPUT_LIMIT"), transport(row(0, listOf("a")) + "\n" + row(1, listOf("b"))).query(request))
        val executor = RootDmpQueryTransport(0) { _, _, _ -> ok(row(0, listOf("a"))).copy(truncated = true) }
        assertEquals(DmpQueryResult.Failure("DMP_OUTPUT_LIMIT"), executor.query(request))
        assertEquals(DmpQueryResult.Failure("DMP_OUTPUT_LIMIT"), transport("a".repeat(2 * 1024 * 1024 + 1)).query(request))
    }

    @Test
    fun embeddedNulCannotBeSilentlyShortenedBySqliteTextFunctions() {
        assertEquals(DmpQueryResult.Failure("DMP_BINARY_TEXT"), transport("Row: 0 eta_v0=E, eta_t0=0").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_BINARY_TEXT"), transport("Row: 0 eta_v0=V610062, eta_t0=0").query(query()))
    }

    @Test
    fun preservesRootFailuresAndNeverExposesDiagnosticText() {
        for (code in listOf("ROOT_REQUIRED", "ROOT_EXECUTOR_CLOSED", "ROOT_UNAVAILABLE")) {
            val executor = RootDmpQueryTransport(0) { _, _, _ -> BoundedRootCommandExecutor.Result.failed(code) }
            assertEquals(DmpQueryResult.Failure(code), executor.query(query()))
        }
        val timedOut = RootDmpQueryTransport(0) { _, _, _ -> ok("private query body").copy(timedOut = true, truncated = true) }
        assertEquals(DmpQueryResult.Failure("DMP_QUERY_TIMEOUT"), timedOut.query(query()))
        val failure = RootDmpQueryTransport(0) { _, _, _ -> ok(row(0, listOf("a"))).copy(stderr = "private diagnostic", exitCode = 1) }
        assertEquals(DmpQueryResult.Failure("DMP_PROVIDER_ERROR"), failure.query(query()))
    }

    @Test
    fun calendarUnionSortsWithNumericProjectionAliasesAndRetainsInstanceIdentity() {
        var command = ""
        val output = row(0, listOf("Series")) + ", eta_sort0=1000, eta_sort1=30, instanceId=NULL\n" +
            row(1, listOf("Occurrence")) + ", eta_sort0=1100, eta_sort1=31, instanceId=77"
        val executor = RootDmpQueryTransport(10) { script, _, _ -> command = script; ok(output) }
        val result = executor.query(DmpQuery(
            "calendar", listOf(DmpProjection("title", "nameCom")),
            selection = "CAST(startTimeCom AS INTEGER)>=1000",
            sortOrder = "CAST(startTimeCom AS INTEGER) DESC, docID DESC",
        )) as DmpQueryResult.Success
        assertEquals(listOf(
            mapOf("title" to "Series", "instance_id" to null),
            mapOf("title" to "Occurrence", "instance_id" to "77"),
        ), result.rows)
        assertTrue(command.contains("CAST(startTimeCom AS INTEGER) AS eta_sort0:CAST(docID AS INTEGER) AS eta_sort1"))
        assertTrue(command.endsWith(" --sort 'eta_sort0 DESC, eta_sort1 DESC'"))
        assertFalse(result.rows.any { row -> row.keys.any { it.startsWith("eta_sort") } })
    }

    @Test
    fun calendarTodoNumericSortColumnsAreDiscardedWithoutAcceptingInstanceId() {
        var command = ""
        val executor = RootDmpQueryTransport(0) { script, _, _ ->
            command = script
            ok(row(0, listOf("Task")) + ", eta_sort0=NULL, eta_sort1=9")
        }
        val request = DmpQuery("calendarTodo", listOf(DmpProjection("text", "contentCom")), sortOrder = "start_timeCom ASC, docID")
        val result = executor.query(request) as DmpQueryResult.Success
        assertEquals(listOf(mapOf("text" to "Task")), result.rows)
        assertTrue(command.endsWith(" --sort 'eta_sort0 ASC, eta_sort1 ASC'"))
        assertEquals(
            DmpQueryResult.Failure("DMP_OUTPUT_INVALID"),
            transport(row(0, listOf("Task")) + ", eta_sort0=1000, eta_sort1=9, instanceId=NULL").query(request),
        )
    }

    @Test
    fun calendarCountPreservesBothUnionRowsForCallerAggregation() {
        var command = ""
        val executor = RootDmpQueryTransport(0) { script, _, _ ->
            command = script
            ok(row(0, listOf("3")) + ", instanceId=NULL\n" + row(1, listOf("7")) + ", instanceId=12")
        }
        val result = executor.query(DmpQuery("calendar", listOf(DmpProjection("count", "COUNT(*)", 128)),
            selection = "startTimeCom>=1000", limit = 2)) as DmpQueryResult.Success
        assertEquals(listOf("3", "7"), result.rows.map { it["count"] })
        assertFalse(command.contains("eta_sort"))
        assertFalse(command.contains(" --sort "))
        assertTrue(command.contains("limit=2"))
    }

    @Test
    fun calendarCompatibilityStillRejectsUnknownColumnsInvalidIdsAndNonIntegers() {
        val calendar = DmpQuery("calendar", listOf(DmpProjection("title", "nameCom")))
        listOf(
            ", other=0", ", instanceId=-1", ", instanceId=1.0", ", instanceId=null",
            ", instanceId=9223372036854775808", ", instanceId=0, instanceId=1", ", eta_sort0=0",
        ).forEach { extra ->
            assertEquals(extra, DmpQueryResult.Failure("DMP_OUTPUT_INVALID"), transport(row(0, listOf("Title")) + extra).query(calendar))
        }
        val sorted = calendar.copy(sortOrder = "docID DESC")
        listOf("1.0", "V31", "1e3", "9223372036854775808").forEach { value ->
            assertEquals(value, DmpQueryResult.Failure("DMP_OUTPUT_INVALID"), transport(row(0, listOf("Title")) + ", eta_sort0=$value").query(sorted))
        }
        assertEquals(DmpQueryResult.Failure("DMP_OUTPUT_INVALID"), transport(row(0, listOf("Title")) + ", instanceId=3").query(query()))
        assertEquals(DmpQueryResult.Failure("DMP_OUTPUT_INVALID"), transport(row(0, listOf("Title")) + ", instanceId=3, eta_sort0=2").query(sorted))
        assertEquals(DmpQueryResult.Failure("DMP_INVALID_QUERY"), transport("").query(calendar.copy(projection = listOf(DmpProjection("instance_id", "docID")))))
    }

    @Test
    fun unsupportedCalendarSortIsRejectedBeforeCallingProvider() {
        var calls = 0
        val executor = RootDmpQueryTransport(0) { _, _, _ -> calls++; error("Unexpected execution") }
        listOf("COALESCE(startTimeCom, 0) DESC", "docID COLLATE NOCASE", "docID DESC; SELECT 1").forEach { order ->
            assertEquals(DmpQueryResult.Failure("DMP_INVALID_QUERY"), executor.query(DmpQuery("calendar",
                listOf(DmpProjection("title", "nameCom")), sortOrder = order)))
        }
        assertEquals(0, calls)
        val ordinary = RootDmpQueryTransport(0) { command, _, _ ->
            assertFalse(command.contains("eta_sort"))
            assertTrue(command.endsWith(" --sort 'titleCom COLLATE NOCASE'"))
            ok(row(0, listOf("Title")))
        }
        assertTrue(ordinary.query(query().copy(sortOrder = "titleCom COLLATE NOCASE")) is DmpQueryResult.Success)
    }

    @Test
    fun emptyCalendarIsVerifiedAgainstTheOriginalExpandedSelection() {
        val commands = mutableListOf<String>()
        val executor = RootDmpQueryTransport(0) { command, _, _ ->
            commands += command
            if (commands.size == 1) ok("No result found.")
            else ok(row(0, listOf("0")) + ", instanceId=NULL\n" + row(1, listOf("0")) + ", instanceId=NULL")
        }
        val selection = "CAST(startTimeCom AS INTEGER)>=1000"
        val result = executor.query(DmpQuery("calendar", listOf(DmpProjection("title", "nameCom")), selection, "docID DESC"))
        assertEquals(DmpQueryResult.Success(emptyList()), result)
        assertEquals(2, commands.size)
        assertTrue(commands[1].contains("limit=2&offset=0"))
        assertTrue(commands[1].endsWith(" --where '$selection'"))
        assertFalse(commands[1].contains(" --sort "))
        assertEquals(DmpQueryResult.Failure("DMP_PROVIDER_ERROR"), transport("No result found.")
            .query(DmpQuery("calendar", listOf(DmpProjection("title", "nameCom")), selection)))
    }

    @Test
    fun emptyCalendarPageIsValidOnlyWhenTotalDoesNotExceedRequestedOffset() {
        for ((offset, expected) in listOf(
            7 to DmpQueryResult.Success(emptyList()),
            10 to DmpQueryResult.Success(emptyList()),
            5 to DmpQueryResult.Failure("DMP_OUTPUT_INVALID"),
        )) {
            var calls = 0
            val executor = RootDmpQueryTransport(0) { _, _, _ ->
                if (++calls == 1) ok("No result found.") else ok(row(0, listOf("2")) + "\n" + row(1, listOf("5")))
            }
            assertEquals(expected, executor.query(DmpQuery("calendarTodo", listOf(DmpProjection("text", "contentCom")),
                selection = "start_timeCom>=1000", offset = offset)))
        }
    }

    @Test
    fun nullCalendarSelectionStaysNullAndMissingAggregateCursorIsAnError() {
        val commands = mutableListOf<String>()
        val executor = RootDmpQueryTransport(0) { command, _, _ ->
            commands += command
            if (commands.size == 1) ok("No result found.") else ok(row(0, listOf("0")))
        }
        assertEquals(DmpQueryResult.Success(emptyList()), executor.query(DmpQuery("calendar", listOf(DmpProjection("title", "nameCom")))))
        assertFalse(commands[1].contains(" --where "))
        var calls = 0
        val missingCount = RootDmpQueryTransport(0) { _, _, _ -> calls++; ok("No result found.") }
        assertEquals(DmpQueryResult.Failure("DMP_OUTPUT_INVALID"), missingCount.query(DmpQuery("calendar",
            listOf(DmpProjection("count", "COUNT(*)")), selection = "startTimeCom>=1000", limit = 2)))
        assertEquals(1, calls)
    }

    private fun query() = DmpQuery("persona/records", listOf(DmpProjection("text", "bodyCom")))

    private fun transport(output: String) = RootDmpQueryTransport(0) { _, _, _ -> ok(output) }

    private fun ok(output: String) = BoundedRootCommandExecutor.Result(0, output, "", false, false)

    private fun row(index: Int, values: List<String?>, truncated: Set<Int> = emptySet()): String =
        "Row: $index " + values.flatMapIndexed { column, value ->
            val encoded = if (value == null) "N" else "V" + value.toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it.toInt() and 255) }
            listOf("eta_v$column=$encoded", "eta_t$column=${if (column in truncated) 1 else 0}")
        }.joinToString(", ")
}
