package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 流体云排查：诊断能读出一条通知申请了实况、是否常驻，以及通知不在时不报。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LiveUpdateDiagnosticsTest {
    @Test fun reportsRequestedOngoingCard() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("live-test", "live", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = Notification.Builder(context, "live-test")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("running")
            .setOngoing(true)
            .setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
            .apply { extras.putBoolean("android.requestPromotedOngoing", true) }
            .build()
        manager.notify(4242, notification)
        val state = AgentExecutionService.liveUpdateState(context, 4242)
        requireNotNull(state)
        assertTrue(state, "requested=true" in state)
        assertTrue(state, "ongoing=true" in state)
        assertTrue(state, "channel=live-test" in state)
        assertNull(AgentExecutionService.liveUpdateState(context, 4343))
    }
}
