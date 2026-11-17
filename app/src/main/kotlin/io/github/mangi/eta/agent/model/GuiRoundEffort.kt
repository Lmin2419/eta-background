package io.github.mangi.eta.agent.model

import io.github.mangi.eta.data.model.ReasoningEffort

/**
 * 11 号方案实验「界面操作轮用较低思考强度」：点完、等完之后看结果的那一轮通常只需判断下一步点哪，
 * 用较低档能更快出第一个字。
 *
 * 只在模型本身支持分档时降档（优先 low，其次 minimal）；不关思考、不碰只有开/关两档的模型，
 * 避免工具链中途切换思考模式被服务商拒绝。已经是低档或没开思考时原样返回。
 */
internal object GuiRoundEffort {
    fun lower(config: AgentModelClient.ModelConfig): AgentModelClient.ModelConfig {
        val current = config.effectiveReasoningEffort
        if (!current.enablesReasoning) return config
        val selectable = config.reasoningCapabilities?.selectableEfforts ?: return config
        val target = listOf(ReasoningEffort.LOW, ReasoningEffort.MINIMAL).firstOrNull { it in selectable } ?: return config
        if (current != ReasoningEffort.DEFAULT && current.rank <= target.rank) return config
        return config.copy(reasoningEffort = target, thinkingEnabled = true)
    }
}
