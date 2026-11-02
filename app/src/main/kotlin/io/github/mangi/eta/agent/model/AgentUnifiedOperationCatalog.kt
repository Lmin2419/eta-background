package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared native-tool contract for the per-run foreground/background entry.
 *
 * Tool names stay unchanged so old screen_* callers keep their compatibility path. The new
 * fields are optional and default to the task's current location, so an absent field preserves
 * the previous foreground behavior.
 */
internal object AgentUnifiedOperationCatalog {
    val names = setOf(
        "launch_app",
        "observe_screen",
        "release_gui",
        "tap",
        "tap_area",
        "tap_element",
        "long_press",
        "long_press_element",
        "swipe",
        "scroll",
        "scroll_element",
        "type_text",
        "input_text",
        "replace_text",
        "clear_text",
        "press_key",
        "wait",
        "wait_for_text",
        "wait_for_package",
        "open_uri",
        "open_system_panel",
    )

    val observationBound = setOf(
        "tap",
        "tap_area",
        "tap_element",
        "long_press",
        "long_press_element",
        "swipe",
        "scroll",
        "scroll_element",
        "type_text",
        "input_text",
        "replace_text",
        "clear_text",
        "press_key",
    )

    val coordinateBound = setOf("tap", "tap_area", "long_press", "swipe")

    fun decorate(tools: JSONArray) {
        for (index in 0 until tools.length()) {
            val function = tools.getJSONObject(index).optJSONObject("function") ?: continue
            val name = function.optString("name")
            if (name !in names) continue
            val parameters = function.optJSONObject("parameters") ?: continue
            if (name == "launch_app") {
                val existing = function.optString("description")
                function.put(
                    "description",
                    existing + " 后台执行时必须提供精确 package_name；后台屏幕不可用时返回明确错误，不会改走主屏。",
                )
            }
            if (name == "observe_screen") {
                val existing = function.optString("description")
                function.put(
                    "description",
                    existing + " 后台执行时返回后台 observation_id、display 坐标契约与同一任务屏幕的截图信息。" +
                        "后台 include_screenshot=true 时检查 screenshot.attached 和图片附件；已收到图片无需再用 read_image 读取同帧，仅路径不表示已看图。",
                )
            }
            val properties = parameters.optJSONObject("properties") ?: JSONObject().also {
                parameters.put("properties", it)
            }
            properties.put("execution_location", JSONObject()
                .put("type", "string")
                .put("enum", JSONArray().put("foreground").put("background"))
                .put(
                    "description",
                    "仅当用户明确要求后台执行时传 background；成功后本任务后续原生 GUI 工具沿用后台位置。" +
                        "省略或 auto 时沿用当前位置；前台请求继续受现有 GUI 操作权管理。" +
                        "后台不可用会返回明确错误，不会自动改走主屏。",
                ))
            if (name in observationBound && !properties.has("observation_id")) {
                properties.put("observation_id", JSONObject()
                    .put("type", "string")
                    .put("description", "后台执行时必填：同一次后台 observe_screen 返回的 observation_id。"))
            }
            if (name in coordinateBound && !properties.has("frame_id")) {
                properties.put("frame_id", JSONObject()
                    .put("type", "string")
                    .put("description", "仅后台 screenshot 坐标需要：同一次后台 observe_screen 截图返回的 frame_id。"))
            }
        }
    }
}
