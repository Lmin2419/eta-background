package com.eta.shadow;

import android.accessibilityservice.AccessibilityService;
import android.app.Application;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class BackgroundScrollTest {
    public static class Service extends AccessibilityService {
        public void onAccessibilityEvent(AccessibilityEvent event) { }
        public void onInterrupt() { }
    }
    private static final AtomicInteger displays = new AtomicInteger(20);
    ServiceController<Service> service;
    int display;
    @Before public void setup() {
        display = displays.incrementAndGet();
        service = Robolectric.buildService(Service.class).create();
        ReflectionHelpers.setStaticField(Boot.class, "service", service.get());
    }
    @After public void teardown() {
        ReflectionHelpers.setStaticField(Boot.class, "service", null); service.destroy();
    }
    AccessibilityNodeInfo node(String id, String text, Rect bounds, boolean scroll) {
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
        node.setPackageName("example.chat"); node.setClassName(scroll ? "androidx.recyclerview.widget.RecyclerView" : "android.widget.TextView");
        node.setViewIdResourceName("example.chat:id/" + id); node.setText(text);
        node.setBoundsInScreen(bounds); node.setVisibleToUser(true); node.setEnabled(true); node.setScrollable(scroll);
        if (scroll) { node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); }
        return node;
    }
    AccessibilityWindowInfo window(AccessibilityNodeInfo root, int type) {
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        shadowOf(window).setRoot(root); shadowOf(window).setType(type);
        return window;
    }
    void show(AccessibilityNodeInfo root) { shadowOf(service.get()).setWindowsOnDisplay(display,
            Collections.singletonList(window(root, AccessibilityWindowInfo.TYPE_APPLICATION))); }
    JSONObject scroll(JSONObject request) throws Exception {
        request.put("displayId", display).put("direction", "up");
        return BackgroundScroll.execute(request, () -> service.get().getWindowsOnAllDisplays().get(display));
    }
    @Test public void multipleNestedContainersRequireTargetInsteadOfPickingLargest() throws Exception {
        AccessibilityNodeInfo outer = node("outer", "", new Rect(0, 0, 400, 800), true);
        AccessibilityNodeInfo list = node("messages", "", new Rect(0, 100, 400, 700), true);
        shadowOf(outer).addChild(list); show(outer);
        JSONObject result = scroll(new JSONObject());
        assertEquals("AMBIGUOUS_SCROLL_TARGET", result.getString("code"));
        assertEquals(2, result.getJSONArray("candidates").length());
        assertFalse(result.getBoolean("operationMayHaveRun"));
    }
    @Test public void movedTextBoundsVerifyMovementEvenWhenTextIsUnchanged() throws Exception {
        AccessibilityNodeInfo list = node("messages", "", new Rect(0, 0, 400, 800), true);
        AccessibilityNodeInfo message = node("row", "same message", new Rect(10, 300, 300, 350), false);
        shadowOf(list).addChild(message); show(list);
        shadowOf(list).setOnPerformActionListener((action, args) -> { message.setBoundsInScreen(new Rect(10, 400, 300, 450)); return true; });
        JSONObject result = scroll(new JSONObject().put("viewId", "messages"));
        assertTrue(result.getBoolean("moved")); assertEquals("CHANGED", result.getString("outcome"));
    }
    @Test public void outsideContainerChangesDoNotProveTheMessageListMoved() throws Exception {
        AccessibilityNodeInfo outer = node("outer", "", new Rect(0, 0, 400, 800), false);
        AccessibilityNodeInfo header = node("header", "old title", new Rect(0, 0, 400, 80), false);
        AccessibilityNodeInfo list = node("messages", "", new Rect(0, 100, 400, 700), true);
        shadowOf(list).addChild(node("row", "latest message", new Rect(10, 500, 300, 550), false));
        shadowOf(outer).addChild(header); shadowOf(outer).addChild(list); show(outer);
        shadowOf(list).setOnPerformActionListener((action, args) -> { header.setText("new title"); return true; });
        JSONObject result = scroll(new JSONObject().put("viewId", "messages"));
        assertTrue(result.getBoolean("accepted")); assertFalse(result.getBoolean("moved"));
        assertEquals("NO_OBSERVED_CHANGE", result.getString("outcome"));
    }
    @Test public void fourthIdenticalIneffectiveScrollIsNotSentToApp() throws Exception {
        AccessibilityNodeInfo list = node("messages", "unchanged", new Rect(0, 0, 400, 800), true); show(list);
        AtomicInteger calls = new AtomicInteger();
        shadowOf(list).setOnPerformActionListener((action, args) -> { calls.incrementAndGet(); return true; });
        for (int i = 0; i < 3; i++) assertEquals("NO_OBSERVED_CHANGE", scroll(new JSONObject()).getString("outcome"));
        JSONObject result = scroll(new JSONObject());
        assertEquals("NO_PROGRESS", result.getString("code")); assertEquals(3, calls.get());
        list.setText("history arrived");
        scroll(new JSONObject()); assertEquals(4, calls.get());
    }
    @Test public void pointSelectsNestedListAndNeverFallsBackToOuterOnRejection() throws Exception {
        AccessibilityNodeInfo outer = node("outer", "", new Rect(0, 0, 400, 800), true);
        AccessibilityNodeInfo list = node("messages", "", new Rect(0, 100, 400, 700), true);
        shadowOf(outer).addChild(list); show(outer);
        AtomicInteger calls = new AtomicInteger();
        shadowOf(outer).setOnPerformActionListener((action, args) -> { throw new AssertionError("wrong outer container"); });
        shadowOf(list).setOnPerformActionListener((action, args) -> { calls.incrementAndGet(); return false; });
        JSONObject result = scroll(new JSONObject().put("x", 100).put("y", 500));
        assertEquals("ACTION_REJECTED", result.getString("code")); assertEquals(1, calls.get());
    }
    @Test public void directNodeClicksStopRepeatingButANewTaskCanTryAgain() throws Exception {
        AccessibilityNodeInfo button = node("button", "Copy", new Rect(0, 0, 400, 800), false);
        button.setClickable(true); show(button);
        AtomicInteger calls = new AtomicInteger();
        shadowOf(button).setOnPerformActionListener((action, args) -> { calls.incrementAndGet(); return true; });
        Boot.RootQuery previous = Boot.rootQuery;
        JSONObject geometry = new JSONObject().put("displayId", display).put("width", 400).put("height", 800)
                .put("rotation", 0).put("density", 420).put("coordinateSpace", "display");
        try {
            Boot.rootQuery = req -> new JSONObject().put("ok", true).put("geometry", geometry);
            JSONObject request = new JSONObject().put("cmd", "click2").put("text", "Copy").put("displayId", display).put("sessionId", "first");
            for (int i = 0; i < 3; i++) assertTrue(call(request).getBoolean("accepted"));
            assertEquals("NO_PROGRESS", call(request).getString("code")); assertEquals(3, calls.get());
            assertTrue(call(request.put("sessionId", "second")).getBoolean("accepted")); assertEquals(4, calls.get());
        } finally { Boot.rootQuery = previous; }
    }
    private JSONObject call(JSONObject request) {
        return ReflectionHelpers.callStaticMethod(Boot.class, "handle", ReflectionHelpers.ClassParameter.from(JSONObject.class, request));
    }
    @Test public void appSnapshotIgnoresStatusBarChangesAndInvisibleText() throws Exception {
        AccessibilityNodeInfo app = node("root", "chat", new Rect(0, 0, 400, 800), false);
        AccessibilityNodeInfo hidden = node("hidden", "hidden old", new Rect(0, 100, 400, 200), false); hidden.setVisibleToUser(false);
        shadowOf(app).addChild(hidden);
        AccessibilityNodeInfo clock = node("clock", "12:00", new Rect(0, 0, 400, 50), false);
        List<AccessibilityWindowInfo> windows = Arrays.asList(window(app, AccessibilityWindowInfo.TYPE_APPLICATION), window(clock, AccessibilityWindowInfo.TYPE_SYSTEM));
        JSONObject before = BackgroundObservation.scene(windows, new JSONObject());
        clock.setText("12:01"); hidden.setText("hidden new");
        JSONObject after = BackgroundObservation.scene(windows, new JSONObject());
        assertEquals(before.getString("fingerprint"), after.getString("fingerprint"));
    }
    @Test public void visibleCanvasDoesNotNeedTextNodesOrGlobalFocusToBeReady() throws Exception {
        AccessibilityNodeInfo canvas = node("canvas", "", new Rect(0, 0, 400, 800), false);
        canvas.setClassName("android.view.SurfaceView"); show(canvas);
        JSONObject request = new JSONObject().put("displayId", display).put("expectedPackage", "example.chat");
        JSONObject state = ReflectionHelpers.callStaticMethod(Boot.class, "inputState", ReflectionHelpers.ClassParameter.from(JSONObject.class, request));
        assertTrue(state.getBoolean("ready")); assertFalse(state.getJSONArray("windows").getJSONObject(0).getBoolean("focused"));
        request.put("displayId", display + 100);
        JSONObject absent = ReflectionHelpers.callStaticMethod(Boot.class, "inputState", ReflectionHelpers.ClassParameter.from(JSONObject.class, request));
        assertFalse(absent.getBoolean("ready"));
    }
}
