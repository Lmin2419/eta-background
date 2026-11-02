package io.github.mangi.eta.agent.accessibility

import android.app.Application
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SettledTextVerificationTest {
    @Test fun optimisticSetTextEchoDoesNotSurviveWidgetRevertingTheValue() {
        val controller = Robolectric.buildService(AgentAccessibilityService::class.java).create()
        try {
            val service = controller.get()
            val node = field("CODE")
            shadowOf(service).setRootInActiveWindow(node)
            val snapshot = service.captureNodeSnapshot(30)!!
            val accepted = AgentAccessibilityService.NodeActionResult.success("ACTION_SET_TEXT", verified = true)
            assertTrue(service.verifySettledText(snapshot, 0, "CODE", accepted).ok)
            node.text = "" // Flutter discards the optimistic text before the next fresh read.
            val result = service.verifySettledText(snapshot, 0, "CODE", accepted)
            assertFalse(result.ok)
            assertFalse(result.verified!!)
            assertEquals("ACTION_OUTCOME_UNKNOWN", result.code)
            node.text = "CODE"; node.isShowingHintText = true
            assertFalse(service.verifySettledText(snapshot, 0, "CODE", accepted).ok)
        } finally { controller.destroy() }
    }

    @Test fun identicalTextInANewWindowIsNotProofOfTheOldWrite() {
        val controller = Robolectric.buildService(AgentAccessibilityService::class.java).create()
        try {
            val service = controller.get()
            shadowOf(service).setRootInActiveWindow(field("CODE"))
            val snapshot = service.captureNodeSnapshot(30)!!
            shadowOf(service).setRootInActiveWindow(field("CODE").apply { packageName = "another.app" })
            val accepted = AgentAccessibilityService.NodeActionResult.success("ACTION_SET_TEXT", verified = true)
            assertFalse(service.verifySettledText(snapshot, 0, "CODE", accepted).ok)
        } finally { controller.destroy() }
    }

    private fun field(value: String) = AccessibilityNodeInfo.obtain().apply {
        packageName = "app"; className = "android.widget.EditText"; text = value
        isEditable = true; isEnabled = true; isVisibleToUser = true; isFocused = true
        setBoundsInScreen(Rect(0, 0, 300, 80))
    }
}
