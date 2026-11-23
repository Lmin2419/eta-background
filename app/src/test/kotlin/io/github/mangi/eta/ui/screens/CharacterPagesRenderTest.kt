package io.github.mangi.eta.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.R
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.core.AppStrings
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.data.repository.CharacterRepository
import io.github.mangi.eta.ui.app.CharacterLibraryStore
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.components.EtaAppOverlayHost
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.screens.characters.CharacterEditorScreen
import io.github.mangi.eta.ui.screens.characters.CharacterLibraryScreen
import io.github.mangi.eta.ui.screens.characters.CharacterPersonaScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 14.2b：角色页面的文字改走三语资源后，英文和简体中文都能画出来，中文和改之前一致。
 * 截图在 build/reports/eta-stage3/（character-*）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CharacterPagesRenderTest {

    @Test
    fun characterPagesRenderInEnglish() = renderAll("en")

    @Test
    @Config(qualifiers = "b+zh+Hans-w411dp-h896dp-xhdpi")
    fun characterPagesRenderInSimplifiedChinese() {
        assertEquals("世界书", RuntimeEnvironment.getApplication().getString(R.string.character_worldbook_section))
        renderAll("zh")
    }

    private fun renderAll(tag: String) {
        val context = RuntimeEnvironment.getApplication()
        AppStrings.init(context)
        SettingsDataStore.init(context)
        Prefs.initLocal(context)
        CharacterRepository.initialize(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val store = CharacterLibraryStore(context, scope)
        fun text(id: Int) = context.getString(id)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            store.loadLibrary()
            render(activity.get(), "character-library-$tag", text(R.string.character_action_new)) {
                CharacterLibraryScreen(store = store, onNavigate = {}, onBack = {})
            }
            store.loadEditor(null)
            render(
                activity.get(), "character-editor-$tag", text(R.string.character_field_description_label),
                tapAfter = text(R.string.character_editor_advanced_title),
                expectAfterTap = text(R.string.character_field_scenario),
            ) {
                CharacterEditorScreen(id = null, store = store, onBack = {}, onSaved = {})
            }
            store.loadPersona()
            render(activity.get(), "character-persona-$tag", text(R.string.character_persona_identity_label)) {
                CharacterPersonaScreen(store = store, onBack = {})
            }
        } finally {
            scope.cancel()
            activity.pause().stop().destroy()
        }
    }

    private fun render(
        activity: ComponentActivity,
        name: String,
        expect: String,
        tapAfter: String? = null,
        expectAfterTap: String? = null,
        content: @Composable () -> Unit,
    ) {
        val panel = Stage3TestPanel(activity, dark = false) {
            Providers { EtaAppOverlayHost { content() } }
        }
        try {
            panel.advance(60)
            repeat(80) {
                if (panel.texts().any { expect in it }) return@repeat
                Thread.sleep(25)
                panel.advance(4)
            }
            val texts = panel.texts()
            assertTrue("$name 缺少「$expect」：$texts", texts.any { expect in it })
            panel.capture(name)
            if (tapAfter != null) {
                val node = panel.nodes().firstOrNull { node -> node.textValues().any { it == tapAfter } && node.boundsInRoot.width > 0f }
                    ?: error("$name 找不到「$tapAfter」")
                panel.tapAt(node)
                panel.advance(60)
                if (expectAfterTap != null) {
                    assertTrue("$name 点开后缺少「$expectAfterTap」：${panel.texts()}", panel.texts().any { expectAfterTap in it })
                }
                panel.capture("$name-advanced")
            }
        } finally {
            panel.close()
        }
    }

    @Composable
    private fun Providers(content: @Composable () -> Unit) {
        val appearance by AppearanceSettingsRepository.settingsFlow()
            .collectAsState(initial = io.github.mangi.eta.data.model.AppearanceSettings())
        CompositionLocalProvider(LocalAppearanceSettings provides appearance) { content() }
    }
}

private fun SemanticsNode.textValues(): List<String> =
    config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

private fun Stage3TestPanel.texts(): List<String> = nodes().flatMap { it.textValues() }

private fun Stage3TestPanel.tapAt(node: SemanticsNode) {
    val center = node.boundsInRoot.center
    val down = android.os.SystemClock.uptimeMillis()
    touch(android.view.MotionEvent.ACTION_DOWN, center.x, center.y, down)
    advance(2)
    touch(android.view.MotionEvent.ACTION_UP, center.x, center.y, down)
}
