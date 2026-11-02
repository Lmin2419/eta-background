package io.github.mangi.eta.agent.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Bounded, content-free measurements. Never inserted into the model transcript. */
internal data class RunPerformanceSummary(
    val elapsedMs: Long,
    val preparationMs: Long,
    val modelMs: Long,
    val toolsMs: Long,
    val pausedMs: Long,
    val otherMs: Long,
    val requests: Int,
    val retries: Int,
    val toolCalls: Int,
    val failedTools: Int,
    val unfinishedTools: Int,
    val imageCount: Int,
    val imageBytes: Long,
    val firstDeltaTotalMs: Long,
    val firstDeltaSamples: Int,
    val slowTools: List<SlowTool>,
) {
    data class SlowTool(val name: String, val elapsedMs: Long)

    fun toJson(): String = JSONObject()
        .put("version", 1)
        .put("elapsed_ms", elapsedMs).put("preparation_ms", preparationMs)
        .put("model_ms", modelMs).put("tools_ms", toolsMs)
        .put("paused_ms", pausedMs).put("other_ms", otherMs)
        .put("requests", requests).put("retries", retries).put("tool_calls", toolCalls)
        .put("failed_tools", failedTools).put("unfinished_tools", unfinishedTools)
        .put("image_count", imageCount).put("image_bytes", imageBytes)
        .put("first_delta_total_ms", firstDeltaTotalMs).put("first_delta_samples", firstDeltaSamples)
        .put("slow_tools", JSONArray().also { array ->
            slowTools.take(3).forEach { array.put(JSONObject().put("name", it.name).put("elapsed_ms", it.elapsedMs)) }
        }).toString()

    fun title(chinese: Boolean): String = if (chinese) {
        "执行统计 · ${seconds(elapsedMs)} 秒 · $toolCalls 次工具"
    } else "Execution · ${seconds(elapsedMs)} s · $toolCalls tools"

    fun details(chinese: Boolean): String = buildString {
        if (chinese) {
            appendLine("本地准备 ${seconds(preparationMs)} 秒；模型请求 ${seconds(modelMs)} 秒；工具执行 ${seconds(toolsMs)} 秒；暂停 ${seconds(pausedMs)} 秒；其余处理 ${seconds(otherMs)} 秒。")
            appendLine("模型请求 $requests 次（重试 $retries 次）；工具 $toolCalls 次（失败 $failedTools 次，未结束 $unfinishedTools 次）；返回图片 $imageCount 张，${imageBytes / 1024} KiB。")
            appendLine("请求至首个文本/推理增量均值：${if (firstDeltaSamples > 0) "${seconds(firstDeltaTotalMs / firstDeltaSamples)} 秒（$firstDeltaSamples 个样本）" else "未记录"}。")
            if (slowTools.isNotEmpty()) appendLine("较慢的工具调用：" + slowTools.joinToString("；") { "${it.name} ${seconds(it.elapsedMs)} 秒" })
            append("从开始执行计至资源收尾，不含排队和最终归档；各阶段不重复计时，并行工具按经过时间计。模型请求含网络与服务端等待；仅统计本任务上报的请求，压缩和子任务内部调用不单独拆分；首增量与单次工具耗时可能包含暂停，仅供定位，不能相加。图片数是工具返回数量，并不等于截图数或模型请求数。")
        } else {
            appendLine("Preparation ${seconds(preparationMs)} s; model requests ${seconds(modelMs)} s; tools ${seconds(toolsMs)} s; paused ${seconds(pausedMs)} s; other ${seconds(otherMs)} s.")
            appendLine("$requests requests ($retries retries); $toolCalls tools ($failedTools failed, $unfinishedTools unfinished); $imageCount returned images, ${imageBytes / 1024} KiB.")
            appendLine("Mean request to first text/reasoning delta: ${if (firstDeltaSamples > 0) "${seconds(firstDeltaTotalMs / firstDeltaSamples)} s ($firstDeltaSamples samples)" else "not recorded"}.")
            if (slowTools.isNotEmpty()) appendLine("Slower tool calls: " + slowTools.joinToString("; ") { "${it.name} ${seconds(it.elapsedMs)} s" })
            append("Measured from execution start through resource cleanup, excluding queueing and final archiving. Phases do not overlap; parallel tools use elapsed time. Requests include network and server waits. Only events reported by this run are counted; internal compaction and subagent requests are not separated. First-delta and individual tool latency may include pauses and are not additive. Returned images are not a screenshot or request count.")
        }
    }

    companion object {
        private fun seconds(ms: Long) = String.format(Locale.ROOT, "%.1f", ms / 1000.0)

        fun fromJson(raw: String?): RunPerformanceSummary? = runCatching {
            require(raw != null && raw.length <= 8192)
            val json = JSONObject(raw)
            require(json.getInt("version") == 1)
            fun duration(key: String) = json.getLong(key).also { require(it >= 0) }
            fun count(key: String) = json.getInt(key).also { require(it >= 0) }
            val slow = json.getJSONArray("slow_tools")
            require(slow.length() <= 3)
            RunPerformanceSummary(
                duration("elapsed_ms"), duration("preparation_ms"), duration("model_ms"), duration("tools_ms"),
                duration("paused_ms"), duration("other_ms"), count("requests"), count("retries"), count("tool_calls"),
                count("failed_tools"), count("unfinished_tools"), count("image_count"), duration("image_bytes"),
                duration("first_delta_total_ms"), count("first_delta_samples"),
                List(slow.length()) { i -> slow.getJSONObject(i).let { item ->
                    val name = item.getString("name")
                    require(name.matches(Regex("[A-Za-z0-9_.:-]{1,64}")))
                    SlowTool(name, item.getLong("elapsed_ms").also { require(it >= 0) })
                } },
            )
        }.getOrNull()
    }
}
