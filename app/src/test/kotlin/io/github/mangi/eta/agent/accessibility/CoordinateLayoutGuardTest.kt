package io.github.mangi.eta.agent.accessibility

import android.app.Application
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.github.mangi.eta.agent.device.RootShellDeviceController
import io.github.mangi.eta.core.AgentLogger
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CoordinateLayoutGuardTest {
    @Test fun keyboardGeometryAndWindowChangesAreRejectedBeforeCoordinateInjection() {
        val controller = Robolectric.buildService(AgentAccessibilityService::class.java).create()
        val service = controller.get()
        ReflectionHelpers.setStaticField(AgentAccessibilityService::class.java, "instance", service)
        val device = RootShellDeviceController(NoOpLogger, rootAvailable = { false })
        try {
            val app = window(AccessibilityWindowInfo.TYPE_APPLICATION, Rect(0, 0, 320, 470), "chat.app")
            shadowOf(service).setWindowsOnDisplay(0, listOf(app))
            val original = service.inputLayout()!!
            assertNull(device.coordinateLayoutError(original))
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(70))
            assertNull("Static screens must not expire by age", device.coordinateLayoutError(original))
            val keyboard = window(AccessibilityWindowInfo.TYPE_INPUT_METHOD, Rect(0, 250, 320, 470), "ime.app")
            shadowOf(service).setWindowsOnDisplay(0, listOf(app, keyboard))
            fun reason(expected: AgentAccessibilityService.InputLayout) = JSONObject(device.coordinateLayoutError(expected)!!).also {
                assertFalse(it.getBoolean("operationMayHaveRun")); assertEquals("STALE_OBSERVATION", it.getString("code"))
            }.getString("reason")
            assertEquals("KEYBOARD_CHANGED", reason(original))
            shadowOf(service).setWindowsOnDisplay(0, listOf(app))
            assertEquals("GEOMETRY_MISMATCH", reason(original.copy(rotation = original.rotation + 1)))
            assertEquals("GEOMETRY_MISMATCH", reason(original.copy(width = original.width + 1)))
            assertEquals("WINDOW_CHANGED", reason(original.copy(windowId = original.windowId + 1)))
            shadowOf(app).setBoundsInScreen(Rect(0, 0, 320, 250))
            assertEquals("WINDOW_BOUNDS_CHANGED", reason(original))
            shadowOf(service).setWindowsOnDisplay(0, emptyList())
            assertEquals("WINDOW_UNAVAILABLE", reason(original))
            shadowOf(service).setWindowsOnDisplay(7, listOf(app))
            assertNull("Private windows must not supply the main-screen layout", service.inputLayout())
        } finally {
            device.close()
            ReflectionHelpers.setStaticField(AgentAccessibilityService::class.java, "instance", null)
            controller.destroy()
        }
    }

    private fun window(type: Int, bounds: Rect, pkg: String) = AccessibilityWindowInfo.obtain().also { window ->
        shadowOf(window).setType(type)
        shadowOf(window).setFocused(type == AccessibilityWindowInfo.TYPE_APPLICATION)
        shadowOf(window).setBoundsInScreen(bounds)
        shadowOf(window).setRoot(AccessibilityNodeInfo.obtain().apply { packageName = pkg; isVisibleToUser = true })
    }

    private object NoOpLogger : AgentLogger {
        override fun debug(message: () -> String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }
}
