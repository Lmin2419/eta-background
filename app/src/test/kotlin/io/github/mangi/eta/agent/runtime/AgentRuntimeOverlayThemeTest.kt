package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.ui.theme.EtaColors
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AgentRuntimeOverlayThemeTest {
    @Test @Config(qualifiers = "w411dp-h896dp-notnight")
    fun lightSystemOverlaySharesBrandPalette() = verifyPalette(false)

    @Test @Config(qualifiers = "w411dp-h896dp-night")
    fun darkSystemOverlayDoesNotFallBackToLightTokens() = verifyPalette(true)

    private fun verifyPalette(dark: Boolean) {
        SettingsDataStore.init(RuntimeEnvironment.getApplication())
        val host = Robolectric.buildService(AgentRuntimeService::class.java).create()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        var colors: EtaColors? = null
        var surface: Color? = null
        val probe: @Composable () -> Unit = {
            colors = EtaTheme.colors
            surface = MiuixTheme.colorScheme.surface
            Text("胖小胖")
        }
        // Exercise the real service host without an app theme surrounding it.
        val factory = AgentRuntimeService::class.java.getDeclaredMethod("createOverlayComposeView", Function2::class.java)
            .apply { isAccessible = true }
        val view = factory.invoke(host.get(), probe) as ComposeView
        try {
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            view.setParentCompositionContext(recomposer)
            activity.get().setContentView(view)
            repeat(20) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                clock.sendFrame(SystemClock.uptimeMillis() * 1_000_000L)
            }
            assertNotNull("service content must be composed", colors)
            assertEquals("tokens must follow the system overlay mode", dark, colors!!.isDark)
            assertEquals("Miuix and Eta surfaces must share the palette", colors!!.surface, surface)
        } finally {
            view.disposeComposition()
            recomposer.cancel()
            scope.cancel()
            activity.pause().stop().destroy()
            host.destroy()
        }
    }
}
