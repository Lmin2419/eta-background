package io.github.mangi.eta.agent.context

import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalContextRequestTest {
    @Test fun sourcesCanListStaticCatalogOrInspectOneSourceAndSearchHasBoundedDefaults() {
        assertNull(parse("""{"action":"sources"}""").source)
        assertEquals("notes", parse("""{"action":"sources","source":"notes"}""").source?.id)
        val search = parse("""{"action":"search","source":"photos"}""")
        assertEquals(PersonalContextAction.SEARCH, search.action)
        assertEquals(10, search.limit)
        assertEquals(0, search.offset)
        assertEquals(PersonalContextSort.NEWEST, search.sort)
    }

    @Test fun missingAndUnsupportedSourcesAreRejected() {
        val invalid = listOf(
            """{"action":"search"}""",
            """{"action":"search","source":"flash_notes"}""",
            """{"action":"search","source":"localApp"}""",
            """{"action":"bill_summary","source":"notes"}""",
        )
        invalid.forEach { expectInvalid { parse(it) } }
        assertEquals(PersonalContextAction.BILL_SUMMARY, parse("""{"action":"bill_summary","source":"bills"}""").action)
    }

    @Test fun extraFieldsDoNotChangeTheSelectedOperation() {
        val request = parse("""{"action":"read","source":"notes","id":"1","limit":1,"extra":true}""")
        assertEquals(PersonalContextAction.READ, request.action)
        assertEquals("notes", request.source?.id)
        assertEquals("1", request.id)
    }

    @Test fun stableIdsAreNonnegativeIntegerTextWithinLongRange() {
        assertEquals("0", parse("""{"action":"read","source":"memories","id":"000"}""").id)
        assertEquals(Long.MAX_VALUE.toString(), parse("""{"action":"read","source":"memories","id":"9223372036854775807"}""").id)
        for (id in listOf("-1", "1.0", "1 OR 1=1", "9223372036854775808", " 1", "")) {
            expectInvalid { PersonalContextRequest.parse(JSONObject().put("action", "read").put("source", "notes").put("id", id)) }
        }
        expectInvalid { parse("""{"action":"read","source":"notes","id":1}""") }
        expectInvalid { parse("""{"action":"read","source":"notes"}""") }
    }

    @Test fun limitsAreValidatedWithoutStringCoercionOrClamping() {
        for (field in listOf("limit", "offset")) {
            for (value in listOf<Any>("10", 1.5, true, JSONObject.NULL)) {
                expectInvalid { PersonalContextRequest.parse(JSONObject().put("action", "search").put("source", "notes").put(field, value)) }
            }
        }
        for ((field, value) in listOf("limit" to 0, "limit" to 31, "offset" to -1, "offset" to 10001)) {
            expectInvalid { PersonalContextRequest.parse(JSONObject().put("action", "search").put("source", "notes").put(field, value)) }
        }
        val maximum = parse("""{"action":"search","source":"notes","limit":30,"offset":10000,"sort":"oldest"}""")
        assertEquals(30, maximum.limit)
        assertEquals(10000, maximum.offset)
        assertEquals(PersonalContextSort.OLDEST, maximum.sort)
    }

    @Test fun queryLimitCountsUnicodeCharactersAndErrorsDoNotEchoPrivateInput() {
        val arguments = JSONObject().put("action", "search").put("source", "notes")
        val emoji = "😀".repeat(200)
        assertEquals(emoji, PersonalContextRequest.parse(arguments.put("query", emoji)).query)
        expectInvalid { PersonalContextRequest.parse(arguments.put("query", emoji + "甲")) }
        expectInvalid { PersonalContextRequest.parse(arguments.put("query", "private\u0000content")) }
        try {
            PersonalContextRequest.parse(JSONObject().put("action", "search").put("source", "私人数据源标识"))
            throw AssertionError("invalid source accepted")
        } catch (error: PersonalContextArgumentException) {
            assertEquals("INVALID_ARGUMENT", error.code)
            assertFalse(error.message.orEmpty().contains("私人数据源标识"))
        }
    }

    @Test fun offsetTimesRetainNanosecondsAndDiscreteBoundsPreserveHalfOpenInterval() {
        val request = parse("""{"action":"search","source":"photos","start_time":"1970-01-01T08:00:00.000000001+08:00","end_time":"1970-01-01T00:00:02.000000001Z"}""")
        assertEquals(Instant.ofEpochSecond(0, 1), request.start)
        val seconds = PersonalContextTimeUnit.SECONDS.toBounds(request.start, request.end)
        assertEquals(1L, seconds.startInclusive)
        assertEquals(3L, seconds.endExclusive)
        val milliseconds = PersonalContextTimeUnit.MILLISECONDS.toBounds(request.start, request.end)
        assertEquals(1L, milliseconds.startInclusive)
        assertEquals(2001L, milliseconds.endExclusive)
        val negative = PersonalContextTimeUnit.MILLISECONDS.toBounds(Instant.ofEpochSecond(-1, 999_999_999), Instant.EPOCH)
        assertEquals(0L, negative.startInclusive)
        assertEquals(0L, negative.endExclusive)
        assertEquals(Long.MIN_VALUE, PersonalContextTimeUnit.MILLISECONDS.toBounds(Instant.ofEpochMilli(Long.MIN_VALUE), null).startInclusive)
        expectInvalid { PersonalContextTimeUnit.MILLISECONDS.toBounds(Instant.ofEpochMilli(Long.MAX_VALUE).plusNanos(1), null) }
    }

    @Test fun datesRequireValidCalendarValuesOffsetsAndIncreasingRange() {
        val invalidTimes = listOf("2026-10-05", "2026-10-05T12:00:00", "2026-02-30T12:00:00Z", "2026-10-05T24:00:00Z", "2026-10-05T12:00:00+08:00:30")
        for (time in invalidTimes) {
            expectInvalid { PersonalContextRequest.parse(JSONObject().put("action", "count").put("source", "bills").put("start_time", time)) }
        }
        expectInvalid { parse("""{"action":"count","source":"bills","start_time":"2026-10-05T08:00+08:00","end_time":"2026-10-05T00:00Z"}""") }
        assertEquals(Instant.parse("2026-10-05T00:00:00Z"), parse("""{"action":"count","source":"bills","start_time":"2026-10-05T08:00+08:00"}""").start)
    }

    @Test fun collectionIndexTimeCannotBeUsedAsBusinessTimeFilter() {
        val request = parse("""{"action":"search","source":"collections","sort":"oldest"}""")
        assertEquals("indexed_time", request.source?.sortField)
        assertEquals("indexed", request.source?.sortTimeKind)
        expectInvalid { parse("""{"action":"count","source":"collections","start_time":"2026-01-01T00:00Z"}""") }
    }

    private fun parse(json: String) = PersonalContextRequest.parse(JSONObject(json))
    private fun expectInvalid(block: () -> Unit) {
        try {
            block()
            throw AssertionError("invalid request accepted")
        } catch (error: PersonalContextArgumentException) {
            assertEquals("INVALID_ARGUMENT", error.code)
            assertTrue(error.message.orEmpty().isNotBlank())
        }
    }
}
