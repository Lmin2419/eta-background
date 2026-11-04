package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared native-tool contract for the per-run foreground/background entry.
 *
 * Only this GUI contract is published. Location defaults to this task’s current location;
 * every new task starts in the foreground. The display protocol stays private to the backend.
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
                    "执行位置；省略沿用本任务位置，新任务初始前台。",
                ))
            if (name == "observe_screen") properties.put("status_only", JSONObject()
                .put("type", "boolean").put("default", false)
                .put("description", "仅后台：只查本任务屏幕/引擎状态，不开应用、不截图、不改变执行位置，不产生可输入的观察；排障时使用。"))
            if (name == "launch_app") properties.put("orientation", JSONObject()
                .put("type", "string").put("enum", JSONArray(listOf("auto", "portrait", "landscape")))
                .put("description", "后台新建屏方向；默认 auto 遵循应用，已有页面不为转向而重建。前台由系统处理。"))
            if (name == "press_key") function.put("description", function.optString("description") +
                " 后台支持 BACK/HOME/ENTER/RECENTS，必传当前 observation_id；其他按键返回不支持，不作用于主屏。")
            if (name == "type_text") function.put("description", function.optString("description") +
                " 后台只支持 replace 且 submit=false；需发送时重新观察并点击发送按钮。")
            if (name in setOf("open_uri", "open_system_panel", "wait_for_package")) {
                function.put("description", function.optString("description") + " 仅前台支持；后台不会回退主屏。")
            }
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
