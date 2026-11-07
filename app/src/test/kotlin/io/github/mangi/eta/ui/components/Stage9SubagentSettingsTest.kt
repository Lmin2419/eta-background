package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.toEntity
import io.github.mangi.eta.data.db.toModelEntities
import io.github.mangi.eta.data.model.SubagentSettings
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.screens.subagent.SubagentSettingsScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.util.concurrent.TimeUnit
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
    @get:org.junit.Rule val settingsStore = io.github.mangi.eta.data.repository.Stage9SettingsStoreRule()
    @Test fun unavailableDefaultModelIsExplainedInsteadOfSilentlyFollowingPrimary() {
        val context = RuntimeEnvironment.getApplication()
        SettingsDataStore.init(context); Prefs.initLocal(context); ProviderRepository.init(context)
        persist { RuntimeConfigRepository.setSubagentSettings(SubagentSettings(modelId = "removed-model")) }
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
    @Test fun defaultAndPerRoleSettingsAreVisibleInTheExistingDetailSheet() {
        val context = RuntimeEnvironment.getApplication()
        SettingsDataStore.init(context); Prefs.initLocal(context); ProviderRepository.init(context)
        persist { RuntimeConfigRepository.setSubagentSettings(SubagentSettings()) }
        for (dark in listOf(false, true)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val panel = Stage3TestPanel(owner.get(), dark) { SubagentSettingsScreen({}) }
            fun text(value: String) = panel.nodes().firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == value } }
            try {
                panel.advance(50)
                panel.capture("stage9b-roles-${if (dark) "dark" else "light"}")
                assertNotNull("Global model must be labelled Default", text("默认模型"))
                assertTrue(panel.nodes().any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text.contains("模型：跟随默认") } })
                panel.click(requireNotNull(text("explore"))); panel.advance(30)
                assertNotNull(text("模型")); assertNotNull(text("思考强度"))
                panel.capture("stage9b-role-detail-${if (dark) "dark" else "light"}")
                panel.click(requireNotNull(text("模型"))); panel.advance(30)
                assertNotNull(text("跟随默认模型"))
                panel.capture("stage9b-role-picker-${if (dark) "dark" else "light"}")
            } finally { panel.close(); owner.pause().stop().destroy() }
        }
    }


    @Test fun rolePickerPersistsOnlyItsRoleAndWarnsWhenAChosenModelDisappears() {
        val context = RuntimeEnvironment.getApplication()
        SettingsDataStore.init(context); Prefs.initLocal(context); ProviderRepository.init(context)
        val models = listOf("A", "B", "C").map { name -> io.github.mangi.eta.data.model.Model(id = "stage9-$name", modelId = name, displayName = "Model $name") }
        persist {
            // Seed the picker input directly: provider creation/selection repair is not this UI test's subject.
            val provider = io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting(
                id = "stage9-ui", name = "Fixture", baseUrl = "https://example.invalid", apiKey = "fixture-not-a-secret", models = models)
            EtaDatabase.get(context).providerDao().replaceProvider(provider.toEntity(), provider.toModelEntities())
            RuntimeConfigRepository.setSubagentSettings(SubagentSettings(modelId = "stage9-C", profiles = mapOf(
                "explore" to io.github.mangi.eta.data.model.SubagentProfileModel(modelId = "removed"),
                "coder" to io.github.mangi.eta.data.model.SubagentProfileModel(modelId = "stage9-B"))))
        }
        val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(owner.get()) { SubagentSettingsScreen({}) }
        fun text(value: String): androidx.compose.ui.semantics.SemanticsNode {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            do {
                panel.nodes().firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == value } }?.let { return it }
                panel.advance(); Thread.sleep(5)
            } while (System.nanoTime() < deadline)
            panel.capture("stage9b-picker-missing")
            error("Missing $value: " + panel.nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text })
        }
        try {
            panel.advance(60)
            assertNotNull(text("模型已停用，暂用默认"))
            panel.capture("stage9b-role-unavailable-light")
            panel.click(text("explore")); panel.advance(30)
            assertNotNull(text("模型已停用，暂用默认"))
            panel.click(text("模型")); panel.advance(30)
            val setText = androidx.compose.ui.semantics.SemanticsActions.SetText
            panel.nodes().first { it.config.getOrNull(setText) != null }.config[setText].action!!(androidx.compose.ui.text.AnnotatedString("Model A"))
            panel.advance(30)
            panel.click(text("Model A")); panel.advance(40)
            persist(tick = { panel.advance() }) {
                kotlinx.coroutines.withTimeout(30_000) {
                    RuntimeConfigRepository.subagentSettingsFlow().first { it.profiles["explore"]?.modelId == "stage9-A" }
                }
                val saved = SettingsDataStore.subagentSettings()
                assertEquals("stage9-C", saved.modelId); assertEquals("stage9-B", saved.profiles["coder"]?.modelId)
            }
        } finally { panel.close(); owner.pause().stop().destroy() }
    }

    private fun persist(tick: () -> Unit = {}, block: suspend () -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val write = scope.async { block() }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (!write.isCompleted && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle(); tick(); Thread.sleep(1)
            }
            assertTrue("DataStore persistence did not finish", write.isCompleted)
            runBlocking { write.await() }
        } finally { scope.cancel() }
    }

}
