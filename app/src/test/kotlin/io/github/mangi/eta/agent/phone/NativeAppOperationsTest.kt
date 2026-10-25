package io.github.mangi.eta.agent.phone

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NativeAppOperationsTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun calendarCreationUsesOneBatchAndVerifiesReminderBackReferences() {
        val provider = FakeProvider()
        val result =
            PhoneOperations.execute(
                context,
                "create_calendar_event",
                event().put("reminder_minutes", org.json.JSONArray(listOf(60, 1440))),
                access = provider,
            )
        assertTrue(result.toString(), result.getBoolean("verified"))
        assertEquals(1, provider.batches)
        assertEquals(3, provider.lastBatchSize)
        assertEquals("1", result.getString("event_id"))
        assertEquals(setOf("1"), provider.reminders.map { it.getAsString("event_id") }.toSet())
        assertEquals(setOf(60, 1440), provider.reminders.map { it.getAsInteger("minutes") }.toSet())
    }

    @Test
    fun calendarBatchBindsEachReminderToItsOwnEvent() {
        val provider = FakeProvider()
        val events =
            org.json
                .JSONArray()
                .put(event().put("reminder_minutes", org.json.JSONArray(listOf(10))))
                .put(
                    event()
                        .put("title", "另一会议")
                        .put("reminder_minutes", org.json.JSONArray(listOf(30)))
                )
        val result =
            PhoneOperations.execute(
                context,
                "create_calendar_events",
                JSONObject().put("events", events),
                access = provider,
            )
        assertTrue(result.toString(), result.getBoolean("verified"))
        assertEquals(1, provider.batches)
        assertEquals(2, provider.events.size)
        assertEquals(
            mapOf("1" to 10, "2" to 30),
            provider.reminders.associate {
                it.getAsString("event_id") to it.getAsInteger("minutes")
            },
        )
    }

    @Test
    fun invalidLaterEventCannotPartiallyCreateCalendarBatch() {
        val provider = FakeProvider()
        val events =
            org.json
                .JSONArray()
                .put(event())
                .put(event().put("end_time", "2026-10-04T10:00:00+08:00"))
        val result =
            PhoneOperations.execute(
                context,
                "create_calendar_events",
                JSONObject().put("events", events),
                access = provider,
            )
        assertEquals("INVALID_ARGUMENT", result.getString("code"))
        assertEquals(0, provider.batches)
        assertTrue(provider.events.isEmpty())
    }

    @Test
    fun calendarDoesNotGuessAccountOrAcceptReversedAndNonUtcAllDayRanges() {
        val multiple = FakeProvider().apply { calendarCount = 2 }
        val selection =
            PhoneOperations.execute(context, "create_calendar_event", event(), access = multiple)
        assertEquals("CALENDAR_SELECTION_REQUIRED", selection.getString("code"))
        assertEquals(0, multiple.batches)
        val provider = FakeProvider()
        for (args in
            listOf(
                event().put("end_time", "2026-10-04T10:00:00+08:00"),
                event().put("all_day", true),
            )) {
            assertEquals(
                "INVALID_ARGUMENT",
                PhoneOperations.execute(context, "create_calendar_event", args, access = provider)
                    .getString("code"),
            )
        }
        assertEquals(0, provider.batches)
    }

    @Test
    fun nativeFailureAfterDispatchIsNotReportedAsSafeToRetry() {
        val provider = FakeProvider().apply { failBatch = true }
        val result =
            PhoneOperations.execute(context, "create_calendar_event", event(), access = provider)
        assertFalse(result.getBoolean("ok"))
        assertEquals("unconfirmed", result.getString("status"))
        assertFalse(result.getBoolean("verified"))
        assertFalse(result.toString().contains("private-server-message"))
    }

    @Test
    fun aNonNullErrorUriIsNeverAConfirmedNoteCreation() {
        val provider = FakeProvider().apply { noteError = true }
        val result =
            PhoneOperations.execute(
                context,
                "create_note",
                JSONObject().put("content", "新便签正文"),
                access = provider,
            )
        assertFalse(result.getBoolean("ok"))
        assertEquals("NOTE_CREATE_REJECTED", result.getString("code"))
        assertFalse(result.has("note_id"))
        assertEquals(provider.callerPackage, provider.lastInsert!!.getAsString("package_name"))
    }

    @Test
    fun noteRequiresReadableProviderBeforeAnyCreation() {
        val provider = FakeProvider().apply { notesReady = false }
        val result =
            PhoneOperations.execute(
                context,
                "create_note",
                JSONObject().put("content", "正文"),
                access = provider,
            )
        assertEquals("NOTES_NOT_READY", result.getString("code"))
        assertNull(provider.lastInsert)
        assertFalse(result.has("status"))
    }

    @Test
    fun noteDeletionUsesUuidAndRecyclesWithoutAcceptingIndexIds() {
        val provider = FakeProvider()
        val bad =
            PhoneOperations.execute(
                context,
                "delete_note",
                JSONObject().put("note_id", "123"),
                access = provider,
            )
        assertEquals("INVALID_ARGUMENT", bad.getString("code"))
        assertNull(provider.deletedUri)
    }

    @Test
    fun clockUsesTypedRepeatMaskAndRestoresDisabledStateOnTimeEdit() {
        val provider = FakeProvider()
        val result =
            PhoneOperations.execute(
                context,
                "set_alarm",
                JSONObject()
                    .put("hour", 8)
                    .put("minute", 30)
                    .put("repeat_days", org.json.JSONArray(listOf("mon", "wed", "fri"))),
                userId = 0,
                access = provider,
            )
        assertTrue(result.toString(), result.getBoolean("verified"))
        assertEquals(
            21.toByte(),
            provider.createdAlarm!!.getByte("android.intent.extra.alarm.DAYS_OF_WEEK"),
        )
        assertFalse(provider.createdAlarm!!.containsKey("android.intent.extra.alarm.DAYS"))
        provider.alarmEnabled = false
        val changed =
            PhoneOperations.execute(
                context,
                "update_alarm_time",
                JSONObject().put("alarm_id", "7").put("hour", 9).put("minute", 0),
                userId = 0,
                access = provider,
            )
        assertTrue(changed.toString(), changed.getBoolean("verified"))
        assertFalse(provider.alarmEnabled)
        assertTrue(provider.calls.indexOf("update_alarm") < provider.calls.indexOf("close_alarm"))
    }

    @Test
    fun clockInvalidTimeCannotReachMutation() {
        val provider = FakeProvider()
        val result = PhoneOperations.execute(context, "set_alarm",
            JSONObject().put("hour", -1).put("minute", 0), userId = 0, access = provider)
        assertEquals("INVALID_ARGUMENT", result.getString("code"))
        assertNull(provider.createdAlarm)
    }

    @Test
    fun clockIgnoresExtraFieldsWithoutChangingProviderTarget() {
        val provider = FakeProvider()
        val result = PhoneOperations.execute(context, "set_alarm",
            JSONObject().put("hour", 8).put("minute", 30).put("provider_uri", "content://anything"),
            userId = 0, access = provider)
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertEquals(8, provider.alarmHour)
        assertEquals(30, provider.alarmMinute)
        assertEquals(1, provider.calls.count { it == "add_alarm" })
    }

    private fun event() =
        JSONObject()
            .put("title", "会议")
            .put("start_time", "2026-10-05T10:00:00+08:00")
            .put("end_time", "2026-10-05T11:00:00+08:00")
            .put("timezone", "Asia/Shanghai")

    private class FakeProvider : PhoneProviderAccess {
        override val callerPackage = "example.caller"
        var calendarCount = 1
        var batches = 0
        var lastBatchSize = 0
        var failBatch = false
        val events = mutableMapOf<Long, ContentValues>()
        val reminders = mutableListOf<ContentValues>()
        var lastInsert: ContentValues? = null
        var noteError = false
        var notesReady = true
        var deletedUri: Uri? = null
        val calls = mutableListOf<String>()
        var createdAlarm: Bundle? = null
        var alarmEnabled = true
        var alarmHour = 8
        var alarmMinute = 30
        var repeatMask = 0

        override fun query(
            uri: Uri,
            columns: Array<String>,
            selection: String?,
            args: Array<String>?,
            sort: String?,
        ): Cursor? {
            if (uri.authority.orEmpty().endsWith("com.nearme.note") && !notesReady) return null
            val rows: List<Map<String, Any?>> =
                when (uri.pathSegments.firstOrNull()) {
                    "calendars" ->
                        (1..calendarCount).map {
                            mapOf(
                                "_id" to it,
                                "calendar_displayName" to "日历$it",
                                "calendar_access_level" to 700,
                                "visible" to 1,
                            )
                        }
                    "events" ->
                        events.entries
                            .filter { args == null || it.key.toString() == args[0] }
                            .map { (id, values) ->
                                values.keySet().associateWith { values.get(it) } +
                                    mapOf("_id" to id, "deleted" to 0)
                            }
                    "reminders" ->
                        reminders
                            .filter { args == null || it.getAsString("event_id") == args[0] }
                            .map { v -> v.keySet().associateWith { v.get(it) } }
                    else -> emptyList()
                }
            return MatrixCursor(columns).apply {
                if (selection != "0")
                    rows.forEach { row -> addRow(columns.map { row[it] }.toTypedArray()) }
            }
        }

        override fun insert(uri: Uri, values: ContentValues): Uri? {
            lastInsert = ContentValues(values)
            return when (uri.pathSegments.first()) {
                "events" -> {
                    val id = events.size.toLong() + 1
                    events[id] = ContentValues(values)
                    uri.buildUpon().appendPath(id.toString()).build()
                }
                "reminders" -> {
                    reminders += ContentValues(values)
                    uri.buildUpon().appendPath(reminders.size.toString()).build()
                }
                "text_note" ->
                    uri.buildUpon()
                        .appendQueryParameter("result", if (noteError) "error" else "success")
                        .appendQueryParameter("message", "private-server-message")
                        .build()
                else -> null
            }
        }

        override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int {
            deletedUri = uri
            return 0
        }

        override fun call(uri: Uri, method: String, arg: String?, extras: Bundle): Bundle {
            calls += method
            when (method) {
                "add_alarm" -> {
                    createdAlarm = Bundle(extras)
                    alarmHour = extras.getInt("android.intent.extra.alarm.HOUR")
                    alarmMinute = extras.getInt("android.intent.extra.alarm.MINUTES")
                    repeatMask = extras.getByte("android.intent.extra.alarm.DAYS_OF_WEEK").toInt()
                }
                "update_alarm" -> {
                    alarmHour = extras.getInt("alarm_hour")
                    alarmMinute = extras.getInt("alarm_minute")
                    alarmEnabled = true
                }
                "close_alarm" -> alarmEnabled = false
                "enable_alarm" -> alarmEnabled = true
            }
            return Bundle().apply {
                putInt("result", 1)
                putLongArray("alarm_id_list", longArrayOf(7))
                putIntArray("alarm_hour_list", intArrayOf(alarmHour))
                putIntArray("alarm_min_list", intArrayOf(alarmMinute))
                putBooleanArray("alarm_state_list", booleanArrayOf(alarmEnabled))
                putIntArray("alarm_repeat_set_list", intArrayOf(repeatMask))
                putStringArray("alarm_label_list", arrayOf(""))
            }
        }

        override fun applyBatch(
            authority: String,
            operations: ArrayList<ContentProviderOperation>,
        ): Array<ContentProviderResult> {
            batches++
            lastBatchSize = operations.size
            if (failBatch) throw IllegalStateException("private-server-message")
            val provider =
                object : ContentProvider() {
                    override fun onCreate() = true

                    override fun query(
                        uri: Uri,
                        projection: Array<out String>?,
                        selection: String?,
                        selectionArgs: Array<out String>?,
                        sortOrder: String?,
                    ): Cursor? = null

                    override fun getType(uri: Uri): String? = null

                    override fun insert(uri: Uri, values: ContentValues?): Uri? =
                        this@FakeProvider.insert(uri, values!!)

                    override fun delete(
                        uri: Uri,
                        selection: String?,
                        selectionArgs: Array<out String>?,
                    ): Int = 0

                    override fun update(
                        uri: Uri,
                        values: ContentValues?,
                        selection: String?,
                        selectionArgs: Array<out String>?,
                    ): Int = 0
                }
            val results = arrayOfNulls<ContentProviderResult>(operations.size)
            operations.forEachIndexed { index, operation ->
                results[index] = operation.apply(provider, results, index)
            }
            @Suppress("UNCHECKED_CAST")
            return results as Array<ContentProviderResult>
        }
    }
}
