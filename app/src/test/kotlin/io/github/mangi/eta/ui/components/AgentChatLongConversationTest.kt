package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.model.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentChatLongConversationTest {
    @Test fun fiveHundredMessagesKeepABoundedViewportWhileScrolling() {
        val messages = List<AgentChatMessageUi>(500) { index ->
            if (index % 2 == 0) UserMessageUi("user-$index", "Compare the options for task $index and explain the result.")
            else AgentMessageUi("answer-$index", buildString {
                append("The result for task $index is ready. This paragraph stays identical across both versions.\n\n")
                append("- First option: reliable execution\n- Second option: a shorter response\n\n")
                if (index % 5 == 1) append("```kotlin\nval result = listOf(1, 2, 3).map { it * 2 }\nprintln(result)\n```\n\n")
                if (index % 7 == 1) append("| Option | Result |\n| --- | --- |\n| First | Available |\n| Second | Available |\n")
            })
        }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        lateinit var list: LazyListState
        val panel = Stage3TestPanel(controller.get()) {
            list = rememberLazyListState()
            AgentConversationMessages(messages, list, false, 160.dp, false, {}, messageActionsEnabled = true)
        }
        try {
            panel.advance(30)
            val bitmap = panel.bitmap()
            try {
                val samples = mutableListOf<Double>()
                var maxVisible = 0
                var moved = 0
                for (start in listOf(20, 200, 360)) {
                    list.requestScrollToItem(start)
                    panel.advance(20)
                    repeat(12) { list.dispatchRawDelta(80f); panel.advance(); panel.draw(bitmap) }
                    repeat(30) {
                        val before = System.nanoTime()
                        if (list.dispatchRawDelta(160f) > 0f) moved++
                        panel.advance()
                        panel.draw(bitmap)
                        samples += (System.nanoTime() - before) / 1_000_000.0
                        maxVisible = maxOf(maxVisible, list.layoutInfo.visibleItemsInfo.size)
                    }
                }
                assertEquals(501, list.layoutInfo.totalItemsCount)
                assertEquals("Every measured frame must actually scroll", 90, moved)
                assertTrue("Lazy viewport must not render the entire conversation", maxVisible in 1..30)
                val sorted = samples.sorted()
                val label = if (System.getenv("ETA_STAGE3_BASELINE") == "1") "baseline" else "after"
                val file = File("build/reports/eta-stage3/performance-$label.json")
                file.parentFile.mkdirs()
                file.writeText("""{"messages":500,"samples":90,"median_ms":${sorted[45]},"p95_ms":${sorted[85]},"max_visible_items":$maxVisible,"scrolled_frames":$moved,"kind":"Robolectric native CPU render proxy; not device FPS"}""")
                panel.capture("long-conversation-$label")
            } finally { bitmap.recycle() }
        } finally { panel.close(); controller.pause().stop().destroy() }
    }
}
