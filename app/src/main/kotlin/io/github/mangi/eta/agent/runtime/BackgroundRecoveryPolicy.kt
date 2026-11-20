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
                // 应用被切到主屏时任务会暂停，模型读到这条结果时用户已经点了「继续」（2026111625）。
                state = "USER_TAKEOVER"; action = "RECLAIM_TO_BACKGROUND"
                message = "应用被切到了主屏，任务已暂停；你看到这条时用户已选择继续。要接着在后台操作，先用 launch_app" +
                    "（execution_location=background）把应用接回后台屏（会保留页面状态），再 observe_screen；" +
                    "不要重放旧输入，不要强停，也不要改走前台操作。"
                result.put("message", message).put("error", message)
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
