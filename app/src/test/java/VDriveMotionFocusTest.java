import android.app.Application;
import android.content.Context;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowMotionEvent;
import static org.junit.Assert.*;

/** Event construction only: real ColorOS IME/focus behavior still needs a device check. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class, shadows = VDriveMotionFocusTest.DisplayAwareMotionEvent.class)
public class VDriveMotionFocusTest {
    // Robolectric 4.17's MotionEvent shadow implements coordinates/flags but leaves
    // nativeSetDisplayId/getDisplayId as no-op/zero. Observe those framework calls
    // explicitly so a missing or incorrect production setDisplayId still fails here.
    @Implements(MotionEvent.class)
    public static class DisplayAwareMotionEvent extends ShadowMotionEvent {
        private int displayId;
        @Implementation protected void setDisplayId(int value) { displayId = value; }
        @Implementation protected int getDisplayId() { return displayId; }
    }

    static final class RecordedMotion {
        final int flags, action, displayId, source;
        final float x, y, pressure;
        RecordedMotion(MotionEvent event) throws Exception {
            flags = event.getFlags();
            action = event.getActionMasked();
            displayId = ((Number) MotionEvent.class.getMethod("getDisplayId").invoke(event)).intValue();
            source = event.getSource();
            x = event.getX();
            y = event.getY();
            pressure = event.getPressure();
        }
        int getFlags() { return flags; }
        int getActionMasked() { return action; }
        int getSource() { return source; }
        float getX() { return x; }
        float getY() { return y; }
        float getPressure() { return pressure; }
    }

    static class Worker extends VDriveWorker {
        final List<RecordedMotion> events = new ArrayList<>();
        boolean cancelAfterDown;

        Worker(Context context) throws Exception {
            super(context);
            work.removeCallbacksAndMessages(null);
            displayId = 7;
        }

        @Override void injectEvent(InputEvent event) throws Exception {
            // Read the actual event passed to injection, before the production finally
            // recycles it; MotionEvent.obtain(copy) does not copy shadow-only state.
            events.add(new RecordedMotion((MotionEvent) event));
        }

        @Override void checkCanceled() throws Exception {
            if (cancelAfterDown && !events.isEmpty()) throw new CanceledException();
            super.checkCanceled();
        }

        @Override void sleepCancellable(long ms) throws Exception {
            checkCanceled();
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(ms));
        }

        @Override boolean hasTaskOnDisplay() { return true; }
        @Override DisplayGeometry geometry(int did) {
            return new DisplayGeometry(1080, 2376, 0, did, 420);
        }
    }

    private Worker worker;

    @Before public void setUp() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
    }

    @After public void tearDown() {
        if (worker == null) return;
        worker.dm.unregisterDisplayListener(worker.geometryListener);
        worker.work.removeCallbacksAndMessages(null);
        worker.work.getLooper().quit();
    }

    private void assertBackgroundEvent(RecordedMotion event, int action) throws Exception {
        int platformFlag = MotionEvent.class.getField("FLAG_NO_FOCUS_CHANGE").getInt(null);
        assertNotEquals(0, event.getFlags() & platformFlag);
        assertEquals(action, event.getActionMasked());
        assertEquals(7, event.displayId);
        assertEquals(InputDevice.SOURCE_TOUCHSCREEN, event.getSource());
    }

    @Test @Config(sdk = 33)
    public void backgroundTapPreservesCoordinatesWithoutRequestingGlobalFocusOnMinimumAppSdk() throws Exception {
        worker.injectTouch(7, 123, 456, 123, 456, 0, 0);
        assertEquals(2, worker.events.size());
        assertBackgroundEvent(worker.events.get(0), MotionEvent.ACTION_DOWN);
        assertBackgroundEvent(worker.events.get(1), MotionEvent.ACTION_UP);
        for (RecordedMotion event : worker.events) {
            assertEquals(123, event.getX(), 0.01);
            assertEquals(456, event.getY(), 0.01);
        }
        assertEquals(1, worker.events.get(0).getPressure(), 0.01);
        assertEquals(0, worker.events.get(1).getPressure(), 0.01);
    }

    @Test public void backgroundSwipeKeepsFocusPolicyAcrossTheWholeGesture() throws Exception {
        worker.injectTouch(7, 100, 200, 300, 400, 0, 32);
        assertEquals(6, worker.events.size());
        assertBackgroundEvent(worker.events.get(0), MotionEvent.ACTION_DOWN);
        for (int i = 1; i < worker.events.size() - 1; i++) {
            assertBackgroundEvent(worker.events.get(i), MotionEvent.ACTION_MOVE);
        }
        RecordedMotion up = worker.events.get(worker.events.size() - 1);
        assertBackgroundEvent(up, MotionEvent.ACTION_UP);
        assertEquals(300, up.getX(), 0.01);
        assertEquals(400, up.getY(), 0.01);
    }

    @Test public void canceledGestureStillReleasesWithoutStealingFocus() throws Exception {
        worker.cancelAfterDown = true;
        try {
            worker.injectTouch(7, 100, 200, 300, 400, 0, 32);
            fail("Expected cancellation after DOWN");
        } catch (VDriveWorker.CanceledException expected) {
            assertEquals(2, worker.events.size());
            assertBackgroundEvent(worker.events.get(1), MotionEvent.ACTION_UP);
            assertEquals(100, worker.events.get(1).getX(), 0.01);
            assertEquals(200, worker.events.get(1).getY(), 0.01);
        }
    }

    @Test public void explicitCancelEventKeepsBackgroundFocusPolicy() throws Exception {
        worker.injectMotion(7, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 100, 200);
        assertEquals(1, worker.events.size());
        assertBackgroundEvent(worker.events.get(0), MotionEvent.ACTION_CANCEL);
        assertEquals(0, worker.events.get(0).getPressure(), 0.01);
    }

    @Test public void manualPreviewTapUsesTheSameNonStealingInputPath() throws Exception {
        JSONObject result = worker.manualInput("tap", new JSONObject()
                .put("displayId", 7).put("x", 180).put("y", 360));
        assertTrue(result.getBoolean("ok"));
        assertEquals("manual_touch", result.getString("via"));
        assertEquals(2, worker.events.size());
        assertBackgroundEvent(worker.events.get(0), MotionEvent.ACTION_DOWN);
        assertBackgroundEvent(worker.events.get(1), MotionEvent.ACTION_UP);
    }

    @Test public void foregroundTouchKeepsNormalFocusBehavior() throws Exception {
        worker.injectTouch(0, 100, 200, 100, 200, 0, 0);
        int platformFlag = MotionEvent.class.getField("FLAG_NO_FOCUS_CHANGE").getInt(null);
        for (RecordedMotion event : worker.events) {
            assertEquals(0, event.getFlags() & platformFlag);
            assertEquals(0, event.displayId);
        }
    }
}
