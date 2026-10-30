import android.app.Application;
import android.content.Context;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class VDriveCommandDeadlineTest {
    private Worker worker;
    static class Worker extends VDriveWorker {
        final CountDownLatch entered = new CountDownLatch(1), unblock = new CountDownLatch(1);
        final AtomicInteger inputs = new AtomicInteger();
        Worker(Context context) throws Exception { super(context); work.removeCallbacksAndMessages(null); }
        @Override long commandTimeoutMs() { return 100; }
        @Override JSONObject handle(JSONObject request) throws Exception {
            String command = request.optString("cmd");
            if (command.equals("hold")) {
                entered.countDown();
                if (!unblock.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test failed to release worker");
            }
            if (command.equals("tap")) inputs.incrementAndGet();
            return new JSONObject().put("ok", true);
        }
    }

    @After public void cleanup() {
        if (worker == null) return;
        worker.unblock.countDown();
        worker.dm.unregisterDisplayListener(worker.geometryListener);
        worker.work.removeCallbacksAndMessages(null);
        worker.work.getLooper().quitSafely();
    }

    private void awaitDrained() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (worker.commandState().equals("draining") && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals("needs_observation", worker.commandState());
    }

    @Test public void timedOutOperationBlocksQueueAndRequiresFreshObservationAfterExit() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        JSONObject timedOut = worker.processLine("{\"cmd\":\"hold\"}");
        assertTrue(worker.entered.await(1, TimeUnit.SECONDS));
        assertEquals("TIMEOUT", timedOut.getString("code"));
        for (int i = 0; i < 20; i++) {
            assertEquals("COMMAND_DRAINING", worker.processLine("{\"cmd\":\"tap\"}").getString("code"));
            worker.keepAlive();
        }
        assertEquals("COMMAND_DRAINING", worker.observationGuard(true).getString("code"));
        worker.unblock.countDown();
        awaitDrained();
        assertEquals(0, worker.inputs.get());
        assertEquals("OBSERVE_AFTER_TIMEOUT", worker.processLine("{\"cmd\":\"tap\"}").getString("code"));
        assertEquals("OBSERVE_AFTER_TIMEOUT", worker.observationGuard(true).getString("code"));
        assertNull(worker.observationGuard(false));
        assertTrue(worker.processLine("{\"cmd\":\"shot\"}").getBoolean("ok"));
        assertNull(worker.observationGuard(true));
        assertTrue(worker.processLine("{\"cmd\":\"tap\"}").getBoolean("ok"));
        assertEquals(1, worker.inputs.get());
    }

    @Test public void cancellationAcknowledgesOnlyCleanupAfterBlockedOperationExits() throws Exception {
        worker = new Worker(RuntimeEnvironment.getApplication());
        assertEquals("TIMEOUT", worker.processLine("{\"cmd\":\"hold\"}").getString("code"));
        assertTrue(worker.entered.await(1, TimeUnit.SECONDS));
        assertFalse(worker.closeSession());
        assertEquals("SESSION_CLOSED", worker.processLine("{\"cmd\":\"tap\"}").getString("code"));
        worker.unblock.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!worker.closeSession() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(worker.closeSession());
        assertEquals(0, worker.inputs.get());
    }
}
