package io.github.mangi.eta.ui.components

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class EtaPreferenceRenderingTest {
    @Test
    fun controlsKeepTheirTitlesWhenTrailingSlotsAreProvided() = withContent({
        Column {
            EtaSwitchPreference(title = "默认启用思考", summary = "测试摘要", checked = true, onCheckedChange = {})
            EtaRadioButtonPreference(title = "默认开场白", selected = true, onClick = {})
            EtaWindowSpinnerPreference(
                title = "选择主题", items = listOf(top.yukonga.miuix.kmp.basic.DropdownItem(text = "跟随系统")),
                selectedIndex = 0, onSelectedIndexChange = {},
            )
        }
    }) { view ->
        val ownerView = view.getChildAt(0)
        val owner = ownerView.javaClass.getMethod("getSemanticsOwner").invoke(ownerView) as SemanticsOwner
        val nodes = descendants(owner.unmergedRootSemanticsNode)
        for (title in listOf("默认启用思考", "测试摘要", "默认开场白", "选择主题", "跟随系统")) {
            val node = nodes.firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == title } == true }
            assertNotNull("缺少可见文本：$title", node)
            assertTrue(node!!.boundsInRoot.width > 0f)
            assertTrue(node.boundsInRoot.height > 0f)
        }
    }

    @Test
    fun trailingSlotIsPlacedAfterTitleWithOrWithoutAnIcon() {
        for (hasIcon in listOf(false, true)) {
            var titleBounds: Rect? = null
            var actionBounds: Rect? = null
            withContent({
                EtaPreferenceRow(
                    title = null,
                    startAction = if (hasIcon) ({ Box(Modifier.size(24.dp)) }) else null,
                    titleContent = { Box(Modifier.size(80.dp, 20.dp).onGloballyPositioned { titleBounds = it.boundsInRoot() }) },
                ) {
                    Box(Modifier.size(44.dp, 24.dp).onGloballyPositioned { actionBounds = it.boundsInRoot() })
                }
            }) {
                assertNotNull(titleBounds)
                assertNotNull(actionBounds)
                val title = requireNotNull(titleBounds)
                val action = requireNotNull(actionBounds)
                assertEquals(if (hasIcon) 56f else 16f, title.left, 0.5f)
                assertTrue(action.left > title.right)
                assertEquals(304f, action.right, 0.5f)
            }
        }
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap(::descendants)

    private fun withContent(content: @Composable () -> Unit, verify: (ComposeView) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val view = ComposeView(controller.get())
        try {
            controller.get().setContentView(view)
            view.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    top.yukonga.miuix.kmp.theme.MiuixTheme { content() }
                }
            }
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            view.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
            view.layout(0, 0, 320, view.measuredHeight)
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            verify(view)
        } finally {
            view.disposeComposition()
            controller.pause().stop().destroy()
        }
    }
}
