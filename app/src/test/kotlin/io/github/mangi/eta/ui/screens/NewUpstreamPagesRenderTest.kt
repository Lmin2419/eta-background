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
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.core.AppStrings
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.ScheduledTaskRepository
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.components.EtaAppOverlayHost
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.screens.imagegen.ImageGenGalleryScreen
import io.github.mangi.eta.ui.screens.imagegen.ImageGenSettingsScreen
import io.github.mangi.eta.ui.screens.scheduled.ScheduledTaskEditorScreen
import io.github.mangi.eta.ui.screens.scheduled.ScheduledTasksScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 胖小胖按自己的样式重写的上游页面（定时任务、AI 生图）能在浅色 / 深色下正常画出来，
 * 编辑页点「触发时间」弹出的 COUI 2.1.0 时间滚轮也能画出来。截图在 build/reports/eta-stage3/。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NewUpstreamPagesRenderTest {

    @Test
    fun scheduledAndImageGenPagesRenderInLightAndDark() {
        val context = RuntimeEnvironment.getApplication()
        AppStrings.init(context)
        SettingsDataStore.init(context)
        Prefs.initLocal(context)
        ProviderRepository.init(context)
        ScheduledTaskRepository.init(context)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            for (dark in listOf(false, true)) {
                val suffix = if (dark) "dark" else "light"
                render(activity.get(), dark, "scheduled-list-$suffix", context.getString(io.github.mangi.eta.R.string.scheduled_quick_start)) {
                    ScheduledTasksScreen(onNavigate = {}, onBack = {})
                }
                render(activity.get(), dark, "scheduled-editor-$suffix", "运行时间", tapAfter = "触发时间",
                    expectAfterTap = "触发时间") {
                    ScheduledTaskEditorScreen(taskId = null, presetId = "evening_briefing", onSaved = {}, onBack = {})
                }
                render(activity.get(), dark, "imagegen-settings-$suffix", "启用 AI 生图") {
                    ImageGenSettingsScreen(onBack = {}, onOpenGallery = {})
                }
                render(activity.get(), dark, "imagegen-gallery-$suffix", "还没有生成过图片") {
                    ImageGenGalleryScreen(onBack = {})
                }
            }
        } finally {
            activity.pause().stop().destroy()
        }
    }

    private fun render(
        activity: ComponentActivity,
        dark: Boolean,
        name: String,
        expect: String,
        tapAfter: String? = null,
        expectAfterTap: String? = null,
        content: @Composable () -> Unit,
    ) {
        val panel = Stage3TestPanel(activity, dark = dark) {
            Providers { EtaAppOverlayHost { content() } }
        }
        try {
            panel.advance(60)
            // 页面里有在后台线程读数据的（生图设置、画廊），多等几轮真实时间。
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
                    assertTrue("$name 点开后缺少「$expectAfterTap」", panel.texts().count { expectAfterTap in it } >= 1)
                }
                panel.capture("$name-picker")
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
