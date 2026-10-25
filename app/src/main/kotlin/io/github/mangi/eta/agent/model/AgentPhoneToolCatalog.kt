package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.tool.RootRequirement
import io.github.mangi.eta.agent.tool.SystemStateControls
import org.json.JSONArray
import org.json.JSONObject

/** 同一份领域目录用于模型、权限元数据和工具页，避免新增入口时漏接一端。 */
internal object AgentPhoneToolCatalog {
    data class Entry(
        val name: String,
        val title: String,
        val description: String,
        val write: Boolean,
        val root: RootRequirement,
        val colorOs: Boolean = false,
        val personal: Boolean = false,
        val direct: Boolean = false,
    )

    val entries =
        listOf(
            Entry(
                "get_flashlight",
                "手电筒状态",
                "查询手电筒实际状态及是否被相机占用。",
                false,
                RootRequirement.NONE,
                direct = true,
            ),
            Entry(
                "set_flashlight",
                "设置手电筒",
                "开启或关闭手电筒并通过系统回调验证；已处于目标状态时不重复切换。",
                true,
                RootRequirement.NONE,
                direct = true,
            ),
            Entry(
                "get_device_state",
                "查询系统开关",
                "查询指定系统开关，不用 GUI。",
                false,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "get_display_state",
                "显示状态",
                "查询亮度、自动亮度、旋转和息屏时间。",
                false,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "set_brightness",
                "设置亮度",
                "按系统亮度滑块百分比设置并读回验证；不支持可靠百分比接口时明确失败。",
                true,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "set_screen_timeout",
                "设置息屏时间",
                "设置无操作后的自动息屏秒数，并读回验证。",
                true,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "get_sound_state",
                "声音状态",
                "读取各类音量、响铃及勿扰状态。",
                false,
                RootRequirement.NONE,
                direct = true,
            ),
            Entry(
                "set_sound_mode",
                "设置响铃模式",
                "设置正常、静音或振动模式并验证；普通身份可能需要勿扰访问权限。",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry("set_do_not_disturb", "设置勿扰模式", "直接设置勿扰策略并读回验证。", true, RootRequirement.REQUIRED),
            Entry(
                "get_hotspot",
                "热点状态",
                "查询互联网共享热点是否开启；需要系统提供兼容接口。",
                false,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "set_hotspot",
                "设置个人热点",
                "使用现有热点配置开启或关闭网络共享，不更改名称和密码。",
                true,
                RootRequirement.REQUIRED,
            ),
            Entry(
                "list_calendars",
                "可用日历",
                "列出当前用户可写的日历 ID 和名称；创建时必须选择真实日历，不能猜 calendar_id。",
                false,
                RootRequirement.PARTIAL,
                personal = true,
            ),
            Entry(
                "read_calendar_event",
                "日程详情",
                "按原始 event_id 读取实时事件与提醒；不得使用 DMP 文档 ID。",
                false,
                RootRequirement.PARTIAL,
                personal = true,
            ),
            Entry(
                "create_calendar_event",
                "创建日程",
                "直接创建日程和提醒，事务完成后读回确认。一次性日程使用起止时间；全天事件使用 UTC 零点和排他的结束零点。超时后先查询，不要重复创建。",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry(
                "create_calendar_events",
                "批量创建日程",
                "一次创建同一日历中的多条日程和提醒；先校验全部参数，再在同一事务中写入并逐条核对。最多 30 条，结果未确认时先查询，不要重复创建。",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry(
                "update_calendar_event",
                "修改日程",
                "按原始 event_id 修改日程字段和提醒，并读回确认；修改时间须同时给出起止时间。当前不修改重复事件。",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry(
                "delete_calendar_event",
                "删除日程",
                "删除精确原始 event_id；重复事件必须明确 scope=series，不能把索引 ID 当作事件 ID。",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry(
                "update_alarm_time",
                "修改闹钟时间",
                "按 alarm_id 修改小时和分钟，保留原启用状态并验证。",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
            Entry(
                "set_alarm_enabled",
                "闹钟开关",
                "启用或关闭精确 alarm_id，读回验证；不按模糊时间匹配多个闹钟。",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
            Entry(
                "delete_alarm",
                "删除闹钟",
                "删除精确 alarm_id 后核对；不得使用 DMP 索引 ID。",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
            Entry(
                "create_note",
                "创建便签",
                "直接创建系统便签，解析实际结果 UUID 并读回；返回未确认时不能重复创建。",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
            Entry(
                "read_note",
                "便签详情",
                "按原始 note_id UUID 读取便签正文；不能使用 DMP 文档 ID。",
                false,
                RootRequirement.REQUIRED,
                true,
                true,
            ),
            Entry(
                "delete_note",
                "回收便签",
                "按原始 note_id UUID 将便签移入回收站并验证，不是永久删除。",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
        )
    val names = entries.mapTo(linkedSetOf()) { it.name }
    val direct = entries.filter { it.direct }.mapTo(linkedSetOf()) { it.name }
    val reads = entries.filter { !it.write && !it.direct }.mapTo(linkedSetOf()) { it.name }
    val writes = entries.filter { it.write && !it.direct }.mapTo(linkedSetOf()) { it.name }

    fun appendTo(tools: JSONArray, direct: Boolean, reads: Boolean, writes: Boolean) {
        entries
            .filter { if (it.direct) direct else if (it.write) writes else reads }
            .forEach { tools.put(schema(it)) }
    }

    private fun schema(entry: Entry): JSONObject {
        val p = JSONObject()
        val required = mutableListOf<String>()
        fun field(key: String, schema: JSONObject, need: Boolean = true) {
            p.put(key, schema)
            if (need) required += key
        }
        fun id(key: String) = field(key, text("原始对象 ID", 36))
        when (entry.name) {
            "set_flashlight",
            "set_hotspot" -> field("enabled", JSONObject().put("type", "boolean"))
            "get_device_state" -> field("target", enum(SystemStateControls.targets))
            "set_brightness" -> field("percent", integer(0, 100))
            "set_screen_timeout" -> field("seconds", integer(15, 1800))
            "set_sound_mode" -> field("mode", enum(listOf("normal", "silent", "vibrate")))
            "set_do_not_disturb" ->
                field("mode", enum(listOf("off", "priority", "alarms", "silent")))
            "read_calendar_event" -> id("event_id")
            "create_calendar_events" -> {
                field("calendar_id", text("可写日历 ID", 19), false)
                val single =
                    schema(entries.first { it.name == "create_calendar_event" })
                        .getJSONObject("function")
                        .getJSONObject("parameters")
                single.getJSONObject("properties").remove("calendar_id")
                field(
                    "events",
                    JSONObject()
                        .put("type", "array")
                        .put("minItems", 1)
                        .put("maxItems", 30)
                        .put("items", single),
                )
            }
            "create_calendar_event",
            "update_calendar_event" -> {
                val create = entry.name == "create_calendar_event"
                if (create)
                    field("calendar_id", text("list_calendars 返回的可写日历 ID；仅一个日历时可省略", 19), false)
                else id("event_id")
                field("title", text("日程标题", 200), create)
                field("start_time", text("带偏移的 ISO 8601 起始时间", 64), create)
                field("end_time", text("带偏移的 ISO 8601 结束时间", 64), create)
                field("timezone", text("IANA 时区，如 Asia/Shanghai", 80), false)
                field("all_day", JSONObject().put("type", "boolean"), false)
                field("location", text("地点", 500), false)
                field("description", text("说明", 8000), false)
                field(
                    "reminder_minutes",
                    JSONObject()
                        .put("type", "array")
                        .put("items", integer(0, 40320))
                        .put("maxItems", 5),
                    false,
                )
            }
            "delete_calendar_event" -> {
                id("event_id")
                field("scope", enum(listOf("event", "series")), false)
            }
            "update_alarm_time" -> {
                id("alarm_id")
                field("hour", integer(0, 23))
                field("minute", integer(0, 59))
            }
            "set_alarm_enabled" -> {
                id("alarm_id")
                field("enabled", JSONObject().put("type", "boolean"))
            }
            "delete_alarm" -> id("alarm_id")
            "create_note" -> {
                field("title", text("标题", 200), false)
                field("content", text("便签正文", 20000))
            }
            "read_note",
            "delete_note" -> id("note_id")
        }
        return AgentPersonalSearchToolCatalog.function(
            entry.name,
            entry.description,
            p,
            *required.toTypedArray(),
        )
    }

    private fun text(description: String, max: Int) =
        JSONObject().put("type", "string").put("description", description).put("maxLength", max)

    private fun integer(min: Int, max: Int) =
        JSONObject().put("type", "integer").put("minimum", min).put("maximum", max)

    private fun enum(values: List<String>) =
        JSONObject().put("type", "string").put("enum", JSONArray(values))
}
