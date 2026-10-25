package io.github.mangi.eta.agent.phone

import android.content.ContentValues
import android.net.Uri
import java.util.UUID
import org.json.JSONObject

internal class NoteOperations(
    private val resolver: PhoneProviderAccess,
    private val userId: Int,
    private val callerPackage: String,
    private val onMutation: () -> Unit = {},
) {
    private val authority = "$userId@com.nearme.note"

    private fun uri(path: String) = Uri.parse("content://$authority/$path")

    fun execute(tool: String, args: JSONObject): JSONObject =
        when (tool) {
            "create_note" -> create(args)
            "read_note" -> {
                val id = id(args)
                read(id)?.let { PhoneOperation.ok(tool).put("note", it) }
                    ?: PhoneOperation.failure("NOTE_NOT_FOUND", "未找到便签")
            }
            "delete_note" -> delete(args)
            else -> PhoneOperation.failure("UNKNOWN_TOOL", "未知便签操作")
        }

    private fun id(args: JSONObject): String {
        val value = PhoneOperation.text(args, "note_id", true, 36)!!
        try {
            if (UUID.fromString(value).toString() != value.lowercase())
                PhoneOperation.error("INVALID_ARGUMENT", "note_id 必须是原始便签 UUID")
        } catch (_: IllegalArgumentException) {
            PhoneOperation.error("INVALID_ARGUMENT", "note_id 必须是原始便签 UUID")
        }
        return UUID.fromString(value).toString()
    }

    private fun create(args: JSONObject): JSONObject {
        val content = PhoneOperation.text(args, "content", true, 20000)!!
        val title = PhoneOperation.text(args, "title", false, 200)
        val values =
            ContentValues().apply {
                put("package_name", callerPackage)
                put("content", content)
                title?.let { put("title", it) }
            }
        resolver.query(uri("rich_notes"), arrayOf("local_id"), "0", null, null)?.use {}
            ?: PhoneOperation.error("NOTES_NOT_READY", "便签尚未提供可验证的数据接口，未执行创建；请检查应用初始化和权限")
        onMutation()
        val result =
            resolver.insert(uri("text_note"), values)
                ?: return PhoneOperation.changed("create_note", false)
        if (result.getQueryParameter("result") != "success")
            PhoneOperation.error("NOTE_CREATE_REJECTED", "便签应用没有接受创建请求")
        val id =
            result.getQueryParameter("localId")
                ?: return PhoneOperation.changed("create_note", false)
        val after = read(id)
        val verified =
            after != null &&
                after.optString("content").contains(content) &&
                (title == null || after.optString("title") == title)
        return PhoneOperation.changed("create_note", verified)
            .put("note_id", id)
            .put("note", after ?: JSONObject.NULL)
    }

    private fun delete(args: JSONObject): JSONObject {
        val id = id(args)
        val before = read(id) ?: return PhoneOperation.failure("NOTE_NOT_FOUND", "未找到便签")
        val target =
            uri("text_note")
                .buildUpon()
                .appendQueryParameter("caller_package", callerPackage)
                .appendQueryParameter("local_id", id)
                .build()
        onMutation()
        val affected = resolver.delete(target, null, null)
        return PhoneOperation.changed("delete_note", affected > 0 && read(id) == null)
            .put("note_id", id)
            .put("mode", "recycle")
            .put("affected", affected)
            .put("before", before)
    }

    private fun read(id: String): JSONObject? {
        val columns = arrayOf("local_id", "raw_title", "raw_text", "update_time")
        val cursor =
            resolver.query(
                uri("rich_notes"),
                columns,
                "local_id=? AND deleted=0 AND recycle_time=0",
                arrayOf(id),
                null,
            ) ?: PhoneOperation.error("NOTES_UNAVAILABLE", "便签未返回可读取结果")
        return cursor.use {
            if (!it.moveToFirst()) null
            else
                JSONObject()
                    .put("note_id", it.getString(0))
                    .put("title", it.getString(1))
                    .put("content", it.getString(2)?.take(24000) ?: "")
                    .put("updated_at", it.getLong(3))
        }
    }
}
