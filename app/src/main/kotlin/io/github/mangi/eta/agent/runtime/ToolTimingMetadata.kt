package io.github.mangi.eta.agent.runtime

import org.json.JSONObject

/** Fixed vocabulary and numeric values only: never retains result bodies, URLs or provider labels. */
internal object ToolTimingMetadata {
    val keys = listOf("http", "browser_start", "navigation", "challenge", "extraction", "action_wait", "screenshot")
    private const val MAX_TOOL_MS = 10 * 60 * 1_000L
    private const val MAX_TOTAL_MS = 365 * 24 * 60 * 60 * 1_000L

    fun fromToolResult(content: String): Map<String, Long> = runCatching {
        if (content.length > 2 * 1024 * 1024 || !content.trimStart().startsWith("{")) return emptyMap()
        read(JSONObject(content).optJSONObject("timing_ms"), MAX_TOOL_MS)
    }.getOrDefault(emptyMap())

    fun sanitize(values: Map<String, Long>, aggregate: Boolean = false): Map<String, Long> {
        val max = if (aggregate) MAX_TOTAL_MS else MAX_TOOL_MS
        return keys.mapNotNull { key -> values[key]?.takeIf { it in 0..max }?.let { key to it } }.toMap()
    }

    fun encode(values: Map<String, Long>): String = JSONObject(sanitize(values)).toString()

    fun decode(raw: String?): Map<String, Long> = runCatching {
        if (raw == null || raw.length > 1024) return emptyMap()
        read(JSONObject(raw), MAX_TOOL_MS)
    }.getOrDefault(emptyMap())

    fun readTotals(json: JSONObject?): Map<String, Long> = read(json, MAX_TOTAL_MS)

    fun add(total: Long, value: Long): Long = (total + value).coerceAtMost(MAX_TOTAL_MS)

    private fun read(json: JSONObject?, maximum: Long): Map<String, Long> = keys.mapNotNull { key ->
        val value = json?.opt(key)
        // Do not accept numeric strings, booleans, fractions, arrays or coercion of NaN/infinity.
        val millis = when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> return@mapNotNull null
        }
        millis.takeIf { it in 0..maximum }?.let { key to it }
    }.toMap()
}
