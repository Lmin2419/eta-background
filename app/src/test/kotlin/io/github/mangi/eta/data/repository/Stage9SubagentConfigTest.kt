package io.github.mangi.eta.data.repository

import android.app.Application
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.subagent.SubagentProfile
import io.github.mangi.eta.agent.subagent.SubagentProfileCatalog
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class Stage9SubagentConfigTest {
    @get:org.junit.Rule val settingsStore = io.github.mangi.eta.data.repository.Stage9SettingsStoreRule()
    @get:Rule val files = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val primary = AgentModelClient.ModelConfig(baseUrl = "https://example.invalid", apiKey = "", model = "primary",
        modelDisplayName = "Primary", systemPrompt = "", terminalTools = false, browserTools = true,
        subagentTools = true, autoCompactionEnabled = false)
    @Before fun init() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        SettingsDataStore.init(context); Prefs.initLocal(context); ProviderRepository.init(context)
        ProviderRepository.addProvider(OpenAiCompatibleProviderSetting(id = "stage9", name = "Fixture",
            baseUrl = "https://example.invalid/v1", models = listOf("role", "default").map { name ->
                Model(id = name, modelId = name, displayName = name, reasoning = true,
                    reasoningCapabilities = ModelReasoningCapabilities(supportedEfforts = listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH),
                        defaultEffort = ReasoningEffort.LOW, canDisable = true))
            } + Model(id = "disabled", modelId = "disabled", displayName = "Disabled", isEnabled = false)))
        SettingsDataStore.setSubagentSettings(SubagentSettings())
    }
    private suspend fun settings(raw: String) = SettingsDataStore.setSubagentSettings(json.decodeFromString<SubagentSettings>(raw))
    // The same reproducer runs on the pre-role API: it then resolves the old global config.
    private suspend fun resolve(profile: SubagentProfile = SubagentProfile.EXPLORE): AgentModelClient.ModelConfig {
        val method = RuntimeConfigRepository::class.java.declaredMethods.firstOrNull {
            it.name == "subagentRuntimeConfig" && it.parameterCount == 3
        } ?: return RuntimeConfigRepository.subagentRuntimeConfig(primary)
        return suspendCoroutineUninterceptedOrReturn { continuation ->
            method.invoke(RuntimeConfigRepository, primary, profile, continuation)
        }
    }
    @Test fun roleWinsAndKeepsParentToolBoundaries() = runBlocking {
        settings("""{"modelId":"default","profiles":{"explore":{"modelId":"role"}}}""")
        val result = resolve()
        assertEquals("role", result.model)
        assertEquals("role", result.modelDisplayName)
        assertEquals(primary.terminalTools, result.terminalTools); assertEquals(primary.browserTools, result.browserTools)
        assertFalse(result.subagentTools); assertFalse(result.supportsSearchSuggestions); assertFalse(result.autoCompactionEnabled)
    }
    @Test fun disabledRoleFallsBackToDefaultBeforePrimary() = runBlocking {
        settings("""{"modelId":"default","profiles":{"explore":{"modelId":"disabled"}}}""")
        assertEquals("default", resolve().model)
    }
    @Test fun missingRoleUsesDefaultAndBothMissingUsePrimary() = runBlocking {
        settings("""{"modelId":"default"}"""); assertEquals("default", resolve().model)
        settings("""{"modelId":"missing","profiles":{"explore":{"modelId":"missing-too"}}}""")
        assertEquals("primary", resolve().model)
    }
    @Test fun roleEffortWinsAndNormalizesAgainstTheActualModel() = runBlocking {
        settings("""{"modelId":"default","reasoningEffort":"off","profiles":{"explore":{"modelId":"role","reasoningEffort":"max"}}}""")
        assertEquals(ReasoningEffort.HIGH, resolve().reasoningEffort)
    }
    @Test fun defaultEffortThenModelDefaultAreUsed() = runBlocking {
        settings("""{"modelId":"default","reasoningEffort":"high","profiles":{"explore":{"modelId":"role"}}}""")
        assertEquals(ReasoningEffort.HIGH, resolve().reasoningEffort)
        settings("""{"profiles":{"explore":{"modelId":"role"}}}""")
        assertEquals(RuntimeConfigRepository.buildRuntimeConfig(requireNotNull(ProviderRepository.providerById("stage9")),
            requireNotNull(ProviderRepository.providerById("stage9")).models.first()).effectiveReasoningEffort, resolve().reasoningEffort)
    }
    @Test fun unsavedTemporaryRoleIgnoresNamedOverrides() = runBlocking {
        settings("""{"modelId":"default","reasoningEffort":"off","profiles":{"temporary":{"modelId":"role","reasoningEffort":"high"}}}""")
        val temporary = SubagentProfile.EXPLORE.copy(name = "temporary", builtin = false, filePath = null)
        assertEquals("default", resolve(temporary).model); assertEquals(ReasoningEffort.OFF, resolve(temporary).reasoningEffort)
        assertEquals("role", resolve(temporary.copy(filePath = "/fixture/temporary.md")).model)
    }
    @Test fun roleSettingsSurviveDataStoreRoundTripAndOldJsonStillReads() = runBlocking {
        settings("""{"modelId":"default","profiles":{"explore":{"modelId":"role","reasoningEffort":"high"}}}""")
        val encoded = json.encodeToJsonElement(SettingsDataStore.subagentSettings()).jsonObject
        assertEquals("role", encoded["profiles"]?.jsonObject?.get("explore")?.jsonObject?.get("modelId")?.jsonPrimitive?.content)
        settings("""{"modelId":"default","reasoningEffort":"off"}""")
        assertEquals("default", SettingsDataStore.subagentSettings().modelId)
        assertEquals("off", SettingsDataStore.subagentSettings().reasoningEffort)
    }
    @Test fun deletingCustomProfileAlsoRemovesItsModelSettings() = runBlocking {
        val directory = files.newFolder()
        SubagentProfileCatalog.saveCustomProfile("reviewer", "Review", "Review files", agentsDir = directory)
        settings("""{"profiles":{"reviewer":{"modelId":"role"},"coder":{"modelId":"default"}}}""")
        assertTrue(SubagentProfileCatalog.deleteCustomProfile("reviewer", directory))
        val profiles = json.encodeToJsonElement(SettingsDataStore.subagentSettings()).jsonObject["profiles"]?.jsonObject
        assertNotNull(profiles)
        assertFalse(requireNotNull(profiles).containsKey("reviewer")); assertTrue(profiles.containsKey("coder"))
    }
    @Test fun renamingCustomProfileMovesSettingsWithoutOverwritingAnotherRole() = runBlocking {
        val directory = files.newFolder()
        SubagentProfileCatalog.saveCustomProfile("reviewer", "Review", "Review files", agentsDir = directory)
        settings("""{"profiles":{"reviewer":{"modelId":"role","reasoningEffort":"high"},"coder":{"modelId":"default"}}}""")
        SubagentProfileCatalog.saveCustomProfile("auditor", "Audit", "Audit files", agentsDir = directory, originalName = "reviewer")
        assertFalse(java.io.File(directory, "reviewer.md").exists())
        val profiles = json.encodeToJsonElement(SettingsDataStore.subagentSettings()).jsonObject.getValue("profiles").jsonObject
        assertFalse(profiles.containsKey("reviewer"))
        assertEquals("role", profiles.getValue("auditor").jsonObject.getValue("modelId").jsonPrimitive.content)
        assertEquals("high", profiles.getValue("auditor").jsonObject.getValue("reasoningEffort").jsonPrimitive.content)
        assertTrue(profiles.containsKey("coder"))
    }

    @Test fun renameCollisionDoesNotOverwriteFilesOrSettings() = runBlocking {
        val directory = files.newFolder()
        SubagentProfileCatalog.saveCustomProfile("reviewer", "Review", "Original", agentsDir = directory)
        SubagentProfileCatalog.saveCustomProfile("auditor", "Audit", "Existing", agentsDir = directory)
        settings("""{"profiles":{"reviewer":{"modelId":"role"},"auditor":{"modelId":"default"}}}""")
        val before = directory.listFiles()!!.associate { it.name to it.readText() }
        val settingsBefore = SettingsDataStore.subagentSettings()
        assertTrue(runCatching { SubagentProfileCatalog.saveCustomProfile("auditor", "Audit", "Replacement",
            agentsDir = directory, originalName = "reviewer") }.isFailure)
        assertEquals(before, directory.listFiles()!!.associate { it.name to it.readText() })
        assertEquals(settingsBefore, SettingsDataStore.subagentSettings())
    }
    @Test fun settingsWriteFailureRollsBackBothRenameAndDeleteFiles() = runBlocking {
        val directory = files.newFolder()
        SubagentProfileCatalog.saveCustomProfile("reviewer", "Review", "Original", agentsDir = directory)
        val before = java.io.File(directory, "reviewer.md").readBytes()
        @Suppress("UNCHECKED_CAST")
        val store = SettingsDataStore::class.java.getDeclaredField("dataStore").apply { isAccessible = true }
            .get(SettingsDataStore) as androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
        val key = androidx.datastore.preferences.core.stringPreferencesKey("subagent_settings_v1")
        store.updateData { it.toMutablePreferences().apply { this[key] = "{broken" } }
        try {
            assertTrue(runCatching { SubagentProfileCatalog.saveCustomProfile("auditor", "Audit", "Replacement",
                agentsDir = directory, originalName = "reviewer") }.isFailure)
            assertFalse(java.io.File(directory, "auditor.md").exists())
            assertArrayEquals(before, java.io.File(directory, "reviewer.md").readBytes())
            assertTrue(runCatching { SubagentProfileCatalog.deleteCustomProfile("reviewer", directory) }.isFailure)
            assertArrayEquals(before, java.io.File(directory, "reviewer.md").readBytes())
        } finally { SettingsDataStore.setSubagentSettings(SubagentSettings()) }
    }

}
