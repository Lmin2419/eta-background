package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.content.pm.ServiceInfo
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34], application = Application::class)
class AgentExecutionServiceTest {
    @Test
    fun foregroundExecutionUsesOnlyServiceTypesSupportedByThePlatform() {
        val controller = Robolectric.buildService(AgentExecutionService::class.java).create()
        try {
            val service = controller.get()
            assertNotNull(shadowOf(service).lastForegroundNotification)
            assertEquals(
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE,
                service.foregroundServiceType,
            )
        } finally {
            controller.destroy()
        }
    }
}
