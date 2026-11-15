package io.github.mangi.eta.ui.app

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 主要页面的真机模式截图：浅色、深色、200% 字体，存到 build/reports/device-mode/。
 * 每张图都检查「不是整页空白」，PixelCopy 成功。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceModeScreensTest {
    @Before fun setUp() = DeviceModeScreens.initEnvironment()

    private fun check(name: String, shot: DeviceModeScreens.Shot, minInk: Int = 300) {
        assertEquals("$name: PixelCopy", android.view.PixelCopy.SUCCESS, shot.result)
        assertTrue("$name must not be blank (ink=${shot.ink})", shot.ink > minInk)
    }

    private fun variants(name: String, deviceMode: Boolean = true, content: @androidx.compose.runtime.Composable () -> Unit) {
        check("$name-light", DeviceModeScreens.capture("$name-light", deviceMode = deviceMode, content = content))
        check("$name-dark", DeviceModeScreens.capture("$name-dark", dark = true, deviceMode = deviceMode, content = content))
        check("$name-font200", DeviceModeScreens.capture("$name-font200", fontScale = 2f, deviceMode = deviceMode, content = content))
    }

    @Test fun homeNewConversation() = variants("home-new") { DeviceModeScreens.Home(emptyList(), title = null) }

    @Test fun homeConversation() = variants("home-chat") {
        DeviceModeScreens.Home(DeviceModeScreens.sampleMessages, title = "整理本周工作进展")
    }

    @Test fun sidebarOpen() = variants("sidebar") {
        DeviceModeScreens.Home(DeviceModeScreens.sampleMessages, title = "整理本周工作进展", paneOpen = true)
    }

    // 设置类页面：COUI 顶栏着色器本机编译不了，走测试骨架（见 DeviceModeScreens.capture）。
    @Test fun settings() = variants("settings", deviceMode = false) { DeviceModeScreens.Settings() }

    @Test fun appearanceSettings() = variants("appearance", deviceMode = false) { DeviceModeScreens.Appearance() }
}

/** 宽屏（平板、横屏）：侧栏常驻在左边。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w900dp-h700dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceModeWideScreensTest {
    @Before fun setUp() = DeviceModeScreens.initEnvironment()

    @Test fun homeWide() {
        val shot = DeviceModeScreens.capture("wide-home-chat") {
            DeviceModeScreens.Home(DeviceModeScreens.sampleMessages, title = "整理本周工作进展")
        }
        assertTrue("wide home must not be blank (ink=${shot.ink})", shot.ink > 300)
    }

    @Test fun settingsWide() {
        val shot = DeviceModeScreens.capture("wide-settings", deviceMode = false) { DeviceModeScreens.Settings() }
        assertTrue("wide settings must not be blank (ink=${shot.ink})", shot.ink > 300)
    }
}
