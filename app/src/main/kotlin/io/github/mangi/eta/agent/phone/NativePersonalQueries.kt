package io.github.mangi.eta.agent.phone

import android.net.Uri
import io.github.mangi.eta.agent.context.PersonalContextTimeUnit
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/** 原始个人数据使用固定字段与绑定参数；调用方不能提供 URI 或 SQL。 */
internal class NativePersonalQueries(
    private val resolver: PhoneProviderAccess,
    private val userId: Int,
) {
    data class Source(
        val uri: String,
        val columns: List<String>,
        val search: List<String>,
        val order: String,
        val time: String,
        val millis: Boolean,
        val where: String? = null,
    )

    fun execute(tool: String, args: JSONObject): JSONObject {
        if (args.has("current_only")) PhoneOperation.boolean(args, "current_only")
        if (args.has("match") && args.optString("match") != "name")
            PhoneOperation.error("INVALID_ARGUMENT", "当前数据源不支持此检索方式")
        val source = sources.getValue(tool)
        val query = PhoneOperation.text(args, "query", false, 200)
        val limit = PhoneOperation.integer(args, "limit", 1, 30, 10).toInt()
        val offset = PhoneOperation.integer(args, "offset", 0, 10000, 0).toInt()
        val sort = PhoneOperation.text(args, "sort", false, 10) ?: "newest"
        if (sort !in setOf("newest", "oldest")) PhoneOperation.error("INVALID_ARGUMENT", "sort 无效")
        val clauses = mutableListOf<String>()
        val values = mutableListOf<String>()
        source.where?.let(clauses::add)
        query?.takeIf(String::isNotBlank)?.let {
            val keyword = "%${it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
            clauses +=
                source.search.joinToString(" OR ", "(", ")") { name ->
                    values += keyword
                    "$name LIKE ? ESCAPE '\\'"
                }
        }
        val start =
            PhoneOperation.text(args, "start_time", false, 64)?.let {
                OffsetDateTime.parse(it).toInstant()
            }
        val end =
            PhoneOperation.text(args, "end_time", false, 64)?.let {
                OffsetDateTime.parse(it).toInstant()
            }
        if (start != null && end != null && end <= start)
            PhoneOperation.error("INVALID_ARGUMENT", "结束时间必须晚于开始")
        if (source.time.isBlank() && (start != null || end != null))
            PhoneOperation.error("TIME_FILTER_UNSUPPORTED", "此来源没有可靠的时间字段")
        val bounds =
            (if (source.millis) PersonalContextTimeUnit.MILLISECONDS
                else PersonalContextTimeUnit.SECONDS)
                .toBounds(start, end)
        bounds.startInclusive?.let {
            clauses += "${source.time} >= ?"
            values += it.toString()
        }
        bounds.endExclusive?.let {
            clauses += "${source.time} < ?"
            values += it.toString()
        }
        val base = Uri.parse(source.uri)
        val uri = base.buildUpon().authority("$userId@${base.authority}").build()
        val order = source.order + if (sort == "oldest") " ASC" else " DESC"
        val cursor =
            resolver.query(
                uri,
                source.columns.toTypedArray(),
                clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
                values.toTypedArray(),
                order,
            ) ?: PhoneOperation.error("PERSONAL_DATA_UNAVAILABLE", "个人数据源没有返回可读取结果")
        val items = JSONArray()
        var more = false
        var truncated = false
        var budget = 40000
        cursor.use { c ->
            if (offset > 0) c.moveToPosition(offset - 1)
            while (c.moveToNext()) {
                if (items.length() >= limit) {
                    more = true
                    break
                }
                val row = JSONObject()
                source.columns.forEach { name ->
                    val index = c.getColumnIndex(name)
                    val raw = if (index < 0 || c.isNull(index)) null else c.getString(index)
                    if (raw != null && raw.length > 1500) truncated = true
                    row.put(name, raw?.take(1500) ?: JSONObject.NULL)
                }
                if (tool == "search_media")
                    row.put("uri", "content://media/external/images/media/${row.optString("_id")}")
                if (tool == "search_contacts") row.put("contact_id", row.optString("_id"))
                if (tool == "search_notes") row.put("note_id", row.optString("local_id"))
                val size = row.toString().length
                if (size > budget) {
                    more = true
                    truncated = true
                    break
                }
                budget -= size
                items.put(row)
            }
        }
        return PhoneOperation.ok(tool)
            .put("backend", "native_provider")
            .put("freshness", "live")
            .put("items", items)
            .put("count", items.length())
            .put("has_more", more)
            .put("truncated", truncated)
            .put(
                "next_offset",
                if (more && offset + items.length() <= 10000) offset + items.length()
                else JSONObject.NULL,
            )
    }

    private fun parseTime(value: String) = try { OffsetDateTime.parse(value).toInstant() } catch (_: java.time.DateTimeException) {
        PhoneOperation.error("INVALID_ARGUMENT", "时间必须是带偏移的 ISO 8601 格式")
    }

    fun probe(tool: String): Boolean {
        val s = sources.getValue(tool)
        val base = Uri.parse(s.uri)
        return resolver
            .query(
                base.buildUpon().authority("$userId@${base.authority}").build(),
                s.columns.toTypedArray(),
                "0",
                null,
                null,
            )
            ?.use { true } ?: false
    }

    companion object {
        private fun columns(value: String) = value.split(',')

        val sources =
            mapOf(
                "search_media" to
                    Source(
                        "content://media/external/images/media",
                        columns(
                            "_id,_display_name,mime_type,relative_path,datetaken,date_added,date_modified,_size"
                        ),
                        columns("_display_name,relative_path"),
                        "date_added",
                        "date_added",
                        false,
                    ),
                "search_files" to
                    Source(
                        "content://media/external/file",
                        columns("_id,_display_name,mime_type,relative_path,date_modified,_size"),
                        columns("_display_name,relative_path"),
                        "date_modified",
                        "date_modified",
                        false,
                        "media_type=0",
                    ),
                "search_audio" to
                    Source(
                        "content://media/external/audio/media",
                        columns(
                            "_id,title,_display_name,artist,album,relative_path,duration,date_modified"
                        ),
                        columns("title,_display_name,artist"),
                        "date_modified",
                        "date_modified",
                        false,
                    ),
                "search_recordings" to
                    Source(
                        "content://media/external/audio/media",
                        columns("_id,title,_display_name,relative_path,duration,date_modified"),
                        columns("title,_display_name,relative_path"),
                        "date_modified",
                        "date_modified",
                        false,
                        "relative_path LIKE '%Record%'",
                    ),
                "search_contacts" to
                    Source(
                        "content://com.android.contacts/contacts",
                        columns(
                            "_id,display_name,lookup,has_phone_number,contact_last_updated_timestamp"
                        ),
                        columns("display_name"),
                        "contact_last_updated_timestamp",
                        "contact_last_updated_timestamp",
                        true,
                    ),
                "search_messages" to
                    Source(
                        "content://sms",
                        columns("_id,thread_id,address,body,date,type,read"),
                        columns("address,body"),
                        "date",
                        "date",
                        true,
                    ),
                "search_call_history" to
                    Source(
                        "content://call_log/calls",
                        columns("_id,number,name,date,duration,type"),
                        columns("number,name"),
                        "date",
                        "date",
                        true,
                    ),
                "search_downloads" to
                    Source(
                        "content://downloads/all_downloads",
                        columns(
                            "_id,title,description,mime_type,total_size,lastmod,status,local_uri"
                        ),
                        columns("title,description"),
                        "lastmod",
                        "lastmod",
                        true,
                    ),
                "search_notes" to
                    Source(
                        "content://com.nearme.note/rich_notes",
                        columns("local_id,raw_title,raw_text,update_time,create_time,folder_id"),
                        columns("raw_title,raw_text"),
                        "update_time",
                        "update_time",
                        true,
                        "deleted=0 AND recycle_time=0 AND encrypted=0",
                    ),
                "search_recording_summaries" to
                    Source(
                        "content://com.coloros.soundrecorder.provider/summary",
                        columns("_id,record_uuid,note_content,note_state,media_id,media_path"),
                        columns("note_content,media_path"),
                        "_id",
                        "",
                        true,
                    ),
                "search_coloros_recordings" to
                    Source(
                        "content://com.coloros.soundrecorder.provider/records",
                        columns(
                            "_id,display_name,_data,duration,date_modified,record_type,relative_path"
                        ),
                        columns("display_name,_data,relative_path"),
                        "date_modified",
                        "date_modified",
                        true,
                        "deleted=0 AND is_recycle=0",
                    ),
            )
    }
}
