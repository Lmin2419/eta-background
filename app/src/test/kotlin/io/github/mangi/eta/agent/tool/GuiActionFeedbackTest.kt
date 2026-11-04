package io.github.mangi.eta.agent.tool

import android.app.Application
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import org.robolectric.Robolectric
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class GuiActionFeedbackTest {
    @Before fun clearBefore() = GuiActionFeedback.disconnect()
    @After fun clearAfter() = GuiActionFeedback.disconnect()

    @Test fun visualFallbackIsBoundedAndKeepsEarlyFeedbackWhileDiscardingMiddleFrames() {
        val controller = Robolectric.buildService(AgentAccessibilityService::class.java).create()
        ReflectionHelpers.setStaticField(AgentAccessibilityService::class.java, "instance", controller.get())
        try {
            val calls = AtomicInteger()
            GuiActionFeedback.begin("a", "app", 7, -1).use { session ->
                session.startFrames(Callable {
                    JSONObject().put("image", listOf("AQ==", "Ag==", "Aw==")[calls.getAndIncrement()])
                        .put("width", 640).put("height", 960)
                })
                val result = feedbackToolResult(session.finish(JSONObject().put("ok", true), false))
                assertEquals(3, calls.get())
                assertEquals(2, result.images.size)
                assertTrue(result.sensitive)
                assertTrue(result.images.first().reference.endsWith("AQ=="))
                assertTrue(result.images.last().reference.endsWith("Aw=="))
                assertFalse(JSONObject(result.content).has("_feedback_images"))
            }
        } finally {
            ReflectionHelpers.setStaticField(AgentAccessibilityService::class.java, "instance", null)
            controller.destroy()
        }
    }

    @Test fun serviceCallbackKeepsTheToastEvenWhenTheNodeTreeDoesNotChange() {
        val session = GuiActionFeedback.begin("a", "app", 7, -1)
        session.use {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED).apply {
                packageName = "app"; eventTime = SystemClock.uptimeMillis(); text.add("激活码不可用")
            }
            ReflectionHelpers.callInstanceMethod<Void>(event, "setDisplayId", ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, 7))
            GuiActionFeedback.onEvent(event)
            val result = session.finish(JSONObject("""{"ok":true,"verified":false,"operationMayHaveRun":true,"code":"ACTION_OUTCOME_UNKNOWN"}"""), false)
            assertEquals("TEXT_OBSERVED", result.getJSONObject("action_feedback").getString("status"))
            assertEquals("激活码不可用", result.getJSONObject("action_feedback").getJSONArray("texts").getJSONObject(0).getString("text"))
            assertFalse(result.getBoolean("verified"))
            assertEquals("ACTION_OUTCOME_UNKNOWN", result.getString("code"))
        }
    }

    @Test fun aRejectedActionIsNotTurnedIntoAConfirmedSuccess() {
        GuiActionFeedback.begin("a", "app", 0, 1).use { session ->
            val result = session.finish(JSONObject("""{"ok":false,"code":"STALE_OBSERVATION","operationMayHaveRun":false}"""), false)
            assertFalse(result.getBoolean("ok"))
            assertEquals("STALE_OBSERVATION", result.getString("code"))
            assertEquals("UNCONFIRMED", result.getJSONObject("action_feedback").getString("status"))
        }
    }

    @Test fun closingFinishedSessionPreservesLateToastUntilTheNextInputGate() {
        GuiActionFeedback.begin("run", "app", 7, -1).use { session ->
            val result = session.finish(JSONObject().put("ok", true), true)
            assertEquals("UNCONFIRMED", result.getJSONObject("action_feedback").getString("status"))
        }
        postToast("后来才出现的失败提示")
        assertNull(GuiActionFeedback.beforeInput("other-run", "app", 7, -1))
        val blocked = GuiActionFeedback.beforeInput("run", "app", 7, -1)!!
        assertFalse(blocked.getBoolean("operationMayHaveRun"))
        assertEquals("LATE_ACTION_FEEDBACK", blocked.getString("code"))
        assertTrue(blocked.getBoolean("requires_fresh_observation"))
        assertEquals("后来才出现的失败提示", blocked.getJSONObject("action_feedback")
            .getJSONArray("texts").getJSONObject(0).getString("text"))
        assertNull(GuiActionFeedback.beforeInput("run", "app", 7, -1))
    }

    @Test fun cancelledSessionCannotLeaveLateFeedbackBehind() {
        GuiActionFeedback.begin("run", "app", 7, -1).close()
        postToast("cancelled")
        assertNull(GuiActionFeedback.beforeInput("run", "app", 7, -1))
        GuiActionFeedback.begin("run", "app", 7, -1).use {
            it.finish(JSONObject().put("ok", true), true)
        }
        GuiActionFeedback.clearOwner("run")
        postToast("cancelled after finish")
        assertNull(GuiActionFeedback.beforeInput("run", "app", 7, -1))
    }

    @Test fun alreadyCapturedTextAvoidsVisualSamplingAndDoesNotClaimBusinessSuccess() {
        val calls = AtomicInteger()
        GuiActionFeedback.begin("run", "app", 7, -1).use {
            postToast("请输入有效号码")
            it.startFrames(Callable { calls.incrementAndGet(); null })
            val result = it.finish(JSONObject().put("ok", true).put("verified", false), false)
            assertEquals(0, calls.get())
            assertFalse(result.getBoolean("verified"))
            assertEquals("TEXT_OBSERVED", result.getJSONObject("action_feedback").getString("status"))
            assertEquals(0, result.getJSONObject("action_feedback").getInt("image_count"))
            assertTrue(result.has("timing_ms"))
        }
    }

    private fun postToast(message: String) {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED).apply {
            packageName = "app"; eventTime = SystemClock.uptimeMillis(); text.add(message)
        }
        ReflectionHelpers.callInstanceMethod<Void>(event, "setDisplayId",
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, 7))
        GuiActionFeedback.onEvent(event)
    }
}
