package io.github.mangi.eta.agent.context

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal class PersonalContextQueryService(
    private val transport: DmpQueryTransport,
    private val now: () -> Instant = Instant::now,
) {
    private val resolver = DmpSourceResolver(transport)

    fun execute(arguments: JSONObject): JSONObject {
        val request = try {
            PersonalContextRequest.parse(arguments)
        } catch (failure: PersonalContextArgumentException) {
            return failure(failure.code, failure.message ?: "个人上下文参数无效")
        }
        val source = request.source
        if (source == null) {
            return base(request).put("sources", JSONArray(PersonalContextSources.all.map { describe(it) }))
        }
        repeat(2) { attempt ->
            val resolved = when (val resolution = resolver.resolve(source, refresh = attempt > 0)) {
                is DmpSourceResolution.Available -> resolution.value
                is DmpSourceResolution.Unavailable -> return failure(resolution.code).put("source", source.id)
            }
            try {
                val result = when (request.action) {
                    PersonalContextAction.SOURCES -> base(request).put("source_info", describe(source, resolved))
                    PersonalContextAction.SEARCH, PersonalContextAction.READ -> records(request, resolved)
                    PersonalContextAction.COUNT -> count(request, resolved)
                    PersonalContextAction.BILL_SUMMARY -> billSummary(request, resolved)
                }
                return if (result.toString().length <= MAX_RESULT_CHARACTERS) result
                else failure("PERSONAL_CONTEXT_OUTPUT_LIMIT").put("source", source.id)
            } catch (failure: QueryFailure) {
                // 系统组件更新后，同一个 run 内的字段缓存也可能失效；只重探一次，不静默切换数据源。
                if (attempt > 0 || failure.code != "DMP_COLUMN_MISSING") {
                    return failure(failure.code).put("source", source.id)
                }
            }
        }
        return failure("PERSONAL_CONTEXT_SCHEMA_UNSUPPORTED").put("source", source.id)
    }

    private fun records(request: PersonalContextRequest, resolved: ResolvedPersonalContextSource): JSONObject {
        val source = resolved.source
        val isRead = request.action == PersonalContextAction.READ
        val fields = source.exposedFields.filter { it.logicalName in resolved.columns }
        val result = query(DmpQuery(
            resource = source.resource,
            projection = fields.map { field ->
                DmpProjection(field.logicalName, resolved.columns.getValue(field.logicalName), if (isRead) 2048 else 512)
            },
            selection = selection(request, resolved),
            sortOrder = if (isRead) null else sort(request, resolved),
            limit = if (isRead) 2 else request.limit + 1,
            offset = if (isRead) 0 else request.offset,
        ))
        if (isRead && result.rows.isEmpty()) return failure("PERSONAL_CONTEXT_NOT_FOUND").put("source", source.id)
        if (isRead && result.rows.size != 1) throw QueryFailure("DMP_OUTPUT_INVALID")
        val items = JSONArray()
        val cellTruncations = result.truncatedFields.toMutableSet()
        var remaining = MAX_RESULT_CHARACTERS - METADATA_RESERVE_CHARACTERS
        var payloadLimited = false
        for (row in result.rows.take(if (isRead) 1 else request.limit)) {
            val item = JSONObject()
            for (field in fields) {
                val value = row[field.logicalName]
                val mapped: Any = when {
                    field.logicalName == "id" -> value?.toLongOrNull()?.takeIf { it >= 0 }?.toString()
                        ?: throw QueryFailure("DMP_OUTPUT_INVALID")
                    value == null -> JSONObject.NULL
                    field.kind == PersonalContextFieldKind.INTEGER -> value.toLongOrNull() ?: value
                    else -> value
                }
                item.put(field.logicalName, mapped)
            }
            row["instance_id"]?.let { item.put("instance_id", it) }
            if (source.id == "calendar" || source.id == "calendar_todos") {
                item.put("record_kind", when {
                    row["instance_id"] != null -> "occurrence"
                    source.id == "calendar_todos" && !isRead && (request.start != null || request.end != null) -> "indexed_record_or_occurrence"
                    else -> "indexed_record"
                })
            }
            if (source.id == "photos") {
                val mediaId = row["origin_id"]?.toLongOrNull()?.takeIf { it > 0 }
                if (mediaId != null) item.put("image_tool_input", "content://media/external/images/media/$mediaId")
            }
            val size = item.toString().length + 1
            if (size > remaining) {
                payloadLimited = true
                break
            }
            remaining -= size
            items.put(item)
        }
        if (items.length() == 0 && result.rows.isNotEmpty()) throw QueryFailure("PERSONAL_CONTEXT_OUTPUT_LIMIT")
        cellTruncations.retainAll(fields.map { it.logicalName }.toSet())
        val hasMore = !isRead && result.rows.size > items.length()
        val nextOffset = request.offset + items.length()
        val paginationLimited = hasMore && nextOffset > PersonalContextRequest.MAX_OFFSET
        return sourceResult(request, resolved)
            .put("items", items)
            .put("count", items.length())
            .put("has_more", hasMore)
            .put("offset", if (isRead) 0 else request.offset)
            .put("next_offset", if (hasMore && !paginationLimited) nextOffset else JSONObject.NULL)
            .put("pagination_limited", paginationLimited)
            .put("truncated", payloadLimited || cellTruncations.isNotEmpty())
            .put("truncated_fields", JSONArray(cellTruncations.toList()))
            .put("sort_time_kind", if (isRead) JSONObject.NULL else source.sortTimeKind)
            .put("pagination_consistency", "live_index")
    }

    private fun count(request: PersonalContextRequest, resolved: ResolvedPersonalContextSource): JSONObject {
        val calendar = resolved.source.id in setOf("calendar", "calendar_todos")
        val result = query(DmpQuery(
            resolved.source.resource,
            listOf(DmpProjection("count", "COUNT(*)", 32)),
            selection(request, resolved),
            limit = if (calendar) 2 else 1,
        ))
        if (result.rows.isEmpty() || result.truncatedFields.isNotEmpty()) throw QueryFailure("DMP_OUTPUT_INVALID")
        val count = result.rows.fold(0L) { total, row ->
            val value = row["count"]?.toLongOrNull()?.takeIf { it >= 0 } ?: throw QueryFailure("DMP_OUTPUT_INVALID")
            try { Math.addExact(total, value) } catch (_: ArithmeticException) { throw QueryFailure("DMP_OUTPUT_INVALID") }
        }
        return sourceResult(request, resolved).put("count", count).put("complete_for_query", true)
    }

    private fun billSummary(request: PersonalContextRequest, resolved: ResolvedPersonalContextSource): JSONObject {
        val fields = listOf("amount", "transaction_type", "category")
        if (fields.any { it !in resolved.columns }) throw QueryFailure("PERSONAL_CONTEXT_FIELD_UNAVAILABLE")
        val result = query(DmpQuery(
            resolved.source.resource,
            fields.map { DmpProjection(it, resolved.columns.getValue(it), 128) },
            selection(request, resolved),
            sortOrder = "docID ASC",
            limit = PersonalContextBillSummary.MAX_RECORDS + 1,
        ))
        val summary = PersonalContextBillSummary.aggregate(result)
        return sourceResult(request, resolved).also { response ->
            summary.keys().forEach { response.put(it, summary.get(it)) }
        }
    }

    private fun selection(request: PersonalContextRequest, resolved: ResolvedPersonalContextSource): String {
        val source = resolved.source
        val clauses = source.fixedFilters.map { filter ->
            val column = resolved.columns[filter.fieldName] ?: throw QueryFailure("PERSONAL_CONTEXT_SCHEMA_UNSUPPORTED")
            when (filter.operator) {
                PersonalContextFilterOperator.EQUALS -> "$column = ${filter.value}"
                PersonalContextFilterOperator.NULL_OR_EQUALS -> "($column IS NULL OR $column = ${filter.value})"
            }
        }.toMutableList()
        request.id?.let { clauses += "${resolved.columns.getValue("id")} = $it" }
        request.query?.let { keyword ->
            val fields = source.searchFields.mapNotNull(resolved.columns::get)
            if (fields.isEmpty()) throw QueryFailure("PERSONAL_CONTEXT_FIELD_UNAVAILABLE")
            val pattern = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''")
            clauses += fields.joinToString(" OR ", "(", ")") {
                "LOWER(CAST($it AS TEXT)) LIKE LOWER('%$pattern%') ESCAPE '\\'"
            }
        }
        if (request.start != null || request.end != null) {
            val field = source.timeField?.let(resolved.columns::get) ?: throw QueryFailure("PERSONAL_CONTEXT_FIELD_UNAVAILABLE")
            val bounds = requireNotNull(source.timeUnit).toBounds(request.start, request.end)
            bounds.startInclusive?.let { clauses += "CAST($field AS INTEGER) >= $it" }
            bounds.endExclusive?.let { clauses += "CAST($field AS INTEGER) < $it" }
        }
        return clauses.joinToString(" AND ") { "($it)" }
    }

    private fun sort(request: PersonalContextRequest, resolved: ResolvedPersonalContextSource): String {
        val column = resolved.source.sortField?.let(resolved.columns::get)
            ?: throw QueryFailure("PERSONAL_CONTEXT_FIELD_UNAVAILABLE")
        val direction = if (request.sort == PersonalContextSort.OLDEST) "ASC" else "DESC"
        return "CAST($column AS INTEGER) $direction, ${resolved.columns.getValue("id")} $direction"
    }

    private fun query(request: DmpQuery): DmpQueryResult.Success = when (val result = transport.query(request)) {
        is DmpQueryResult.Success -> result
        is DmpQueryResult.Failure -> throw QueryFailure(result.code)
    }

    private fun sourceResult(request: PersonalContextRequest, resolved: ResolvedPersonalContextSource): JSONObject =
        base(request)
            .put("source", resolved.source.id)
            .put("source_name", resolved.source.label)
            .put("read_scope", "original_indexed_document")
            .put("unsupported_fields", JSONArray(resolved.unavailableFields.toList()))
            .put("time_field", resolved.source.timeField ?: JSONObject.NULL)
            .put("time_description", resolved.source.timeDescription ?: JSONObject.NULL)
            .put("field_time_units", JSONObject().also { units ->
                resolved.source.exposedFields.filter { it.logicalName in resolved.columns }.forEach { field ->
                    field.timeUnit?.let { units.put(field.logicalName, if (it.unitsPerSecond == 1L) "epoch_seconds" else "epoch_milliseconds") }
                }
            })
            .put("evidence_kind", if (resolved.source.id in INFERRED_SOURCES) "system_record_or_inference" else "indexed_record")

    private fun base(request: PersonalContextRequest): JSONObject = JSONObject()
        .put("ok", true)
        .put("tool", "personal_context")
        .put("action", request.action.wireName)
        .put("backend", "coloros_dmp")
        .put("freshness", "indexed")
        .put("queried_at", now().toString())
        .put("index_updated_at", JSONObject.NULL)

    private fun describe(source: PersonalContextSource, resolved: ResolvedPersonalContextSource? = null): JSONObject = JSONObject()
        .put("source", source.id)
        .put("name", source.label)
        .put("availability", if (resolved == null) "not_checked" else "available")
        .put("time_field", source.timeField ?: JSONObject.NULL)
        .put("time_description", source.timeDescription ?: JSONObject.NULL)
        .put("sort_time_kind", source.sortTimeKind)
        .put("read_scope", "original_indexed_document")
        .put("fields", JSONArray(source.exposedFields.filter { resolved == null || it.logicalName in resolved.columns }.map { field ->
            JSONObject().put("name", field.logicalName)
                .put("type", if (field.logicalName == "id" || field.kind != PersonalContextFieldKind.INTEGER) "string" else "integer_or_original_text")
                .put("description", field.description ?: JSONObject.NULL)
        }))
        .put("unsupported_fields", JSONArray(resolved?.unavailableFields?.toList().orEmpty()))

    private fun failure(code: String, message: String = errorMessage(code)): JSONObject = JSONObject()
        .put("ok", false).put("tool", "personal_context").put("code", code).put("message", message)

    private fun errorMessage(code: String): String = when (code) {
        "ROOT_REQUIRED", "ROOT_UNAVAILABLE" -> "个人上下文索引需要可用的 Root 授权"
        "ROOT_EXECUTOR_CLOSED" -> "个人上下文查询已取消"
        "DMP_ACCESS_DENIED" -> "系统未允许访问此个人上下文来源，请检查系统数据授权及应用隐藏或加密状态"
        "DMP_RESOURCE_UNAVAILABLE" -> "当前系统未提供或未准备好此个人上下文来源"
        "DMP_QUERY_TIMEOUT" -> "个人上下文查询超时，请缩小查询范围后重试"
        "PERSONAL_CONTEXT_NOT_FOUND" -> "索引中未找到此记录；记录可能已移除或索引已变化"
        "PERSONAL_CONTEXT_SCHEMA_UNSUPPORTED", "DMP_COLUMN_MISSING" -> "此系统版本的数据字段暂不兼容，未移除必要的保护条件"
        "PERSONAL_CONTEXT_FIELD_UNAVAILABLE" -> "此系统版本缺少本次筛选、排序或汇总所需的字段"
        "PERSONAL_CONTEXT_OUTPUT_LIMIT", "DMP_OUTPUT_LIMIT" -> "个人上下文结果超过读取预算，请缩小查询范围"
        "DMP_BINARY_TEXT", "DMP_INVALID_UTF8", "DMP_OUTPUT_INVALID" -> "系统返回的数据格式无法完整读取，未将其当成空结果"
        else -> "个人上下文数据源查询失败，未将其当成没有数据"
    }

    private class QueryFailure(val code: String) : RuntimeException(code)

    private companion object {
        const val MAX_RESULT_CHARACTERS = 48_000
        const val METADATA_RESERVE_CHARACTERS = 4_000
        val INFERRED_SOURCES = setOf("todos", "memories", "events", "bills", "flights", "hotels", "trains")
    }
}
