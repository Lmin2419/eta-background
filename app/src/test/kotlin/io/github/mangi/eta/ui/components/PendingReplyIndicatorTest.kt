package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "b+zh+Hans-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PendingReplyIndicatorTest {
    @Test fun labelPhasesFollowTheElapsedWait() {
        assertEquals(PendingReplyLabel(PendingReplyPhase.Waiting, 0), pendingReplyLabel(-5))
        assertEquals(PendingReplyLabel(PendingReplyPhase.Waiting, 2), pendingReplyLabel(2_999))
        assertEquals(PendingReplyLabel(PendingReplyPhase.Counting, 3), pendingReplyLabel(3_000))
        assertEquals(PendingReplyLabel(PendingReplyPhase.Counting, 19), pendingReplyLabel(19_999))
        assertEquals(PendingReplyLabel(PendingReplyPhase.SlowStart, 20), pendingReplyLabel(20_000))
    }

    @Test fun onlyAStreamingTurnWithoutAssistantOutputIsPending() {
        val user = AgentTimelineEntry.Message(UserMessageUi("user-1", "hello"))
        val answer = AgentTimelineEntry.Message(AgentMessageUi("assistant-1", "hi", isStreaming = true))
        val work = AgentTimelineEntry.WorkProcess("work-1", listOf(ThinkingMessageUi("t", "…", isStreaming = true)))
        assertEquals("user-1", pendingReplyMessageId(listOf(user), isStreaming = true))
        assertNull(pendingReplyMessageId(listOf(user), isStreaming = false))
        assertNull(pendingReplyMessageId(listOf(user, answer), isStreaming = true))
        assertNull(pendingReplyMessageId(listOf(user, work), isStreaming = true))
        assertNull(pendingReplyMessageId(emptyList(), isStreaming = true))
    }

    @Test fun clockKeepsTheFirstStartForAMessage() {
        val first = PendingReplyClock.startedAt("clock-test", 1_000)
        assertEquals(first, PendingReplyClock.startedAt("clock-test", 9_000))
    }

    @Test fun blankWaitShowsARowThatYieldsToTheFirstOutput() {
        for (dark in listOf(false, true)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val messages = mutableStateOf<List<AgentChatMessageUi>>(listOf(
                UserMessageUi("user-0", "上一轮"), AgentMessageUi("assistant-0", "上一轮回复"),
                UserMessageUi("user-wait-$dark", "帮我总结这份很长的文档"),
            ))
            val streaming = mutableStateOf(true)
            val panel = Stage3TestPanel(controller.get(), dark) {
                AgentConversationMessages(messages.value, rememberLazyListState(), streaming.value, 120.dp, true, {})
            }
            fun pendingNode() = panel.nodes().firstOrNull {
                it.config.getOrNull(SemanticsProperties.TestTag) == PendingReplyTestTag
            }
            try {
                panel.advance(30)
                val node = assertNotNull(pendingNode())
                assertEquals(listOf(controller.get().getString(R.string.chat_waiting_reply)),
                    node.config.getOrNull(SemanticsProperties.ContentDescription))
                assertNotNull("Announced once as a polite live region", node.config.getOrNull(SemanticsProperties.LiveRegion))
                panel.capture("pending-reply-${if (dark) "dark" else "light"}")

                messages.value = messages.value + AgentMessageUi("assistant-wait", "好的，", isStreaming = true)
                panel.advance(10)
                assertNull("First output replaces the waiting row", pendingNode())

                messages.value = messages.value.dropLast(1)
                streaming.value = false
                panel.advance(10)
                assertNull("Idle conversations never show the waiting row", pendingNode())
            } finally {
                panel.close(); controller.pause().stop().destroy()
            }
        }
    }

    private fun <T> assertNotNull(value: T?): T { org.junit.Assert.assertNotNull(value); return value!! }
}
