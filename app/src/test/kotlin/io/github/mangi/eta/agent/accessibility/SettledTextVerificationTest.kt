package io.github.mangi.eta.agent.accessibility

import android.app.Application
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
    @Test fun screenshotOnlyFeedbackFindsOnlyTheFocusedMainDisplayApp() {
        val controller = Robolectric.buildService(AgentAccessibilityService::class.java).create()
        try {
            val service = controller.get()
            val background = AccessibilityWindowInfo.obtain()
            shadowOf(background).setType(AccessibilityWindowInfo.TYPE_APPLICATION)
            shadowOf(background).setRoot(field("background"))
            shadowOf(background).setFocused(true)
            shadowOf(service).setWindowsOnDisplay(7, listOf(background))
            assertNull(service.foregroundFeedbackWindow())
            val foreground = AccessibilityWindowInfo.obtain()
            shadowOf(foreground).setType(AccessibilityWindowInfo.TYPE_APPLICATION)
            shadowOf(foreground).setRoot(field("surface"))
            shadowOf(foreground).setFocused(true)
            shadowOf(service).setWindowsOnDisplay(0, listOf(foreground))
            assertEquals("app", service.foregroundFeedbackWindow()?.first)
            shadowOf(service).setWindowsOnDisplay(0, emptyList())
            assertNull(service.foregroundFeedbackWindow())
        } finally { controller.destroy() }
    }

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

    @Test fun appendAndPasteRecheckFullExpectedValueAndDropTheirPrivateProof() {
        val controller = Robolectric.buildService(AgentAccessibilityService::class.java).create()
        try {
            val service = controller.get()
            val node = field("existing+inserted")
            shadowOf(service).setRootInActiveWindow(node)
            val snapshot = service.captureNodeSnapshot(30)!!
            val proof = AgentAccessibilityService.TextVerification(snapshot.serviceToken, node,
                node.windowId, "app", "android.widget.EditText", "existing+inserted")
            val accepted = AgentAccessibilityService.NodeActionResult.success("ACTION_PASTE", verified = true)
                .copy(clipboardWritten = true, textVerification = proof)
            val settled = service.verifySettledText(accepted)
            assertTrue(settled.ok)
            assertNull(settled.textVerification)
            node.text = "existing" // Widget rejects the appended part after its optimistic echo.
            val rejected = service.verifySettledText(accepted)
            assertEquals("ACTION_OUTCOME_UNKNOWN", rejected.code)
            assertFalse(rejected.verified!!)
            assertTrue(rejected.clipboardWritten)
            assertNull(rejected.textVerification)
        } finally { controller.destroy() }
    }

    @Test fun aClaimWithoutTextIdentityCannotBecomeVerifiedAfterSettling() {
        val controller = Robolectric.buildService(AgentAccessibilityService::class.java).create()
        try {
            val service = controller.get()
            shadowOf(service).setRootInActiveWindow(field("something"))
            val accepted = AgentAccessibilityService.NodeActionResult.success("ACTION_SET_TEXT", verified = true)
            val result = service.verifySettledText(accepted)
            assertFalse(result.ok)
            assertEquals("ACTION_OUTCOME_UNKNOWN", result.code)
        } finally { controller.destroy() }
    }

    private fun field(value: String) = AccessibilityNodeInfo.obtain().apply {
        packageName = "app"; className = "android.widget.EditText"; text = value
        isEditable = true; isEnabled = true; isVisibleToUser = true; isFocused = true
        setBoundsInScreen(Rect(0, 0, 300, 80))
    }
}
