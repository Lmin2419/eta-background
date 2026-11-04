package io.github.mangi.eta.agent.tool

import org.junit.Assert.*
import org.junit.Test

class ActionFeedbackBufferTest {
    private var time = 100L
    private val buffer = ActionFeedbackBuffer { time }

    @Test fun disappearingToastSurvivesUntilTheActionReadsIt() {
        val ticket = buffer.begin("run1", "app", 0, 4)
        time = 120
        buffer.record("app", 0, null, time, "notification_or_toast", listOf("请输入正确的激活码"))
        time = 2400 // The app's toast has disappeared; our copied evidence still exists.
        assertEquals("请输入正确的激活码", buffer.read(ticket).single().text)
        assertEquals(20L, buffer.read(ticket).single().elapsedMs)
        buffer.close(ticket)
        buffer.record("app", 0, null, time, "notification_or_toast", listOf("late"))
        assertEquals(1, buffer.read(ticket).size)
    }

    @Test fun foregroundAndTwoBackgroundTasksCannotConsumeOneAnothersMessages() {
        val foreground = buffer.begin("fg", "app", 0, 1)
        val background = buffer.begin("bg", "app", 7, 2)
        val other = buffer.begin("other", "other.app", 8, 3)
        time = 110
        buffer.record("app", 7, null, time, "announcement", listOf("后台提示"))
        buffer.record("other.app", 7, null, time, "announcement", listOf("wrong display"))
        buffer.record("app", null, null, time, "announcement", listOf("unattributed"))
        assertTrue(buffer.read(foreground).isEmpty())
        assertEquals("后台提示", buffer.read(background).single().text)
        assertTrue(buffer.read(other).isEmpty())
    }

    @Test fun unknownDisplayRequiresAnExactWindowAndAmbiguousOwnersAreRejected() {
        val a = buffer.begin("a", "app", 9, 2)
        time = 110
        buffer.record("app", null, 2, time, "window_text", listOf("known window"))
        val b = buffer.begin("b", "app", 9, 2)
        buffer.record("app", 9, 2, time, "window_text", listOf("ambiguous"))
        assertEquals(1, buffer.read(a).size)
        assertTrue(buffer.read(b).isEmpty())
    }

    @Test fun oldFutureAndPostDisconnectEventsAreRejected() {
        val a = buffer.begin("a", "app", 0, 2)
        buffer.record("app", 0, 2, 99, "window_text", listOf("old"))
        buffer.record("app", 0, 2, 101, "window_text", listOf("future"))
        buffer.clear()
        time = 110
        buffer.record("app", 0, 2, time, "window_text", listOf("disconnected"))
        assertTrue(buffer.read(a).isEmpty())
    }

    @Test fun boundedNoiseCannotPushOutTheToast() {
        val a = buffer.begin("a", "app", 0, 2)
        time = 110
        repeat(30) { buffer.record("app", 0, 2, time, "content_text", listOf("noise$it".repeat(200))) }
        buffer.record("app", 0, null, time, "notification_or_toast", listOf("提交失败"))
        repeat(30) { buffer.record("app", 0, null, time, "notification_or_toast", listOf("提交失败")) }
        assertEquals(8, buffer.read(a).size)
        assertEquals(1, buffer.read(a).count { it.text == "提交失败" })
        assertTrue(buffer.read(a).all { it.text.length <= 512 })
        time = 6000
        buffer.record("app", 0, null, time, "notification_or_toast", listOf("expired"))
        assertFalse(buffer.listening("app"))
        assertFalse(buffer.read(a).any { it.text == "expired" })
    }

    @Test fun lateToastSurvivesTheListeningDeadlineAndIsDeliveredOnceBeforeNextInput() {
        val ticket = buffer.begin("run", "app", 7, 4)!!
        time = 300
        buffer.record("app", 7, 4, time, "content_text", listOf("processing"))
        assertEquals("processing", buffer.complete(ticket).single().text)
        time = 1600 // action returned, but a short network failure toast arrives later
        buffer.record("app", 7, null, time, "notification_or_toast", listOf("提交失败"))
        time = 10_000 // the model takes longer to decide than the toast remains visible
        assertFalse(buffer.listening("app"))
        val pending = buffer.takeLate("run", "app", 7, 4)!!
        assertEquals(ticket.actionId, pending.actionId)
        assertEquals(listOf("提交失败"), pending.signals.map { it.text })
        assertNull(buffer.takeLate("run", "app", 7, 4))
    }

    @Test fun lateEvidenceCannotFollowAnotherOwnerDisplayOrPage() {
        fun arm() {
            val ticket = buffer.begin("run", "app", 7, 4)
            buffer.complete(ticket)
            time += 10
            buffer.record("app", 7, null, time, "announcement", listOf("late"))
        }
        arm()
        assertNull(buffer.takeLate("other", "app", 7, 4))
        assertNotNull(buffer.takeLate("run", "app", 7, 4))
        arm()
        assertNull(buffer.takeLate("run", "app", 0, 4))
        assertNull(buffer.takeLate("run", "app", 7, 4)) // discarded on ownership change
        arm()
        assertNull(buffer.takeLate("run", "app", 7, 5))
        arm()
        buffer.clearOwner("run")
        assertNull(buffer.takeLate("run", "app", 7, 4))
    }

    @Test fun beginningTheNextActionDoesNotAttributePrecedingEventsToIt() {
        val ticket = buffer.begin("run", "app", 7, 4)!!
        buffer.complete(ticket)
        time = 500
        assertNull(buffer.takeLate("run", "app", 7, 4))
        val next = buffer.begin("run", "app", 7, 4)!!
        buffer.record("app", 7, 4, 499, "announcement", listOf("queued old event"))
        assertTrue(buffer.read(next).isEmpty())
        assertTrue(next.actionId > ticket.actionId)
    }

    @Test fun longOperationStillReturnsEvidenceCapturedBeforeListenerExpired() {
        val ticket = buffer.begin("run", "app", 7, 4)
        time = 200
        buffer.record("app", 7, null, time, "announcement", listOf("notice"))
        time = 6000
        assertFalse(buffer.listening("app"))
        assertEquals("notice", buffer.complete(ticket).single().text)
    }

    @Test fun lateRetentionIsBoundedAndExplicitCancellationClearsIt() {
        val ticket = buffer.begin("run", "app", 7, 4)
        buffer.complete(ticket)
        time = 200
        buffer.record("app", 7, null, time, "announcement", listOf("late"))
        time = 120_101
        assertNull(buffer.takeLate("run", "app", 7, 4))
        val next = buffer.begin("run", "app", 7, 4)
        buffer.complete(next)
        time += 10
        buffer.record("app", 7, null, time, "announcement", listOf("cancelled"))
        buffer.clearOwner("run")
        assertNull(buffer.takeLate("run", "app", 7, 4))
    }
}
