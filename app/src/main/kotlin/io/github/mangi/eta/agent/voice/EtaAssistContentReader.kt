package io.github.mangi.eta.agent.voice

import android.app.assist.AssistStructure
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.text.InputType
import android.view.View
import org.json.JSONObject

/** 在后台展开系统提供的 Assist 数据；不读取无障碍节点，也不执行应用附带的 Intent。 */
internal object EtaAssistContentReader {
    fun read(context: Context, state: VoiceInteractionSession.AssistState): String {
        val output = BoundedText()
        val structure = state.assistStructure
        val component = structure?.activityComponent
        output.line("焦点应用", state.isFocused.toString())
        component?.let {
            output.line("应用组件", it.flattenToShortString())
            try {
                val info = context.packageManager.getApplicationInfo(it.packageName, 0)
                output.line("应用名称", context.packageManager.getApplicationLabel(info).toString())
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                // 包在采集后卸载时仍保留系统提供的组件信息。
            }
        }
        state.assistContent?.let { content ->
            output.line("网页链接", content.webUri?.toString())
            output.line("结构化内容", content.structuredData, 6_000)
            output.line("页面动作", content.intent?.action)
            output.line("页面链接", content.intent?.dataString)
            content.clipData?.let { clip ->
                for (index in 0 until minOf(clip.itemCount, 4)) {
                    val item = clip.getItemAt(index)
                    output.line("应用提供的文本", item.text?.toString())
                    output.line("应用提供的链接", item.uri?.toString())
                }
            }
        }
        try {
            appendData(output, state.assistData)
        } catch (_: android.os.BadParcelableException) {
            output.line("提示", "部分应用附加数据不可读")
        }
        if (structure != null) {
            var visited = 0
            var truncated = structure.windowNodeCount > 4
            val stack = ArrayDeque<Pair<AssistStructure.ViewNode, Int>>()
            for (windowIndex in 0 until minOf(structure.windowNodeCount, 4)) {
                val window = structure.getWindowNodeAt(windowIndex)
                output.line("窗口", window.title?.toString())
                stack.addLast(window.rootViewNode to 0)
                while (stack.isNotEmpty() && visited < MAX_NODES && !output.full) {
                    val (node, depth) = stack.removeLast()
                    visited++
                    if (node.visibility != View.VISIBLE || isPassword(node.inputType)) continue
                    output.line("控件", node.className)
                    output.line("文本", node.text?.toString())
                    output.line("描述", node.contentDescription?.toString())
                    output.line("提示", node.hint)
                    if (depth < MAX_DEPTH) {
                        val children = minOf(node.childCount, MAX_NODES - visited - stack.size)
                        if (children < node.childCount) truncated = true
                        for (child in children - 1 downTo 0) {
                            stack.addLast(node.getChildAt(child) to depth + 1)
                        }
                    } else if (node.childCount > 0) truncated = true
                }
                if (visited >= MAX_NODES || output.full) break
            }
            if (truncated || stack.isNotEmpty() || visited >= MAX_NODES) output.line("提示", "界面节点已按容量截断")
        }
        return output.toString()
    }

    private fun appendData(output: BoundedText, data: Bundle?) {
        if (data == null) return
        // Bundle 可能包含任意 Parcelable；只接收有界的简单文本和基础值。
        @Suppress("DEPRECATION")
        for (key in data.keySet().sorted().take(24)) {
            val value = data.get(key)
            if (value is CharSequence || value is Number || value is Boolean) {
                output.line("应用数据：${key.take(128)}", value.toString())
            }
        }
    }

    internal fun isPassword(inputType: Int): Boolean {
        val type = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (type == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) || (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private class BoundedText {
        private val text = StringBuilder()
        val full: Boolean get() = text.length >= MAX_CHARS
        fun line(label: String, value: String?, limit: Int = 600) {
            if (value.isNullOrBlank() || full) return
            val line = "$label：${JSONObject.quote(value.take(limit))}\n"
            val remaining = MAX_CHARS - text.length
            text.append(line.take(remaining))
        }
        override fun toString(): String = text.toString() + if (full) "\n[应用内容已截断]" else ""
    }

    private const val MAX_NODES = 400
    private const val MAX_DEPTH = 24
    private const val MAX_CHARS = 10_000
}
