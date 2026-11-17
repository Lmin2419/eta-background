package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 工具报错讲人话（etax/design/10 第二期）：失败结果统一补上 retryable（原样重试有没有意义）和 hint（下一步查什么）；
 * 同一次运行里同一工具、同样参数第二次失败时加 repeat_failures 提醒不要第三次原样重试。
 * 只改给模型看的结果，不改工具本身的行为，也不拦截执行。工具自己给了 hint / retryable 的保留原样。
 */
internal class ToolFailureAnnotator {
    private val failures = mutableMapOf<String, Int>()

    fun annotate(call: AgentModelClient.ToolCall, result: AgentModelClient.ToolResult): AgentModelClient.ToolResult {
        val json = runCatching { JSONObject(result.content) }.getOrNull() ?: return result
        val exit = json.opt("exit_code")?.toString()?.toIntOrNull()
        val ok = json.optBoolean("ok", exit == null || exit == 0)
        if (ok) return result
        val code = json.optString("code")
        var changed = false
        if (!json.has("hint")) hintFor(code, exit)?.let { json.put("hint", it); changed = true }
        if (!json.has("retryable")) retryableFor(code, exit)?.let { json.put("retryable", it); changed = true }
        val key = call.name + '\u0000' + canonicalArguments(call.argumentsJson)
        val count = (failures[key] ?: 0) + 1
        failures[key] = count
        if (count >= 2) {
            val earlier = json.optString("hint").takeIf { it.isNotBlank() && !it.startsWith(REPEAT_HINT) }
            json.put("repeat_failures", count)
            json.put("hint", REPEAT_HINT + (earlier?.let { " $it" }.orEmpty()))
            changed = true
        }
        return if (changed) result.copy(content = json.toString()) else result
    }

    companion object {
        const val REPEAT_HINT = "同样的调用已经失败两次以上，不要再原样重试：先排查原因或换参数、换方法。"

        /** 参数 JSON 按键排序后比较：键顺序不同算同一个调用。 */
        internal fun canonicalArguments(raw: String): String =
            runCatching { canonical(JSONObject(raw)) }.getOrDefault(raw.trim())

        private fun canonical(value: Any?): String = when (value) {
            is JSONObject -> value.keys().asSequence().sorted()
                .joinToString(",", "{", "}") { key -> JSONObject.quote(key) + ":" + canonical(value.opt(key)) }
            is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
            is String -> JSONObject.quote(value)
            null, JSONObject.NULL -> "null"
            else -> value.toString()
        }

        internal fun hintFor(code: String, exit: Int?): String? = when {
            code.isBlank() && exit == 127 -> "命令不存在：确认是否已安装、名字有没有拼错、是否在 PATH 里。"
            code.isBlank() && exit == 126 -> "命令没有执行权限：检查文件权限，或换 Root 身份重跑。"
            code.isBlank() && exit != null -> "看 stderr 结尾和退出码，定位到具体文件和行，只改出错的那一处再重跑。"
            code == "TOOL_ERROR" -> "工具内部异常，可以原样重试一次；再失败就换方法。"
            code == "FILE_NOT_FOUND" || (code.endsWith("_NOT_FOUND") && code.startsWith("FILE")) ->
                "先列出父目录确认文件名和路径，注意大小写、空格和扩展名。"
            code == "WEB_ACCESS_DENIED" -> "网站拒绝访问（可能要登录或被限流），换一个来源，或改用浏览器工具。"
            code == "ROOT_UNAVAILABLE" || code == "ROOT_REQUIRED" -> "需要 Root：先用 terminal(action=check_root) 确认授权状态。"
            code.endsWith("_DENIED") || "PERMISSION" in code -> "权限不足：确认是否需要 Root 或系统授权；Root 已授权时换 Root 身份重跑。"
            code == "TOOL_NOT_AVAILABLE" -> "这个工具本轮没有提供，用目录里现有的工具。"
            code == "TRUNCATED_TOOL_CALL" -> "参数太长被截断了，拆成几次小的调用。"
            code == "WINDOW_NOT_READY" || code == "PAGE_NOT_READY" || code == "INPUT_NOT_READY" ->
                "界面还没准备好：稍等后只重新观察，不要重复点击或输入。"
            code == "UI_NOT_QUIET" -> "页面还在变化：等待后重新观察。"
            code == "NO_PROGRESS" -> "停止重试界面操作，报告看到的提示。"
            code.endsWith("TIMEOUT") -> "超时了：可以原样重试一次；再超时就缩小范围（更短的命令、更少的数据）或换方法。"
            code == "BAD_ARGUMENT" || "INVALID" in code || code.endsWith("_MISSING") ->
                "参数不对：对照工具说明检查参数名、类型和取值范围后再调用。"
            code.endsWith("_UNAVAILABLE") -> "这项能力当前不可用：不要原样重试，换别的方法，或告诉用户需要开启什么。"
            else -> null
        }

        internal fun retryableFor(code: String, exit: Int?): Boolean? = when {
            code == "TOOL_ERROR" || code.endsWith("TIMEOUT") || code == "WINDOW_NOT_READY" || code == "PAGE_NOT_READY" ||
                code == "INPUT_NOT_READY" || code == "UI_NOT_QUIET" -> true
            code == "TOOL_NOT_AVAILABLE" || code == "NO_PROGRESS" || code == "TRUNCATED_TOOL_CALL" || code == "BAD_ARGUMENT" ||
                code == "FILE_NOT_FOUND" || "INVALID" in code || code.endsWith("_UNAVAILABLE") || code.endsWith("_DENIED") -> false
            code.isBlank() && exit != null -> false
            else -> null
        }
    }
}
