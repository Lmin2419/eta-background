package io.github.mangi.eta.data.repository

import android.app.Application
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.ModelPreset
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.model.presetMatches
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 09 第三期 3.1：模型预设的存取、坏数据当作空列表、打勾判断。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ModelPresetRepositoryTest {
    @Before fun setUp() {
        SettingsDataStore.init(RuntimeEnvironment.getApplication())
        runBlocking { ModelPresetRepository.save(emptyList()) }
    }

    @Test fun addRenameAndDeleteKeepTheListInOrder() = runBlocking {
        val daily = ModelPresetRepository.add(" 日常 ", "p1:m-small", ReasoningEffort.OFF)
        val deep = ModelPresetRepository.add("深度", "p2:m-large", ReasoningEffort.HIGH)
        assertEquals(listOf("日常", "深度"), ModelPresetRepository.presets().map { it.name })
        ModelPresetRepository.rename(daily.id, "快速 ")
        assertEquals(listOf("快速", "深度"), ModelPresetRepository.presets().map { it.name })
        ModelPresetRepository.delete(daily.id)
        assertEquals(listOf(deep), ModelPresetRepository.presets())
    }

    @Test fun brokenJsonReadsAsEmpty() = runBlocking {
        SettingsDataStore.setModelPresetsRaw("{not json")
        assertEquals(emptyList<ModelPreset>(), ModelPresetRepository.presets())
        assertEquals(emptyList<ModelPreset>(), ModelPresetRepository.decode(null))
        assertEquals(emptyList<ModelPreset>(), ModelPresetRepository.decode(""))
        // 坏数据之后照常能存。
        ModelPresetRepository.add("日常", "p1:m", ReasoningEffort.MEDIUM)
        assertEquals(1, ModelPresetRepository.presets().size)
    }

    @Test fun encodeDecodeRoundTrips() {
        val presets = listOf(ModelPreset("a", "日常", "p1:m", ReasoningEffort.LOW), ModelPreset("b", "深度", "p2:m", ReasoningEffort.XHIGH))
        assertEquals(presets, ModelPresetRepository.decode(ModelPresetRepository.encode(presets)))
    }

    @Test fun presetMatchesNeedsBothModelAndEffort() {
        val preset = ModelPreset("a", "深度", "p2:m", ReasoningEffort.HIGH)
        assertTrue(presetMatches(preset, "p2:m", ReasoningEffort.HIGH))
        assertFalse(presetMatches(preset, "p2:m", ReasoningEffort.LOW))
        assertFalse(presetMatches(preset, "p1:m", ReasoningEffort.HIGH))
        assertFalse(presetMatches(preset, null, ReasoningEffort.HIGH))
    }
}
