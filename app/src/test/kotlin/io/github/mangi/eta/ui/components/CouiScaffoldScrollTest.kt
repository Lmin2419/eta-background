package io.github.mangi.eta.ui.components

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * 2026111603 真机反馈：服务商详情页（配置、模型两个标签）滑不动。
 * 原因：COUI 版 [MiuixScaffold] 把一个没挂到顶栏上的 Miuix 折叠滚动行为传给内容，没有顶栏去量折叠范围，
 * 它会吞掉所有往上推的手势。这里按真机模式（COUI 骨架）放一个长列表往上拖，列表必须滚动。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
class CouiScaffoldScrollTest {
    private var previous = false

    @Before fun deviceMode() { previous = EtaCouiSupport.shaders; EtaCouiSupport.shaders = true }

    @After fun restore() { EtaCouiSupport.shaders = previous; ShadowLooper.idleMainLooper() }

    @Test fun contentUnderTheCouiTopBarScrolls() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val list = LazyListState()
        // 只测布局与手势：不画（本机渲染器编译不了 COUI 顶栏的着色器）。
        val panel = Stage3TestPanel(controller.get(), blur = false) {
            Box(Modifier.fillMaxSize().drawWithContent { }) {
                MiuixScaffold(title = "OpenAI", onBack = {}) { _, scrollBehavior, _ ->
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
                        items(60) { Box(Modifier.fillMaxWidth().height(64.dp)) }
                    }
                }
            }
        }
        try {
            panel.advance(10)
            val view = panel.root() as View
            panel.dragFinger(view.width * .5f, view.height * .8f, view.width * .5f, view.height * .25f)
            assertTrue("the list must scroll under the COUI top bar (first=${list.firstVisibleItemIndex}, offset=${list.firstVisibleItemScrollOffset})",
                list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
        } finally { runCatching { panel.close() }; runCatching { controller.pause().stop().destroy() } }
    }
}
