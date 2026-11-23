package io.github.mangi.eta.config

import android.app.Application
import android.content.Context
import io.github.mangi.eta.config.KeepScreenOnPolicy.RunFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class KeepScreenOnModeTest {
    private val chat = RunFacts(active = true, ownsGui = false, scheduled = false, usesBackgroundScreen = false)
    private val guiOwner = chat.copy(ownsGui = true)
    private val backgroundScreen = chat.copy(usesBackgroundScreen = true)
    private val scheduled = chat.copy(scheduled = true)
    private val scheduledOnMainScreen = scheduled.copy(ownsGui = true)

    @Test
    fun legacySwitchMigratesToForegroundOrOff() {
        assertEquals(KeepScreenOnMode.FOREGROUND, KeepScreenOnMode.resolve(null, null))
        assertEquals(KeepScreenOnMode.FOREGROUND, KeepScreenOnMode.resolve(null, true))
        assertEquals(KeepScreenOnMode.OFF, KeepScreenOnMode.resolve(null, false))
        assertEquals(KeepScreenOnMode.ALL, KeepScreenOnMode.resolve("all", false))
        assertEquals(KeepScreenOnMode.OFF, KeepScreenOnMode.resolve("off", true))
        assertEquals("unknown values fall back to the legacy switch", KeepScreenOnMode.OFF, KeepScreenOnMode.resolve("bogus", false))
    }

    @Test
    fun offNeverKeepsTheScreenOn() {
        assertFalse(KeepScreenOnPolicy.shouldKeepOn(KeepScreenOnMode.OFF, listOf(guiOwner, chat), etaUiVisible = true))
    }

    @Test
    fun allKeepsTheScreenOnForAnyActiveRun() {
        assertTrue(KeepScreenOnPolicy.shouldKeepOn(KeepScreenOnMode.ALL, listOf(scheduled), etaUiVisible = false))
        assertTrue(KeepScreenOnPolicy.shouldKeepOn(KeepScreenOnMode.ALL, listOf(backgroundScreen), etaUiVisible = false))
        assertFalse(KeepScreenOnPolicy.shouldKeepOn(KeepScreenOnMode.ALL, listOf(chat.copy(active = false)), etaUiVisible = true))
        assertFalse(KeepScreenOnPolicy.shouldKeepOn(KeepScreenOnMode.ALL, emptyList(), etaUiVisible = true))
    }

    @Test
    fun foregroundModeSkipsBackgroundScreenAndScheduledWorkUnlessTheUserIsWatching() {
        val mode = KeepScreenOnMode.FOREGROUND
        assertTrue(KeepScreenOnPolicy.shouldKeepOn(mode, listOf(guiOwner), etaUiVisible = false))
        assertTrue(KeepScreenOnPolicy.shouldKeepOn(mode, listOf(chat), etaUiVisible = false))
        assertFalse(KeepScreenOnPolicy.shouldKeepOn(mode, listOf(backgroundScreen), etaUiVisible = false))
        assertTrue("watching the conversation in 胖小胖", KeepScreenOnPolicy.shouldKeepOn(mode, listOf(backgroundScreen), etaUiVisible = true))
        assertFalse(KeepScreenOnPolicy.shouldKeepOn(mode, listOf(scheduled), etaUiVisible = false))
        assertFalse(KeepScreenOnPolicy.shouldKeepOn(mode, listOf(scheduled), etaUiVisible = true))
        assertTrue("a scheduled run driving the main screen needs it awake",
            KeepScreenOnPolicy.shouldKeepOn(mode, listOf(scheduledOnMainScreen), etaUiVisible = false))
        assertFalse(KeepScreenOnPolicy.shouldKeepOn(mode, listOf(guiOwner.copy(active = false), backgroundScreen), etaUiVisible = false))
    }

    @Test
    fun prefsReadLegacySwitchAndWriteBothKeys() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("keep_screen_on_mode_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(KeepScreenOnMode.FOREGROUND, Prefs.keepScreenOnMode(prefs))
        prefs.edit().putBoolean(Prefs.Keys.AGENT_KEEP_SCREEN_ON_ENABLED, false).commit()
        assertEquals(KeepScreenOnMode.OFF, Prefs.keepScreenOnMode(prefs))

        assertTrue(Prefs.setKeepScreenOnMode(KeepScreenOnMode.ALL, prefs))
        assertEquals(KeepScreenOnMode.ALL, Prefs.keepScreenOnMode(prefs))
        assertTrue(prefs.getBoolean(Prefs.Keys.AGENT_KEEP_SCREEN_ON_ENABLED, false))

        assertTrue(Prefs.setKeepScreenOnMode(KeepScreenOnMode.OFF, prefs))
        assertEquals(KeepScreenOnMode.OFF, Prefs.keepScreenOnMode(prefs))
        assertFalse(prefs.getBoolean(Prefs.Keys.AGENT_KEEP_SCREEN_ON_ENABLED, true))
        assertFalse(Prefs.setKeepScreenOnMode(KeepScreenOnMode.ALL, null))
    }
}
