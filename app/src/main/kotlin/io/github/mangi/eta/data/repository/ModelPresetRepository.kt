package io.github.mangi.eta.data.repository

import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.ModelPreset
import io.github.mangi.eta.data.model.ReasoningEffort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 模型预设：整个列表存成一条 JSON；解析失败当作空列表（09 第三期 3.1）。 */
object ModelPresetRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val serializer = ListSerializer(ModelPreset.serializer())

    fun presetsFlow(): Flow<List<ModelPreset>> = SettingsDataStore.modelPresetsRawFlow().map(::decode)

    suspend fun presets(): List<ModelPreset> = presetsFlow().first()

    suspend fun save(presets: List<ModelPreset>) = SettingsDataStore.setModelPresetsRaw(encode(presets))

    suspend fun add(name: String, modelId: String, effort: ReasoningEffort): ModelPreset {
        val preset = ModelPreset(java.util.UUID.randomUUID().toString(), name.trim(), modelId, effort)
        save(presets() + preset)
        return preset
    }

    suspend fun rename(id: String, name: String) =
        save(presets().map { if (it.id == id) it.copy(name = name.trim()) else it })

    suspend fun delete(id: String) = save(presets().filterNot { it.id == id })

    internal fun decode(raw: String?): List<ModelPreset> =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    internal fun encode(presets: List<ModelPreset>): String = json.encodeToString(serializer, presets)
}
