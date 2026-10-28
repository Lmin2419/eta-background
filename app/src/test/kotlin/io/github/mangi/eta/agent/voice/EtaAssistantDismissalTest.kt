package io.github.mangi.eta.agent.voice

import android.app.Application
import android.os.Looper
import io.github.mangi.eta.data.datastore.SettingsDataStore
import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class EtaAssistantDismissalTest {
    @Test
    fun systemDismissUsesTransitionAndStillCleansUpWhenNoFramesArrive() {
        val application = RuntimeEnvironment.getApplication()
        SettingsDataStore.init(application)
        val controller = Robolectric.buildService(EtaAssistantOverlayService::class.java).create()
        try {
            val service = controller.get()
            ReflectionHelpers.setField(service, "activeRunId", "background-run")
            EtaAssistantOverlayService.dismiss(application)
            EtaAssistantOverlayService.dismiss(application)
            assertNull("退出只脱钩本地任务", ReflectionHelpers.getField<String?>(service, "activeRunId"))
            assertNull("系统入口不能 stopService 跳过动画", shadowOf(application).nextStoppedService)
            assertFalse(shadowOf(service).isStoppedBySelf)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertFalse(shadowOf(service).isStoppedBySelf)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
            assertTrue("没有 Compose 帧时仍必须回收窗口和服务", shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
    }
}
