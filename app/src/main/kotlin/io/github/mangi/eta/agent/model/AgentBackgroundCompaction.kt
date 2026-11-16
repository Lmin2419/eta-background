package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentTokenUsage
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 主对话的后台压缩策略（etax/design/05 §6）。只有主对话传入；子代理、标题生成、重写回复不传，保持同步行为。
 *
 * [key] 在同一对话的多次运行间不变（模型会话 ID），后台结果可以跨轮接上。
 * [modelConfig] 为 null 表示跟随对话模型（仍能复用提示缓存）。
 */
internal data class BackgroundCompactionPolicy(
    val key: String,
    val startRatio: Double = 0.60,
    val keepRecentTurns: Int = 4,
    val modelConfig: AgentModelClient.ModelConfig? = null,
    val providerFactory: ((AgentModelClient.ModelConfig) -> AgentProviderClient)? = null,
)

/** 进程内按会话保存一个后台压缩任务；任务完成后由会话在下一个安全点取走并应用。 */
internal object BackgroundCompactionRegistry {
    class Result(
        val cut: Int,
        val fingerprint: String,
        val summary: List<JSONObject>,
        val usage: AgentTokenUsage?,
        val archiveId: String,
    )

    enum class State { RUNNING, READY, FAILED }

    private class Slot(val token: Any, var state: State, var result: Result? = null)

    private val slots = HashMap<String, Slot>()
    private val failureCounts = HashMap<String, Int>()

    /** 测试可替换为同步执行；默认起一条守护线程。 */
    @Volatile
    internal var launcher: (Runnable) -> Unit = { work ->
        Thread(work, "agent-context-precompact").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun state(key: String): State? = slots[key]?.state

    @Synchronized
    fun failures(key: String): Int = failureCounts[key] ?: 0

    /** 已有任务时不重复启动；返回是否启动。 */
    fun start(key: String, work: () -> Result): Boolean {
        val token = Any()
        synchronized(this) {
            if (slots[key] != null) return false
            slots[key] = Slot(token, State.RUNNING)
        }
        launcher(Runnable {
            val outcome = runCatching(work)
            synchronized(this) {
                val slot = slots[key]
                if (slot == null || slot.token !== token) return@synchronized
                outcome.onSuccess { slot.state = State.READY; slot.result = it }
                    .onFailure {
                        slot.state = State.FAILED
                        failureCounts[key] = (failureCounts[key] ?: 0) + 1
                    }
            }
        })
        return true
    }

    /** 取走已完成的结果并清除；没有就返回 null。 */
    @Synchronized
    fun takeReady(key: String): Result? {
        val slot = slots[key] ?: return null
        if (slot.state != State.READY) return null
        slots.remove(key)
        return slot.result
    }

    /** 清掉失败的任务（失败次数保留），返回是否清掉了。 */
    @Synchronized
    fun clearFailed(key: String): Boolean {
        if (slots[key]?.state != State.FAILED) return false
        slots.remove(key)
        return true
    }

    @Synchronized
    fun clear(key: String) {
        slots.remove(key)
        failureCounts.remove(key)
    }

    /**
     * 被压范围的指纹。下一轮的历史是从存储重建的（不带推理内容、服务商的临时状态和屏幕观察），
     * 所以只比较「角色 + 文字 + 工具调用编号」，不比较原始 JSON；屏幕观察消息不参与。
     */
    fun fingerprint(messages: List<JSONObject>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        messages.forEachIndexed { index, message ->
            if (index > 0) digest.update('\n'.code.toByte())
            digest.update(normalized(message).toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isObservation(message: JSONObject): Boolean = message.optBoolean("_eta_observation")

    private fun normalized(message: JSONObject): String = buildString {
        append(message.optString("role")).append('\u0001')
        append(message.optString("tool_call_id")).append('\u0001')
        when (val content = message.opt("content")) {
            is String -> append(content.trim())
            is org.json.JSONArray -> for (index in 0 until content.length()) {
                val part = content.optJSONObject(index) ?: continue
                if (part.optString("type") in setOf("text", "input_text", "output_text")) append(part.optString("text").trim())
            }
            else -> Unit
        }
        append('\u0001')
        AgentConversationCodec.parseToolCalls(message).forEach { append(it.id).append(':').append(it.name).append(';') }
    }
}
