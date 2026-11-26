package io.github.mangi.eta.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.mangi.eta.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** 电脑会话里点开图片：看图器向电脑取原图（`dsh-image:` 地址），取不到时说明原因，不是一片黑。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h896dp-xhdpi")
class RemoteImageViewerTest {
    private lateinit var activity: ComponentActivity
    private lateinit var controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
    private var panel: Stage3TestPanel? = null

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity = controller.get()
    }

    @After
    fun tearDown() {
        panel?.close()
        panel = null
        controller.pause().stop().destroy()
    }

    private fun open(loader: suspend (String) -> String?): Stage3TestPanel {
        var previewer: ChatImagePreviewer? = null
        panel = Stage3TestPanel(activity) {
            CompositionLocalProvider(LocalRemoteImageLoader provides loader) {
                ChatImagePreviewHost { previewer = LocalChatImagePreviewer.current }
            }
        }
        val p = panel!!
        p.advance(5)
        previewer!!.open(listOf("dsh-image:abc"))
        p.advance(60)
        return p
    }

    private fun hasText(p: Stage3TestPanel, text: String) = p.nodes().any { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }

    @Test
    fun viewerFetchesTheDesktopImage() {
        val asked = mutableListOf<String>()
        val p = open { source -> asked += source; PNG_1PX }
        assertEquals(listOf("dsh-image:abc"), asked.distinct())
        assertEquals(false, hasText(p, activity.getString(R.string.chat_image_remote_failed)))
        assertNotNull(p.nodes().firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == activity.getString(R.string.action_save) } == true
        })
    }

    @Test
    fun failedFetchExplainsInsteadOfShowingBlack() {
        val p = open { null }
        assertEquals(true, hasText(p, activity.getString(R.string.chat_image_remote_failed)))
    }

    private companion object {
        const val PNG_1PX = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
}
