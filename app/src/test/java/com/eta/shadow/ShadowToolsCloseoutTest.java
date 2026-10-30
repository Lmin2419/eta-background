package com.eta.shadow;

import android.app.Application;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37, application = Application.class)
public class ShadowToolsCloseoutTest {
    private ShadowTools open(ShadowTools.LineTransport transport) {
        ShadowTools tools = new ShadowTools("awaiting-user", transport);
        ReflectionHelpers.setField(tools, "mode", "hidden");
        ReflectionHelpers.setField(tools, "display", 42);
        ReflectionHelpers.setField(tools, "openedPackage", "app.game");
        return tools;
    }

    @Test public void modelReplyWithoutFinishParksTheGameBeforeReturningOwnership() throws Exception {
        List<JSONObject> requests = new ArrayList<>();
        ShadowTools tools = open((port, payload, timeout) -> {
            JSONObject req = new JSONObject(payload); requests.add(req);
            return req.getString("cmd").equals("finish") ? "{\"ok\":true,\"parked\":true}" : "{\"ok\":true}";
        });
        assertTrue(tools.closeSession(true));
        assertEquals(2, requests.size());
        assertEquals("finish", requests.get(0).getString("cmd"));
        assertEquals("notify", requests.get(0).getString("mode"));
        assertEquals("release_session", requests.get(1).getString("cmd"));
        assertTrue(requests.get(1).getBoolean("keep"));
        assertTrue(tools.closeSession(true));
        assertEquals(3, requests.size()); // Retry does not finish or destroy the page again.
    }

    @Test public void failedParkingRetriesWithoutSendingADestructiveRelease() throws Exception {
        List<JSONObject> requests = new ArrayList<>();
        AtomicBoolean ready = new AtomicBoolean();
        ShadowTools tools = open((port, payload, timeout) -> {
            JSONObject req = new JSONObject(payload); requests.add(req);
            if (!ready.get()) return "{\"ok\":false,\"code\":\"INTERNAL\"}";
            return "{\"ok\":true,\"parked\":true}";
        });
        assertFalse(tools.closeSession(true));
        assertEquals(1, requests.size());
        assertEquals("finish", requests.get(0).getString("cmd"));
        ready.set(true);
        assertTrue(tools.closeSession(true));
        assertTrue(requests.get(2).getBoolean("keep"));
    }

    @Test public void explicitCancellationStillReleasesInsteadOfRetainingTheGame() throws Exception {
        List<JSONObject> requests = new ArrayList<>();
        ShadowTools tools = open((port, payload, timeout) -> { requests.add(new JSONObject(payload)); return "{\"ok\":true}"; });
        assertTrue(tools.closeSession(false));
        assertEquals(1, requests.size());
        assertEquals("release_session", requests.get(0).getString("cmd"));
        assertFalse(requests.get(0).getBoolean("keep"));
    }

    @Test public void takeoverDuringHeartbeatPausesAndPreservesMovingTaskEvenWhenCancelled() throws Exception {
        List<JSONObject> requests = new ArrayList<>();
        ShadowTools tools = open((port, payload, timeout) -> {
            JSONObject req = new JSONObject(payload); requests.add(req);
            if (req.getString("cmd").equals("keepalive")) return "{\"ok\":false,\"code\":\"APP_TAKEN_OVER\"}";
            return "{\"ok\":true,\"parked\":true}";
        });
        AtomicBoolean notified = new AtomicBoolean();
        tools.setTakeoverListener(() -> notified.set(true));
        tools.keepAlive();
        assertTrue(notified.get());
        assertTrue(tools.closeSession(false));
        assertEquals("finish", requests.get(1).getString("cmd"));
        assertTrue(requests.get(2).getBoolean("keep"));
    }

    @Test public void alreadyReleasedTakeoverDoesNotRetryParkingForever() throws Exception {
        ShadowTools tools = open((port, payload, timeout) -> new JSONObject(payload).getString("cmd").equals("finish")
                ? "{\"ok\":false,\"code\":\"APP_TAKEN_OVER\"}" : "{\"ok\":true}");
        assertTrue(tools.closeSession(true));
    }
}
