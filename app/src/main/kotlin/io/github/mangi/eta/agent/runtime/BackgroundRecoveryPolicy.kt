package io.github.mangi.eta.agent.runtime

import org.json.JSONObject

/** Advice describes evidence, not an instruction to restart apps or replay input. */
internal object BackgroundRecoveryPolicy {
    fun decorate(name: String, raw: String?): String? {
        val result = runCatching { JSONObject(raw.orEmpty()) }.getOrNull() ?: return raw
        val code = result.optString("code")
        val state: String
        val action: String
        val message: String
        when {
            code == "UI_NOT_QUIET" -> {
                state = "PAGE_ACTIVE"; action = "OBSERVE_TARGET_OR_WAIT_TEXT"
                message = "未静止不等于卡死。观察目标按钮或等待指定文字，不重复等待整页静止。"
            }
            code == "APP_TAKEN_OVER" -> {
                state = "USER_TAKEOVER"; action = "WAIT_FOR_USER"
                message = "应用已移出后台，等待用户决定；不要拉回、强停或重开。"
            }
            code == "CLEANUP_PENDING" -> {
                state = "RELEASING"; action = "CHECK_STATE"
                message = "释放尚未确认。先检查状态，避免连续重开；不能把新 display 当作原页面已恢复。"
            }
            code in setOf("NO_SESSION", "DISPLAY_NOT_FOUND", "STALE_SCREEN", "SYSTEM_RESTARTED", "SESSION_CLOSED") -> {
                state = "SESSION_LOST"; action = "REPORT_STATE_LOSS"
                message = "原会话不可用，不能承诺保留原页面；需要用户决定是否重新打开。"
            }
            code == "NEED_FORCE_STOP" -> {
                state = "MOVE_FAILED"; action = "ASK_USER"
                message = "保留状态的迁移未成功。强停会丢失页面状态，须取得用户明确同意。"
                // Do not forward the low-level "pass allowForceStop=true" hint as
                // an instruction to the model. Keep the failure code for diagnosis.
                result.put("message", message).put("error", message)
            }
            code in setOf("INPUT_NOT_READY", "OBSERVER_UNAVAILABLE", "WINDOW_NOT_READY") ||
                (name == "screen_open" && result.optBoolean("ok") && result.has("inputReady") && !result.optBoolean("inputReady")) -> {
                val reason = result.optJSONObject("inputState")?.optString("reason").orEmpty()
                val observerMissing = code == "OBSERVER_UNAVAILABLE" || reason == "OBSERVER_UNAVAILABLE"
                state = if (observerMissing) "OBSERVER_UNAVAILABLE" else "WAITING_FOR_WINDOW"
                action = if (observerMissing) "RESTORE_OBSERVER" else "OBSERVE_EXISTING_SESSION"
                message = "启动已发出不等于窗口就绪。先确认已有会话与观察服务；不要输入、销毁或反复重开。"
            }
            else -> return raw
        }
        result.put("recovery", JSONObject().put("state", state).put("next_action", action)
            .put("message", message).put("automatic_force_stop", false))
        return result.toString()
    }
}
