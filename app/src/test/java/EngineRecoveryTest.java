import android.app.Application;
import android.os.DeadObjectException;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class EngineRecoveryTest {
    private VDrive.WorkerFactory original;
    private java.util.function.BooleanSupplier health;
    private String credential;
    private EngineShutdown shutdown;
    private final List<String> sessions = new ArrayList<>();
    private static class Worker implements VDrive.Worker {
        volatile boolean closed;
        Runnable onRelease = () -> {};
        public JSONObject processLine(String request) { try { return new JSONObject().put("ok", true); } catch (Exception e) { throw new AssertionError(e); } }
        public boolean closeSession() { closed = true; onRelease.run(); return true; }
        public void keepAlive() {}
        public int displayId() { return 7; }
        public void setReleaseListener(Runnable callback) { onRelease = callback; }
    }
    @Before public void setUp() { original = VDrive.factory; health = VDrive.health; credential = VDrive.credential; shutdown = VDrive.shutdown; VDrive.systemInvalid = false; VDrive.health = () -> true; }
    @After public void tearDown() throws Exception {
        VDrive.systemInvalid = false; VDrive.health = health;
        for (String id : sessions) request(id, "release_session");
        VDrive.factory = original;
        VDrive.credential = credential;
        VDrive.shutdown = shutdown;
    }
    private String session() { String id = UUID.randomUUID().toString(); sessions.add(id); return id; }
    private JSONObject request(String id, String command) throws Exception {
        return VDrive.processLine(new JSONObject().put("sessionId", id).put("cmd", command).put("component", "app." + id.replace('-', '_')).toString());
    }
    @Test public void wrappedDeadBinderDuringCreationInvalidatesHealthWithoutAllocatingSession() throws Exception {
        VDrive.factory = context -> { throw new InvocationTargetException(new DeadObjectException()); };
        assertEquals("SYSTEM_RESTARTED", request(session(), "launch").getString("code"));
        JSONObject status = request("read", "status");
        assertFalse(status.getBoolean("ok")); assertFalse(status.getBoolean("healthy"));
        assertEquals(0, status.getInt("count"));
        assertEquals("SYSTEM_RESTARTED", request(session(), "launch").getString("code"));
    }
    @Test public void deadServiceIsDetectedWithZeroTasks() throws Exception {
        VDrive.health = () -> false;
        assertFalse(request("read", "ping").getBoolean("pong"));
        assertEquals("SYSTEM_RESTARTED", request(session(), "launch").getString("code"));
    }
    @Test public void lostSystemWithNoSessionsReleasesListenerAndRequestsOnlyOwnExit() throws Exception {
        List<Runnable> deadlines = new ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger exit = new java.util.concurrent.atomic.AtomicInteger(-1);
        VDrive.shutdown = new EngineShutdown((delay, action) -> deadlines.add(action), exit::set);
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            assertTrue(VDrive.shutdown.attach(socket));
            VDrive.health = () -> false;
            assertEquals("SYSTEM_RESTARTED", request("read", "status").getString("code"));
            assertTrue(socket.isClosed());
            assertEquals("SYSTEM_RESTARTED", request(session(), "launch").getString("code"));
            assertEquals(1, deadlines.size());
            assertEquals(-1, exit.get());
            deadlines.get(0).run();
            assertEquals(75, exit.get());
        }
    }
    @Test public void requestedExitCannotAcceptMoreWorkOnAnExistingConnection() throws Exception {
        VDrive.shutdown = new EngineShutdown((delay, action) -> {}, code -> {});
        VDrive.shutdown.request(0);
        assertEquals("ENGINE_STOPPING", request("read", "status").getString("code"));
        assertEquals("ENGINE_STOPPING", request(session(), "launch").getString("code"));
    }
    @Test public void blockedCreationDoesNotBlockStatusAndReleaseCancelsItsReservation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), proceed = new CountDownLatch(1);
        Worker worker = new Worker();
        VDrive.factory = context -> { entered.countDown(); if (!proceed.await(5, TimeUnit.SECONDS)) throw new AssertionError("stuck test"); return worker; };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        String id = session();
        try {
            Future<JSONObject> launch = pool.submit(() -> request(id, "launch"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            JSONObject status = pool.submit(() -> request("read", "status")).get(1, TimeUnit.SECONDS);
            assertEquals(1, status.getInt("count"));
            assertEquals("CLEANUP_PENDING", request(id, "release_session").getString("code"));
            proceed.countDown();
            assertEquals("SESSION_CLOSED", launch.get(2, TimeUnit.SECONDS).getString("code"));
            assertTrue(worker.closed); assertEquals(0, request("read", "status").getInt("count"));
        } finally { proceed.countDown(); pool.shutdownNow(); }
    }
    @Test public void failedCreationReturnsCapacityForOtherTasks() throws Exception {
        VDrive.factory = context -> { throw new IllegalArgumentException("fake constructor failure"); };
        assertEquals("ENGINE_ERROR", request(session(), "launch").getString("code"));
        VDrive.factory = context -> new Worker();
        assertTrue(request(session(), "launch").getBoolean("ok"));
        assertTrue(request(session(), "launch").getBoolean("ok"));
        assertEquals("SESSION_LIMIT", request(session(), "launch").getString("code"));
    }
    @Test public void socketRejectsUnauthenticatedMutationBeforeCreatingWorker() throws Exception {
        VDrive.credential = new String(new char[64]).replace('\0', 'a');
        VDrive.factory = context -> { throw new AssertionError("unauthenticated launch reached worker"); };
        java.lang.reflect.Method serve = VDrive.class.getDeclaredMethod("serve", java.net.Socket.class);
        serve.setAccessible(true);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            Future<?> serving = pool.submit(() -> {
                try { serve.invoke(null, server.accept()); } catch (Exception error) { throw new AssertionError(error); }
            });
            try (java.net.Socket client = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                client.setSoTimeout(2000);
                new java.io.PrintWriter(client.getOutputStream(), true).println("{\"cmd\":\"launch\",\"component\":\"app.test\"}");
                String response = new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream())).readLine();
                assertEquals("UNAUTHORIZED", new JSONObject(response.substring(7)).getString("code"));
            }
            serving.get(2, TimeUnit.SECONDS);
            assertEquals(0, request("read", "status").getInt("count"));
        } finally { pool.shutdownNow(); }
    }
}
