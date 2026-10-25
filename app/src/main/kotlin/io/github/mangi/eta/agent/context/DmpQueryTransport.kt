package io.github.mangi.eta.agent.context

import io.github.mangi.eta.agent.device.BoundedRootCommandExecutor
import io.github.mangi.eta.agent.device.DeviceToolContract
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal data class DmpProjection(val name: String, val expression: String, val maxChars: Int = 1024)

internal data class DmpQuery(
    val resource: String,
    val projection: List<DmpProjection>,
    val selection: String? = null,
    val sortOrder: String? = null,
    val limit: Int = 20,
    val offset: Int = 0,
)

internal sealed interface DmpQueryResult {
    data class Success(
        val rows: List<Map<String, String?>>,
        val truncatedFields: Set<String> = emptySet(),
    ) : DmpQueryResult

    data class Failure(val code: String, val missingColumn: String? = null) : DmpQueryResult
}

internal fun interface DmpQueryTransport {
    fun query(query: DmpQuery): DmpQueryResult
}

/** 只接受领域层构造的查询；数据经带类型标记的十六进制传输，不依赖 CLI 的文本转义。 */
internal class RootDmpQueryTransport internal constructor(
    private val userId: Int,
    private val execute: (command: String, timeoutMillis: Long, maxOutputBytes: Int) -> BoundedRootCommandExecutor.Result,
) : DmpQueryTransport {
    constructor(root: BoundedRootCommandExecutor, userId: Int) : this(
        userId,
        { command, timeoutMillis, maxOutputBytes -> root.execute(command, timeoutMillis, maxOutputBytes) },
    )

    override fun query(query: DmpQuery): DmpQueryResult {
        if (!validQuery(query)) return failure("DMP_INVALID_QUERY")
        return when (val outcome = queryOnce(query)) {
            is QueryOutcome.Parsed -> outcome.result
            QueryOutcome.Empty -> confirmEmpty(query)
        }
    }

    private fun confirmEmpty(query: DmpQuery): DmpQueryResult {
        if (query.projection.any { COUNT_EXPRESSION.containsMatchIn(SQL_STRING.replace(it.expression, "")) }) {
            return failure("DMP_OUTPUT_INVALID")
        }
        val calendar = query.resource in CALENDAR_RESOURCES
        // CLI 无法区分 null cursor 和零行；日历展开还可能把 SQL 异常吞成 null。
        // 恒假资源探测不经过展开路径，因此日历复核必须保留原条件并合计两部分数量。
        val probe = DmpQuery(query.resource, listOf(DmpProjection("count", "COUNT(*)", 16)),
            selection = if (calendar) query.selection else "0", limit = if (calendar) 2 else 1)
        return when (val checked = queryOnce(probe)) {
            QueryOutcome.Empty -> failure(if (calendar) "DMP_PROVIDER_ERROR" else "DMP_RESOURCE_UNAVAILABLE")
            is QueryOutcome.Parsed -> when (val result = checked.result) {
                is DmpQueryResult.Failure -> result
                is DmpQueryResult.Success -> {
                    if (result.rows.isEmpty() || result.truncatedFields.isNotEmpty()) return failure("DMP_OUTPUT_INVALID")
                    val maximum = if (calendar) query.offset.toLong() else 0L
                    var total = 0L
                    for (row in result.rows) {
                        val value = row["count"] ?: return failure("DMP_OUTPUT_INVALID")
                        if (!UNSIGNED_INTEGER.matches(value)) return failure("DMP_OUTPUT_INVALID")
                        val count = value.toLongOrNull() ?: return failure("DMP_OUTPUT_INVALID")
                        if (count > maximum - total) return failure("DMP_OUTPUT_INVALID")
                        total += count
                    }
                    DmpQueryResult.Success(emptyList())
                }
            }
        }
    }

    private fun queryOnce(query: DmpQuery): QueryOutcome {
        val sortColumns = calendarSortColumns(query) ?: return parsedFailure("DMP_INVALID_QUERY")
        val command = buildCommand(query, sortColumns)
        if (!Charsets.UTF_8.newEncoder().canEncode(command) || command.toByteArray(Charsets.UTF_8).size > MAX_COMMAND_BYTES) {
            return parsedFailure("DMP_INVALID_QUERY")
        }
        val result = execute(command, QUERY_TIMEOUT_MS, MAX_OUTPUT_BYTES)
        if (result.errorCode.isNotEmpty()) {
            return parsedFailure(if (result.errorCode in ROOT_ERRORS) result.errorCode else "DMP_PROVIDER_ERROR")
        }
        if (result.timedOut) return parsedFailure("DMP_QUERY_TIMEOUT")
        if (result.truncated || result.stdout.length.toLong() + result.stderr.length > MAX_OUTPUT_BYTES ||
            result.stdout.toByteArray(Charsets.UTF_8).size.toLong() + result.stderr.toByteArray(Charsets.UTF_8).size > MAX_OUTPUT_BYTES
        ) return parsedFailure("DMP_OUTPUT_LIMIT")
        classifyProviderFailure(query, result.stdout, result.stderr)?.let { return QueryOutcome.Parsed(it) }
        if (!result.ok || result.stderr.isNotBlank()) return parsedFailure("DMP_PROVIDER_ERROR")
        val output = result.stdout.trim()
        if (output == "No result found.") return QueryOutcome.Empty
        if (output.isEmpty()) return parsedFailure("DMP_OUTPUT_INVALID")
        val rows = ArrayList<Map<String, String?>>()
        val truncated = linkedSetOf<String>()
        for (line in output.lineSequence()) {
            if (line.isBlank()) continue
            if (rows.size >= query.limit) return parsedFailure("DMP_OUTPUT_LIMIT")
            val match = ROW.matchEntire(line) ?: return parsedFailure("DMP_OUTPUT_INVALID")
            if (match.groupValues[1].toIntOrNull() != rows.size) return parsedFailure("DMP_OUTPUT_INVALID")
            val cells = match.groupValues[2].split(',').map(String::trim)
            val expectedColumns = query.projection.size * 2 + sortColumns.size
            val hasInstanceId = query.resource == "calendar" && cells.size == expectedColumns + 1
            if (cells.size != expectedColumns && !hasInstanceId) return parsedFailure("DMP_OUTPUT_INVALID")
            val row = linkedMapOf<String, String?>()
            query.projection.forEachIndexed { index, projection ->
                val valuePrefix = "eta_v$index="
                val truncatePrefix = "eta_t$index="
                if (!cells[index * 2].startsWith(valuePrefix) || !cells[index * 2 + 1].startsWith(truncatePrefix)) {
                    return parsedFailure("DMP_OUTPUT_INVALID")
                }
                val encoded = cells[index * 2].removePrefix(valuePrefix)
                val flag = cells[index * 2 + 1].removePrefix(truncatePrefix)
                if (flag != "0" && flag != "1") return parsedFailure("DMP_OUTPUT_INVALID")
                val value = when {
                    encoded == "E" -> return parsedFailure("DMP_BINARY_TEXT")
                    encoded == "N" -> {
                        if (flag != "0") return parsedFailure("DMP_OUTPUT_INVALID")
                        null
                    }
                    encoded.startsWith('V') -> {
                        val hex = encoded.drop(1)
                        if (hex.length % 2 != 0 || hex.length > projection.maxChars * 8 || !HEX.matches(hex)) {
                            return parsedFailure("DMP_OUTPUT_INVALID")
                        }
                        val bytes = ByteArray(hex.length / 2) { position ->
                            ((hex[position * 2].digitToInt(16) shl 4) or hex[position * 2 + 1].digitToInt(16)).toByte()
                        }
                        val text = try {
                            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                        } catch (_: CharacterCodingException) {
                            return parsedFailure("DMP_INVALID_UTF8")
                        }
                        if ('\u0000' in text) return parsedFailure("DMP_BINARY_TEXT")
                        val characters = text.codePointCount(0, text.length)
                        if (characters > projection.maxChars || flag == "1" && characters != projection.maxChars) {
                            return parsedFailure("DMP_OUTPUT_INVALID")
                        }
                        text
                    }
                    else -> return parsedFailure("DMP_OUTPUT_INVALID")
                }
                row[projection.name] = value
                if (flag == "1") truncated += projection.name
            }
            sortColumns.forEachIndexed { index, _ ->
                val prefix = "eta_sort$index="
                val cell = cells[query.projection.size * 2 + index]
                if (!cell.startsWith(prefix) || !validRawInteger(cell.removePrefix(prefix))) {
                    return parsedFailure("DMP_OUTPUT_INVALID")
                }
            }
            if (hasInstanceId) {
                val cell = cells.last()
                val prefix = "instanceId="
                if (!cell.startsWith(prefix)) return parsedFailure("DMP_OUTPUT_INVALID")
                val value = cell.removePrefix(prefix)
                if (value != "NULL" && (!UNSIGNED_INTEGER.matches(value) || value.toLongOrNull() == null)) {
                    return parsedFailure("DMP_OUTPUT_INVALID")
                }
                row["instance_id"] = value.takeUnless { it == "NULL" }
            }
            rows += row
        }
        return if (rows.isEmpty()) parsedFailure("DMP_OUTPUT_INVALID")
        else QueryOutcome.Parsed(DmpQueryResult.Success(rows, truncated))
    }

    private fun buildCommand(query: DmpQuery, sortColumns: List<SortColumn>): String {
        val uri = URI("content", AUTHORITY, "/${query.resource}", "report=true&limit=${query.limit}&offset=${query.offset}", null).toASCIIString()
        val projections = query.projection.flatMapIndexed { index, item ->
            val expression = "(${item.expression})"
            val text = "CAST($expression AS TEXT)"
            listOf(
                "CASE WHEN $expression IS NULL THEN 'N' WHEN instr($text,char(0))>0 THEN 'E' ELSE 'V'||hex(substr($text,1,${item.maxChars})) END AS eta_v$index",
                "CASE WHEN length($text)>${item.maxChars} THEN 1 ELSE 0 END AS eta_t$index",
            )
        } + sortColumns.mapIndexed { index, column -> "CAST(${column.name} AS INTEGER) AS eta_sort$index" }
        val sortOrder = if (sortColumns.isEmpty()) query.sortOrder else sortColumns.mapIndexed { index, column ->
            "eta_sort$index ${column.direction}"
        }.joinToString(", ")
        return buildString {
            append("content query --user ").append(userId)
            append(" --uri ").append(DeviceToolContract.quote(uri))
            append(" --projection ").append(DeviceToolContract.quote(projections.joinToString(":")))
            query.selection?.takeIf(String::isNotBlank)?.let { append(" --where ").append(DeviceToolContract.quote(it)) }
            sortOrder?.takeIf(String::isNotBlank)?.let { append(" --sort ").append(DeviceToolContract.quote(it)) }
        }
    }

    private fun validQuery(query: DmpQuery): Boolean =
        userId >= 0 && query.limit in 1..MAX_ROWS && query.offset in 0..MAX_OFFSET &&
            query.projection.size in 1..MAX_COLUMNS && query.projection.size.toLong() * query.limit <= MAX_CELLS &&
            query.resource.length in 1..256 && query.resource.count { it == '#' } <= 1 &&
            query.resource.split('/').all { RESOURCE_SEGMENT.matches(it) && it != "." && it != ".." } &&
            query.projection.map { it.name }.toSet().size == query.projection.size &&
            query.projection.all {
                FIELD.matches(it.name) && it.maxChars in 1..MAX_FIELD_CHARS &&
                    (query.resource != "calendar" || it.name != "instance_id") &&
                    it.expression.isNotBlank() && it.expression.length <= MAX_EXPRESSION_CHARS &&
                    it.expression.none { character -> character == ':' || character == '\u0000' }
            } && validClause(query.selection, MAX_SELECTION_CHARS) && validClause(query.sortOrder, MAX_SORT_CHARS)

    private fun validClause(value: String?, limit: Int): Boolean =
        value == null || value.length <= limit && '\u0000' !in value

    /** 日历时间窗口会展开为联合查询，排序必须引用 SELECT 中的数值别名。 */
    private fun calendarSortColumns(query: DmpQuery): List<SortColumn>? {
        if (query.resource !in CALENDAR_RESOURCES || query.sortOrder.isNullOrBlank()) return emptyList()
        val terms = query.sortOrder.split(',')
        if (terms.size > MAX_SORT_COLUMNS) return null
        return terms.map { term ->
            val parsed = NUMERIC_SORT.matchEntire(term.trim()) ?: return null
            val name = parsed.groupValues[1].ifEmpty { parsed.groupValues[2] }
            val direction = parsed.groupValues[3].ifEmpty { "ASC" }.uppercase(Locale.ROOT)
            SortColumn(name, direction)
        }
    }

    private fun validRawInteger(value: String): Boolean =
        value == "NULL" || SIGNED_INTEGER.matches(value) && value.toLongOrNull() != null

    private data class SortColumn(val name: String, val direction: String)

    private fun classifyProviderFailure(query: DmpQuery, stdout: String, stderr: String): DmpQueryResult.Failure? {
        val diagnostic = "$stderr\n$stdout"
        if (ACCESS_ERROR.containsMatchIn(diagnostic)) return failure("DMP_ACCESS_DENIED")
        val missing = MISSING_COLUMN.find(diagnostic)
        if (missing != null) {
            val raw = missing.groupValues[1].trimEnd(':').trim('"', '\'', '`')
            val column = raw.substringAfterLast('.')
            val referenced = (query.projection.map { it.expression } + listOfNotNull(query.selection, query.sortOrder))
                .flatMap { IDENTIFIER.findAll(SQL_STRING.replace(it, "")).map { token -> token.value }.toList() }
                .filterNot { it.uppercase(Locale.ROOT) in SQL_KEYWORDS }.toSet()
            return failure("DMP_COLUMN_MISSING", column.takeIf { FIELD.matches(it) && it in referenced })
        }
        if (RESOURCE_ERROR.containsMatchIn(diagnostic)) return failure("DMP_RESOURCE_UNAVAILABLE")
        if (PROVIDER_ERROR.containsMatchIn(diagnostic)) return failure("DMP_PROVIDER_ERROR")
        return null
    }

    private fun failure(code: String, missingColumn: String? = null) = DmpQueryResult.Failure(code, missingColumn)
    private fun parsedFailure(code: String) = QueryOutcome.Parsed(failure(code))

    private sealed interface QueryOutcome {
        data class Parsed(val result: DmpQueryResult) : QueryOutcome
        data object Empty : QueryOutcome
    }

    private companion object {
        const val AUTHORITY = "com.oplus.oss.provider.PersonalizationProvider"
        const val QUERY_TIMEOUT_MS = 15_000L
        const val MAX_OUTPUT_BYTES = 2 * 1024 * 1024
        const val MAX_COMMAND_BYTES = 64 * 1024
        const val MAX_ROWS = 1001
        const val MAX_COLUMNS = 16
        const val MAX_CELLS = 8192
        const val MAX_FIELD_CHARS = 2048
        const val MAX_EXPRESSION_CHARS = 512
        const val MAX_SELECTION_CHARS = 8192
        const val MAX_SORT_CHARS = 1024
        const val MAX_OFFSET = 1_000_000
        const val MAX_SORT_COLUMNS = 4
        val CALENDAR_RESOURCES = setOf("calendar", "calendarTodo")
        val ROOT_ERRORS = setOf("ROOT_REQUIRED", "ROOT_UNAVAILABLE", "ROOT_EXECUTOR_CLOSED")
        val FIELD = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        val RESOURCE_SEGMENT = Regex("[A-Za-z0-9_][A-Za-z0-9_.#-]*")
        val ROW = Regex("Row: (0|[1-9][0-9]*) (.+)")
        val HEX = Regex("[0-9A-Fa-f]*")
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val COUNT_EXPRESSION = Regex("\\bCOUNT\\s*\\(", RegexOption.IGNORE_CASE)
        val NUMERIC_SORT = Regex("(?:CAST\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+AS\\s+INTEGER\\s*\\)|([A-Za-z_][A-Za-z0-9_]*))(?:\\s+(ASC|DESC))?", RegexOption.IGNORE_CASE)
        val SIGNED_INTEGER = Regex("-?(?:0|[1-9][0-9]*)")
        val UNSIGNED_INTEGER = Regex("0|[1-9][0-9]*")
        val SQL_STRING = Regex("'(?:''|[^'])*'")
        val SQL_KEYWORDS = setOf("CASE", "WHEN", "THEN", "ELSE", "END", "AS", "TEXT", "INTEGER", "REAL", "NULL", "IS", "NOT", "AND", "OR", "CAST", "COUNT", "SUM", "MAX", "MIN", "COALESCE", "LENGTH", "HEX", "SUBSTR", "ASC", "DESC")
        val MISSING_COLUMN = Regex("(?:no such column\\s*:|invalid column\\s*:?)[ \\t]*([^\\s,();]+)", RegexOption.IGNORE_CASE)
        val ACCESS_ERROR = Regex("SecurityException|Permission Denial|permission denied|not authorized|not authorised|access denied", RegexOption.IGNORE_CASE)
        val RESOURCE_ERROR = Regex("no such table|unknown (?:uri|url|resource)|(?:failed to find|could not find|no) (?:content )?provider|unsupported resource|not a legal resource|illegal resource", RegexOption.IGNORE_CASE)
        val PROVIDER_ERROR = Regex("Error while accessing provider|(?:java\\.[A-Za-z.]+|android\\.[A-Za-z.]+)Exception:|(?:^|[\\n,]\\s*|Row: [0-9]+ )(?:error|error_code|errorCode|error_message|errorMessage)=", RegexOption.IGNORE_CASE)
    }
}
