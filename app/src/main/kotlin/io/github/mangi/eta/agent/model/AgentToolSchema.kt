package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

internal object AgentToolSchema {
    fun coordinateSpace(): JSONObject =
        JSONObject()
            .put("type", "string")
            .put("enum", JSONArray().put("normalized").put("screen").put("display").put("screenshot"))
            .put(
                "description",
                "必填。normalized（推荐）：x、y 取 0–999，相对整块屏幕的比例，看截图时用它，不受图片缩放影响；" +
                    "screen：真实屏幕像素，ui_nodes 的 center/bounds 就是这个坐标；" +
                    "display：后台虚拟屏像素，与节点 bounds 相同；" +
                    "screenshot：最近一次附图的原始像素，后台使用时必须同时携带同一次观察的 frame_id。",
            )

    fun function(
        name: String,
        description: String,
        parameters: JSONObject,
    ): JSONObject =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", parameters),
            )
}
