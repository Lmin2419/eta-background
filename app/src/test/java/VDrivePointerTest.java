import android.app.Application;
import android.content.Context;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class VDrivePointerTest {
    static class Worker extends VDriveWorker {
        JSONObject observed;
        boolean visualOnly;
        int touches;
        float touchedX, touchedY;
        Worker(Context c) throws Exception { super(c); work.removeCallbacksAndMessages(null); displayId = 7; }
        @Override DisplayGeometry geometry(int did) { return new DisplayGeometry(3200, 1440, 0, did, 420); }
        @Override boolean hasTaskOnDisplay() { return true; }
        @Override JSONObject proxyA11y(JSONObject req) {
            observed = req;
            try { return visualOnly ? new JSONObject().put("ok", false).put("code", "NO_NODE") : success(); }
            catch (Exception e) { throw new AssertionError(e); }
        }
        @Override void injectTouch(int did, float x1, float y1, float x2, float y2, long hold, int move) {
            touches++; touchedX = x1; touchedY = y1;
        }
        private JSONObject success() { try { return new JSONObject().put("ok", true); } catch(Exception e) { throw new AssertionError(e); } }
    }
    Worker worker;
    @Before public void setup() throws Exception { worker = new Worker(RuntimeEnvironment.getApplication()); }
    @After public void teardown() {
        if (worker != null) {
            worker.dm.unregisterDisplayListener(worker.geometryListener);
            worker.work.removeCallbacksAndMessages(null); worker.work.getLooper().quit();
        }
    }

    @Test public void normalTapNeverBecomesNodeLongClick() throws Exception {
        worker.tap(new JSONObject().put("x", 100).put("y", 100));
        assertEquals(0, worker.observed.getInt("holdMs"));
        worker.press(new JSONObject().put("x", 100).put("y", 100));
        assertEquals(800, worker.observed.getInt("holdMs"));
    }
    @Test public void visualOnlyPageUsesRealCoordinateInputWhenNoNodeExists() throws Exception {
        worker.visualOnly = true;
        JSONObject result = worker.handle(new JSONObject().put("cmd", "tap").put("x", 300).put("y", 500)
            .put("expectedGeometry", worker.geometry(7).json()));
        assertTrue(result.getBoolean("ok")); assertEquals("inject", result.getString("via"));
        assertEquals(1, worker.touches); assertEquals(300, worker.touchedX, 0.01); assertEquals(500, worker.touchedY, 0.01);
    }
    @Test public void screenshotCoordinatesMapToLogicalDisplayOnce() throws Exception {
        worker.lastScreenshot = new JSONObject().put("frameId", "7:frame").put("displayId", 7)
            .put("width", 1600).put("height", 720).put("geometry", worker.geometry(7).json());
        JSONObject request = new JSONObject().put("x", 1500).put("y", 650).put("coordinateSpace", "screenshot").put("frameId", "7:frame");
        assertNull(worker.preparePointerCoordinates(request));
        assertEquals(3000, request.getDouble("x"), 0.01);
        assertEquals(1300, request.getDouble("y"), 0.01);
    }
    @Test public void staleScreenshotAndRotatedNodesAreRejectedBeforeInjection() throws Exception {
        JSONObject request = new JSONObject().put("x", 100).put("y", 100).put("coordinateSpace", "screenshot").put("frameId", "old");
        assertEquals("STALE_OBSERVATION", worker.preparePointerCoordinates(request).getString("code"));
        request = new JSONObject().put("x", 100).put("y", 100).put("expectedGeometry", new DisplayGeometry(1440, 3200, 1).json());
        assertEquals("STALE_OBSERVATION", worker.preparePointerCoordinates(request).getString("code"));
        assertNull(worker.observed);
    }
    @Test public void unchangedStaticFrameIsUsableAfterSeventySeconds() throws Exception {
        worker.lastScreenshot = new JSONObject().put("frameId", "7:static").put("displayId", 7)
            .put("width", 1600).put("height", 720).put("geometry", worker.geometry(7).json())
            .put("capturedAt", android.os.SystemClock.elapsedRealtime() - 70_000);
        JSONObject request = new JSONObject().put("cmd", "tap").put("x", 100).put("y", 100)
            .put("coordinateSpace", "screenshot").put("frameId", "7:static");
        assertTrue(worker.handle(request).getBoolean("ok"));
        assertEquals(200, worker.observed.getInt("x"));
        assertNull(worker.lastScreenshot); // Explicitly consumed by the action, never by age.
    }
    @Test public void mismatchExplainsActualExpectedAndChangedField() throws Exception {
        JSONObject result = worker.preparePointerCoordinates(new JSONObject().put("x", 1).put("y", 1)
            .put("expectedGeometry", new DisplayGeometry(1440, 3200, 1, 7, 640).json()));
        assertEquals("GEOMETRY_MISMATCH", result.getString("reason"));
        assertEquals(3200, result.getJSONObject("actual").getInt("width"));
        assertEquals(1440, result.getJSONObject("expected").getInt("width"));
        assertFalse(result.getBoolean("frameAgeChecked"));
        assertTrue(result.getJSONArray("changedFields").toString().contains("rotation"));
    }
    @Test public void rotationDuringNodeActionNeverFallsThroughToASecondTouch() throws Exception {
        JSONObject ambiguous = new JSONObject().put("ok", false).put("code", "GEOMETRY_CHANGED_DURING_OPERATION");
        assertSame(ambiguous, worker.stalePointerFallback(new JSONObject(), ambiguous, 7));
        JSONObject request = new JSONObject().put("expectedGeometry", new DisplayGeometry(1440, 3200, 1).json());
        assertEquals("STALE_OBSERVATION", worker.stalePointerFallback(request, null, 7).getString("code"));
        assertNull(worker.stalePointerFallback(new JSONObject(), new JSONObject().put("code", "NO_NODE"), 7));
    }
}
