package com.eta.shadow;

import android.app.Application;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class ShadowToolsGeometryTest {
    JSONObject geometry() throws Exception {
        return new JSONObject().put("displayId", 8).put("width", 1080).put("height", 2376)
            .put("rotation", 0).put("density", 420).put("coordinateSpace", "display");
    }
    ShadowTools tools(ShadowTools.LineTransport transport) {
        ShadowTools tools = new ShadowTools("task:8", transport);
        ReflectionHelpers.setField(tools, "mode", "hidden"); ReflectionHelpers.setField(tools, "display", 8);
        return tools;
    }
    JSONObject call(ShadowTools tools, String method, String command, JSONObject args) throws Exception {
        String result = ReflectionHelpers.callInstanceMethod(tools, method,
            ReflectionHelpers.ClassParameter.from(String.class, command), ReflectionHelpers.ClassParameter.from(JSONObject.class, args));
        return new JSONObject(result);
    }
    @Test public void emptyObserverGeometryCannotPoisonTheFullScreenshotGeometry() throws Exception {
        List<JSONObject> inputs = new ArrayList<>();
        ShadowTools tools = tools((port, payload, timeout) -> {
            JSONObject req = new JSONObject(payload);
            if (req.getString("cmd").equals("shot")) return new JSONObject().put("ok", true).put("dims", "540x1188").put("geometry", geometry()).toString();
            if (port == ShadowTools.OBSERVER_PORT) return "{\"ok\":true,\"geometry\":{\"coordinateSpace\":\"display\"}}";
            inputs.add(req); JSONObject invalid = ScreenGeometry.check(req.optJSONObject("expectedGeometry"), geometry());
            return invalid == null ? "{\"ok\":true}" : invalid.toString();
        });
        call(tools, "eng", "shot", new JSONObject()); call(tools, "obs", "dump", new JSONObject());
        assertTrue(call(tools, "eng", "tap", new JSONObject().put("x", 100).put("y", 100)).getBoolean("ok"));
        assertEquals(1080, inputs.get(0).getJSONObject("expectedGeometry").getInt("width"));
    }
    @Test public void bothAdoptedAndReusedOpenInitializeGeometry() throws Exception {
        for (String flag : new String[]{"adopted", "reused"}) {
            List<JSONObject> inputs = new ArrayList<>();
            ShadowTools tools = tools((port, payload, timeout) -> {
                JSONObject req = new JSONObject(payload);
                if (req.getString("cmd").equals("launch")) return new JSONObject().put("ok", true).put("mode", "hidden")
                    .put("displayId", 8).put(flag, true).put("geometry", geometry()).toString();
                if (port == ShadowTools.OBSERVER_PORT) return "{\"ok\":true,\"count\":1}";
                inputs.add(req); return "{\"ok\":true}";
            });
            ReflectionHelpers.callInstanceMethod(tools, "open", ReflectionHelpers.ClassParameter.from(JSONObject.class,
                new JSONObject().put("package", "app.private")));
            call(tools, "eng", "tap", new JSONObject().put("x", 10).put("y", 10));
            assertEquals(8, inputs.get(0).getJSONObject("expectedGeometry").getInt("displayId"));
        }
    }
    @Test public void stateReconcilesReleasedSessionAndRetainsOnlyGeometryDiagnostics() throws Exception {
        ShadowTools tools = tools((port, payload, timeout) -> {
            JSONObject req = new JSONObject(payload);
            if (req.optString("cmd").equals("status")) return "{\"ok\":true,\"sessions\":[]}";
            return ScreenGeometry.reject("STALE_OBSERVATION", "GEOMETRY_MISMATCH", geometry(),
                new JSONObject(geometry().toString()).put("rotation", 1)).put("text", "must not be retained").toString();
        });
        call(tools, "eng", "tap", new JSONObject().put("x", 1).put("y", 1));
        String raw = ReflectionHelpers.callInstanceMethod(tools, "state"); JSONObject state = new JSONObject(raw);
        assertFalse(state.getBoolean("sessionValid")); assertEquals(-1, state.getJSONObject("session").getInt("display"));
        assertEquals("GEOMETRY_MISMATCH", state.getJSONObject("lastInputRejection").getString("reason"));
        assertFalse(raw.contains("must not be retained"));
    }
    @Test public void missingObservationAndDensityDifferencesHaveDistinctMeaning() throws Exception {
        assertNull(ScreenGeometry.check(geometry(), new JSONObject(geometry().toString()).put("density", 640)));
        assertEquals("INVALID_OBSERVATION", ScreenGeometry.check(new JSONObject(), geometry()).getString("code"));
        assertEquals("GEOMETRY_UNAVAILABLE", ScreenGeometry.check(geometry(), new JSONObject()).getString("code"));
    }
}
