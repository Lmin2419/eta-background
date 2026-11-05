import android.app.Application;
import android.content.Context;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class VDriveObservationAdmissionTest {
    private Worker worker;
    private final ExecutorService caller = Executors.newSingleThreadExecutor();

    /** Holds the named command on the worker until released, like a slow capture or input. */
    static class Worker extends VDriveWorker {
        final CountDownLatch entered = new CountDownLatch(1), unblock = new CountDownLatch(1);
        Worker(Context context) throws Exception { super(context); work.removeCallbacksAndMessages(null); }
        @Override long commandTimeoutMs() { return 10_000; }
        @Override JSONObject handle(JSONObject request) throws Exception {
            entered.countDown();
            if (!unblock.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test failed to release worker");
            return new JSONObject().put("ok", true);
        }
    }

    @After public void cleanup() {
        caller.shutdownNow();
        if (worker == null) return;
        worker.unblock.countDown();
        worker.dm.unregisterDisplayListener(worker.geometryListener);
        worker.work.removeCallbacksAndMessages(null);
        worker.work.getLooper().quitSafely();
    }

    private Future<JSONObject> start(String command) throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        Future<JSONObject> running = caller.submit(() -> worker.processLine("{\"cmd\":\"" + command + "\"}"));
        assertTrue(worker.entered.await(2, TimeUnit.SECONDS));
        return running;
    }

    @Test public void nodeReadIsAdmittedWhileOnlyACaptureRuns() throws Exception {
        Future<JSONObject> shot = start("shot");
        assertNull("observe_screen reads nodes in parallel with its capture", worker.observationGuard(false));
        assertEquals("COMMAND_BUSY", worker.observationGuard(true).getString("code"));
        worker.unblock.countDown();
        assertTrue(shot.get(2, TimeUnit.SECONDS).getBoolean("ok"));
    }

    @Test public void nodeReadStillWaitsForAnInput() throws Exception {
        Future<JSONObject> tap = start("tap");
        assertEquals("COMMAND_BUSY", worker.observationGuard(false).getString("code"));
        assertEquals("COMMAND_BUSY", worker.observationGuard(true).getString("code"));
        worker.unblock.countDown();
        assertTrue(tap.get(2, TimeUnit.SECONDS).getBoolean("ok"));
    }

    @Test public void onlyTheScreenshotCommandIsReadOnly() {
        assertTrue(VDriveWorker.isReadOnlyCommand("{\"cmd\":\"shot\",\"scale\":0.5}"));
        assertFalse(VDriveWorker.isReadOnlyCommand("{\"cmd\":\"tap\"}"));
        assertFalse(VDriveWorker.isReadOnlyCommand("{\"cmd\":\"tree\",\"note\":\"shot\"}"));
        assertFalse(VDriveWorker.isReadOnlyCommand("not json"));
    }
}
