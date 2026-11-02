package io.github.mangi.eta.data.repository

import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class NotificationHistoryRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: NotificationHistoryRepository
    private var clockNow = 0L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("eta_notification_history.db")
        clockNow = System.currentTimeMillis()
        repository = NotificationHistoryRepository(context) { clockNow }
    }

    @After
    fun tearDown() {
        context.deleteDatabase("eta_notification_history.db")
    }

    @Test
    fun `query and package filters only return matching notification`() {
        val now = clockNow
        repository.record("one", "com.example.food", "订单配送中", "骑手即将送达", null, now)
        repository.record("two", "com.example.chat", "新消息", "今晚见", null, now - 1)

        val result = JSONObject(repository.search("骑手", "com.example.food", 24, 20))

        assertEquals(1, result.getInt("count"))
        assertEquals("com.example.food", result.getJSONArray("items").getJSONObject(0).getString("package_name"))
    }

    @Test
    fun `same notification key replaces prior content and expired records are excluded`() {
        val now = clockNow
        repository.record("same", "com.example.food", "旧状态", null, null, now - 1)
        repository.record("same", "com.example.food", "已送达", null, null, now)
        repository.record("expired", "com.example.food", "很久以前", null, null, now - 8L * 24 * 60 * 60 * 1_000)

        val serialized = repository.search("", "", 168, 20)
        val result = JSONObject(serialized)

        assertEquals(1, result.getInt("count"))
        assertFalse(serialized.contains("旧状态"))
        assertFalse(serialized.contains("很久以前"))
        assertEquals(1, result.getInt("total_matches"))
        assertEquals("notification_records", result.getString("count_unit"))
        assertFalse(result.getBoolean("complete_chat_history"))
    }

    @Test
    fun `sixty one records sharing a timestamp are paged without duplicates or gaps`() {
        repeat(61) { index -> record("message-$index", clockNow) }

        val first = search(limit = 50)
        val second = search(limit = 50, cursor = first.getString("next_cursor"))

        assertEquals(50, first.getInt("count"))
        assertEquals(11, second.getInt("count"))
        assertEquals(61, first.getInt("total_matches"))
        assertEquals(61, second.getInt("total_matches"))
        assertTrue(first.getBoolean("has_more"))
        assertFalse(second.getBoolean("has_more"))
        assertFalse(second.getBoolean("truncated"))
        assertTrue(second.isNull("next_cursor"))
        val ids = recordIds(first) + recordIds(second)
        assertEquals(61, ids.size)
        assertEquals(61, ids.toSet().size)
    }

    @Test
    fun `explicit interval includes start but excludes end`() {
        val start = clockNow - 5_000
        val end = clockNow - 1_000
        record("before", start - 1)
        record("start", start)
        record("inside", end - 1)
        record("end", end)

        val result = search(startTime = start, endTime = end)

        assertEquals(listOf("inside", "start"), titles(result))
        assertEquals(2, result.getInt("total_matches"))
        assertEquals(Instant.ofEpochMilli(start).toString(), result.getString("query_start_time"))
        assertEquals(Instant.ofEpochMilli(end).toString(), result.getString("query_end_time"))
    }

    @Test
    fun `default end includes current millisecond and stays frozen across pages`() {
        record("current", clockNow)
        record("earlier", clockNow - 1)
        val first = search(limit = 1)
        assertEquals(listOf("current"), titles(first))
        clockNow += 10_000
        record("arrived later", clockNow)

        val second = search(limit = 1, cursor = first.getString("next_cursor"))

        assertTrue(second.getBoolean("ok"))
        assertEquals(listOf("earlier"), titles(second))
        assertEquals(2, second.getInt("total_matches"))
        assertEquals(first.getString("query_end_time"), second.getString("query_end_time"))
        assertEquals(first.getString("query_start_time"), second.getString("query_start_time"))
    }

    @Test
    fun `updating content at the same key and timestamp invalidates old pages`() {
        record("latest", clockNow)
        record("older", clockNow - 1)
        val first = search(limit = 1)
        repository.record("latest", PACKAGE, "changed", "body", null, clockNow)

        val changed = search(limit = 1, cursor = first.getString("next_cursor"))

        assertFalse(changed.getBoolean("ok"))
        assertEquals("NOTIFICATION_HISTORY_CHANGED", changed.getString("code"))
        assertFalse(changed.has("items"))
    }

    @Test
    fun `a late record inside frozen time window cannot silently shift the page`() {
        record("latest", clockNow)
        record("older", clockNow - 100)
        val first = search(limit = 1)
        record("late delivery", clockNow - 50)

        val changed = search(limit = 1, cursor = first.getString("next_cursor"))

        assertEquals("NOTIFICATION_HISTORY_CHANGED", changed.getString("code"))
    }

    @Test
    fun `records outside package filter do not invalidate pagination`() {
        record("latest", clockNow)
        record("older", clockNow - 1)
        val first = search(limit = 1)
        repository.record("other", "com.example.other", "unrelated", null, null, clockNow)

        val second = search(limit = 1, cursor = first.getString("next_cursor"))

        assertTrue(second.getBoolean("ok"))
        assertEquals(listOf("older"), titles(second))
    }

    @Test
    fun `cursor is bound to original filters and explicit times`() {
        record("latest", clockNow)
        record("older", clockNow - 1)
        val start = clockNow - 1_000
        val end = clockNow + 1
        val first = search(limit = 1, startTime = start, endTime = end)
        val cursor = first.getString("next_cursor")
        val variants = listOf(
            repository.search("other", PACKAGE, 24, 1, cursor = cursor),
            repository.search("", "com.example.other", 24, 1, cursor = cursor),
            repository.search("", PACKAGE, 48, 1, cursor = cursor),
            repository.search("", PACKAGE, 24, 1, startTime = start - 1, cursor = cursor),
            repository.search("", PACKAGE, 24, 1, endTime = end + 1, cursor = cursor),
        )
        variants.forEach {
            assertEquals("NOTIFICATION_HISTORY_CURSOR_INVALID", JSONObject(it).getString("code"))
        }
        assertTrue(search(limit = 1, startTime = start, endTime = end, cursor = cursor).getBoolean("ok"))
    }

    @Test
    fun `malformed and oversized cursors fail instead of starting a fresh query`() {
        listOf("not-base64!", "e30", "a".repeat(16_385)).forEach { cursor ->
            val result = search(cursor = cursor)
            assertFalse(result.getBoolean("ok"))
            assertEquals("NOTIFICATION_HISTORY_CURSOR_INVALID", result.getString("code"))
        }
    }

    @Test
    fun `query metacharacters remain literal and package filter is not SQL`() {
        repository.record("literal", PACKAGE, "value_100%\\x", null, null, clockNow)
        repository.record("other", PACKAGE, "valueY100anything", null, null, clockNow)

        val literal = JSONObject(repository.search("_100%\\", PACKAGE, 24, 20))
        val injection = JSONObject(repository.search("", "' OR 1=1 --", 24, 20))

        assertEquals(1, literal.getInt("total_matches"))
        assertEquals(0, injection.getInt("total_matches"))
    }

    @Test
    fun `exactly full page is complete and result size remains bounded`() {
        repeat(50) { record("message-$it", clockNow - it) }
        val exact = search(limit = 50)
        assertEquals(50, exact.getInt("count"))
        assertFalse(exact.getBoolean("has_more"))
        assertFalse(exact.getBoolean("truncated"))

        record("extra", clockNow)
        val bounded = search(limit = Int.MAX_VALUE)
        assertEquals(50, bounded.getInt("count"))
        assertEquals(51, bounded.getInt("total_matches"))
        assertTrue(bounded.getBoolean("has_more"))
    }

    @Test
    fun `future end is capped at query time and explicit end survives continuation`() {
        record("current", clockNow)
        record("earlier", clockNow - 1)
        record("future", clockNow + 1_000)
        val futureEnd = clockNow + 60_000
        val first = search(limit = 1, endTime = futureEnd)
        assertEquals(2, first.getInt("total_matches"))
        assertEquals(Instant.ofEpochMilli(clockNow + 1).toString(), first.getString("query_end_time"))
        clockNow += 2_000

        val second = search(limit = 1, endTime = futureEnd, cursor = first.getString("next_cursor"))

        assertTrue(second.getBoolean("ok"))
        assertEquals(listOf("earlier"), titles(second))
        assertEquals(2, second.getInt("total_matches"))
    }

    @Test
    fun `old records are excluded even when no new notification triggered cleanup`() {
        record("old", clockNow)
        clockNow += 8L * 24 * 60 * 60 * 1_000
        val result = search()
        assertEquals(0, result.getInt("total_matches"))
        assertEquals("retained_only", result.getString("coverage"))
    }

    @Test
    fun `cursor does not extend retention when cleanup has not run`() {
        record("latest", clockNow)
        record("older", clockNow - 1)
        val first = search(limit = 1)
        clockNow += 8L * 24 * 60 * 60 * 1_000

        val result = search(limit = 1, cursor = first.getString("next_cursor"))

        assertEquals("NOTIFICATION_HISTORY_CHANGED", result.getString("code"))
        assertFalse(result.has("items"))
    }

    @Test
    fun `invalid or excessive time ranges are explicit errors`() {
        listOf(
            search(startTime = clockNow, endTime = clockNow),
            search(startTime = clockNow + 10, endTime = clockNow),
            search(startTime = clockNow - 8L * 24 * 60 * 60 * 1_000, endTime = clockNow),
            search(startTime = -1, endTime = clockNow),
        ).forEach {
            assertEquals("NOTIFICATION_HISTORY_TIME_RANGE_INVALID", it.getString("code"))
        }
    }

    private fun record(key: String, postedAt: Long) = repository.record(key, PACKAGE, key, "body", null, postedAt)

    private fun search(
        limit: Int = 20,
        startTime: Long? = null,
        endTime: Long? = null,
        cursor: String? = null,
    ): JSONObject = JSONObject(repository.search("", PACKAGE, 24, limit, startTime, endTime, cursor))

    private fun titles(result: JSONObject): List<String> = result.getJSONArray("items").let { rows ->
        (0 until rows.length()).map { rows.getJSONObject(it).getString("title") }
    }

    private fun recordIds(result: JSONObject): List<String> = result.getJSONArray("items").let { rows ->
        (0 until rows.length()).map { rows.getJSONObject(it).getString("record_id") }
    }

    private companion object {
        const val PACKAGE = "com.example.chat"
    }
}
