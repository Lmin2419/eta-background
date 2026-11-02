package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** One stable native entry; saved instructions are fetched on demand, never expanded into the schema. */
internal object AgentCommonTaskToolCatalog {
    fun appendTo(tools: JSONArray, writable: Boolean = true) {
        val actions = if (writable) listOf("list", "get", "save", "delete") else listOf("list", "get")
        val properties = JSONObject()
            .put("action", JSONObject().put("type", "string").put("enum", JSONArray(actions)))
            .put("id", string("Task ID from list/get; required for get/delete and updating save.", 64))
            .put("name", string("list: optional name filter; save: required task name.", 60))
            .put("limit", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 20))
            .put("offset", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 64))
        if (writable) properties
            .put("instruction", string("save: full reusable instruction, not chat logs, coordinates or secrets.", 8000))
            .put("revision", string("Required for update/delete: latest revision from list/get/save; refresh after conflict.", 64))
        tools.put(AgentToolSchema.function("common_task",
            "Eta built-in 常用任务 library shared with the task screen and home shortcuts. list returns names/IDs only; get reads one workflow. " +
                (if (writable) "Only save/edit/delete when the user asks; save without id creates (same name/body retries reuse it). " else "Read-only in roleplay. ") +
                "Reading or saving never starts a run or schedule. When the user asks to run a saved task, get it and execute with existing tools in this run, respecting current instructions and permissions.",
            JSONObject().put("type", "object").put("properties", properties).put("required", JSONArray().put("action"))))
    }

    private fun string(description: String, max: Int) = JSONObject().put("type", "string")
        .put("description", description).put("maxLength", max)
}
