package io.github.mangi.eta.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaMotion
import io.github.mangi.eta.ui.theme.EtaTheme
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusRingTest {
    @Test fun allStatusesExposeAccessibleLabelsAndRenderInBothThemes() = withPanel { panel ->
        listOf(false, true).forEach { dark ->
            panel.dark.value = dark
            panel.reduced.value = true
            panel.advance()
            val labels = descriptions(panel.root().semanticsOwner.unmergedRootSemanticsNode)
            listOf("Running", "Needs your attention", "Paused", "Completed", "Not completed", "Queued")
                .forEach { assertTrue("Missing status: $it", it in labels) }
            panel.capture(if (dark) "dark" else "light")
        }
    }

    @Test fun runningFramesOnlyInvalidateDrawingAndStopWhenMotionIsReduced() = withPanel { panel ->
        panel.reduced.value = false
        panel.advance()
        assertTrue("Visible running ring should request frames", panel.hasFrameAwaiters())
        val recompositions = panel.recomposer.changeCount
        panel.advance()
        assertEquals("Running frames must not recompose", recompositions, panel.recomposer.changeCount)
        panel.reduced.value = true
        panel.advance()
        assertFalse("Reduced motion must dispose infinite animations", panel.hasFrameAwaiters())
        val staticCount = panel.recomposer.changeCount
        panel.advance()
        assertEquals(staticCount, panel.recomposer.changeCount)
    }

    @Test fun hiddenRetainedRingHasNoRunningAnimation() = withPanel { panel ->
        panel.visible.value = false
        panel.advance()
        assertFalse("Hidden ring must dispose infinite animations", panel.hasFrameAwaiters())
        val changes = panel.recomposer.changeCount
        panel.advance()
        assertEquals(changes, panel.recomposer.changeCount)
    }

    private fun descriptions(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + node.children.flatMap(::descriptions)

    private fun withPanel(block: (Panel) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Panel(controller.get())
        try { panel.advance(); block(panel) }
        finally { panel.close(); controller.pause().stop().destroy() }
    }

    private class Panel(activity: ComponentActivity) {
        val dark = mutableStateOf(false)
        val reduced = mutableStateOf(false)
        val visible = mutableStateOf(true)
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        private val view = ComposeView(activity)
        init {
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            view.setParentCompositionContext(recomposer)
            view.setContent {
                AgentAppTheme(AppearanceSettings(themeMode = if (dark.value) AppearanceThemeMode.DARK else AppearanceThemeMode.LIGHT), false) {
                    EtaTheme(colors = EtaTheme.colors, motion = EtaMotion(reduced.value)) {
                        CompositionLocalProvider(LocalSquircleEnabled provides false) {
                            Column(Modifier.fillMaxSize().background(EtaTheme.colors.canvas).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Eta · 静流", style = EtaTheme.type.title1, color = EtaTheme.colors.textPrimary)
                                Text("Phase 1 · theme & status", style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
                                EtaCard {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        TaskVisualStatus.entries.forEach { status ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                StatusRing(status, size = 20.dp, visible = visible.value)
                                                Text(status.label(), style = EtaTheme.type.rowTitle, color = status.color())
                                            }
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf(12.dp, 16.dp, 20.dp, 28.dp).forEach {
                                        StatusRing(TaskVisualStatus.Running, size = it, visible = visible.value)
                                    }
                                }
                                Text("Brand / raised / sunken", style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf(EtaTheme.colors.accent, EtaTheme.colors.surfaceRaised, EtaTheme.colors.surfaceSunken).forEach {
                                        androidx.compose.foundation.layout.Box(Modifier.size(64.dp).background(it, EtaTheme.shapes.radiusMd))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            activity.setContentView(view)
        }
        fun root(): ViewRootForTest = findRoot(view) ?: error("No Compose root")
        private fun findRoot(view: View): ViewRootForTest? {
            if (view is ViewRootForTest) return view
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) findRoot(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        fun advance() = repeat(30) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            clock.sendFrame(SystemClock.uptimeMillis() * 1_000_000L)
            root().measureAndLayoutForTest()
        }
        fun capture(name: String) {
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val target = File("build/reports/eta-theme/$name.png")
            requireNotNull(target.parentFile).mkdirs()
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun hasFrameAwaiters() = clock.hasAwaiters
        fun close() { recomposer.cancel(); scope.cancel() }
    }
}
