package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

/**
 * Subagent 模型与思考配置。
 *
 * [modelId] 为空表示跟随主 Agent 当前生效的模型；指定具体 Model.id 且对应服务商与模型仍启用时，
 * Subagent 优先使用该模型运行。
 */
@Serializable
internal data class SubagentSettings(
    val modelId: String? = null,
    val reasoningEffort: String? = null,
) {
    val resolvedReasoningEffort: ReasoningEffort?
        get() = ReasoningEffort.fromWireValue(reasoningEffort)
}
