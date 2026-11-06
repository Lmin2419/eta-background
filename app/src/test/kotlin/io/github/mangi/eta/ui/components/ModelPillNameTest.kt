package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.compactModelName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModelPillNameTest {
    @Test fun rawModelIdsDropTheirPathAndSnapshotDate() {
        assertEquals("DeepSeek-V3.1-Terminus", compactModelName("deepseek-ai/DeepSeek-V3.1-Terminus", "deepseek-ai/DeepSeek-V3.1-Terminus"))
        assertEquals("claude-sonnet-4-5", compactModelName("claude-sonnet-4-5-20250929", "claude-sonnet-4-5-20250929"))
        assertEquals("gpt-4o", compactModelName("gpt-4o-2024-08-06", "gpt-4o-2024-08-06"))
        assertEquals("gemini-2.5-pro", compactModelName("models/gemini-2.5-pro", "models/gemini-2.5-pro"))
        assertEquals("qwen-max", compactModelName("qwen-max-latest", "qwen-max-latest"))
        // A four-digit release tag is part of the model name, not a snapshot date.
        assertEquals("kimi-k2-0905-preview", compactModelName("kimi-k2-0905-preview", "kimi-k2-0905-preview"))
    }

    @Test fun aNameTheUserChoseIsShownAsWritten() {
        assertEquals("我的长模型名称 2025-01-01", compactModelName("我的长模型名称 2025-01-01", "org/some-model-20250101"))
        assertEquals("deepseek-ai/x", compactModelName("deepseek-ai/x", "deepseek-ai/y"))
    }

    @Test fun aLongNameUsesTheTopBarWidthAndKeepsBothEnds() {
        val id = "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B"
        val model = AgentModelOptionUi("m", "p", "SiliconFlow", "openai", id, id, 128_000)
        val state = AgentModelPickerUiState(selectedModel = model)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get()) {
            HomeConversationTopBar(state, false, false, AgentContextUsageUi(null, null), false, false, null, 1,
                {}, {}, {}, {}, {}, {}, {}, {})
        }
        try {
            panel.advance(5)
            val pill = panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() == id }
            val text = pill.children.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString { it.text }
            assertEquals("DeepSeek-R1-Distill-Qwen-32B", text)
            // Previously capped at 14 characters (~120px); the pill now takes the free top-bar width.
            assertTrue("pill width ${pill.size.width}px", pill.size.width > 400)
            panel.capture("model-pill-long-name")
        } finally { panel.close(); controller.pause().stop().destroy() }
    }
}
