import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowDisplayManager;
import static org.junit.Assert.*;

/** Resource rollback and handoff acknowledgement regressions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class VDriveLifecycleTest {
    private Worker worker;

    static class Worker extends VDriveWorker {
        boolean windowReady;
        int readinessCalls;
        boolean failDetach;
        JSONObject windowProbe;
        boolean realHandover;
        int taskDisplay = 42, moves;
        Worker(Context context) throws Exception {
            super(context);
            work.removeCallbacksAndMessages(null);
        }
        @Override String focusedComponent() { return ""; }
        @Override DisplayGeometry geometry(int display) {
            return new DisplayGeometry(1080, 2376, 0, display, 420);
        }
        @Override JSONObject handover(JSONObject request) throws Exception {
            if (realHandover) return super.handover(request);
            return new JSONObject().put("ok", true).put("moved", true)
                .put("taskId", 71).put("toDisplay", 0);
        }
        @Override boolean foregroundWindowReady(String pkg, int display) {
            readinessCalls++;
            return windowProbe == null ? windowReady : super.foregroundWindowReady(pkg, display);
        }
        @Override JSONObject proxyA11y(JSONObject request, int timeout) { return windowProbe; }
        @Override int displayOfTask(int task) { return taskDisplay; }
        @Override boolean waitForTaskDisplay(int task, int display, int attempts) { return taskDisplay == display; }
        @Override void moveTask(int task, int display) { moves++; taskDisplay = display; }
        @Override void tickSession(Session session, long now) { }
        @Override void sleepCancellable(long ms) { }
        @Override void detachCaptureSurface(Session session) {
            if (failDetach) throw new IllegalStateException("audit: detach rejected");
            super.detachCaptureSurface(session);
        }
    }

    @Implements(DisplayManager.class)
    public static class RejectDisplayCreation extends ShadowDisplayManager {
        @Implementation
        protected VirtualDisplay createVirtualDisplay(String name, int width, int height,
                int density, Surface surface, int flags, VirtualDisplay.Callback callback, Handler handler) {
            throw new IllegalStateException("audit: virtual display creation rejected");
        }
    }

    @After public void cleanUpFixture() {
        if (worker == null) return;
        for (VDriveWorker.Frame frame : worker.frames.values()) frame.close();
        worker.frames.clear();
        if (worker.reader != null) worker.reader.close();
        worker.dm.unregisterDisplayListener(worker.geometryListener);
        worker.work.removeCallbacksAndMessages(null);
        worker.work.getLooper().quit();
    }

    @Test @Config(shadows = RejectDisplayCreation.class)
    public void failedDisplayCreationMustReleasePreparedCapture() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        try {
            worker.create(new JSONObject().put("width", 32).put("height", 64).put("density", 160));
            fail("Fault injection did not run");
        } catch (IllegalStateException expected) {
            assertEquals("audit: virtual display creation rejected", expected.getMessage());
        }
        assertNull("Failed creation left an ImageReader attached to the worker", worker.reader);
    }

    @Test public void unreadyFrontWindowMustNotBeReportedAsCompletedHandoff() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        VDriveWorker.Session session = worker.new Session("audit.target");
        session.orphanAt = 1;
        session.displayId = 42;
        session.taskId = 71;
        worker.sessions.put(session.pkg, session);
        worker.load(session);
        JSONObject result = worker.presentCompleted(new JSONObject());
        assertFalse(result.getBoolean("windowReady"));
        assertFalse("Moved task without a ready front window still returned ok=true", result.getBoolean("ok"));
    }

    @Test public void normalFinishReleasesCaptureWhilePreservingSessionIdentity() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        VDriveWorker.Session session = worker.new Session("audit.target");
        session.displayId = 42;
        session.taskId = 71;
        session.reader = ImageReader.newInstance(32, 64, PixelFormat.RGBA_8888, 3);
        worker.sessions.put(session.pkg, session);
        worker.load(session);
        VDriveWorker.Frame frame = worker.new Frame();
        Bitmap bitmap = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888);
        frame.bmp = bitmap;
        worker.frames.put(42, frame);
        JSONObject result = worker.finish(new JSONObject().put("mode", "notify"));
        assertTrue(result.getBoolean("ok"));
        assertNull(worker.reader);
        assertNull(session.reader);
        assertTrue(bitmap.isRecycled());
        assertTrue(worker.frames.isEmpty());
        assertSame(session, worker.sessions.get("audit.target"));
    }

    @Test public void successfulHandoffRequiresTwoReadyWindowObservations() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        worker.windowReady = true;
        VDriveWorker.Session session = worker.new Session("audit.target");
        session.orphanAt = 1;
        session.displayId = 42;
        session.taskId = 71;
        worker.sessions.put(session.pkg, session);
        worker.load(session);
        JSONObject result = worker.presentCompleted(new JSONObject());
        assertTrue(result.getBoolean("ok"));
        assertTrue(result.getBoolean("windowReady"));
        assertEquals(2, worker.readinessCalls);
    }

    @Test public void failedDetachKeepsCaptureUsableAndDoesNotAcknowledgeParking() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        VDriveWorker.Session session = worker.new Session("audit.target");
        session.displayId = 42;
        session.reader = ImageReader.newInstance(32, 64, PixelFormat.RGBA_8888, 3);
        worker.sessions.put(session.pkg, session);
        worker.load(session);
        worker.failDetach = true;
        try { worker.finish(new JSONObject().put("mode", "notify")); fail("detach must fail"); }
        catch (IllegalStateException expected) { assertEquals("audit: detach rejected", expected.getMessage()); }
        assertNotNull(worker.reader);
        assertSame(worker.reader, session.reader);
        assertEquals(0, session.orphanAt);
        assertFalse(worker.isParked());
        worker.failDetach = false;
        assertTrue(worker.finish(new JSONObject().put("mode", "notify")).getBoolean("parked"));
        assertNull(worker.reader);
    }

    @Test public void focusedWindowAlsoNeedsVisibleApplicationBoundsOnTheTargetDisplay() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        JSONObject window = new JSONObject().put("package", "audit.target").put("type", 1)
                .put("focused", true).put("visible", true).put("bounds", new org.json.JSONArray("[0,0,1080,2300]"));
        worker.windowProbe = new JSONObject().put("ok", true).put("displayId", 0)
                .put("windows", new org.json.JSONArray().put(window));
        assertTrue(worker.foregroundWindowReady("audit.target", 0));
        window.put("visible", false);
        assertFalse(worker.foregroundWindowReady("audit.target", 0));
        window.put("visible", true).put("bounds", new org.json.JSONArray("[0,0,0,0]"));
        assertFalse(worker.foregroundWindowReady("audit.target", 0));
        window.put("bounds", new org.json.JSONArray("[2000,0,3000,2300]"));
        assertFalse(worker.foregroundWindowReady("audit.target", 0));
        window.put("bounds", new org.json.JSONArray("[0,0,1080,2300]")).put("type", 3);
        assertFalse(worker.foregroundWindowReady("audit.target", 0));
        window.put("type", 1);
        worker.windowProbe.put("displayId", 42);
        assertFalse(worker.foregroundWindowReady("audit.target", 0));
    }

    @Test public void repeatingHandoffObservesMovedTaskWithoutAnotherMove() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        worker.realHandover = true;
        VDriveWorker.Session session = worker.new Session("audit.target");
        session.orphanAt = 1; session.displayId = 42; session.taskId = 71;
        worker.sessions.put(session.pkg, session);
        worker.load(session);
        JSONObject request = new JSONObject().put("package", session.pkg).put("toDisplay", 0);
        assertTrue(worker.handover(request).getBoolean("ok"));
        assertEquals(1, worker.moves);
        assertEquals(71, session.taskId);
        assertTrue(worker.handover(request).getBoolean("ok"));
        assertEquals(1, worker.moves);
    }
}
