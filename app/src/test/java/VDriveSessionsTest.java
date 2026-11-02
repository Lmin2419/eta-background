import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class VDriveSessionsTest {
    private final Map<String, FakeWorker> made = new LinkedHashMap<>();
    private final List<String> ids = new ArrayList<>();
    private VDrive.WorkerFactory original;
    private java.util.function.LongSupplier originalClock;
    private static class FakeWorker implements VDrive.Worker {
        JSONObject lastRequest;
        volatile boolean acknowledgeClose = true;
        boolean parked, capturing, movedToMain;
        boolean failPresent;
        boolean handingOver, needsObservation, draining, takenOver;
        int closeCalls, reconcileCalls;
        Runnable releaseListener = () -> { };
        final CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        public JSONObject processLine(String request) {
            try {
            JSONObject json = new JSONObject(request);
            lastRequest = json;
            if (json.optString("cmd").equals("launch")) { parked = false; capturing = true; }
            if (json.optString("cmd").equals("finish")) { parked = true; capturing = false; }
            if (json.optString("cmd").equals("preview_input")) needsObservation = true;
            if (json.optString("cmd").equals("present")) {
                parked = true;
                if (failPresent) return new JSONObject().put("ok", false).put("code", "HANDOVER_FAILED");
                movedToMain = true;
                releaseListener.run();
            }
            if (json.optString("cmd").equals("hold")) {
                entered.countDown();
                try { finish.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            return new JSONObject().put("ok", true).put("displayId", 12).put("parked", parked);
            } catch (org.json.JSONException e) { throw new AssertionError(e); }
        }
        public boolean closeSession() {
            closeCalls++;
            finish.countDown();
            if (acknowledgeClose) { capturing = false; releaseListener.run(); }
            return acknowledgeClose;
        }
        public boolean isParked() { return parked; }
        public boolean isCapturing() { return capturing; }
        public boolean isHandingOver() { return handingOver; }
        public boolean isTakenOver() { return takenOver; }
        public JSONObject foregroundGuard() {
            if (movedToMain && !parked) { takenOver = true; handingOver = true; }
            if (!takenOver) return null;
            try { return new JSONObject().put("ok", false).put("code", "APP_TAKEN_OVER"); }
            catch (Exception error) { throw new AssertionError(error); }
        }
        public String commandState() { return draining ? "draining" : needsObservation ? "needs_observation" : "idle"; }
        public JSONObject observationGuard(boolean mutating) {
            if (!draining && !(mutating && needsObservation)) return null;
            try { return new JSONObject().put("ok", false).put("code", draining ? "COMMAND_DRAINING" : "OBSERVE_AFTER_TIMEOUT"); }
            catch (Exception error) { throw new AssertionError(error); }
        }
        public void observationAccepted() { needsObservation = false; }
        public boolean claimParked() { if (!parked) return false; parked = false; return true; }
        public void setReleaseListener(Runnable listener) { releaseListener = listener; }
        public void reconcileForeground() {
            reconcileCalls++;
            if (movedToMain) { capturing = false; releaseListener.run(); }
        }
        public void keepAlive() { }
        public int displayId() { return 12; }
        public JSONObject geometrySnapshot() throws Exception {
            return new JSONObject().put("displayId", 12).put("width", 1080).put("height", 2376)
                    .put("rotation", 0).put("density", 420).put("coordinateSpace", "display");
        }
    }
    @Before public void setUp() {
        original = VDrive.factory;
        originalClock = VDrive.clock;
        VDrive.factory = context -> {
            FakeWorker worker = new FakeWorker();
            made.put(ids.get(ids.size() - 1), worker);
            return worker;
        };
    }
    @After public void tearDown() throws Exception {
        for (FakeWorker worker : made.values()) worker.acknowledgeClose = true;
        for (String id : ids) send(id, "release_session");
        VDrive.factory = original;
        VDrive.clock = originalClock;
    }
    private String open(String app) throws Exception {
        String id = UUID.randomUUID().toString(); ids.add(id);
        assertTrue(VDrive.processLine(new JSONObject().put("cmd", "launch").put("sessionId", id)
                .put("component", app).toString()).getBoolean("ok"));
        return id;
    }
    private JSONObject send(String id, String cmd) throws Exception {
        return VDrive.processLine(new JSONObject().put("cmd", cmd).put("sessionId", id).toString());
    }
    private JSONObject keep(String id) throws Exception {
        return VDrive.processLine(new JSONObject().put("cmd", "release_session").put("sessionId", id).put("keep", true).toString());
    }
    private void park(String id) throws Exception {
        assertTrue(send(id, "finish").getBoolean("ok"));
        assertTrue(keep(id).getBoolean("ok"));
    }
    private int count() throws Exception { return VDrive.processLine("{\"cmd\":\"status\"}").getInt("count"); }

    @Test public void geometryValidationDoesNotQueueBehindAWorkerWaitingForObserver() throws Exception {
        String id = open("app.private"); FakeWorker worker = made.get(id);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<JSONObject> holding = executor.submit(() -> send(id, "hold"));
        try {
            assertTrue(worker.entered.await(1, TimeUnit.SECONDS));
            JSONObject result = VDrive.processLine(new JSONObject().put("cmd", "validate")
                    .put("sessionId", id).put("displayId", 12).toString());
            assertTrue(result.getBoolean("ok"));
            assertEquals(1080, result.getJSONObject("geometry").getInt("width"));
            assertFalse(holding.isDone());
        } finally { worker.finish.countDown(); holding.get(2, TimeUnit.SECONDS); executor.shutdownNow(); }
    }

    @Test public void activeForegroundHintRetainsTakeoverReasonAfterCleanupWithoutStoppingOtherTask() throws Exception {
        String a = open("app.active"), b = open("app.other");
        assertTrue(send(a, "foreground_hint").getBoolean("ok"));
        made.get(a).movedToMain = true;
        assertEquals("APP_TAKEN_OVER", send(a, "foreground_hint").getString("code"));
        assertEquals("APP_TAKEN_OVER", send(a, "keepalive").getString("code"));
        assertTrue(send(a, "status").getBoolean("sessionTakenOver"));
        assertEquals("APP_TAKEN_OVER", VDrive.processLine(new JSONObject().put("cmd", "launch")
                .put("sessionId", a).put("component", "app.active").toString()).getString("code"));
        assertTrue(send(b, "tap").getBoolean("ok"));
        assertEquals(2, made.size());
        assertTrue(send(a, "release_session").getBoolean("ok"));
    }

    @Test public void directNodeValidationDetectsActiveTaskMovement() throws Exception {
        String id = open("app.nodes");
        made.get(id).movedToMain = true;
        JSONObject request = new JSONObject().put("cmd", "validate").put("sessionId", id)
                .put("displayId", 12).put("action", "click2");
        assertEquals("APP_TAKEN_OVER", VDrive.processLine(request.toString()).getString("code"));
        assertEquals("launch", made.get(id).lastRequest.getString("cmd"));
    }

    @Test public void nextOwnerReusesParkedAppAndLateOldCleanupCannotCloseIt() throws Exception {
        String old = open("app.same");
        FakeWorker worker = made.get(old);
        park(old);
        assertFalse(worker.capturing);
        String other = open("app.other");
        String next = open("app.same");
        assertEquals(2, made.size());
        assertEquals(0, worker.closeCalls);
        assertTrue(worker.capturing);
        assertEquals(next, worker.lastRequest.getString("sessionId"));
        assertTrue(send(old, "release_session").getBoolean("ok"));
        assertTrue(keep(old).getBoolean("ok"));
        assertEquals(0, worker.closeCalls);
        assertEquals(2, count());
        assertEquals("SESSION_CLOSED", send(old, "tap").getString("code"));
        assertTrue(send(next, "tap").getBoolean("ok"));
        assertTrue(send(other, "tap").getBoolean("ok"));
        assertEquals(0, made.get(other).closeCalls);
    }

    @Test public void foregroundHintsReconcileActiveAndCompletedSessionsWithoutAssumingMovement() throws Exception {
        String id = open("app.front");
        FakeWorker worker = made.get(id);
        send(id, "foreground_hint");
        assertEquals(1, worker.reconcileCalls);
        assertFalse(worker.takenOver);
        assertEquals(1, count());
        park(id);
        send(id, "foreground_hint");
        assertEquals(2, worker.reconcileCalls);
        assertEquals(1, count()); // A hint alone is not evidence of a completed handoff.
        worker.movedToMain = true;
        send(id, "foreground_hint");
        assertEquals(0, count()); // No 10-second reaper delay.
        String next = open("app.front");
        worker.releaseListener.run(); // Delayed callback from an old worker cannot remove its successor.
        assertEquals(1, count());
        assertTrue(send(next, "tap").getBoolean("ok"));
    }

    @Test public void completedPageSurvivesTimeExpiryAndCannotUndoClosing() throws Exception {
        java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong(1000000);
        VDrive.clock = time::get;
        String id = open("app.expiry");
        park(id);
        time.addAndGet(59000);
        assertTrue(keep(id).getBoolean("ok"));
        time.addAndGet(2000);
        VDrive.reapExpired();
        assertEquals(1, count());
        time.addAndGet(TimeUnit.HOURS.toMillis(1));
        VDrive.reapExpired();
        assertEquals(1, count());
        assertEquals(0, made.get(id).closeCalls);
        send(id, "release_session");
        String closing = open("app.closing");
        FakeWorker worker = made.get(closing);
        worker.acknowledgeClose = false;
        assertEquals("CLEANUP_PENDING", send(closing, "release_session").getString("code"));
        assertEquals("CLEANUP_PENDING", keep(closing).getString("code"));
        assertEquals(1, count());
    }

    @Test public void retainedPagesStillRespectTwoScreensAndYieldOnlyWhenCapacityIsNeeded() throws Exception {
        String oldest = open("app.retained"); park(oldest);
        String active = open("app.active");
        String next = open("app.new");
        assertEquals(2, count());
        assertEquals(1, made.get(oldest).closeCalls);
        assertEquals(0, made.get(active).closeCalls);
        assertTrue(send(next, "tap").getBoolean("ok"));
    }

    @Test public void explicitTapUsesOriginalOwnerAndFailedHandoffKeepsThePage() throws Exception {
        String id = "game-run:child:" + UUID.randomUUID(); ids.add(id);
        assertTrue(VDrive.processLine(new JSONObject().put("cmd", "launch").put("sessionId", id)
                .put("component", "app.game").toString()).getBoolean("ok"));
        String request = new JSONObject().put("cmd", "present_completed").put("runId", "game-run:child").put("package", "app.game").toString();
        assertEquals("APP_BUSY", VDrive.processLine(request).getString("code"));
        park(id);
        FakeWorker worker = made.get(id);
        assertEquals("APP_BUSY", VDrive.processLine(request.replace("game-run:child", "game-run")).getString("code"));
        worker.failPresent = true;
        assertEquals("HANDOVER_FAILED", VDrive.processLine(request).getString("code"));
        assertEquals(1, count());
        assertEquals(0, worker.closeCalls);
        assertEquals("APP_BUSY", VDrive.processLine(request.replace("game-run", "stale-run")).getString("code"));
        worker.failPresent = false;
        assertTrue(VDrive.processLine(request).getBoolean("ok"));
        assertEquals("present", worker.lastRequest.getString("cmd"));
        assertTrue(worker.movedToMain);
        assertEquals(0, count());
        assertEquals("NOT_FOUND", VDrive.processLine(request).getString("code"));
    }

    @Test public void unfinishedWorkCannotBeParkedOrReassigned() throws Exception {
        String id = open("app.active");
        assertEquals("NOT_PARKED", keep(id).getString("code"));
        assertTrue(send(id, "tap").getBoolean("ok"));
    }

    @Test public void inFlightRequestMustFinishBeforeOwnershipCanBeReturned() throws Exception {
        String id = open("app.busy");
        assertTrue(send(id, "finish").getBoolean("ok"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<JSONObject> held = executor.submit(() -> send(id, "hold"));
            assertTrue(made.get(id).entered.await(1, TimeUnit.SECONDS));
            assertEquals("CLEANUP_PENDING", keep(id).getString("code"));
            made.get(id).finish.countDown();
            assertTrue(held.get(1, TimeUnit.SECONDS).getBoolean("ok"));
            assertTrue(keep(id).getBoolean("ok"));
        } finally { executor.shutdownNow(); }
    }

    @Test public void lateLaunchCannotResurrectReleasedSession() throws Exception {
        String id = UUID.randomUUID().toString(); ids.add(id);
        assertTrue(send(id, "release_session").getBoolean("ok"));
        assertEquals("SESSION_CLOSED", VDrive.processLine(new JSONObject().put("cmd", "launch")
                .put("sessionId", id).put("component", "app.late").toString()).getString("code"));
        assertTrue(made.isEmpty());
    }
    @Test public void failedCleanupKeepsSlotAndRefusesFurtherActions() throws Exception {
        String first = open("app.one"); open("app.two");
        made.get(first).acknowledgeClose = false;
        assertEquals("CLEANUP_PENDING", send(first, "release_session").getString("code"));
        assertEquals("SESSION_CLOSED", send(first, "tap").getString("code"));
        String fourth = UUID.randomUUID().toString(); ids.add(fourth);
        JSONObject request = new JSONObject().put("cmd", "launch").put("sessionId", fourth).put("component", "app.four");
        assertEquals("CLEANUP_PENDING", VDrive.processLine(request.toString()).getString("code"));
        made.get(first).acknowledgeClose = true;
        assertTrue(send(first, "release_session").getBoolean("ok"));
        assertTrue(VDrive.processLine(request.toString()).getBoolean("ok"));
    }
    @Test public void blockedWorkerDoesNotBlockAnotherScreen() throws Exception {
        String a = open("app.a"), b = open("app.b");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JSONObject> held = executor.submit(() -> send(a, "hold"));
            assertTrue(made.get(a).entered.await(1, TimeUnit.SECONDS));
            Future<JSONObject> other = executor.submit(() -> send(b, "tap"));
            assertTrue(other.get(1, TimeUnit.SECONDS).getBoolean("ok"));
            assertFalse(held.isDone());
            made.get(a).finish.countDown();
            assertTrue(held.get(1, TimeUnit.SECONDS).getBoolean("ok"));
        } finally { executor.shutdownNow(); }
    }
    @Test public void sameAppCannotBeOwnedByTwoSessions() throws Exception {
        open("app.shared");
        String id = UUID.randomUUID().toString(); ids.add(id);
        assertEquals("APP_BUSY", VDrive.processLine(new JSONObject().put("cmd", "launch")
                .put("sessionId", id).put("component", "app.shared").toString()).getString("code"));
    }

    @Test public void backgroundFinishCannotBringAnAppOntoTheMainScreen() throws Exception {
        String id = open("app.background");
        for (String mode : new String[]{"handover", "background"}) {
            assertTrue(VDrive.processLine(new JSONObject().put("cmd", "finish").put("sessionId", id)
                    .put("mode", mode).toString()).getBoolean("ok"));
            assertEquals("notify", made.get(id).lastRequest.getString("mode"));
        }
        assertEquals("BACKGROUND_ONLY", send(id, "handover").getString("code"));
        assertEquals("finish", made.get(id).lastRequest.getString("cmd"));
    }

    @Test public void pendingHandoffCannotBeAdoptedOrEvictedByAnotherRun() throws Exception {
        String first = open("app.first"); park(first);
        FakeWorker worker = made.get(first); worker.handingOver = true;
        open("app.second");
        String next = UUID.randomUUID().toString(); ids.add(next);
        JSONObject request = new JSONObject().put("cmd", "launch").put("sessionId", next).put("component", "app.third");
        assertEquals("CLEANUP_PENDING", VDrive.processLine(request.toString()).getString("code"));
        assertEquals(0, worker.closeCalls);
        request.put("component", "app.first");
        assertEquals("APP_BUSY", VDrive.processLine(request.toString()).getString("code"));
        assertEquals(0, worker.closeCalls);
        worker.handingOver = false;
        request.put("component", "app.third");
        assertTrue(VDrive.processLine(request.toString()).getBoolean("ok"));
        assertEquals(1, worker.closeCalls);
    }

    @Test public void nodeObservationUsesSameTimeoutGateAndAcknowledgesOnlyMatchingCompletedRead() throws Exception {
        String id = open("app.nodes");
        FakeWorker worker = made.get(id); worker.needsObservation = true;
        JSONObject request = new JSONObject().put("cmd", "validate").put("sessionId", id)
                .put("displayId", 12).put("action", "click2");
        assertEquals("OBSERVE_AFTER_TIMEOUT", VDrive.processLine(request.toString()).getString("code"));
        request.put("action", "list");
        assertTrue(VDrive.processLine(request.toString()).getBoolean("ok"));
        assertTrue(worker.needsObservation);
        request.put("observed", true).put("expectedGeometry", worker.geometrySnapshot().put("rotation", 1));
        VDrive.processLine(request.toString());
        assertTrue(worker.needsObservation);
        request.put("expectedGeometry", worker.geometrySnapshot());
        assertTrue(VDrive.processLine(request.toString()).getBoolean("ok"));
        assertFalse(worker.needsObservation);
        worker.draining = true;
        assertEquals("COMMAND_DRAINING", VDrive.processLine(request.toString()).getString("code"));
    }
    private String previewOpen(String run, String app) throws Exception {
        String id = run + ":" + UUID.randomUUID(); ids.add(id);
        assertTrue(VDrive.processLine(new JSONObject().put("cmd", "launch").put("sessionId", id)
                .put("component", app).toString()).getBoolean("ok"));
        return id;
    }
    private JSONObject preview(String run, String app, String cmd, String owner) throws Exception {
        return VDrive.processLine(new JSONObject().put("cmd", cmd).put("runId", run).put("package", app)
                .put("owner", owner).toString());
    }
    @Test public void previewCannotReadAnotherRunsScreenOrClaimAParkedPage() throws Exception {
        String id = previewOpen("preview-a", "app.preview"); park(id);
        assertEquals("PREVIEW_UNAVAILABLE", preview("other", "app.preview", "preview_frame", "owner-0123456789abcdef").getString("code"));
        assertTrue(preview("preview-a", "app.preview", "preview_frame", "owner-0123456789abcdef").getBoolean("ok"));
        assertTrue(made.get(id).parked);
        assertEquals("SESSION_CLOSED", send(id, "tap").getString("code"));
        assertEquals(1, count());
    }
    @Test public void manualLeaseBlocksAiAndHandoffButNotOtherTasksThenExpires() throws Exception {
        String id = previewOpen("preview-a", "app.preview");
        String other = open("app.other");
        long now = VDrive.clock.getAsLong();
        VDrive.clock = () -> now;
        assertTrue(preview("preview-a", "app.preview", "preview_acquire", "owner-0123456789abcdef").getBoolean("ok"));
        assertEquals("MANUAL_CONTROL", send(id, "tap").getString("code"));
        assertTrue(send(other, "tap").getBoolean("ok"));
        assertEquals("MANUAL_LEASE_EXPIRED", preview("preview-a", "app.preview", "preview_input", "other-0123456789abcdef").getString("code"));
        assertTrue(preview("preview-a", "app.preview", "preview_frame", "owner-0123456789abcdef").getBoolean("ok"));
        assertTrue(preview("preview-a", "app.preview", "preview_input", "owner-0123456789abcdef").getBoolean("ok"));
        VDrive.clock = () -> now + 10001;
        assertEquals("MANUAL_LEASE_EXPIRED", preview("preview-a", "app.preview", "preview_input", "owner-0123456789abcdef").getString("code"));
        assertTrue(send(id, "tap").getBoolean("ok"));
    }
    @Test public void uncertainWorkerCannotBeTakenOverEvenWithNoActiveRpc() throws Exception {
        String id = previewOpen("preview-drain", "app.preview");
        made.get(id).draining = true;
        assertEquals("INPUT_DRAIN_PENDING", preview("preview-drain", "app.preview", "preview_acquire", "owner-0123456789abcdef").getString("code"));
        made.get(id).draining = false;
        JSONObject req = new JSONObject().put("cmd", "preview_acquire").put("runId", "preview-drain")
                .put("package", "app.preview").put("owner", "owner-0123456789abcdef").put("requireParked", true);
        assertEquals("TASK_NOT_SETTLED", VDrive.processLine(req.toString()).getString("code"));
        park(id);
        assertTrue(VDrive.processLine(req.toString()).getBoolean("ok"));
        assertEquals("APP_BUSY", VDrive.processLine(new JSONObject().put("cmd", "present_completed")
                .put("runId", "preview-drain").put("package", "app.preview").toString()).getString("code"));
        assertTrue(preview("preview-drain", "app.preview", "preview_release", "owner-0123456789abcdef").getBoolean("ok"));
        assertTrue(made.get(id).parked);
    }

    @Test public void completedPageCanBeControlledRepeatedlyWithoutClearingTheAiObservationGuard() throws Exception {
        String id = previewOpen("completed-manual", "app.completed");
        park(id);
        FakeWorker worker = made.get(id);
        worker.needsObservation = true; // Drained, but the AI still owes a new observation.
        String owner = "owner-0123456789abcdef";
        assertTrue(preview("completed-manual", "app.completed", "preview_acquire", owner).getBoolean("ok"));
        for (int i = 0; i < 3; i++) {
            assertEquals("PREVIEW_OBSERVATION_REQUIRED", preview("completed-manual", "app.completed", "preview_input", owner).getString("code"));
            assertTrue(preview("completed-manual", "app.completed", "preview_frame", owner).getBoolean("ok"));
            assertTrue(preview("completed-manual", "app.completed", "preview_input", owner).getBoolean("ok"));
            assertTrue(worker.needsObservation);
        }
        assertTrue(preview("completed-manual", "app.completed", "preview_release", owner).getBoolean("ok"));
        assertTrue(worker.parked);
        assertTrue(worker.needsObservation);
    }

    @Test public void closingAReadonlyWindowCannotReleaseAnotherWindowsManualCapture() throws Exception {
        previewOpen("two-windows", "app.preview");
        String owner = "owner-0123456789abcdef";
        assertTrue(preview("two-windows", "app.preview", "preview_acquire", owner).getBoolean("ok"));
        assertEquals("MANUAL_CONTROL", preview("two-windows", "app.preview", "preview_release", "other-0123456789abcdef").getString("code"));
        assertTrue(preview("two-windows", "app.preview", "preview_release", owner).getBoolean("ok"));
    }

}
