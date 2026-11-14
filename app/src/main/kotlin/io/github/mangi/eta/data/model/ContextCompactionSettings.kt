package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

/**
 * 上下文压缩配置（etax/design/05 §6）。
 *
 * [background] 为真时，用量到 [startPercent]% 就在后台把较早的对话整理成摘要，前台照常运行；
 * 最近 [keepRecentTurns] 轮用户对话原样保留。到 85% 后台还没好就等它，后台失败则退回同步压缩。
 * [modelId] 为空表示跟随对话模型（仍能复用提示缓存）；[reasoningEffort] 为空时取最低可用档位。
 */
@Serializable
internal data class ContextCompactionSettings(
    val background: Boolean = true,
    val modelId: String? = null,
    val reasoningEffort: String? = null,
    val startPercent: Int = 60,
    val keepRecentTurns: Int = 4,
) {
    val resolvedReasoningEffort: ReasoningEffort?
        get() = ReasoningEffort.fromWireValue(reasoningEffort)

    fun normalized(): ContextCompactionSettings = copy(
        startPercent = startPercent.coerceIn(30, 80),
        keepRecentTurns = keepRecentTurns.coerceIn(1, 10),
    )
}
