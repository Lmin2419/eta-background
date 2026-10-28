package com.eta.shadow;

import android.accessibilityservice.AccessibilityService;
import android.app.Application;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class BootNodeTargetTest {
    public static class Service extends AccessibilityService {
        public void onAccessibilityEvent(AccessibilityEvent event) { }
        public void onInterrupt() { }
    }
    ServiceController<Service> controller;
    final List<String> actions = new ArrayList<>();
    @Before public void setup() {
        controller = Robolectric.buildService(Service.class).create();
        ReflectionHelpers.setStaticField(Boot.class, "service", controller.get());
    }
    @After public void teardown() { ReflectionHelpers.setStaticField(Boot.class, "service", null); controller.destroy(); }
    AccessibilityNodeInfo node(String name, Rect bounds) {
        AccessibilityNodeInfo n = AccessibilityNodeInfo.obtain();
        n.setText(name); n.setBoundsInScreen(bounds); n.setVisibleToUser(true); n.setEnabled(true); n.setClickable(true);
        shadowOf(n).setOnPerformActionListener((action, args) -> {
            actions.add(name + ":" + action); n.setText(name + " clicked"); return true;
        });
        return n;
    }
    void window(AccessibilityNodeInfo root) {
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        shadowOf(window).setRoot(root);
        shadowOf(controller.get()).setWindowsOnDisplay(7, Collections.singletonList(window));
    }
    JSONObject tap(long hold) throws Exception {
        return ReflectionHelpers.callStaticMethod(Boot.class, "tapNode", ReflectionHelpers.ClassParameter.from(JSONObject.class,
            new JSONObject().put("displayId", 7).put("x", 90).put("y", 90).put("holdMs", hold)));
    }
    @Test public void choosesSmallButtonInsteadOfItsClickableContainer() throws Exception {
        AccessibilityNodeInfo root = node("container", new Rect(0, 0, 400, 800));
        shadowOf(root).addChild(node("button", new Rect(70, 70, 110, 110)));
        window(root);
        assertTrue(tap(0).getBoolean("ok"));
        assertEquals(Collections.singletonList("button:" + AccessibilityNodeInfo.ACTION_CLICK), actions);
    }
    @Test public void surfaceViewRequiresRealCoordinateInput() throws Exception {
        AccessibilityNodeInfo root = node("game", new Rect(0, 0, 400, 800));
        root.setClassName("android.view.SurfaceView"); window(root);
        assertEquals("NO_NODE", tap(0).getString("code")); assertTrue(actions.isEmpty());
    }
    @Test public void longPressDoesNotSilentlyBecomeAnOrdinaryNodeClick() throws Exception {
        window(node("button", new Rect(70, 70, 110, 110)));
        assertEquals("NO_NODE", tap(800).getString("code")); assertTrue(actions.isEmpty());
    }
}
