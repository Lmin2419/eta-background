package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** Chat 请求副本中的 ID 修复不回写持久历史，只接受完整且无歧义的工具交换。 */
internal object ChatToolCallHistory {
    fun referencedIds(messages: JSONArray): Set<String> = buildSet {
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            when (message.optString("role")) {
                "assistant" -> {
                    val calls = message.optJSONArray("tool_calls") ?: continue
                    for (callIndex in 0 until calls.length()) {
                        calls.optJSONObject(callIndex)?.nonBlankString("id")?.let(::add)
                    }
                }
                "tool" -> message.nonBlankString("tool_call_id")?.let(::add)
            }
        }
    }

    fun repairCompleteBatches(messages: JSONArray) {
        val reservedIds = referencedIds(messages).toMutableSet()
        val seenCallIds = mutableSetOf<String>()
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            if (message.optString("role") != "assistant") continue
            val calls = message.optJSONArray("tool_calls") ?: continue
            val pairs = completeBatch(messages, index, calls)
            pairs?.forEachIndexed { callIndex, pair ->
                if (pair.id in seenCallIds) {
                    val base = "call_eta_history_${index}_$callIndex"
                    var replacement = base
                    var suffix = 1
                    while (!reservedIds.add(replacement)) replacement = "${base}_${suffix++}"
                    pair.call.put("id", replacement)
                    pair.result.put("tool_call_id", replacement)
                }
            }
            // 歧义批次保持原样，其已有 ID 仍不能与后续可修复批次重用。
            for (callIndex in 0 until calls.length()) {
                calls.optJSONObject(callIndex)?.nonBlankString("id")?.let(seenCallIds::add)
            }
        }
    }

    private fun completeBatch(
        messages: JSONArray,
        assistantIndex: Int,
        calls: JSONArray,
    ): List<CallResultPair>? {
        if (calls.length() == 0) return null
        val callsById = linkedMapOf<String, JSONObject>()
        for (index in 0 until calls.length()) {
            val call = calls.optJSONObject(index) ?: return null
            val id = call.nonBlankString("id") ?: return null
            if (callsById.put(id, call) != null) return null
        }
        val resultsById = mutableMapOf<String, JSONObject>()
        var index = assistantIndex + 1
        while (index < messages.length()) {
            val result = messages.optJSONObject(index) ?: break
            if (result.optString("role") != "tool") break
            val id = result.nonBlankString("tool_call_id") ?: return null
            if (id !in callsById || resultsById.put(id, result) != null) return null
            index++
        }
        if (callsById.keys != resultsById.keys) return null
        return callsById.map { (id, call) -> CallResultPair(id, call, resultsById.getValue(id)) }
    }

    private fun JSONObject.nonBlankString(key: String): String? =
        (opt(key) as? String)?.takeIf { it.isNotBlank() }

    private data class CallResultPair(
        val id: String,
        val call: JSONObject,
        val result: JSONObject,
    )
}
