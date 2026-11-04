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
            "Eta 内置常用任务，与任务页面和桌面快捷方式共用数据。list 只列摘要，get 按需读取一个流程；读取或保存不启动执行/定时。" +
                (if (writable) "仅用户要求时保存/修改/删除；save 无 id 创建，相同名称和内容重试不重复创建。" else "角色会话只读。") +
                "用户要求执行时读取最新版，在当前会话用当前工具执行；不重放保存的工具参数。",
            JSONObject().put("type", "object").put("properties", properties).put("required", JSONArray().put("action"))))
    }

    private fun string(description: String, max: Int) = JSONObject().put("type", "string")
        .put("description", description).put("maxLength", max)
}
