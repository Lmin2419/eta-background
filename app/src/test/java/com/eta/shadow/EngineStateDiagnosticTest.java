package com.eta.shadow;

import android.app.Application;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class EngineStateDiagnosticTest {
    private final Map<String, Object> saved = new HashMap<>();
    @Before public void setUp() {
        for (String field : new String[]{"lastReason", "connectionReason", "nextAttemptAt"})
            saved.put(field, ReflectionHelpers.getStaticField(Engine.class, field));
        ReflectionHelpers.setStaticField(Engine.class, "lastReason", null);
        ReflectionHelpers.setStaticField(Engine.class, "connectionReason", null);
        // Tests must not deploy an engine or request su on the build machine.
        ReflectionHelpers.setStaticField(Engine.class, "nextAttemptAt", Long.MAX_VALUE);
    }
    @After public void tearDown() {
        saved.forEach((key, value) -> ReflectionHelpers.setStaticField(Engine.class, key, value));
    }
    private JSONObject state(String status) throws Exception {
        ShadowTools tools = new ShadowTools("diagnostic-test", (port, payload, timeout) -> {
            if (status == null) throw new java.io.IOException("offline");
            return status;
        });
        return new JSONObject((String) ReflectionHelpers.callInstanceMethod(tools, "state"));
    }
    @Test public void connectionLossRetainsItsCauseAfterListenerCloses() throws Exception {
        JSONObject lost = state("{\"ok\":false,\"healthy\":false,\"code\":\"SYSTEM_RESTARTED\",\"pid\":31176}");
        assertEquals("SYSTEM_RESTARTED", lost.getJSONObject("recovery").getString("code"));
        assertEquals(31176, lost.getJSONObject("engine").getInt("pid"));
        assertTrue(lost.getString("fix").contains("不需要重启手机"));
        assertEquals("SYSTEM_RESTARTED", state(null).getJSONObject("recovery").getString("code"));
    }
    @Test public void specificDeploymentFailureIsReportedAlongsideInvalidEngine() throws Exception {
        ReflectionHelpers.setStaticField(Engine.class, "lastReason", "PORT_BUSY");
        JSONObject result = state("{\"ok\":false,\"code\":\"SYSTEM_RESTARTED\"}");
        assertEquals("SYSTEM_RESTARTED", result.getJSONObject("engine").getString("code"));
        assertEquals("PORT_BUSY", result.getJSONObject("recovery").getString("lastDeploymentCode"));
        assertTrue(result.getJSONObject("recovery").getLong("retryAfterMs") > 0);
        assertTrue(result.getString("fix").contains("8733"));
        assertFalse(result.getString("fix").contains("需要先重新获取 root"));
    }
    @Test public void missingConnectionDoesNotAssertRootDenial() throws Exception {
        JSONObject result = state(null);
        assertEquals("ENGINE_DOWN", result.getJSONObject("recovery").getString("code"));
        assertTrue(result.getString("fix").contains("无法判断 Root"));
        ReflectionHelpers.setStaticField(Engine.class, "lastReason", "NO_ROOT");
        assertEquals("NO_ROOT", state(null).getJSONObject("recovery").getString("code"));
        assertTrue(Engine.reasonHint().contains("没能拿到 root"));
    }
}
