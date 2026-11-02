package io.github.mangi.eta.agent.tool

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgentNotificationHistoryQueryTest {
    private lateinit var context: Context
    private lateinit var repository: NotificationHistoryRepository
    private lateinit var tools: AgentPersonalContextTools
    private var now = 0L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("eta_notification_history.db")
        repository = NotificationHistoryRepository(context)
        tools = AgentPersonalContextTools(context)
        now = System.currentTimeMillis()
        grantAccess(true)
    }

    @After
    fun tearDown() {
        context.deleteDatabase("eta_notification_history.db")
    }

    @Test
    fun offsetTimesReachTheQueryWithInclusiveStartAndExclusiveEnd() {
        val start = now - 10_000
        val end = now - 5_000
        record("before", start - 1)
        record("start", start)
        record("inside", end - 1)
        record("end", end)
        val result = query(JSONObject()
            .put("start_time", atShanghai(start)).put("end_time", atShanghai(end)))

        assertTrue(result.getBoolean("ok"))
        assertEquals(2, result.getInt("count"))
        assertEquals(2, result.getInt("total_matches"))
        assertEquals(Instant.ofEpochMilli(start).toString(), result.getString("query_start_time"))
        assertEquals(Instant.ofEpochMilli(end).toString(), result.getString("query_end_time"))
        assertEquals("inside", result.getJSONArray("items").getJSONObject(0).getString("title"))
        assertEquals("start", result.getJSONArray("items").getJSONObject(1).getString("title"))
    }

    @Test
    fun nativeToolCanReadSixtyOneRecordsWithoutRootOrIncreasingPageLimit() {
        repeat(61) { record("record-$it", now - it) }
        val first = query(JSONObject().put("package_name", PACKAGE).put("limit", 50))
        val second = query(JSONObject().put("package_name", PACKAGE).put("limit", 50)
            .put("cursor", first.getString("next_cursor")))

        assertEquals(50, first.getInt("count"))
        assertEquals(11, second.getInt("count"))
        assertEquals(61, second.getInt("total_matches"))
        assertEquals(first.getString("query_end_time"), second.getString("query_end_time"))
        assertFalse(second.getBoolean("has_more"))
        assertEquals("notification_records", second.getString("count_unit"))
        assertFalse(second.getBoolean("complete_chat_history"))
    }

    @Test
    fun invalidOrTimezoneFreeDatesDoNotSilentlyFallBackToRollingSearch() {
        listOf("today", "2026-11-02T10:00:00", "999999999999999999999").forEach { date ->
            val result = query(JSONObject().put("start_time", date))
            assertFalse(result.getBoolean("ok"))
            assertEquals("NOTIFICATION_HISTORY_TIME_RANGE_INVALID", result.getString("code"))
            assertFalse(result.has("items"))
        }
    }

    @Test
    fun changedFiltersAreRejectedThroughTheTool() {
        repeat(2) { record("record-$it", now - it) }
        val first = query(JSONObject().put("package_name", PACKAGE).put("limit", 1))
        val changed = query(JSONObject().put("package_name", "com.example.other")
            .put("cursor", first.getString("next_cursor")))

        assertEquals("NOTIFICATION_HISTORY_CURSOR_INVALID", changed.getString("code"))
    }

    @Test
    fun permissionRemainsRequiredEvenWhenDatabaseAlreadyContainsRecords() {
        record("private", now)
        grantAccess(false)

        val result = query(JSONObject())

        assertFalse(result.getBoolean("ok"))
        assertEquals("NOTIFICATION_HISTORY_ACCESS_REQUIRED", result.getString("code"))
        assertFalse(result.has("items"))
    }

    private fun query(args: JSONObject): JSONObject {
        val result = tools.execute("search_notification_history", args)!!
        assertTrue(result.sensitive)
        return JSONObject(result.content)
    }

    private fun grantAccess(granted: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(
                ComponentName(context, AgentNotificationHistoryService::class.java), granted,
            )
    }

    private fun record(key: String, postedAt: Long) = repository.record(key, PACKAGE, key, "body", null, postedAt)

    private fun atShanghai(value: Long): String = Instant.ofEpochMilli(value).atOffset(ZoneOffset.ofHours(8)).toString()

    private companion object {
        const val PACKAGE = "com.example.chat"
    }
}
