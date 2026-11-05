package io.github.mangi.eta.agent.tool

import android.os.SystemClock
import org.json.JSONObject

/** One instance per ShadowTools binding; legacy and native tools share the same budget.
 * Synchronized because observe_screen records its node read and capture concurrently. */
class BackgroundFeedbackGuard {
    private val guard = ActionRepeatGuard(SystemClock::uptimeMillis)
    private var dump: JSONObject? = null
    private var shot: JSONObject? = null
    private var pendingTarget: String? = null
    private var pendingState = ""

    @Synchronized fun before(name: String, args: JSONObject): JSONObject? {
        pendingTarget = target(name, args)
        pendingState = state()
        return if (guard.blocked(pendingTarget, pendingState)) JSONObject().put("ok", false)
            .put("code", "NO_PROGRESS").put("operationMayHaveRun", false)
            .put("message", "同一目标已提交两次但未确认结果，本次未执行。请查看短提示、输入状态和最终结果，勿换成坐标/长按重复提交。") else null
    }

    @Synchronized fun record(name: String, result: JSONObject) {
        if (name == "screen_dump" && result.optBoolean("ok") && result.has("nodes")) dump = JSONObject(result.toString())
        if (name == "screen_shot" && result.optBoolean("ok")) shot = JSONObject(result.toString())
        if (name == "screen_finish") { if (result.optBoolean("ok")) clear(); return }
        if (name == "screen_open") {
            if (result.optBoolean("ok") && result.optInt("displayId", result.optInt("display", -1)) != dump?.optInt("displayId")) clear()
            return
        }
        if (name !in setOf("screen_click", "screen_tap", "screen_press")) return
        val changed = result.optJSONObject("after")?.optBoolean("screen_changed") == true || result.optString("outcome") == "CHANGED"
        guard.record(pendingTarget, pendingState, result.optBoolean("ok") || result.optBoolean("operationMayHaveRun"), changed)
    }

    @Synchronized fun clear() { guard.clear(); dump = null; shot = null; pendingTarget = null }

    private fun state(): String {
        val current = dump ?: return ""
        // dump geometry/nodes have no observation timestamp; new observation IDs cannot bypass a retry limit.
        return java.security.MessageDigest.getInstance("SHA-256").digest(
            (current.optJSONObject("geometry")?.toString().orEmpty() + current.optJSONArray("nodes")?.toString().orEmpty()).toByteArray()
        ).joinToString("") { "%02x".format(it) }
    }

    private fun target(name: String, args: JSONObject): String? {
        val current = dump ?: return null
        val array = current.optJSONArray("nodes") ?: return null
        val nodes = (0 until minOf(array.length(), 400)).mapNotNull(array::optJSONObject)
        val node = when (name) {
            "screen_click" -> {
                if (!args.has("text") && !args.has("desc") && !args.has("view_id") && !args.has("viewId")) return null
                val candidates = nodes.filter { n ->
                    n.optBoolean("click") && listOf("text" to "text", "desc" to "desc", "view_id" to "vid", "viewId" to "vid").all { (a, f) ->
                        !args.has(a) || if (args.optBoolean("exact")) n.optString(f) == args.optString(a)
                        else n.optString(f).contains(args.optString(a))
                    }
                }
                candidates.getOrNull(args.optInt("index", 0))
            }
            "screen_tap", "screen_press" -> {
                if (!args.has("x") || !args.has("y")) return null
                var x = args.optDouble("x"); var y = args.optDouble("y")
                when (args.optString("coordinate_space", args.optString("coordinateSpace", "display"))) {
                    "display" -> Unit
                    "screenshot" -> {
                        val frame = shot ?: return null
                        if (frame.optString("frameId") != args.optString("frame_id", args.optString("frameId"))) return null
                        val geo = frame.optJSONObject("geometry") ?: return null
                        if (frame.optInt("width") <= 0 || frame.optInt("height") <= 0) return null
                        x *= geo.optInt("width").toDouble() / frame.optInt("width")
                        y *= geo.optInt("height").toDouble() / frame.optInt("height")
                    }
                    else -> return null
                }
                nodes.filter { n -> n.optBoolean("click") && n.optJSONArray("bounds")?.let { b ->
                    b.length() == 4 && x >= b.optInt(0) && x < b.optInt(2) && y >= b.optInt(1) && y < b.optInt(3)
                } == true }.minByOrNull { n -> val b = n.getJSONArray("bounds");
                    (b.optInt(2) - b.optInt(0)).toLong() * (b.optInt(3) - b.optInt(1)) }
            }
            else -> null
        } ?: return null
        return "${current.optInt("displayId")}:${node.optString("pkg")}:${node.optJSONArray("bounds")}"
    }
}
