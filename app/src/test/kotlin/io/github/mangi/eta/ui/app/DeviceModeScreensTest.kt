package io.github.mangi.eta.ui.app

import android.app.Application
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.getOrNull
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

    /** 09 第二期 2.1：完成的一轮，思考和步骤收成一行；点一下展开。 */
    @Test fun processFolded() {
        check("home-fold-light", DeviceModeScreens.capture("home-fold-light") {
            DeviceModeScreens.Home(DeviceModeScreens.processMessages, title = "修复登录校验")
        })
        check("home-fold-expanded-light", DeviceModeScreens.capture("home-fold-expanded-light", act = { panel ->
            val row = panel.nodes().first { node ->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                    ?.any { it.text.startsWith("过程") || it.text.startsWith("Process") } == true
            }
            panel.click(row)
        }) {
            DeviceModeScreens.Home(DeviceModeScreens.processMessages, title = "修复登录校验")
        })
    }

    /** 提示条跟随页面颜色：浅色页白色胶囊、深色页深灰胶囊（2026111606 用户反馈：浅色页上弹出黑胶囊不协调）。 */
    @Test fun toastFollowsPage() {
        for (dark in listOf(false, true)) {
            val name = if (dark) "toast-dark" else "toast-light"
            check(name, DeviceModeScreens.capture(name, dark = dark) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    DeviceModeScreens.Home(DeviceModeScreens.sampleMessages, title = "整理本周工作进展")
                    io.github.mangi.eta.ui.components.EtaToast("已切换到 Example Model", {},
                        androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 120.dp),
                        durationMillis = 60_000)
                }
            })
        }
    }

    /** 电脑会话页与首页统一：顶部玻璃控件浮在消息上，没有实底顶栏。 */
    @Test fun desktopChat() = variants("desktop-chat") { DeviceModeScreens.DesktopChat() }

    @Test fun homeUnseenDot() {
        try { check("home-unseen-light", DeviceModeScreens.capture("home-unseen-light") { DeviceModeScreens.HomeWithUnseen() }) }
        finally { io.github.mangi.eta.agent.runtime.FinishedUnseen.resetForTest() }
    }

    /** 选择文字面板的内容：整轮思考与回答在同一个可选区域（09 第零期 0.4）。 */
    @Test fun turnSelectionPanel() = variants("selection-panel", deviceMode = false) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
            .background(io.github.mangi.eta.ui.theme.EtaTheme.colors.surface).padding(16.dp)) {
            io.github.mangi.eta.ui.components.TurnSelectionContent(listOf(
                io.github.mangi.eta.ui.components.TurnSegment("先看一下登录页的报错：密码框校验把空格也算成了字符。", thinking = true),
                io.github.mangi.eta.ui.components.TurnSegment("我先读一下校验函数，再改。", thinking = false),
                io.github.mangi.eta.ui.components.TurnSegment("改完要跑一遍测试，确认没有别的地方依赖原来的行为。", thinking = true),
                io.github.mangi.eta.ui.components.TurnSegment("修好了：去掉首尾空格后再校验，测试 12 项全部通过。", thinking = false),
            ))
        }
    }

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
