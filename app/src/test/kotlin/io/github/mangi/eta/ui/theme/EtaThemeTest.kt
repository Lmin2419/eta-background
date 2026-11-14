package io.github.mangi.eta.ui.theme

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.ui.components.EtaPreferenceTheme
import io.github.mangi.eta.ui.components.StatusSuccess
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
import java.time.Duration
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class EtaThemeTest {
    @Test fun neutralPaletteAndBrandMatchDesignInBothModes() {
        assertEquals(Color(0xFFFBFAF8), lightEtaColors().canvas)
        assertEquals(Color(0xFFD97757), lightEtaColors().brand)
        assertEquals(Color(0xFFB4532F), lightEtaColors().accent)
        assertEquals(Color(0xFF161616), darkEtaColors().canvas)
        assertEquals(Color(0xFFE38D6F), darkEtaColors().accent)
        listOf(lightEtaColors(), darkEtaColors()).forEach { palette ->
            val bridged = palette.toMiuixColors(lightColorScheme())
            assertEquals(palette.canvas, bridged.background)
            assertEquals(palette.surface, bridged.surfaceContainer)
            assertEquals(palette.accent, bridged.primary)
            assertEquals(palette.accentSoft, bridged.tertiaryContainer)
            assertEquals(palette.textPrimary, bridged.onSurface)
            assertEquals(palette.danger, bridged.error)
        }
    }

    @Test fun monetAndCustomAccentsReplaceOnlyTheAccentFamily() {
        val generated = lightColorScheme(primary = Color.Magenta, onPrimary = Color.Black,
            primaryContainer = Color.Yellow, onPrimaryContainer = Color.Blue)
        listOf(false, true).forEach { dark ->
            listOf(false, true).forEach { monet ->
                AppearanceAccentColor.entries.forEach { accent ->
                    val appearance = AppearanceSettings(monetEnabled = monet, accentColor = accent)
                    val base = if (dark) darkEtaColors() else lightEtaColors()
                    val resolved = resolveEtaColors(appearance, dark, generated)
                    val expected = if (monet || accent != AppearanceAccentColor.SYSTEM)
                        base.withAccent(Color.Magenta, Color.Yellow, Color.Blue, Color.Black) else base
                    assertEquals(expected, resolved)
                    assertEquals(resolved.brand, resolved.running)
                    assertEquals(resolved.accentSoft, resolved.runningSoft)
                }
            }
        }
    }

    @Test fun pureBlackOptionKeepsItsExistingScopeAndPreservesRaisedSurfaces() {
        listOf(false, true).forEach { dark ->
            listOf(false, true).forEach { monet ->
                val resolved = resolveEtaColors(AppearanceSettings(monetEnabled = monet, pureBlackEnabled = true), dark, lightColorScheme())
                val base = if (dark) darkEtaColors() else lightEtaColors()
                assertEquals(if (dark && monet) Color.Black else base.canvas, resolved.canvas)
                assertEquals(if (dark && monet) Color.Black else base.surface, resolved.surface)
                assertEquals(base.surfaceRaised, resolved.surfaceRaised)
            }
        }
    }

    @Test fun liveThemeSwitchesReachMiuixSettingsAndStatusConsumers() = withTheme { host ->
        listOf(AppearanceThemeMode.LIGHT, AppearanceThemeMode.DARK).forEach { mode ->
            listOf(false, true).forEach { monet ->
                listOf(AppearanceAccentColor.SYSTEM, AppearanceAccentColor.GREEN).forEach { accent ->
                    host.settings.value = AppearanceSettings(themeMode = mode, monetEnabled = monet, accentColor = accent)
                    host.advance()
                    val palette = host.palette!!
                    assertEquals(mode == AppearanceThemeMode.DARK, palette.isDark)
                    assertEquals(palette.canvas, host.miuix!!.background)
                    assertEquals(palette.accent, host.miuix!!.primary)
                    assertEquals(palette.successText, host.success)
                    val default = if (palette.isDark) darkEtaColors() else lightEtaColors()
                    if (!monet && accent == AppearanceAccentColor.SYSTEM) assertEquals(default, palette)
                    if (accent == AppearanceAccentColor.GREEN) assertNotEquals(default.accent, palette.accent)
                }
            }
        }
        val unscaled = host.density
        host.settings.value = host.settings.value.copy(interfaceScale = 0.8f)
        host.advance()
        assertEquals(unscaled * 0.8f, host.density, 0.001f)
    }

    @Test
    @Config(qualifiers = "night")
    fun systemThemeFollowsNightMode() = withTheme { host ->
        assertEquals(AppearanceThemeMode.SYSTEM, host.settings.value.themeMode)
        assertEquals(darkEtaColors(), host.palette)
        assertEquals(darkEtaColors().canvas, host.miuix!!.background)
    }

    @Test fun reducedMotionUpdatesWhileComposedAndCapsAllDurations() = withTheme { host ->
        val resolver = host.activity.contentResolver
        val previous = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
            host.advance()
            assertTrue(host.motion!!.reduceMotion)
            assertTrue(listOf(host.motion!!.micro, host.motion!!.short, host.motion!!.medium, host.motion!!.long).all { it <= 120 })
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
            host.advance()
            assertFalse(host.motion!!.reduceMotion)
            assertEquals(380, host.motion!!.long)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous)
        }
    }

    private fun withTheme(block: (ThemeHost) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val host = ThemeHost(controller.get())
        try { host.advance(); block(host) }
        finally { host.close(); controller.pause().stop().destroy() }
    }

    private class ThemeHost(val activity: ComponentActivity) {
        val settings = mutableStateOf(AppearanceSettings())
        var palette: EtaColors? = null
        var miuix: Colors? = null
        var success: Color? = null
        var motion: EtaMotion? = null
        var density = 0f
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
        private val recomposer = Recomposer(scope.coroutineContext)
        init {
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            val view = ComposeView(activity)
            view.setParentCompositionContext(recomposer)
            view.setContent {
                AgentAppTheme(settings.value, applyInterfaceScale = true) {
                    EtaPreferenceTheme {
                        val palette = EtaTheme.colors
                        val miuix = MiuixTheme.colorScheme
                        val success = StatusSuccess
                        val motion = EtaTheme.motion
                        val density = LocalDensity.current.density
                        SideEffect {
                            this.palette = palette; this.miuix = miuix; this.success = success
                            this.motion = motion; this.density = density
                        }
                    }
                }
            }
            activity.setContentView(view)
        }
        fun advance() = repeat(8) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            clock.sendFrame(SystemClock.uptimeMillis() * 1_000_000L)
        }
        fun close() { recomposer.cancel(); scope.cancel() }
    }
}
