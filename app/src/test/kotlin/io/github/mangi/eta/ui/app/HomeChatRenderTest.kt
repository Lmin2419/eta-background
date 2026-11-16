package io.github.mangi.eta.ui.app

import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.ui.components.AgentChatBody
import io.github.mangi.eta.ui.components.EtaContextMenu
import io.github.mangi.eta.ui.components.EtaCouiSupport
import io.github.mangi.eta.ui.components.EtaMenuDivider
import io.github.mangi.eta.ui.components.EtaMenuItem
import io.github.mangi.eta.ui.components.HomeConversationTopBar
import io.github.mangi.eta.ui.components.Stage3Models
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.navigation.AppRoute
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 2026111407–11 真机上手机对话整页空白（顶部按钮、消息、输入框都没有），侧栏和电脑会话页正常。
 *
 * 原因：COUI 模式下 [EtaContextMenu] 为了收集菜单项，往父布局里放了一个 0 尺寸的 Column。
 * 侧栏里的长按菜单把这个节点放进了 [io.github.mangi.eta.ui.components.ConversationSidePaneScaffold]
 * 的自定义布局，那个布局按子节点顺序取「侧栏、对话页」，于是把 0 尺寸节点当成了对话页，
 * 真正的对话页没有被测量和摆放。本机测试默认走旧小窗（不放这个节点），所以一直没测出来。
 *
 * 这里按真机的 COUI 模式（[EtaCouiSupport.shaders] = true）、用硬件渲染（RenderNode）截图检查。
 * 关闭模糊只是为了避开本机渲染器编译不了的模糊着色器，与这个问题无关。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeChatRenderTest {
    private var previousShaders = true

    @Before fun deviceMode() {
        previousShaders = EtaCouiSupport.shaders
        EtaCouiSupport.shaders = true
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
    }

    @After fun restore() {
        EtaCouiSupport.shaders = previousShaders
        System.clearProperty("robolectric.pixelCopyRenderMode")
    }

    @Test fun contextMenuAddsNoLayoutNodeToItsParent() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var children = -1
        val panel = Stage3TestPanel(controller.get(), blur = false) {
            Layout(content = {
                EtaContextMenu(false, onDismiss = {}) {
                    EtaMenuItem("Pin", onClick = {})
                    EtaMenuDivider()
                    EtaMenuItem("Delete", onClick = {}, destructive = true)
                }
            }) { measurables, constraints ->
                children = measurables.size
                layout(constraints.maxWidth, constraints.maxHeight) {}
            }
        }
        try {
            panel.advance(5)
            assertEquals("the closed menu must not occupy a slot in its parent's layout", 0, children)
        } finally { runCatching { panel.close() }; runCatching { controller.pause().stop().destroy() } }
    }

    @Test fun homeChatIsDrawnNextToTheSidebar() {
        io.github.mangi.eta.data.datastore.SettingsDataStore.init(RuntimeEnvironment.getApplication())
        val messages = List<AgentChatMessageUi>(6) { index ->
            if (index % 2 == 0) UserMessageUi("user-$index", "请整理这次讨论的重点。")
            else AgentMessageUi("answer-$index", "第 $index 项：检查消息区域与输入卡片的衔接。")
        }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get(), blur = false) {
            AgentAppShell(
                currentRoute = AppRoute.Home, isCurrentRoute = true,
                conversationPaneState = ConversationPaneUiState(emptyList(), null, ""),
                isConversationPaneOpen = false, onBack = {}, onOpenConversationPane = {}, onDismissConversationPane = {},
                onSearchConversations = {}, onNewConversation = {}, onOpenCommonTasks = {}, onOpenTerminal = {},
                onLaunchKimiWeb = {}, kimiWebLabel = "", canStopKimiWeb = false, onStopKimiWeb = {}, onRefreshKimiWeb = {},
                onOpenBrowser = {}, onSelectConversation = {}, onConversationRename = {}, onConversationExport = {},
                onConversationDelete = {}, onOpenTools = {}, onOpenSkills = {}, onOpenCharacters = {},
                onOpenPermissions = {}, onOpenSettings = {}, onOpenModelProviders = {},
                homeTopBar = {
                    HomeConversationTopBar(Stage3Models, false, false, AgentContextUsageUi(12500, 128000), true, true, null, 0,
                        {}, {}, {}, {}, {}, {}, {}, {})
                },
            ) { padding ->
                AgentChatBody(messages, Stage3Models, false, "", false, ReasoningEffort.OFF, listOf(ReasoningEffort.OFF),
                    emptyList(), emptyList(), messageEdit = null, onReasoningEffortChange = {}, onCompactContext = {}, canCompactContext = true,
                    onModelSelected = {}, onSubmit = {}, onStop = {}, onAttachImage = {}, onRemoveImage = {}, onAttachFiles = {}, onAttachFolder = {},
                    onAttachFilePath = {}, onRemoveFileReference = {}, onEditMessage = {}, onCancelMessageEdit = {}, onDeleteMessage = {},
                    onRegenerateMessage = {}, onSelectReplyCandidate = { _, _ -> }, onSuggestionClick = {}, onRunTraceClick = {}, onOpenBrowser = {},
                    modifier = Modifier.fillMaxSize().padding(padding))
            }
        }
        try {
            panel.advance(40)
            val activity = controller.get()
            val decor = activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            var result = -1
            PixelCopy.request(activity.window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(PixelCopy.SUCCESS, result)
            File("build/reports/home-diag/home-with-sidebar-device-mode.png").also { it.parentFile?.mkdirs() }
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // 测试窗口自带的标题栏在最上面 ~112px，从它下面开始数文字（深色）像素。
            var ink = 0
            for (y in 240 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
                val p = bitmap.getPixel(x, y)
                if (((p shr 16) and 0xff) + ((p shr 8) and 0xff) + (p and 0xff) < 300) ink++
            }
            assertTrue("home chat (messages, composer) must be drawn next to the sidebar, ink=$ink", ink > 500)
        } finally { runCatching { panel.close() }; runCatching { controller.pause().stop().destroy() } }
    }
}
