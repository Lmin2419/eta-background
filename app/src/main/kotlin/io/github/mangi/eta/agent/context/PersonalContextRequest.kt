package io.github.mangi.eta.agent.context

import java.math.BigDecimal
import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject

internal class PersonalContextArgumentException(message: String) : IllegalArgumentException(message) {
    val code: String = "INVALID_ARGUMENT"
}

internal enum class PersonalContextAction(val wireName: String) {
    SOURCES("sources"), SEARCH("search"), READ("read"), COUNT("count"), BILL_SUMMARY("bill_summary");
}

internal enum class PersonalContextSort(val wireName: String) { NEWEST("newest"), OLDEST("oldest") }

internal data class PersonalContextRequest(
    val action: PersonalContextAction,
    val source: PersonalContextSource?,
    val query: String? = null,
    val start: Instant? = null,
    val end: Instant? = null,
    val limit: Int = 10,
    val offset: Int = 0,
    val sort: PersonalContextSort = PersonalContextSort.NEWEST,
    val id: String? = null,
) {
    companion object {
        const val MAX_QUERY_CHARACTERS = 200
        const val MAX_LIMIT = 30
        const val MAX_OFFSET = 10_000

        private val timePattern = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(?::[0-9]{2}(?:\\.[0-9]{1,9})?)?(?:Z|[+-][0-9]{2}:[0-9]{2})$")
        private val idPattern = Regex("^[0-9]{1,19}$")

        fun parse(arguments: JSONObject): PersonalContextRequest {
            val actionName = requiredString(arguments, "action", "必须提供有效的 action")
            val action = PersonalContextAction.entries.firstOrNull { it.wireName == actionName }
                ?: invalid("不支持此个人上下文操作")
            val source = if (arguments.has("source")) {
                PersonalContextSources.find(requiredString(arguments, "source", "source 必须是有效的数据源名称"))
                    ?: invalid("不支持此个人上下文数据源")
            } else null
            if (action != PersonalContextAction.SOURCES && source == null) invalid("当前操作必须提供 source")
            if (action == PersonalContextAction.BILL_SUMMARY && source?.id != "bills") invalid("bill_summary 仅支持 bills 数据源")

            val query = optionalString(arguments, "query", "query 必须是文本")?.let { value ->
                if (value.codePointCount(0, value.length) > MAX_QUERY_CHARACTERS) invalid("query 超过 200 个字符")
                if ('\u0000' in value || !Charsets.UTF_8.newEncoder().canEncode(value)) invalid("query 包含无效字符")
                value.trim().takeIf(String::isNotEmpty)
            }
            val start = parseTime(arguments, "start_time")
            val end = parseTime(arguments, "end_time")
            if (start != null && end != null && start >= end) invalid("时间范围必须满足 start_time 早于 end_time")
            if ((start != null || end != null) && source?.timeField == null) invalid("此数据源没有已确认的业务时间字段，不能按时间筛选")
            source?.timeUnit?.toBounds(start, end)
            val limit = optionalInteger(arguments, "limit", 10, 1, MAX_LIMIT)
            val offset = optionalInteger(arguments, "offset", 0, 0, MAX_OFFSET)
            val sort = optionalString(arguments, "sort", "sort 必须是 newest 或 oldest")?.let { value ->
                PersonalContextSort.entries.firstOrNull { it.wireName == value } ?: invalid("sort 必须是 newest 或 oldest")
            } ?: PersonalContextSort.NEWEST
            val id = if (action == PersonalContextAction.READ) {
                val value = requiredString(arguments, "id", "read 必须提供整数文本 id")
                if (!idPattern.matches(value)) invalid("id 必须是最多 19 位的非负整数文本")
                value.toLongOrNull()?.toString() ?: invalid("id 超过支持的整数范围")
            } else null
            return PersonalContextRequest(action, source, query, start, end, limit, offset, sort, id)
        }

        private fun parseTime(arguments: JSONObject, name: String): Instant? {
            val value = optionalString(arguments, name, "时间参数必须是带时区偏移的 ISO 8601 文本") ?: return null
            if (!timePattern.matches(value)) invalid("时间参数必须包含 ISO 8601 日期、时间及 Z 或时区偏移")
            return try {
                OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
            } catch (_: DateTimeException) {
                invalid("时间参数不是有效的日期或时区偏移")
            }
        }

        private fun optionalInteger(arguments: JSONObject, name: String, default: Int, minimum: Int, maximum: Int): Int {
            if (!arguments.has(name)) return default
            val value = arguments.opt(name)
            if (value !is Number) invalid("分页参数必须是 JSON 整数")
            val integer = try { BigDecimal(value.toString()).intValueExact() } catch (_: NumberFormatException) {
                invalid("分页参数必须是有限整数")
            } catch (_: ArithmeticException) {
                invalid("分页参数必须是范围内的整数")
            }
            if (integer !in minimum..maximum) invalid("分页参数超出允许范围")
            return integer
        }

        private fun optionalString(arguments: JSONObject, name: String, message: String): String? =
            if (arguments.has(name)) arguments.opt(name) as? String ?: invalid(message) else null

        private fun requiredString(arguments: JSONObject, name: String, message: String): String =
            arguments.opt(name) as? String ?: invalid(message)

        private fun invalid(message: String): Nothing = throw PersonalContextArgumentException(message)
    }
}
