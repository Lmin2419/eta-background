package io.github.mangi.eta.ui.app

import android.app.Application
import androidx.activity.ComponentActivity
import io.github.mangi.eta.ui.components.EtaCouiSupport
import io.github.mangi.eta.ui.components.EtaRenderDiagnostics
import io.github.mangi.eta.ui.components.FrameWindow
import io.github.mangi.eta.ui.components.Stage3TestPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 07 第四期·采样源精简与渲染诊断。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
class RenderSourcesTest {
    @Test fun homeOnTheCouiPathDoesNotRecordTheMiuixMessageLayer() {
        DeviceModeScreens.initEnvironment()
        val previous = EtaCouiSupport.shaders
        EtaCouiSupport.shaders = true
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get(), blur = true) {
            DeviceModeScreens.Home(DeviceModeScreens.sampleMessages, title = "整理本周工作进展")
        }
        try {
            runCatching { panel.advance(5) }
            val names = EtaRenderDiagnostics.sources.keys
            assertTrue("chat page COUI source is registered: $names", "对话页(COUI)" in names)
            assertFalse("the Miuix message layer is skipped on the COUI path: $names", "消息区(Miuix)" in names)
        } finally {
            EtaCouiSupport.shaders = previous
            runCatching { panel.close() }; runCatching { controller.pause().stop().destroy() }
        }
        assertTrue("sources are unregistered when the page leaves: ${EtaRenderDiagnostics.sources}",
            "对话页(COUI)" !in EtaRenderDiagnostics.sources)
    }

    @Test fun frameWindowCountsLateFramesInTheLastTwoSeconds() {
        val window = FrameWindow(windowMs = 2_000)
        val period = 8_333_333L // 120Hz
        window.add(0, 5_000_000)
        window.add(1_000_000_000, 12_000_000)
        window.add(2_500_000_000, 6_000_000) // 第一帧已超出 2 秒窗口
        val stats = window.stats(period)
        assertEquals(2, stats.frames)
        assertEquals(1, stats.late)
        assertEquals(9f, stats.avgMs, 0.01f)
        assertEquals(12f, stats.maxMs, 0.01f)
    }
}
