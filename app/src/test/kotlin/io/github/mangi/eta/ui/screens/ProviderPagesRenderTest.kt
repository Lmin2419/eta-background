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
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.ui.app.CommunityCatalogStore
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.components.EtaAppOverlayHost
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.pages.providers.CommunityCatalogScreen
import io.github.mangi.eta.ui.pages.providers.ModelProviderListScreen
import io.github.mangi.eta.ui.screens.subagent.SubagentSettingsScreen
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
 * 14.2c：服务商列表、社区目录、子代理设置的文字改走三语资源后，英文和简体中文都能画出来。
 * 截图在 build/reports/eta-stage3/（providers-*、catalog-*、subagent-*）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProviderPagesRenderTest {

    @Test
    fun providerPagesRenderInEnglish() = renderAll("en")

    @Test
    @Config(qualifiers = "b+zh+Hans-w411dp-h896dp-xhdpi")
    fun providerPagesRenderInSimplifiedChinese() {
        assertEquals("从目录添加", RuntimeEnvironment.getApplication().getString(R.string.catalog_add_from_catalog))
        renderAll("zh")
    }

    private fun renderAll(tag: String) {
        val context = RuntimeEnvironment.getApplication()
        AppStrings.init(context)
        SettingsDataStore.init(context)
        Prefs.initLocal(context)
        ProviderRepository.init(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val catalogStore = CommunityCatalogStore(context, scope)
        fun text(id: Int) = context.getString(id)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            render(activity.get(), "providers-list-$tag", text(R.string.catalog_add_from_catalog)) {
                ModelProviderListScreen(onNavigate = {}, onBack = {})
            }
            catalogStore.openCatalog()
            render(activity.get(), "catalog-$tag", text(R.string.catalog_i18n_community_title)) {
                CommunityCatalogScreen(store = catalogStore, onNavigate = {}, onBack = {})
            }
            render(activity.get(), "subagent-settings-$tag", text(R.string.subagent_settings_enable)) {
                SubagentSettingsScreen(onBack = {})
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
