package io.github.mangi.eta.agent.browser

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A batch is a short, already-decided sequence, not a retry/workflow engine. */
internal object BrowserActionBatch {
    const val MAX_STEPS = 8
    val singleActions = setOf(
        "navigate", "get_readable", "get_text", "find_elements", "click", "type", "scroll",
        "screenshot", "get_page_info", "go_back", "go_forward", "reload", "wait_for_selector", "get_responses",
    )
    val inputActions = setOf("click", "type", "scroll", "go_back", "go_forward", "reload", "navigate")

    fun parse(args: JSONObject): List<JSONObject> {
        val actions = args.optJSONArray("actions") ?: throw JSONException("batch 需要 actions 数组")
        if (actions.length() !in 1..MAX_STEPS) throw JSONException("actions 必须包含 1–$MAX_STEPS 个动作")
        return (0 until actions.length()).map { index ->
            val step = actions.optJSONObject(index) ?: throw JSONException("actions[$index] 必须为对象")
            val name = step.optString("action")
            if (name !in singleActions) throw JSONException("actions[$index].action 无效，不支持嵌套 batch")
            if (name == "navigate" && step.optString("url").isBlank()) throw JSONException("navigate 缺少 url")
            if (name == "type" && step.opt("text") !is String) throw JSONException("type 缺少 text")
            if (name in setOf("click", "type")) {
                val selector = step.optString("selector").isNotBlank()
                val x = step.opt("coordinate_x") as? Number
                val y = step.opt("coordinate_y") as? Number
                if (!selector && (x == null || y == null)) throw JSONException("$name 需要 selector 或整数坐标")
                listOf("coordinate_x", "coordinate_y").forEach { key ->
                    if (step.has(key)) {
                        val coordinate = (step.opt(key) as? Number)?.toDouble()
                        if (coordinate == null || coordinate != coordinate.toInt().toDouble()) throw JSONException("$key 必须为整数")
                    }
                }
                if (step.has("coordinate_x") != step.has("coordinate_y")) throw JSONException("坐标必须成对提供")
            }
            if (name == "wait_for_selector" && step.optString("selector").isBlank()) throw JSONException("wait_for_selector 缺少 selector")
            JSONObject(step.toString())
        }
    }

    fun execute(
        steps: List<JSONObject>,
        check: () -> Unit,
        action: (JSONObject) -> BrowserToolResult,
    ): BrowserToolResult {
        val results = JSONArray()
        val images = mutableListOf<BrowserImage>()
        var last = JSONObject()
        var completed = 0
        var previousInputMayHaveRun = false
        for ((index, step) in steps.withIndex()) {
            check()
            val result = action(step)
            last = JSONObject(result.content)
            val summary = JSONObject()
                .put("index", index).put("action", step.getString("action"))
                .put("ok", last.optBoolean("ok"))
            listOf("code", "status", "message", "operationMayHaveRun", "feedback", "outcome", "elapsed_ms").forEach { key ->
                if (last.has(key)) summary.put(key, last.opt(key))
            }
            results.put(summary)
            // Retain at most one screenshot; requesting many must not multiply image tokens.
            if (result.images.isNotEmpty()) { images.clear(); images.add(result.images.last()) }
            if (!last.optBoolean("ok")) {
                val failedInputMayHaveRun = last.optBoolean("operationMayHaveRun", false)
                last.put("failed_step_operation_may_have_run", failedInputMayHaveRun)
                last.put("operationMayHaveRun", previousInputMayHaveRun || failedInputMayHaveRun)
                return BrowserToolResult(BrowserPayloadLimiter.serialize(
                    last.put("action", "batch").put("steps", results)
                        .put("completed_count", completed).put("failed_index", index)
                        .put("remaining_count", steps.size - index - 1)
                        .put("automatic_replay", false)
                ), images)
            }
            if (step.getString("action") in inputActions) previousInputMayHaveRun = true
            completed++
        }
        return BrowserToolResult(BrowserPayloadLimiter.serialize(
            last.put("action", "batch").put("steps", results)
                .put("operationMayHaveRun", previousInputMayHaveRun)
                .put("completed_count", completed).put("remaining_count", 0)
                .put("automatic_replay", false)
        ), images)
    }
}
