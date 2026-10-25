package io.github.mangi.eta.agent.device

import android.content.Context
import org.json.JSONObject

/** 结构化设备工具只操作 Eta 所属用户，不能把前台用户误当成调用方用户。 */
internal object DeviceToolContract {
    // Android UID 的用户段宽度由系统 UserHandle 定义；公开 SDK 不提供数值 userId 的读取接口。
    fun appUserId(context: Context): Int = context.applicationInfo.uid / 100_000

    fun userScopeError(arguments: JSONObject, userId: Int): JSONObject? {
        if (!arguments.has("user_id")) return null
        val requested = arguments.opt("user_id")
        if (requested !is Number || requested.toDouble() != requested.toInt().toDouble() || requested.toInt() < 0) {
            return failure("INVALID_ARGUMENT", "user_id 必须是非负整数")
        }
        if (requested.toInt() == userId) return null
        return failure("USER_SCOPE_UNSUPPORTED", "此工具只支持 Eta 所属的 Android 用户，不自动跨用户或工作资料执行")
            .put("user_id", userId)
            .put("requested_user_id", requested.toInt())
    }

    fun validPackageName(value: String): Boolean =
        value.length <= 255 && (value == "android" || PACKAGE_NAME.matches(value))

    fun validSetting(namespace: String, key: String): Boolean =
        namespace in setOf("system", "secure", "global") && SETTING_KEY.matches(key)

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun failure(code: String, message: String): JSONObject =
        JSONObject().put("ok", false).put("code", code).put("message", message)

    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    private val SETTING_KEY = Regex("[A-Za-z0-9_.-]{1,200}")
}
