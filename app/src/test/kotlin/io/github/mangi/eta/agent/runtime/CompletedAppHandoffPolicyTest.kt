package io.github.mangi.eta.agent.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletedAppHandoffPolicyTest {
    @Test fun pendingWindowOverridesLegacySuccessAndNeverLaunchesAgain() {
        assertEquals(CompletedAppHandoff.Pending, completedAppHandoff(JSONObject("""{"ok":true,"moved":true,"windowReady":false}""")))
        assertEquals(CompletedAppHandoff.Pending, completedAppHandoff(JSONObject("""{"ok":false,"code":"HANDOFF_PENDING"}""")))
        assertEquals(CompletedAppHandoff.Unavailable, completedAppHandoff(JSONObject("""{"ok":false,"code":"NOT_FOUND","moved":true}""")))
    }
    @Test fun onlyMissingRetainedTaskAllowsLauncherFallback() {
        assertEquals(CompletedAppHandoff.Ready, completedAppHandoff(JSONObject("""{"ok":true,"windowReady":true}""")))
        assertEquals(CompletedAppHandoff.Launch, completedAppHandoff(JSONObject("""{"ok":false,"code":"NOT_FOUND"}""")))
        assertEquals(CompletedAppHandoff.Unavailable, completedAppHandoff(JSONObject("""{"ok":false,"code":"TIMEOUT"}""")))
        assertEquals(CompletedAppHandoff.Unavailable, completedAppHandoff(null))
    }
}
