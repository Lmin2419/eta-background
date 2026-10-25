package io.github.mangi.eta.agent.phone

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject

/** 日程与提醒共用 Provider 事务；写入目标始终是原始事件 ID，而非搜索索引 ID。 */
internal class CalendarOperations(
    private val context: Context,
    private val userId: Int? = null,
    private val onMutation: () -> Unit = {},
    private val access: PhoneProviderAccess =
        AppPhoneProviderAccess(context.contentResolver, context.opPackageName),
) {
    private val resolver
        get() = access

    private fun uri(table: String): Uri =
        Uri.parse("content://${userId?.let { "$it@" }.orEmpty()}com.android.calendar/$table")

    private val eventColumns =
        arrayOf(
            "_id",
            "calendar_id",
            "title",
            "description",
            "eventLocation",
            "dtstart",
            "dtend",
            "eventTimezone",
            "allDay",
            "rrule",
            "deleted",
        )

    fun execute(tool: String, args: JSONObject): JSONObject =
        when (tool) {
            "list_calendars" -> {
                PhoneOperation.ok(tool).put("items", JSONArray(calendars()))
            }
            "read_calendar_event" -> {
                read(PhoneOperation.id(args, "event_id"))?.let {
                    PhoneOperation.ok(tool).put("event", it)
                } ?: missing()
            }
            "search_calendar_events" -> search(args)
            "create_calendar_event" -> create(args)
            "create_calendar_events" -> createMany(args)
            "update_calendar_event" -> update(args)
            "delete_calendar_event" -> delete(args)
            else -> PhoneOperation.failure("UNKNOWN_TOOL", "未知日历操作")
        }

    private fun calendars(): List<JSONObject> =
        rows(
                uri("calendars"),
                arrayOf("_id", "calendar_displayName", "calendar_access_level", "visible"),
                "calendar_access_level >= ?",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "_id ASC",
                50,
            )
            .map { it.put("id", it.getString("_id")).put("name", it.opt("calendar_displayName")) }

    private fun selectCalendar(args: JSONObject): Long {
        val available = calendars()
        if (args.has("calendar_id")) {
            val id = PhoneOperation.id(args, "calendar_id")
            if (available.none { it.optString("_id") == id.toString() })
                PhoneOperation.error("CALENDAR_NOT_WRITABLE", "指定日历不存在或不可写")
            return id
        }
        val visible = available.filter { it.optString("visible") == "1" }
        val choices = visible.ifEmpty { available }
        if (choices.size != 1)
            PhoneOperation.error(
                "CALENDAR_SELECTION_REQUIRED",
                "请先调用 list_calendars 并指定 calendar_id，不能猜测默认日历",
            )
        return choices.single().getString("_id").toLong()
    }

    private fun eventValues(args: JSONObject, create: Boolean): ContentValues {
        val values = ContentValues()
        if (create) values.put("calendar_id", selectCalendar(args))
        PhoneOperation.text(args, "title", create, 200)?.let { values.put("title", it) }
        PhoneOperation.text(args, "description", false, 8000)?.let { values.put("description", it) }
        PhoneOperation.text(args, "location", false, 500)?.let { values.put("eventLocation", it) }
        if (
            create ||
                args.has("start_time") ||
                args.has("end_time") ||
                args.has("all_day") ||
                args.has("timezone")
        ) {
            val start = PhoneOperation.time(args, "start_time")
            val end = PhoneOperation.time(args, "end_time")
            if (end <= start) PhoneOperation.error("INVALID_ARGUMENT", "日程结束必须晚于开始")
            val allDay = PhoneOperation.boolean(args, "all_day", false)
            if (allDay && (start % DAY_MS != 0L || end % DAY_MS != 0L))
                PhoneOperation.error("INVALID_ARGUMENT", "全天日程须使用 UTC 零点及排他的结束日期零点")
            values.put("dtstart", start)
            values.put("dtend", end)
            values.put("eventTimezone", if (allDay) "UTC" else PhoneOperation.timezone(args))
            values.put("allDay", if (allDay) 1 else 0)
        }
        return values
    }

    private fun reminders(args: JSONObject): List<Int>? {
        if (!args.has("reminder_minutes")) return null
        val items =
            args.optJSONArray("reminder_minutes")
                ?: PhoneOperation.error("INVALID_ARGUMENT", "reminder_minutes 必须是数组")
        if (items.length() > 5) PhoneOperation.error("INVALID_ARGUMENT", "单个日程最多设置5条提醒")
        return (0 until items.length())
            .map { i ->
                PhoneOperation.integer(
                        JSONObject().put("minutes", items.opt(i)),
                        "minutes",
                        0,
                        40320,
                    )
                    .toInt()
            }
            .distinct()
    }

    private fun create(args: JSONObject): JSONObject {
        val result = createBatch(listOf(args), "create_calendar_event")
        val item = result.getJSONArray("events").getJSONObject(0)
        return result.put("event_id", item.getString("event_id")).put("event", item)
    }

    private fun createMany(args: JSONObject): JSONObject {
        val events =
            args.optJSONArray("events") ?: PhoneOperation.error("INVALID_ARGUMENT", "events 必须是数组")
        if (events.length() !in 1..30) PhoneOperation.error("INVALID_ARGUMENT", "每批最多创建30个日程")
        val requests =
            (0 until events.length()).map { index ->
                val event =
                    events.optJSONObject(index)
                        ?: PhoneOperation.error("INVALID_ARGUMENT", "日程条目必须是对象")
                JSONObject(event.toString()).also {
                    if (args.has("calendar_id")) it.put("calendar_id", args.get("calendar_id"))
                }
            }
        return createBatch(requests, "create_calendar_events")
    }

    private fun createBatch(requests: List<JSONObject>, tool: String): JSONObject {
        val plans = requests.map { eventValues(it, true) to reminders(it) }
        val operations = arrayListOf<ContentProviderOperation>()
        val positions = mutableListOf<Int>()
        for ((values, reminders) in plans) {
            if (!reminders.isNullOrEmpty()) values.put("hasAlarm", 1)
            val position = operations.size
            positions += position
            operations +=
                ContentProviderOperation.newInsert(uri("events")).withValues(values).build()
            reminders.orEmpty().forEach { minutes ->
                operations +=
                    ContentProviderOperation.newInsert(uri("reminders"))
                        .withValueBackReference("event_id", position)
                        .withValue("minutes", minutes)
                        .withValue("method", CalendarContract.Reminders.METHOD_ALERT)
                        .build()
            }
        }
        onMutation()
        val committed = resolver.applyBatch(uri("events").authority!!, operations)
        val events = JSONArray()
        var verified = true
        positions.forEachIndexed { index, position ->
            val inserted =
                committed.getOrNull(position)?.uri
                    ?: PhoneOperation.error("OPERATION_UNCONFIRMED", "日历未返回创建的事件 ID")
            val id = ContentUris.parseId(inserted)
            val after = read(id)
            val (values, reminders) = plans[index]
            val matches = matches(after, values) && reminderMatches(after, reminders)
            verified = verified && matches
            events.put(
                JSONObject()
                    .put("event_id", id.toString())
                    .put("verified", matches)
                    .put("title", after?.opt("title") ?: JSONObject.NULL)
                    .put("dtstart", after?.opt("dtstart") ?: JSONObject.NULL)
                    .put("dtend", after?.opt("dtend") ?: JSONObject.NULL)
            )
        }
        return PhoneOperation.changed(tool, verified)
            .put("events", events)
            .put("count", events.length())
    }

    private fun update(args: JSONObject): JSONObject {
        val id = PhoneOperation.id(args, "event_id")
        val before = read(id) ?: return missing()
        if (before.optString("rrule").isNotBlank() && !before.isNull("rrule"))
            PhoneOperation.error("RECURRING_EVENT_SCOPE_REQUIRED", "重复日程需要明确实例或整组修改范围，当前接口不改写重复事件")
        val values = eventValues(args, false)
        val reminders = reminders(args)
        if (values.size() == 0 && reminders == null)
            PhoneOperation.error("INVALID_ARGUMENT", "没有需要修改的字段")
        val operations = arrayListOf<ContentProviderOperation>()
        if (reminders != null) values.put("hasAlarm", if (reminders.isEmpty()) 0 else 1)
        operations +=
            ContentProviderOperation.newUpdate(ContentUris.withAppendedId(uri("events"), id))
                .withValues(values)
                .withExpectedCount(1)
                .build()
        if (reminders != null) {
            operations +=
                ContentProviderOperation.newDelete(uri("reminders"))
                    .withSelection("event_id=?", arrayOf(id.toString()))
                    .build()
            reminders.forEach {
                operations +=
                    ContentProviderOperation.newInsert(uri("reminders"))
                        .withValue("event_id", id)
                        .withValue("minutes", it)
                        .withValue("method", CalendarContract.Reminders.METHOD_ALERT)
                        .build()
            }
        }
        onMutation()
        resolver.applyBatch(uri("events").authority!!, operations)
        val after = read(id)
        return PhoneOperation.changed(
                "update_calendar_event",
                matches(after, values) && reminderMatches(after, reminders),
            )
            .put("event_id", id.toString())
            .put("before", before)
            .put("event", after ?: JSONObject.NULL)
    }

    private fun delete(args: JSONObject): JSONObject {
        val id = PhoneOperation.id(args, "event_id")
        val before = read(id) ?: return missing()
        val scope = PhoneOperation.text(args, "scope", false, 20) ?: "event"
        if (scope !in setOf("event", "series"))
            PhoneOperation.error("INVALID_ARGUMENT", "scope 必须是 event 或 series")
        if (!before.isNull("rrule") && before.optString("rrule").isNotBlank() && scope != "series")
            PhoneOperation.error("RECURRING_EVENT_SCOPE_REQUIRED", "删除重复日程需明确 scope=series")
        onMutation()
        val affected = resolver.delete(ContentUris.withAppendedId(uri("events"), id), null, null)
        return PhoneOperation.changed("delete_calendar_event", affected > 0 && read(id) == null)
            .put("event_id", id.toString())
            .put("affected", affected)
    }

    private fun read(id: Long): JSONObject? =
        rows(
                uri("events"),
                eventColumns + "hasAlarm",
                "_id=? AND deleted=0",
                arrayOf(id.toString()),
                null,
                1,
            )
            .firstOrNull()
            ?.also {
                it.put("event_id", id.toString())
                it.put(
                    "reminders",
                    JSONArray(
                        rows(
                            uri("reminders"),
                            arrayOf("minutes", "method"),
                            "event_id=?",
                            arrayOf(id.toString()),
                            null,
                            20,
                        )
                    ),
                )
            }

    private fun search(args: JSONObject): JSONObject {
        val text = PhoneOperation.text(args, "query", false, 200)
        val limit = PhoneOperation.integer(args, "limit", 1, 30, 10).toInt()
        val offset = PhoneOperation.integer(args, "offset", 0, 10000, 0).toInt()
        val ascending =
            PhoneOperation.text(args, "sort", false, 10)?.let {
                if (it !in setOf("newest", "oldest"))
                    PhoneOperation.error("INVALID_ARGUMENT", "sort 无效")
                it == "oldest"
            } ?: false
        val clauses = mutableListOf("deleted=0")
        val bindings = mutableListOf<String>()
        text?.takeIf(String::isNotBlank)?.let {
            clauses +=
                "(title LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\' OR eventLocation LIKE ? ESCAPE '\\')"
            val escaped = "%${it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
            repeat(3) { bindings += escaped }
        }
        val hasWindow = args.has("start_time") || args.has("end_time")
        val result =
            if (hasWindow) {
                val start = PhoneOperation.time(args, "start_time")
                val end = PhoneOperation.time(args, "end_time")
                if (end <= start || end - start > 366L * DAY_MS)
                    PhoneOperation.error("INVALID_ARGUMENT", "日程实例查询须指定不超过366天的完整时间窗口")
                clauses += "begin < ? AND (end > ? OR (begin = end AND begin >= ?))"
            bindings += listOf(end.toString(), start.toString(), start.toString())
            rows(
                    uri("instances/when/$start/$end"),
                    arrayOf(
                        "event_id",
                        "title",
                        "description",
                        "eventLocation",
                        "begin",
                        "end",
                        "allDay",
                    ),
                    clauses.joinToString(" AND "),
                    bindings.toTypedArray(),
                    "begin ${if (ascending) "ASC" else "DESC"}, event_id ASC",
                    limit + 1,
                    offset,
                    1500,
                )
            } else
                rows(
                    uri("events"),
                    eventColumns,
                    clauses.joinToString(" AND "),
                    bindings.toTypedArray(),
                    "dtstart ${if (ascending) "ASC" else "DESC"}, _id ASC",
                    limit + 1,
                    offset,
                    1500,
                )
        val page = result.take(limit)
        page.forEach { if (!it.has("event_id")) it.put("event_id", it.optString("_id")) }
        return PhoneOperation.ok("search_calendar_events")
            .put("backend", "calendar_provider")
            .put("freshness", "live")
            .put("items", JSONArray(page))
            .put("has_more", result.size > limit)
            .put(
                "next_offset",
                if (result.size > limit) offset + page.size else JSONObject.NULL,
            )
    }

    private fun rows(
        uri: Uri,
        columns: Array<String>,
        selection: String?,
        args: Array<String>?,
        sort: String?,
        limit: Int,
        offset: Int = 0,
        maxText: Int = 8000,
    ): List<JSONObject> {
        val cursor =
            resolver.query(uri, columns, selection, args, sort)
                ?: PhoneOperation.error("CALENDAR_UNAVAILABLE", "日历未返回可读取结果，请检查权限和隐私保护")
        return cursor.use { c ->
            if (offset > 0) c.moveToPosition(offset - 1)
            buildList {
                while (size < limit && c.moveToNext()) {
                    add(
                        JSONObject().also { row ->
                            columns.forEachIndexed { i, name ->
                                row.put(
                                    name,
                                    if (c.isNull(i)) JSONObject.NULL
                                    else c.getString(i).take(maxText),
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    private fun matches(after: JSONObject?, expected: ContentValues): Boolean =
        after != null &&
            expected.keySet().all { key -> after.optString(key) == expected.getAsString(key) }

    private fun reminderMatches(after: JSONObject?, expected: List<Int>?): Boolean =
        expected == null ||
            after?.optJSONArray("reminders")?.let { list ->
                (0 until list.length())
                    .map { list.getJSONObject(it).getString("minutes").toInt() }
                    .sorted() == expected.sorted()
            } == true

    private fun missing() = PhoneOperation.failure("CALENDAR_EVENT_NOT_FOUND", "未找到当前用户的有效日历事件")

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
