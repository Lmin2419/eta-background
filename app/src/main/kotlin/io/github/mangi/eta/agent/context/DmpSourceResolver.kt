package io.github.mangi.eta.agent.context

import java.util.concurrent.ConcurrentHashMap

internal data class ResolvedPersonalContextSource(
    val source: PersonalContextSource,
    val columns: Map<String, String>,
    val unavailableFields: Set<String>,
)

internal sealed interface DmpSourceResolution {
    data class Available(val value: ResolvedPersonalContextSource) : DmpSourceResolution
    data class Unavailable(val code: String) : DmpSourceResolution
}

/** 缓存只属于当前工具执行器；字段探测不读取个人记录，也不移除缺失的保护条件。 */
internal class DmpSourceResolver(private val transport: DmpQueryTransport) {
    private val resolved = ConcurrentHashMap<String, ResolvedPersonalContextSource>()

    fun resolve(source: PersonalContextSource, refresh: Boolean = false): DmpSourceResolution {
        if (refresh) resolved.remove(source.id)
        else resolved[source.id]?.let { return DmpSourceResolution.Available(it) }
        val candidates = source.fields.associate { it.logicalName to it.physicalCandidates.toMutableList() }
            .toMutableMap()
        val unavailable = linkedSetOf<String>()
        val attempts = source.fields.sumOf { it.physicalCandidates.size } + 1
        repeat(attempts) {
            if (Thread.currentThread().isInterrupted) return DmpSourceResolution.Unavailable("ROOT_EXECUTOR_CLOSED")
            val columns = candidates.mapValues { it.value.first() }
            val probe = DmpQuery(
                resource = source.resource,
                projection = columns.map { (name, column) -> DmpProjection(name, "COUNT($column)", 16) },
                selection = "0",
                limit = 1,
            )
            when (val result = transport.query(probe)) {
                is DmpQueryResult.Success -> {
                    if (result.rows.size != 1 || result.truncatedFields.isNotEmpty() ||
                        columns.keys.any { result.rows.single()[it] != "0" }
                    ) return DmpSourceResolution.Unavailable("DMP_OUTPUT_INVALID")
                    val value = ResolvedPersonalContextSource(source, columns, unavailable.toSet())
                    resolved[source.id] = value
                    return DmpSourceResolution.Available(value)
                }
                is DmpQueryResult.Failure -> {
                    if (result.code != "DMP_COLUMN_MISSING") return DmpSourceResolution.Unavailable(result.code)
                    val missing = columns.entries.firstOrNull { it.value == result.missingColumn }?.key
                        ?: return DmpSourceResolution.Unavailable("PERSONAL_CONTEXT_SCHEMA_UNSUPPORTED")
                    val remaining = candidates.getValue(missing)
                    remaining.removeAt(0)
                    if (remaining.isEmpty()) {
                        if (source.field(missing)?.required == true) {
                            return DmpSourceResolution.Unavailable("PERSONAL_CONTEXT_SCHEMA_UNSUPPORTED")
                        }
                        candidates.remove(missing)
                        unavailable += missing
                    }
                }
            }
        }
        return DmpSourceResolution.Unavailable("PERSONAL_CONTEXT_SCHEMA_UNSUPPORTED")
    }
}
