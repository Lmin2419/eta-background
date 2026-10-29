import android.app.Application;
import android.content.Context;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class VDriveInputReadinessTest {
    // Robolectric's MotionEvent shadow omits native display-id storage. Observe the actual
    // setDisplayId invocation at that boundary, while retaining real event source/tool/timing tests.
    public static class DisplayRoute {
        static final List<Integer> displays = new ArrayList<>();
        public static void setDisplayId(int display) { displays.add(display); }
    }
    static class Worker extends VDriveWorker {
        final List<MotionEvent> events = new ArrayList<>();
        final List<String> launchRoutes = new ArrayList<>();
        int probes, readyAfter = 1, injectCalls;
        boolean rejectMove;
        Worker(Context context) throws Exception {
            super(context); work.removeCallbacksAndMessages(null); displayId = 7;
            motionSetDisplay = DisplayRoute.class.getMethod("setDisplayId", int.class);
        }
        @Override DisplayGeometry geometry(int did) { return new DisplayGeometry(1080, 2376, 0, did, 420); }
        @Override boolean hasTaskOnDisplay() { return true; }
        @Override void sleepCancellable(long ms) throws Exception { checkCanceled(); Thread.sleep(Math.min(ms, 10)); }
        @Override JSONObject proxyA11y(JSONObject request, int timeout) {
            try {
                if ("input_state".equals(request.optString("cmd"))) return new JSONObject().put("ok", true).put("ready", ++probes >= readyAfter);
                if ("snapshot".equals(request.optString("cmd"))) return new JSONObject().put("ok", true).put("observable", false);
                return new JSONObject().put("ok", false).put("code", "NO_NODE");
            } catch (Exception error) { throw new AssertionError(error); }
        }
        @Override JSONObject launch(JSONObject request) throws Exception {
            String route = request.optString("via"); launchRoutes.add(route);
            return new JSONObject().put("ok", true).put("displayId", 7).put("via", route);
        }
        @Override void injectEvent(InputEvent event) throws Exception {
            MotionEvent motion = (MotionEvent) event;
            if (rejectMove && motion.getActionMasked() == MotionEvent.ACTION_MOVE) throw new IllegalStateException("rejected");
            events.add(MotionEvent.obtain(motion)); injectCalls++;
        }
    }
    Worker worker;
    @Before public void setup() throws Exception { DisplayRoute.displays.clear(); worker = new Worker(RuntimeEnvironment.getApplication()); }
    @After public void teardown() {
        for (MotionEvent event : worker.events) event.recycle();
        worker.dm.unregisterDisplayListener(worker.geometryListener);
        worker.work.removeCallbacksAndMessages(null); worker.work.getLooper().quit();
    }
    @Test public void gestureCarriesTouchscreenFingerDisplayAndConsistentDownTime() throws Exception {
        worker.injectTouch(7, 100, 200, 200, 400, 0, 16);
        assertEquals(MotionEvent.ACTION_DOWN, worker.events.get(0).getActionMasked());
        MotionEvent last = worker.events.get(worker.events.size() - 1);
        assertEquals(MotionEvent.ACTION_UP, last.getActionMasked()); assertEquals(0f, last.getPressure(), 0);
        long down = worker.events.get(0).getDownTime();
        for (MotionEvent event : worker.events) {
            assertEquals(InputDevice.SOURCE_TOUCHSCREEN, event.getSource());
            assertEquals(MotionEvent.TOOL_TYPE_FINGER, event.getToolType(0));
            assertEquals(down, event.getDownTime());
        }
        assertEquals(worker.events.size(), DisplayRoute.displays.size());
        for (int display : DisplayRoute.displays) assertEquals(7, display);
    }
    @Test public void rejectedMoveStillReleasesAtLastAcceptedPosition() throws Exception {
        worker.rejectMove = true;
        assertThrows(IllegalStateException.class, () -> worker.injectTouch(7, 100, 200, 200, 400, 0, 16));
        assertEquals(2, worker.events.size());
        MotionEvent release = worker.events.get(1);
        assertEquals(MotionEvent.ACTION_UP, release.getActionMasked());
        assertEquals(100f, release.getX(), 0); assertEquals(200f, release.getY(), 0);
    }
    @Test public void adoptedAndColdLaunchBothWaitForTheWindowWithoutRecreatingAnything() throws Exception {
        for (String route : new String[]{"adopted", "am", "atms"}) {
            worker.probes = 0; worker.readyAfter = 2;
            JSONObject result = worker.handle(new JSONObject().put("cmd", "launch").put("component", "example.app").put("via", route));
            assertTrue(result.getBoolean("inputReady")); assertEquals(2, worker.probes);
            assertEquals("example.app", result.getJSONObject("inputState").getString("expectedPackage"));
        }
        assertEquals(3, worker.launchRoutes.size()); assertEquals(0, worker.injectCalls);
    }
    @Test public void invisibleWindowRejectsBeforeInjectingAnyGesture() throws Exception {
        worker.readyAfter = Integer.MAX_VALUE;
        JSONObject result = worker.handle(new JSONObject().put("cmd", "swipe")
                .put("x1", 100).put("y1", 300).put("x2", 100).put("y2", 100));
        assertEquals("INPUT_NOT_READY", result.getString("code"));
        assertFalse(result.getBoolean("operationMayHaveRun")); assertEquals(0, worker.injectCalls);
    }
    @Test public void slowLaunchKeepsTheAppAndReportsPendingWindowInsteadOfRelaunching() throws Exception {
        worker.readyAfter = Integer.MAX_VALUE;
        JSONObject result = worker.handle(new JSONObject().put("cmd", "launch").put("component", "example.game").put("via", "am"));
        assertTrue(result.getBoolean("ok")); assertFalse(result.getBoolean("inputReady"));
        assertEquals(1, worker.launchRoutes.size()); assertEquals(0, worker.injectCalls);
    }
    @Test public void canvasAcceptsGestureButReportsOutcomeUnknown() throws Exception {
        JSONObject result = worker.handle(new JSONObject().put("cmd", "swipe")
                .put("x1", 100).put("y1", 300).put("x2", 100).put("y2", 100).put("durationMs", 16));
        assertTrue(result.getBoolean("accepted")); assertFalse(result.getBoolean("verified"));
        assertEquals("UNKNOWN", result.getString("outcome")); assertTrue(worker.injectCalls > 0);
    }
}
