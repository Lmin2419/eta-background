package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.NotificationManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class RuntimeDiagnosticsTest {
    @Test fun reportIsBoundedAndRejectsFreeFormDiagnosticPayloads() {
        val context = RuntimeEnvironment.getApplication()
        repeat(150) { RuntimeDiagnostics.record("screen_cleanup", "retrying") }
        RuntimeDiagnostics.record("screen_phase", "secret-user-prompt")
        RuntimeDiagnostics.record("api-key", "ready")
        val report = RuntimeDiagnostics.snapshot(context) { JSONObject().put("reachable", false) }
        assertEquals(120, JSONObject(report).getJSONArray("events").length())
        assertFalse(report.contains("secret-user-prompt"))
        assertFalse(report.contains("api-key"))
    }

    @Test fun disabledNotificationsAreDetectedForTheFallbackAndSettings() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(AgentExecutionService.taskNotificationsEnabled(context))
        shadowOf(manager).setNotificationsEnabled(true)
        assertTrue(AgentExecutionService.taskNotificationsEnabled(context))
    }
}
