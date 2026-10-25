package io.github.mangi.eta.agent.tool

import org.json.JSONException
import org.json.JSONObject

// Android 的 getString 会把 JSON null、对象和数组转成文本；写操作必须保留真实文本语义。
internal fun JSONObject.requiredText(name: String): String =
    opt(name) as? String ?: throw JSONException("$name 缺失或不是文本")
