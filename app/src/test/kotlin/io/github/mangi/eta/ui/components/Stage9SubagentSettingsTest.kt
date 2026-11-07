package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.SubagentSettings
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.screens.subagent.SubagentSettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Stage9SubagentSettingsTest {
    @Test fun unavailableDefaultModelIsExplainedInsteadOfSilentlyFollowingPrimary() {
        val context = RuntimeEnvironment.getApplication()
        SettingsDataStore.init(context); Prefs.initLocal(context); ProviderRepository.init(context)
        runBlocking { RuntimeConfigRepository.setSubagentSettings(SubagentSettings(modelId = "removed-model")) }
        for (dark in listOf(false, true)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val panel = Stage3TestPanel(owner.get(), dark) { SubagentSettingsScreen({}) }
            try {
                panel.advance(40)
                panel.capture("stage9a-subagent-unavailable-${if (dark) "dark" else "light"}")
                val texts = panel.nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                assertTrue("A removed model must have an explicit warning: $texts", texts.any { it.contains("模型已停用") })
            } finally { panel.close(); owner.pause().stop().destroy() }
        }
    }
}
