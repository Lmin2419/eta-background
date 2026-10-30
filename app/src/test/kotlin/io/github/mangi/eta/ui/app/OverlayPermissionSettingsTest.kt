package io.github.mangi.eta.ui.app

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 37], application = Application::class)
class OverlayPermissionSettingsTest {
    @Test fun authorizedUserCanStillOpenSettingsForTheInstalledPackage() {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(Settings.canDrawOverlays(context))
        OverlayPermissionSettings.open(context)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test fun missingPackagePageAndProtectedListFallBackToActualAppDetails() {
        val attempted = mutableListOf<Intent>()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) {
                attempted += intent
                when (attempted.size) {
                    1 -> throw ActivityNotFoundException()
                    2 -> throw SecurityException()
                }
            }
        }
        assertEquals(OverlayPermissionSettings.Destination.APP_DETAILS, OverlayPermissionSettings.launch(context))
        assertEquals(3, attempted.size)
        assertNull(attempted[1].data)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, attempted.last().action)
        assertEquals("package:${context.packageName}", attempted.last().dataString)
        assertTrue(attempted.all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })
    }

    @Test fun unavailableSettingsGiveUserFeedbackInsteadOfIgnoringTheClick() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        OverlayPermissionSettings.open(context)
        assertNotNull(ShadowToast.getTextOfLatestToast())
    }
}
