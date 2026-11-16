package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

/**
 * 模型预设（etax/design/09 第三期 3.1）：一个名字对应「模型 + 思考强度」，标题胶囊菜单里一键切换。
 * [modelId] 与模型选择器里的 id 相同（`AgentModelOptionUi.id`）。
 */
@Serializable
data class ModelPreset(
    val id: String,
    val name: String,
    val modelId: String,
    val effort: ReasoningEffort,
)

/** 当前模型和思考强度都对得上时，菜单里这个预设打勾。 */
fun presetMatches(preset: ModelPreset, modelId: String?, effort: ReasoningEffort?): Boolean =
    modelId != null && preset.modelId == modelId && preset.effort == effort
