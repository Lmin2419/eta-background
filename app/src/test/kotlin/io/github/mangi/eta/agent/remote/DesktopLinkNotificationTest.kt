package io.github.mangi.eta.agent.remote

import android.app.Application
import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36], application = Application::class)
class DesktopLinkNotificationTest {
    @Test
    fun computerSyncHasItsOwnQuietChannels() {
        val context = RuntimeEnvironment.getApplication()
        DesktopLinkService.ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val low = manager.getNotificationChannel(DesktopLinkService.CHANNEL_ONGOING_LOW)
        assertEquals(NotificationManager.IMPORTANCE_LOW, low.importance)
        assertNull(low.sound)
        assertFalse(low.shouldVibrate())
        val ongoing = manager.getNotificationChannel(DesktopLinkService.CHANNEL_ONGOING)
        assertNull(ongoing.sound)
        val silent = manager.getNotificationChannel(DesktopLinkService.CHANNEL_REMINDERS_SILENT)
        assertNull(silent.sound)
        assertFalse(silent.shouldVibrate())
        val loud = manager.getNotificationChannel(DesktopLinkService.CHANNEL_REMINDERS)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, loud.importance)
        assertNotNull(loud.group)
    }

    @Test
    fun notificationOpenIntentCarriesTheSession() {
        val context = RuntimeEnvironment.getApplication()
        val pending = DesktopLinkNav.openIntent(context, "s1", 3)
        val intent = org.robolectric.Shadows.shadowOf(pending).savedIntent
        assertEquals(DesktopLinkNav.ACTION_OPEN_DESKTOP, intent.action)
        assertEquals("s1", intent.getStringExtra(DesktopLinkNav.EXTRA_SESSION_ID))
        DesktopLinkNav.accept(intent)
        assertEquals("s1", DesktopLinkNav.requests.value)
        DesktopLinkNav.consume()
        assertNull(DesktopLinkNav.requests.value)
    }
}
