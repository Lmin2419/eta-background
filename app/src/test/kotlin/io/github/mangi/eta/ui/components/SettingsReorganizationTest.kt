package io.github.mangi.eta.ui.components

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.R
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.AssistantSettingsScreen
import io.github.mangi.eta.ui.SettingsScreen
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [36],
    application = Application::class,
    qualifiers = "w411dp-h896dp-xhdpi",
    shadows = [SettingsTestLocaleConfig::class],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsReorganizationTest {

    @Test
    fun allFortyTwoLegacyItemsReachableByFingerScrollAcrossMainAndMore() {
        val context = RuntimeEnvironment.getApplication()
        initEnvironment(context)

        val titles = expectedTitles(context)
        val expectedMain = titles.filterIndexed { index, _ -> index !in 21..28 }
        val expectedMore = titles.filterIndexed { index, _ -> index in 21..28 }
        val groupTitles = mainGroupTitles(context)

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val mainPanel = Stage3TestPanel(activity.get()) {
            TestProviders {
                EtaAppOverlayHost {
                    SettingsScreen(activity.get(), onNavigate = {}, onBack = {})
                }
            }
        }
        try {
            mainPanel.advance(40)
            val seen = mainPanel.collectScrollingTexts(maxSteps = 28)

            assertEquals("must expose exactly eight main groups", 8, groupTitles.size)
            groupTitles.forEach { group ->
                assertTrue("missing main group: $group. seen=$seen", group in seen)
            }
            expectedMain.forEach { title ->
                assertTrue("missing main legacy item: $title. seen=$seen", title in seen)
            }
        } finally {
            mainPanel.close()
        }

        val morePanel = Stage3TestPanel(activity.get()) {
            TestProviders {
                EtaAppOverlayHost {
                    AssistantSettingsScreen(activity.get(), onNavigate = {}, onBack = {})
                }
            }
        }
        try {
            morePanel.advance(40)
            val seen = morePanel.collectScrollingTexts(maxSteps = 12)
            expectedMore.forEach { title ->
                assertTrue("missing migrated more-page item: $title. seen=$seen", title in seen)
            }
        } finally {
            morePanel.close()
        }

        activity.pause().stop().destroy()
    }

    @Test
    fun mainRoutesUseRealTapsAndScrollReturnsToTopReadinessArea() {
        val context = RuntimeEnvironment.getApplication()
        initEnvironment(context)

        var navigated: AppRoute? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(activity.get()) {
            TestProviders {
                EtaAppOverlayHost {
                    SettingsScreen(
                        context = activity.get(),
                        onNavigate = { navigated = it },
                        onBack = {},
                    )
                }
            }
        }
        try {
            panel.advance(40)

            val moreText = context.getString(R.string.eta_more_assistant_settings)
            assertTrue("more entry not reachable", panel.scrollDownUntil({ moreText in panel.visibleTexts() }))
            assertTrue("more entry not tappable", panel.tapText(moreText))
            assertEquals(AppRoute.AssistantSettings, navigated)

            navigated = null
            val permissionsText = context.getString(R.string.ui_permission_health_3048bb)
            assertTrue(
                "permissions entry not reachable",
                panel.scrollDownUntil({ permissionsText in panel.visibleTexts() }),
            )
            assertTrue("permissions entry not tappable", panel.tapText(permissionsText))
            assertEquals(AppRoute.Permissions, navigated)

            val sourceCode = context.getString(R.string.ui_about_github_star_title)
            assertTrue(
                "source-code row not reachable at bottom",
                panel.scrollDownUntil({ sourceCode in panel.visibleTexts() }, maxSteps = 36),
            )

            // 阶段 10：就绪卡标题改为一句状态（检查中 / 全部就绪 / 还差 N 项）。
            val readinessLines = setOf(context.getString(R.string.eta_ready_checking_line), context.getString(R.string.eta_ready_all_line)) +
                (0..5).map { context.getString(R.string.eta_ready_pending_line, it) }
            assertTrue(
                "top readiness area not restored after dragging back",
                panel.scrollUpUntil({ panel.visibleTexts().any { it in readinessLines } }, maxSteps = 40),
            )
        } finally {
            panel.close()
            activity.pause().stop().destroy()
        }
    }

    @Test
    fun assistantMorePageShowsMigrationItemsAndEmptyHintWhenCold() {
        val context = RuntimeEnvironment.getApplication()
        initEnvironment(context)
        setHistory(context, false)

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var coldRoute: AppRoute? = null

        val coldPanel = Stage3TestPanel(activity.get()) {
            TestProviders {
                EtaAppOverlayHost {
                    AssistantSettingsScreen(activity.get(), onNavigate = { coldRoute = it }, onBack = {})
                }
            }
        }
        try {
            coldPanel.advance(40)
            val emptyHint = context.getString(R.string.eta_assistant_details_unavailable)
            assertTrue(
                "cold more page must show the empty hint. seen=${coldPanel.visibleTexts()}",
                emptyHint in coldPanel.visibleTexts(),
            )

            val enhance = context.getString(R.string.capability_enhancements)
            expectedTitles(context).slice(21..28).forEach {
                assertTrue("unexpected cold enhancement: $it", it !in coldPanel.visibleTexts())
            }
            assertTrue(
                "cold more page must keep the system-enhance entry reachable",
                coldPanel.scrollDownUntil({ enhance in coldPanel.visibleTexts() }, maxSteps = 16),
            )
            assertTrue(coldPanel.tapText(enhance))
            assertEquals(AppRoute.SystemEnhance, coldRoute)
        } finally {
            coldPanel.close()
        }

        setHistory(context, true)
        val warmPanel = Stage3TestPanel(activity.get()) {
            TestProviders {
                EtaAppOverlayHost {
                    AssistantSettingsScreen(activity.get(), onNavigate = {}, onBack = {})
                }
            }
        }
        try {
            warmPanel.advance(40)
            val vendorTitle = context.getString(R.string.ui_enable_vendor_assistant_custom_models_c8e465)
            val titleNode = warmPanel.nodes().first { vendorTitle in it.textValues() }
            assertTrue("disconnected framework options must remain disabled",
                generateSequence(titleNode) { it.parent }.any { it.config.contains(SemanticsProperties.Disabled) })
            val seen = warmPanel.collectScrollingTexts(maxSteps = 14)
            val expectedMore = expectedTitles(context).filterIndexed { index, _ -> index in 21..28 }
            expectedMore.forEach { title ->
                assertTrue("warm more page missing: $title. seen=$seen", title in seen)
            }
        } finally {
            warmPanel.close()
            activity.pause().stop().destroy()
        }
    }

    @Test
    fun completionAlertsAndLocalThinkingKeepTheirOriginalPreferences() {
        val context = RuntimeEnvironment.getApplication()
        initEnvironment(context)
        val before = kotlinx.coroutines.runBlocking { AppearanceSettingsRepository.settings() }
        val prefs = requireNotNull(Prefs.localAgentPreferences())
        val thinkingBefore = prefs.getBoolean(Prefs.Keys.AGENT_THINKING_ENABLED, true)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(activity.get()) {
            TestProviders { EtaAppOverlayHost { SettingsScreen(activity.get(), {}, {}) } }
        }
        try {
            panel.advance(40)
            val thinking = context.getString(R.string.ui_deep_thinking_enabled_by_default_c032d6)
            assertTrue(panel.tapText(thinking))
            assertEquals(!thinkingBefore, prefs.getBoolean(Prefs.Keys.AGENT_THINKING_ENABLED, thinkingBefore))
            val completion = context.getString(R.string.appearance_task_completion_alerts)
            assertTrue(panel.scrollDownUntil({ completion in panel.visibleTexts() }))
            val expected = before.copy(taskCompletionAlertsEnabled = !before.taskCompletionAlertsEnabled)
            val observer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val written = observer.async { AppearanceSettingsRepository.settingsFlow().first { it == expected } }
                assertTrue(panel.tapText(completion))
                // Keep the UI write coroutine and its frame/looper callbacks moving while DataStore persists.
                // Elapsed time never makes this pass; the deadline only diagnoses a missing write.
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
                while (!written.isCompleted && System.nanoTime() < deadline) {
                    panel.advance()
                    Thread.sleep(1)
                }
                assertTrue("The tapped completion preference was not persisted: expected=$expected", written.isCompleted)
                val after = kotlinx.coroutines.runBlocking { written.await() }
                assertEquals(expected, after)
                assertEquals(expected, kotlinx.coroutines.runBlocking { AppearanceSettingsRepository.settings() })
            } finally {
                observer.cancel()
            }
        } finally {
            panel.close()
            activity.pause().stop().destroy()
        }
    }

    @Test
    fun stage5ScreenshotsLightTopBottomAssistantAndDark200() {
        val context = RuntimeEnvironment.getApplication()
        initEnvironment(context)

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()

        val lightPanel = Stage3TestPanel(activity.get()) {
            TestProviders {
                EtaAppOverlayHost {
                    SettingsScreen(activity.get(), onNavigate = {}, onBack = {})
                }
            }
        }
        try {
            lightPanel.advance(40)
            lightPanel.capture("settings-stage5-after-top")

            val sourceCode = context.getString(R.string.ui_about_github_star_title)
            assertTrue(
                "light page bottom not reachable",
                lightPanel.scrollDownUntil({ sourceCode in lightPanel.visibleTexts() }, maxSteps = 36),
            )
            lightPanel.capture("settings-stage5-after-bottom")
        } finally {
            lightPanel.close()
        }

        val assistantPanel = Stage3TestPanel(activity.get()) {
            TestProviders {
                EtaAppOverlayHost {
                    AssistantSettingsScreen(activity.get(), onNavigate = {}, onBack = {})
                }
            }
        }
        try {
            assistantPanel.advance(40)
            assistantPanel.capture("settings-stage5-assistant")
        } finally {
            assistantPanel.close()
        }

        val darkPanel = Stage3TestPanel(activity.get(), dark = true, fontScale = 2f) {
            TestProviders {
                EtaAppOverlayHost {
                    SettingsScreen(activity.get(), onNavigate = {}, onBack = {})
                }
            }
        }
        try {
            darkPanel.advance(40)

            val sourceCode = context.getString(R.string.ui_about_github_star_title)
            assertTrue(
                "dark 200% long page bottom not reachable",
                darkPanel.scrollDownUntil({ sourceCode in darkPanel.visibleTexts() }, maxSteps = 48),
            )
            darkPanel.capture("settings-stage5-dark-200")
        } finally {
            darkPanel.close()
            activity.pause().stop().destroy()
        }
    }

    @Composable
    private fun TestProviders(content: @Composable () -> Unit) {
        val appearance by AppearanceSettingsRepository.settingsFlow().collectAsState(initial = io.github.mangi.eta.data.model.AppearanceSettings())
        val readiness = remember { EngineReadinessProbe { true } }
        CompositionLocalProvider(
            LocalAppearanceSettings provides appearance,
            LocalEngineReadinessProbe provides readiness,
        ) {
            content()
        }
    }

    private fun initEnvironment(context: Application) {
        SettingsDataStore.init(context)
        Prefs.initLocal(context)
        ProviderRepository.init(context)
        setHistory(context, true)
    }

    private fun setHistory(context: Context, value: Boolean) {
        context.getSharedPreferences("eta_enhancement_ui_history", 0)
            .edit()
            .putBoolean("has_connected", value)
            .putBoolean("has_used_systemizer", value)
            .commit()
    }

    private fun mainGroupTitles(context: Context): List<String> = listOf(
        context.getString(R.string.eta_settings_models),
        context.getString(R.string.eta_settings_capabilities),
        context.getString(R.string.eta_settings_background),
        context.getString(R.string.eta_settings_assistant),
        context.getString(R.string.eta_settings_permissions),
        context.getString(R.string.eta_settings_personalization),
        context.getString(R.string.eta_settings_data),
        context.getString(R.string.ui_about_bed172),
    )

    /** Hard-coded contract for the 42 legacy settings rows, in the old visual order. */
    private fun expectedTitles(context: Context): List<String> = listOf(
        context.getString(R.string.ui_model_provider_e8c7f5), // 0
        context.getString(R.string.ui_deep_thinking_enabled_by_default_c032d6), // 1
        context.getString(R.string.settings_auto_compaction), // 2
        context.getString(R.string.ui_memory_b55ff5), // 3
        "Subagent", // 4
        context.getString(R.string.route_skills), // 5
        context.getString(R.string.route_mcp_servers), // 6
        context.getString(R.string.eta_characters), // 7
        context.getString(R.string.settings_tools_list), // 8
        context.getString(R.string.ui_enable_web_browsing_tools_8b6b03), // 9
        context.getString(R.string.web_tools_settings_title), // 10
        context.getString(R.string.ui_enable_device_direct_tools_e2d595), // 11
        context.getString(R.string.ui_allow_reading_of_sensitive_device_information_feaec0), // 12
        context.getString(R.string.ui_allow_sensitive_device_operation_3d42ea), // 13
        context.getString(R.string.ui_enable_terminal_file_tools_18bb43), // 14
        context.getString(R.string.ui_linux_tool_environment_314d22), // 15
        context.getString(R.string.capability_enhancements), // 16
        context.getString(R.string.ui_eta_system_assistant_003e9b), // 17
        context.getString(R.string.native_assistant_title), // 18
        context.getString(R.string.ui_long_press_the_power_button_1958d0), // 19
        context.getString(R.string.ui_automatically_set_default_assistant_f86963), // 20
        context.getString(R.string.ui_enable_vendor_assistant_custom_models_c8e465), // 21
        context.getString(R.string.ui_only_take_over_with_agent_prefix_d17556), // 22
        context.getString(R.string.ui_maintain_hey_google_detection_after_screen_rest_9d6877), // 23
        context.getString(R.string.ui_lock_screen_evokes_automatic_voice_input_1cde18), // 24
        context.getString(R.string.ui_bright_screen_evokes_automatic_voice_input_4358fe), // 25
        context.getString(R.string.ui_convert_google_apps_to_system_apps_0f6d89), // 26
        context.getString(R.string.ui_long_press_on_the_gesture_bar_triggers_a_circle_to_s_b80117), // 27
        context.getString(R.string.ui_long_press_with_two_fingers_to_trigger_a_circle_sear_ab597a), // 28
        context.getString(R.string.appearance_title), // 29
        context.getString(R.string.speech_settings_title), // 30
        context.getString(R.string.settings_language), // 31
        context.getString(R.string.data_backup_title), // 32
        context.getString(R.string.ui_floating_window_permissions_076b77), // 33
        context.getString(R.string.ui_accessibility_enhancement_tools_8fd257), // 34
        context.getString(R.string.ui_enforce_accessibility_55e838), // 35
        context.getString(R.string.background_notifications_title), // 36
        context.getString(R.string.background_diagnostics_title), // 37
        context.getString(R.string.ui_about_version_title), // 38
        context.getString(R.string.ui_about_update_title), // 39
        context.getString(R.string.ui_about_feedback_title), // 40
        context.getString(R.string.ui_about_github_star_title), // 41
    )
}

private fun SemanticsNode.isVisibleInViewport(): Boolean {
    val bounds = boundsInRoot
    return bounds.width > 0f && bounds.height > 0f && bounds.center.y in 150f..1700f
}

private fun SemanticsNode.textValues(): List<String> =
    config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

private fun Stage3TestPanel.visibleTexts(): Set<String> =
    nodes()
        .filter { it.isVisibleInViewport() }
        .flatMap { it.textValues() }
        .toSet()

private fun Stage3TestPanel.collectScrollingTexts(
    maxSteps: Int,
    settleFrames: Int = 18,
): Set<String> {
    val seen = linkedSetOf<String>()
    fun snapshot() {
        seen += visibleTexts()
    }
    snapshot()
    var unchanged = 0
    repeat(maxSteps) {
        val before = visibleTexts()
        dragFinger(fromX = 411f, fromY = 1300f, toY = 700f, settleFrames = settleFrames)
        snapshot()
        unchanged = if (before == visibleTexts()) unchanged + 1 else 0
        if (unchanged >= 3) return seen
    }
    return seen
}

private fun Stage3TestPanel.scrollDownUntil(
    predicate: () -> Boolean,
    maxSteps: Int = 40,
): Boolean {
    if (predicate()) return true
    repeat(maxSteps) {
        dragFinger(fromX = 411f, fromY = 1300f, toY = 700f, settleFrames = 60)
        if (predicate()) return true
    }
    return predicate()
}

private fun Stage3TestPanel.scrollUpUntil(
    predicate: () -> Boolean,
    maxSteps: Int = 40,
): Boolean {
    if (predicate()) return true
    repeat(maxSteps) {
        dragFinger(fromX = 411f, fromY = 700f, toY = 1300f, settleFrames = 60)
        if (predicate()) return true
    }
    return predicate()
}

private fun Stage3TestPanel.tapText(text: String): Boolean {
    val node = nodes().firstOrNull { node ->
        node.isVisibleInViewport() && text in node.textValues()
    } ?: return false
    val center = node.boundsInRoot.center
    tapFinger(center.x, center.y)
    return true
}

/** Robolectric does not load AGP's generated per-app locale configuration. */
@org.robolectric.annotation.Implements(android.app.LocaleConfig::class)
class SettingsTestLocaleConfig {
    @org.robolectric.annotation.Implementation
    fun getSupportedLocales(): android.os.LocaleList =
        android.os.LocaleList.forLanguageTags("en,zh-Hans,zh-Hant")
}
