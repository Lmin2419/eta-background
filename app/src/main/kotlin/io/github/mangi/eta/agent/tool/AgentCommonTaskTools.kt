package io.github.mangi.eta.agent.tool

import android.content.Context
import io.github.mangi.eta.data.repository.CommonTask
import io.github.mangi.eta.data.repository.CommonTaskRepository
import io.github.mangi.eta.ui.CommonTaskShortcuts
import org.json.JSONArray
import org.json.JSONObject

internal class AgentCommonTaskTools(
    private val repository: CommonTaskRepository,
    private val writable: Boolean = true,
    private val onSaved: (CommonTask) -> Boolean = { true },
    private val onDeleted: (String) -> Unit = {},
) {
    constructor(context: Context, writable: Boolean) : this(CommonTaskRepository(context), writable,
        { CommonTaskShortcuts.update(context, it) }, { CommonTaskShortcuts.disable(context, it) })

    fun execute(args: JSONObject): String = try {
        val action = text(args, "action", 16)
        require(action in setOf("list", "get", "save", "delete"))
        if (!writable && action in setOf("save", "delete")) {
            error("COMMON_TASK_READ_ONLY", "角色会话中的常用任务只读，不把剧情写入现实任务")
        } else {
            val result = JSONObject().put("ok", true).put("action", action).put("started", false)
            when (action) {
                "list" -> {
                    val query = optionalText(args, "name", 60).orEmpty()
                    val limit = integer(args, "limit", 20, 1..20)
                    val offset = integer(args, "offset", 0, 0..CommonTaskRepository.MAX_TASKS)
                    val tasks = repository.load().filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
                    result.put("tasks", JSONArray(tasks.drop(offset).take(limit).map { summary(it) }))
                        .put("total", tasks.size)
                    if (offset + limit < tasks.size) result.put("next_offset", offset + limit)
                }
                "get" -> {
                    val id = text(args, "id", 64)
                    val task = repository.load().firstOrNull { it.id == id }
                    require(task != null) { "COMMON_TASK_NOT_FOUND" }
                    result.put("task", summary(task).put("instruction", task.instruction))
                }
                "save" -> {
                    val id = optionalText(args, "id", 64)
                    val revision = if (id != null) text(args, "revision", 64) else null
                    require(id != null || !args.has("revision"))
                    val task = repository.save(id, text(args, "name", 60), text(args, "instruction", 8000),
                        expectedRevision = revision, uniqueName = true)
                    result.put("task", summary(task))
                        .put("shortcut_updated", runCatching { onSaved(task) }.getOrDefault(false))
                }
                "delete" -> {
                    val id = text(args, "id", 64)
                    repository.delete(id, text(args, "revision", 64))
                    result.put("deleted", true).put("shortcut_updated", runCatching { onDeleted(id) }.isSuccess)
                }
            }
            result.toString()
        }
    } catch (failure: IllegalArgumentException) {
        when (failure.message) {
            "COMMON_TASK_CONFLICT" -> error("COMMON_TASK_CONFLICT", "任务已被修改，请 get 读取最新流程后再决定是否修改；本次未写入")
            "COMMON_TASK_NOT_FOUND" -> error("COMMON_TASK_NOT_FOUND", "任务不存在，请 list 刷新列表")
            "COMMON_TASK_LIMIT" -> error("COMMON_TASK_LIMIT", "常用任务已达 64 个，本次未创建")
            "COMMON_TASK_NAME_EXISTS" -> error("COMMON_TASK_NAME_EXISTS", "已有同名任务，请 list/get 确认目标后再修改，不会自动覆盖")
            else -> error("INVALID_ARGUMENT", "请检查 action、id、name、instruction、revision 及分页范围")
        }
    } catch (_: Exception) {
        error("COMMON_TASK_STORAGE_ERROR", "常用任务暂时无法读取或保存；没有清空现有任务，请到常用任务页面检查")
    }

    private fun summary(task: CommonTask) = JSONObject().put("id", task.id).put("name", task.name)
        .put("revision", task.revision)

    private fun text(args: JSONObject, key: String, max: Int): String {
        val value = args.opt(key)
        require(value is String && value.isNotBlank() && value.length <= max)
        return value.trim()
    }

    private fun optionalText(args: JSONObject, key: String, max: Int): String? =
        if (args.has(key)) text(args, key, max) else null

    private fun integer(args: JSONObject, key: String, default: Int, range: IntRange): Int {
        if (!args.has(key)) return default
        val value = args.opt(key)
        require(value is Number && value.toDouble() == value.toInt().toDouble() && value.toInt() in range)
        return value.toInt()
    }

    private fun error(code: String, message: String) = JSONObject().put("ok", false)
        .put("code", code).put("message", message).put("started", false).toString()
}
