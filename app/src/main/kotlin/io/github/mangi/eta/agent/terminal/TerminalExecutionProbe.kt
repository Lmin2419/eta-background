package io.github.mangi.eta.agent.terminal

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** 在实际命令 Shell 内采样；结果仅描述该进程，不作为后续调用的授权凭据。 */
internal object TerminalExecutionProbe {
    private val commands = listOf("sh", "toybox", "busybox", "cmd", "am", "pm", "dumpsys", "settings", "rg", "grep", "find", "python3")

    fun marker(): String = "__ETA_RUNTIME_${UUID.randomUUID().toString().replace("-", "")}"

    fun script(marker: String): String = """
        printf '$marker:begin\n'
        printf 'uid=%s\n' "${'$'}(id -u 2>/dev/null)"
        printf 'executable=%s\n' "${'$'}(readlink /proc/${'$'}${'$'}/exe 2>/dev/null)"
        if [ -n "${'$'}{KSH_VERSION-}" ]; then printf 'provider=mksh\n';
        elif [ -n "${'$'}{BASH_VERSION-}" ]; then printf 'provider=bash\n';
        elif [ -n "${'$'}{ETA_BUSYBOX-}" ] && [ "${'$'}{ASH_STANDALONE-}" = 1 ]; then printf 'provider=busybox_ash\n';
        else printf 'provider=sh\n'; fi
        for eta_probe_command in ${commands.joinToString(" ")}; do
          command -v "${'$'}eta_probe_command" >/dev/null 2>&1 && printf 'command=%s\n' "${'$'}eta_probe_command"
        done
        unset eta_probe_command
        printf '$marker:end\n'
    """.trimIndent()

    data class Output(val text: String, val runtime: JSONObject?, val metadataBytes: Int)

    fun extract(text: String, marker: String, identity: String, environment: TerminalEnvironment): Output {
        val startToken = "$marker:begin\n"
        val start = text.indexOf(startToken)
        if (text.isNotEmpty() && startToken.startsWith(text)) {
            return Output("", null, text.toByteArray(Charsets.UTF_8).size)
        }
        if (start < 0) return Output(text, null, 0)
        val endToken = "$marker:end"
        val end = text.indexOf(endToken, start + startToken.length)
        if (end < 0) return Output(text.take(start), null, text.substring(start).toByteArray(Charsets.UTF_8).size)
        val endExclusive = (end + endToken.length).let { if (text.getOrNull(it) == '\n') it + 1 else it }
        val entries = text.substring(start + startToken.length, end).lineSequence().take(32).toList()
        fun value(key: String): String? = entries.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')?.take(1_024)?.ifBlank { null }
        val observedUid = value("uid")?.toIntOrNull()
        val executable = value("executable")
        val provider = when (executable?.substringAfterLast('/')) {
            "busybox" -> "busybox_ash"
            "mksh" -> "mksh"
            "bash" -> "bash"
            "dash" -> "dash"
            "toybox" -> "toybox_sh"
            else -> value("provider") ?: "unknown"
        }
        val runtime = JSONObject()
            .put("host_identity", identity)
            .put("uid", observedUid ?: JSONObject.NULL)
            .put("uid_scope", if (environment.isLinux) "guest" else "android")
            .put("shell_provider", provider)
            .put("shell_executable", executable ?: JSONObject.NULL)
            .put("available_commands", JSONArray(entries.filter { it.startsWith("command=") }.map { it.substringAfter('=') }.filter { it in commands }))
            .put("capability_scope", "当前 Shell 的命令解析结果；不表示具备服务访问权限，也不替代执行前授权检查")
        return Output(
            text = text.take(start) + text.substring(endExclusive),
            runtime = runtime,
            metadataBytes = text.substring(start, endExclusive).toByteArray(Charsets.UTF_8).size,
        )
    }
}
