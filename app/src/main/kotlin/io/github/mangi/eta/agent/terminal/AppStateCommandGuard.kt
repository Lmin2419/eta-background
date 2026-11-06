package io.github.mangi.eta.agent.terminal

import org.json.JSONArray
import org.json.JSONObject

/** Prevent ordinary terminal requests from bypassing the disabled app-freeze tool.
 * This is an accidental-use guard, not a sandbox for arbitrary privileged programs. */
internal object AppStateCommandGuard {
    private val packageMutation = Regex(
        """(?:^|[\s;/|&])(?:/system/bin/)?(?:pm|cmd\s+package)\s+(?:--user\s+\S+\s+)?(?:disable(?:-user|-until-used)?|enable|default-state|suspend|unsuspend|hide|unhide)\b""",
        RegexOption.IGNORE_CASE,
    )
    fun rejects(value: Any?): Boolean = when (value) {
        is JSONObject -> value.keys().asSequence().any { rejects(value.opt(it)) }
        is JSONArray -> (0 until value.length()).any { rejects(value.opt(it)) }
        is String -> packageMutation.containsMatchIn(value.replace("\\\n", "").replace("'", "").replace("\"", "").replace("\\", ""))
        else -> false
    }
    fun rejection(): String = JSONObject().put("ok", false).put("code", "APP_STATE_MANUAL_ONLY")
        .put("command_accepted", false)
        .put("message", "应用冻结、解冻和启用状态需由用户在系统设置中修改；胖小胖不通过终端自动修改。").toString()
}
