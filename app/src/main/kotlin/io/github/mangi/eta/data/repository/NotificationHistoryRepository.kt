package io.github.mangi.eta.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

/** 仅在用户授予通知访问后保存有限期通知，内容不进入 Agent 会话持久记录。 */
internal class NotificationHistoryRepository(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val database = Database(context.applicationContext)

    fun record(
        key: String,
        packageName: String,
        title: String?,
        text: String?,
        subText: String?,
        postedAt: Long,
    ) {
        if (title.isNullOrBlank() && text.isNullOrBlank() && subText.isNullOrBlank()) return
        val values = ContentValues().apply {
            put("notification_key", key.take(MAX_KEY_CHARS))
            put("package_name", packageName.take(MAX_PACKAGE_CHARS))
            put("title", title.bounded())
            put("text", text.bounded())
            put("sub_text", subText.bounded())
            put("posted_at", postedAt)
        }
        database.writableDatabase.transaction {
            insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            delete(TABLE, "posted_at<?", arrayOf((now() - RETENTION_MS).toString()))
            execSQL(
                "DELETE FROM $TABLE WHERE notification_key NOT IN " +
                    "(SELECT notification_key FROM $TABLE ORDER BY posted_at DESC LIMIT $MAX_RECORDS)",
            )
        }
    }

    fun search(
        query: String,
        packageName: String,
        maxAgeHours: Int,
        limit: Int,
        startTime: Long? = null,
        endTime: Long? = null,
        cursor: String? = null,
    ): String {
        if (query.length > 200 || packageName.length > MAX_PACKAGE_CHARS) {
            return error("NOTIFICATION_HISTORY_QUERY_INVALID", "查询关键词或包名超出长度限制")
        }
        val pageSize = limit.coerceIn(1, MAX_PAGE_SIZE)
        val ageHours = maxAgeHours.coerceIn(1, RETENTION_DAYS * 24)
        val filter = digest(query, packageName, ageHours.toString())
        val continuation = if (cursor.isNullOrBlank()) null else (
            decodeCursor(cursor)
                ?: return error("NOTIFICATION_HISTORY_CURSOR_INVALID", "分页游标无效，请重新查询")
            )
        // Include records posted in the current millisecond while retaining an exclusive end.
        val currentTime = now()
        val upperNow = currentTime + 1
        val end = continuation?.end ?: minOf(endTime ?: upperNow, upperNow)
        val start = continuation?.start ?: startTime ?: (end - ageHours * HOUR_MS)
        val retentionStart = continuation?.retentionStart ?: (currentTime - RETENTION_MS)
        if (start < 0 || end <= start || end - start > RETENTION_MS) {
            return error("NOTIFICATION_HISTORY_TIME_RANGE_INVALID", "时间区间须为起始含、结束不含，且跨度不超过 7 天")
        }
        if (continuation != null && (
                continuation.filter != filter ||
                    startTime != null && startTime != start ||
                    endTime != null && endTime != end && endTime != continuation.requestedEnd
                )) {
            return error("NOTIFICATION_HISTORY_CURSOR_INVALID", "分页过滤条件或时间区间已改变，请重新查询")
        }

        val clauses = mutableListOf("posted_at>=?", "posted_at<?")
        val args = mutableListOf(maxOf(start, retentionStart, currentTime - RETENTION_MS).toString(), end.toString())
        if (packageName.isNotBlank()) {
            clauses += "package_name=?"
            args += packageName
        }
        if (query.isNotBlank()) {
            clauses += "(title LIKE ? ESCAPE '\\' OR text LIKE ? ESCAPE '\\' OR sub_text LIKE ? ESCAPE '\\')"
            val pattern = "%${query.escapeLike()}%"
            repeat(3) { args += pattern }
        }
        val db = database.readableDatabase
        // The count, fingerprint and selected page must refer to the same database view.
        // The retained store is bounded to 1,000 rows; hash rows incrementally rather than
        // storing another copy of notification contents or creating a snapshot database.
        db.beginTransactionNonExclusive()
        try {
            val fingerprint = MessageDigest.getInstance("SHA-256")
            var total = 0
            var anchorFound = continuation == null
            db.query(
                TABLE, COLUMNS, clauses.joinToString(" AND "), args.toTypedArray(),
                null, null, ORDER, (MAX_RECORDS + 1).toString(),
            ).use { rows ->
                while (rows.moveToNext()) {
                    total++
                    if (total > MAX_RECORDS) {
                        return error("NOTIFICATION_HISTORY_LIMIT_EXCEEDED", "通知记录超出保留上限，本次不返回不完整统计")
                    }
                    for (column in COLUMNS.indices) fingerprint.field(rows.getString(column))
                    if (continuation != null && rows.getString(0) == continuation.afterKey &&
                        rows.getLong(5) == continuation.afterTime
                    ) anchorFound = true
                }
            }
            val snapshot = fingerprint.digest().hex()
            if (continuation != null && continuation.fingerprint != snapshot) {
                return error("NOTIFICATION_HISTORY_CHANGED", "匹配的保留通知已更新或被回收，请从第一页重新查询；不要合并旧页计数")
            }
            if (!anchorFound) {
                return error("NOTIFICATION_HISTORY_CURSOR_INVALID", "分页位置不在本次查询中，请重新查询")
            }
            if (continuation != null) {
                clauses += "(posted_at<? OR (posted_at=? AND notification_key<?))"
                args += continuation.afterTime.toString()
                args += continuation.afterTime.toString()
                args += continuation.afterKey
            }
            val items = JSONArray()
            var lastKey = ""
            var lastTime = 0L
            var hasMore = false
            db.query(
                TABLE, COLUMNS, clauses.joinToString(" AND "), args.toTypedArray(),
                null, null, ORDER, (pageSize + 1).toString(),
            ).use { rows ->
                while (rows.moveToNext()) {
                    if (items.length() == pageSize) {
                        hasMore = true
                        break
                    }
                    lastKey = rows.getString(0)
                    lastTime = rows.getLong(5)
                    items.put(
                        JSONObject()
                            .put("record_id", digest(lastKey))
                            .put("package_name", rows.getString(1))
                            .put("title", rows.getString(2))
                            .put("text", rows.getString(3))
                            .put("sub_text", rows.getString(4))
                            .put("posted_at", lastTime),
                    )
                }
            }
            val next = if (hasMore) PageCursor(
                filter, start, end, retentionStart, continuation?.requestedEnd ?: endTime,
                lastTime, lastKey, snapshot,
            ).encode() else null
            db.setTransactionSuccessful()
            return JSONObject()
                .put("ok", true)
                .put("tool", "search_notification_history")
                .put("items", items)
                .put("count", items.length())
                .put("total_matches", total)
                .put("has_more", hasMore)
                .put("next_cursor", next ?: JSONObject.NULL)
                .put("query_start_time", Instant.ofEpochMilli(start).toString())
                .put("query_end_time", Instant.ofEpochMilli(end).toString())
                .put("retention_start_time", Instant.ofEpochMilli(retentionStart).toString())
                .put("retention_days", RETENTION_DAYS)
                .put("truncated", hasMore)
                .put("count_unit", "notification_records")
                .put("coverage", "retained_only")
                .put("complete_chat_history", false)
                .put("note", "通知可能合并、未发送或被相同通知键覆盖；total_matches 仅为保留记录数，不能视作聊天消息总数。")
                .toString()
        } finally {
            db.endTransaction()
        }
    }

    private data class PageCursor(
        val filter: String,
        val start: Long,
        val end: Long,
        val retentionStart: Long,
        val requestedEnd: Long?,
        val afterTime: Long,
        val afterKey: String,
        val fingerprint: String,
    ) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
            JSONObject()
                .put("v", 1).put("filter", filter).put("start", start).put("end", end)
                .put("retention_start", retentionStart)
                .put("requested_end", requestedEnd ?: JSONObject.NULL)
                .put("after_time", afterTime).put("after_key", afterKey)
                .put("fingerprint", fingerprint).toString().toByteArray(Charsets.UTF_8),
        )
    }

    private fun decodeCursor(value: String): PageCursor? = runCatching {
        require(value.length <= MAX_CURSOR_CHARS)
        val json = JSONObject(String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8))
        require(json.getInt("v") == 1)
        PageCursor(
            json.getString("filter"), json.getLong("start"), json.getLong("end"),
            json.getLong("retention_start"),
            if (json.isNull("requested_end")) null else json.getLong("requested_end"),
            json.getLong("after_time"), json.getString("after_key"), json.getString("fingerprint"),
        ).also {
            require(it.filter.matches(Regex("[0-9a-f]{64}")) && it.fingerprint.matches(Regex("[0-9a-f]{64}")))
            require(it.afterKey.length <= MAX_KEY_CHARS && it.afterTime >= it.start && it.afterTime < it.end)
            require(it.retentionStart <= it.end)
        }
    }.getOrNull()

    private fun error(code: String, message: String): String = JSONObject()
        .put("ok", false).put("tool", "search_notification_history")
        .put("code", code).put("message", message).toString()

    private fun digest(vararg fields: String?): String = MessageDigest.getInstance("SHA-256").apply {
        fields.forEach { field(it) }
    }.digest().hex()

    private fun MessageDigest.field(value: String?) {
        val bytes = value?.toByteArray(Charsets.UTF_8)
        val length = bytes?.size ?: -1
        update(byteArrayOf((length ushr 24).toByte(), (length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte()))
        bytes?.let(::update)
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun String?.bounded(): String? = this?.take(MAX_FIELD_CHARS)

    private fun String.escapeLike(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private class Database(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (" +
                    "notification_key TEXT PRIMARY KEY NOT NULL," +
                    "package_name TEXT NOT NULL," +
                    "title TEXT,text TEXT,sub_text TEXT,posted_at INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX notification_history_posted_at ON $TABLE(posted_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "eta_notification_history.db"
        const val TABLE = "notification_history"
        const val MAX_RECORDS = 1_000
        const val RETENTION_DAYS = 7
        const val HOUR_MS = 60L * 60 * 1_000
        const val RETENTION_MS = RETENTION_DAYS * 24L * HOUR_MS
        const val MAX_FIELD_CHARS = 4_000
        const val MAX_KEY_CHARS = 1_000
        const val MAX_PACKAGE_CHARS = 255
        const val MAX_PAGE_SIZE = 50
        const val MAX_CURSOR_CHARS = 16_384
        const val ORDER = "posted_at DESC, notification_key DESC"
        val COLUMNS = arrayOf("notification_key", "package_name", "title", "text", "sub_text", "posted_at")
    }
}
