package io.github.mangi.eta.agent.phone

import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/** 时钟业务接口保留调度副作用，不能用直接改库代替。 */
internal class ClockOperations(
    private val resolver: PhoneProviderAccess,
    userId: Int,
    private val onMutation: () -> Unit = {},
) {
    private val uri = Uri.parse("content://$userId@com.oplus.alarmclock.ai")

    fun execute(tool: String, args: JSONObject): JSONObject =
        when (tool) {
            "list_alarms" -> {
                val query = PhoneOperation.text(args, "query", false, 200).orEmpty()
                val limit = PhoneOperation.integer(args, "limit", 1, 50, 20).toInt()
                val offset = PhoneOperation.integer(args, "offset", 0, 10000, 0).toInt()
                val enabledOnly = PhoneOperation.boolean(args, "enabled_only", true)
                val hour = if (args.has("hour")) PhoneOperation.integer(args, "hour", 0, 23).toInt() else null
                val minute = if (args.has("minute")) PhoneOperation.integer(args, "minute", 0, 59).toInt() else null
                val matches = alarms().filter {
                    (!enabledOnly || it.getBoolean("enabled")) &&
                        (hour == null || it.getInt("hour") == hour) && (minute == null || it.getInt("minute") == minute) &&
                        (query.isBlank() || it.optString("label").contains(query, true))
                }.sortedBy { it.getString("alarm_id").toLong() }
                val page = matches.drop(offset).take(limit)
                PhoneOperation.ok(tool).put("items", JSONArray(page)).put("has_more", matches.size > offset + page.size)
                    .put("next_offset", if (matches.size > offset + page.size) offset + page.size else JSONObject.NULL)
            }
            "set_alarm" -> create(args)
            "update_alarm_time",
            "set_alarm_enabled",
            "delete_alarm" -> change(tool, args)
            else -> PhoneOperation.failure("UNKNOWN_TOOL", "未知时钟操作")
        }

    private fun call(method: String, extras: Bundle = Bundle()): Bundle {
        if (method != "get_alarm_list") onMutation()
        val result =
            resolver.call(uri, method, null, extras)
                ?: PhoneOperation.error("CLOCK_UNAVAILABLE", "时钟未返回操作结果")
        if (!result.containsKey("result"))
            PhoneOperation.error("CLOCK_RESULT_UNCONFIRMED", "时钟没有返回明确状态，请先查询结果")
        if (result.getInt("result", -1) != 1)
            PhoneOperation.error("CLOCK_OPERATION_REJECTED", "时钟拒绝操作，可能未找到目标、状态不匹配或达到容量限制")
        return result
    }

    private fun alarms(bundle: Bundle = call("get_alarm_list")): List<JSONObject> {
        if (!bundle.containsKey("alarm_id_list")) return emptyList()
        val ids =
            bundle.getLongArray("alarm_id_list")
                ?: PhoneOperation.error("CLOCK_PROTOCOL_UNSUPPORTED", "时钟返回的 ID 类型不兼容")
        val hours = bundle.getIntArray("alarm_hour_list")
        val minutes = bundle.getIntArray("alarm_min_list")
        val enabled = bundle.getBooleanArray("alarm_state_list")
        if (hours?.size != ids.size || minutes?.size != ids.size || enabled?.size != ids.size)
            PhoneOperation.error("CLOCK_PROTOCOL_UNSUPPORTED", "时钟返回的记录字段不完整")
        val labels = bundle.getStringArray("alarm_label_list")
        val repeats = bundle.getIntArray("alarm_repeat_set_list")
        val uuids = bundle.getStringArray("alarm_uuid_list")
        return ids.indices.map { index ->
            JSONObject()
                .put("alarm_id", ids[index].toString())
                .put("hour", hours[index])
                .put("minute", minutes[index])
                .put("enabled", enabled[index])
                .put("label", labels?.getOrNull(index) ?: "")
                .put("repeat_mask", repeats?.getOrNull(index) ?: JSONObject.NULL)
                .put("uuid", uuids?.getOrNull(index) ?: JSONObject.NULL)
        }
    }

    private fun create(args: JSONObject): JSONObject {
        val hour = PhoneOperation.integer(args, "hour", 0, 23).toInt()
        val minute = PhoneOperation.integer(args, "minute", 0, 59).toInt()
        val days =
            args.optJSONArray("repeat_days")
                ?: if (args.has("repeat_days"))
                    PhoneOperation.error("INVALID_ARGUMENT", "repeat_days 必须是数组")
                else JSONArray()
        if (days.length() > 7) PhoneOperation.error("INVALID_ARGUMENT", "重复星期数量无效")
        val bits =
            (0 until days.length())
                .map {
                    dayBits[days.optString(it)]
                        ?: PhoneOperation.error("INVALID_ARGUMENT", "重复星期无效")
                }
                .fold(0) { a, b -> a or b }
        val bundle =
            Bundle().apply {
                putInt("android.intent.extra.alarm.HOUR", hour)
                putInt("android.intent.extra.alarm.MINUTES", minute)
                putByte("android.intent.extra.alarm.DAYS_OF_WEEK", bits.toByte())
                putInt("android.intent.extra.alarm.DELETE_AFTER_USE", 0)
                PhoneOperation.text(args, "label", false, 100)?.let { putString("label", it) }
            }
        val created =
            alarms(call("add_alarm", bundle)).singleOrNull()
                ?: return PhoneOperation.changed("set_alarm", false)
        val after =
            alarms().firstOrNull { it.optString("alarm_id") == created.optString("alarm_id") }
        val verified =
            after != null &&
                after.optInt("hour") == hour &&
                after.optInt("minute") == minute &&
                after.optInt("repeat_mask", -1) == bits &&
                after.optBoolean("enabled") &&
                (!args.has("label") || after.optString("label") == args.optString("label"))
        return PhoneOperation.changed("set_alarm", verified)
            .put("alarm_id", created.getString("alarm_id"))
            .put("alarm", after ?: created)
    }

    private fun change(tool: String, args: JSONObject): JSONObject {
        val id = PhoneOperation.id(args, "alarm_id")
        val before =
            alarms().firstOrNull { it.optString("alarm_id") == id.toString() }
                ?: PhoneOperation.error("ALARM_NOT_FOUND", "未找到指定闹钟")
        val bundle = Bundle().apply { putLong("alarm_id", id) }
        val expected = JSONObject(before.toString())
        when (tool) {
            "update_alarm_time" -> {
                val hour = PhoneOperation.integer(args, "hour", 0, 23).toInt()
                val minute = PhoneOperation.integer(args, "minute", 0, 59).toInt()
                bundle.putInt("alarm_hour", hour)
                bundle.putInt("alarm_minute", minute)
                call("update_alarm", bundle)
                // 原生改时接口会启用闹钟；恢复原启用状态后再核对，避免意外启用旧闹钟。
                if (!before.getBoolean("enabled"))
                    call(
                        "close_alarm",
                        Bundle().apply {
                            putLong("alarm_id", id)
                            putInt("close_type", 1)
                        },
                    )
                expected.put("hour", hour).put("minute", minute)
            }
            "set_alarm_enabled" -> {
                val enabled = PhoneOperation.boolean(args, "enabled")
                if (before.getBoolean("enabled") != enabled) {
                    if (!enabled) bundle.putInt("close_type", 1)
                    call(if (enabled) "enable_alarm" else "close_alarm", bundle)
                }
                expected.put("enabled", enabled)
            }
            "delete_alarm" -> call("delete_alarm", bundle)
        }
        val after = alarms().firstOrNull { it.optString("alarm_id") == id.toString() }
        val verified =
            if (tool == "delete_alarm") after == null
            else
                after != null &&
                    listOf("hour", "minute", "enabled", "repeat_mask", "label").all {
                        after.opt(it) == expected.opt(it)
                    }
        return PhoneOperation.changed(tool, verified)
            .put("alarm_id", id.toString())
            .put("before", before)
            .put("alarm", after ?: JSONObject.NULL)
    }

    companion object {
        private val dayBits =
            mapOf(
                "mon" to 1,
                "tue" to 2,
                "wed" to 4,
                "thu" to 8,
                "fri" to 16,
                "sat" to 32,
                "sun" to 64,
            )
    }
}
