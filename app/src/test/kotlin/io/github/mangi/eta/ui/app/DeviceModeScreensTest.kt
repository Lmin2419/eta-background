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

    private fun variants(name: String, deviceMode: Boolean = true, minInk: Int = 300, content: @androidx.compose.runtime.Composable () -> Unit) {
        check("$name-light", DeviceModeScreens.capture("$name-light", deviceMode = deviceMode, content = content), minInk)
        check("$name-dark", DeviceModeScreens.capture("$name-dark", dark = true, deviceMode = deviceMode, content = content), minInk)
        check("$name-font200", DeviceModeScreens.capture("$name-font200", fontScale = 2f, deviceMode = deviceMode, content = content), minInk)
    }

    @Test fun homeNewConversation() = variants("home-new") { DeviceModeScreens.Home(emptyList(), title = null) }

    @Test fun homeConversation() = variants("home-chat") {
        DeviceModeScreens.Home(DeviceModeScreens.sampleMessages, title = "整理本周工作进展")
    }

    @Test fun recheckAfterGivingUp() = check("home-recheck-light", DeviceModeScreens.capture("home-recheck-light") {
        DeviceModeScreens.Home(DeviceModeScreens.gaveUpMessages, title = "安装 Python 依赖")
    })

    /** 回复中选了别的模型：标题胶囊第二行是「→ 新模型 · 下一轮」（2026111601）。 */
    @Test fun modelQueuedForNextTurn() = check("home-queued-light", DeviceModeScreens.capture("home-queued-light") {
        val models = io.github.mangi.eta.ui.components.Stage3Models
        DeviceModeScreens.Home(DeviceModeScreens.sampleMessages, title = "整理本周工作进展",
            models = models.copy(queuedModel = models.providerGroups.single().models.last()), streaming = true)
    })

    /** 电脑会话页与首页统一：顶部玻璃控件浮在消息上，没有实底顶栏。 */
    @Test fun desktopChat() = variants("desktop-chat") { DeviceModeScreens.DesktopChat() }

    /** 终端页顶部换成玻璃控件（返回圆钮 + 标题胶囊）。 */
    // 终端占位是深色底、少量浅色字，深色模式下「反差像素」本来就少，门槛放低。
    @Test fun terminalTopBar() = variants("terminal", minInk = 60) { DeviceModeScreens.TerminalShell() }

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
