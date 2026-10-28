package com.eta.shadow;

import android.accessibilityservice.AccessibilityService;
import android.app.Application;
import android.hardware.display.DisplayManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class BootGeometryTest {
    public static class Service extends AccessibilityService {
        public void onAccessibilityEvent(AccessibilityEvent event) { }
        public void onInterrupt() { }
    }
    ServiceController<Service> controller;
    Boot.RootQuery original;
    JSONObject geometry;
    @Before public void setup() throws Exception {
        controller = Robolectric.buildService(Service.class).create();
        ReflectionHelpers.setStaticField(Boot.class, "service", controller.get());
        original = Boot.rootQuery;
        geometry = new JSONObject().put("displayId", 8).put("width", 1080).put("height", 2376)
            .put("rotation", 0).put("density", 420).put("coordinateSpace", "display").put("source", "root-display-manager");
        Boot.rootQuery = request -> new JSONObject().put("ok", true).put("geometry", geometry);
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain(); root.setVisibleToUser(true); root.setEnabled(true);
        root.setBoundsInScreen(new android.graphics.Rect(0, 0, 1080, 2376));
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain(); shadowOf(window).setRoot(root);
        shadowOf(window).setBoundsInScreen(new android.graphics.Rect(0, 0, 1080, 2376));
        shadowOf(controller.get()).setWindowsOnDisplay(8, Collections.singletonList(window));
        assertNull(controller.get().getSystemService(DisplayManager.class).getDisplay(8));
    }
    @After public void teardown() {
        Boot.rootQuery = original; ReflectionHelpers.setStaticField(Boot.class, "service", null); controller.destroy();
    }
    JSONObject call(JSONObject request) {
        return ReflectionHelpers.callStaticMethod(Boot.class, "handle", ReflectionHelpers.ClassParameter.from(JSONObject.class, request));
    }
    JSONObject request(String cmd) throws Exception {
        return new JSONObject().put("cmd", cmd).put("displayId", 8).put("sessionId", "task:private").put("expectedGeometry", geometry);
    }
    @Test public void privateDisplayTreeReturnsRootGeometryInsteadOfAnEmptyObject() throws Exception {
        JSONObject result = call(request("dump"));
        assertTrue(result.getBoolean("ok")); assertEquals(1, result.getInt("count"));
        assertEquals(1080, result.getJSONObject("geometry").getInt("width"));
    }
    @Test public void missingControlsHaveTheirOwnErrorsOnAnInvisiblePrivateDisplay() throws Exception {
        assertEquals("NO_EDITABLE", call(request("settext").put("text", "test")).getString("code"));
        assertEquals("NO_NODES", call(request("click2").put("text", "不存在")).getString("code"));
        assertEquals("NO_NODE", call(request("tapnode").put("x", 50).put("y", 50)).getString("code"));
    }
    @Test public void rootCallbackUsesSnapshotWithoutRecursivelyQueryingTheWorker() throws Exception {
        Boot.rootQuery = request -> { throw new AssertionError("Root callback must not query its blocked worker"); };
        JSONObject request = request("tapnode"); request.remove("sessionId"); request.put("engineGeometry", geometry).put("x", 1).put("y", 1);
        assertEquals("NO_NODE", call(request).getString("code"));
    }
    @Test public void actualMissingGeometryIsNotReportedAsRotation() throws Exception {
        Boot.rootQuery = request -> new JSONObject().put("ok", true).put("geometry", new JSONObject().put("coordinateSpace", "display"));
        JSONObject result = call(request("settext"));
        assertEquals("GEOMETRY_UNAVAILABLE", result.getString("code"));
        assertEquals("ACTUAL_GEOMETRY_MISSING", result.getString("reason"));
    }
    @Test public void realRotationDuringAnActionIsStillRejectedWithBothSnapshots() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Boot.rootQuery = request -> new JSONObject().put("ok", true).put("geometry", calls.incrementAndGet() == 1 ? geometry :
            new JSONObject(geometry.toString()).put("width", 2376).put("height", 1080).put("rotation", 1));
        JSONObject result = call(request("settext"));
        assertEquals("GEOMETRY_CHANGED_DURING_OPERATION", result.getString("code"));
        assertTrue(result.getBoolean("operationMayHaveRun"));
        assertEquals(1, result.getJSONObject("actual").getInt("rotation"));
    }
}
