import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.json.JSONObject;
import com.eta.shadow.ScreenGeometry;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Headless virtual-display engine, uid 0 via app_process.
 * Line-delimited JSON over 127.0.0.1:8733 (or stdin).
 *
 * M1 changes vs first draft:
 *  - launch/handover talk to ActivityTaskManager directly (no `am` fork)
 *  - frame buffer + bitmap reused instead of reallocated per capture
 *  - virtual display auto-released when orphaned (after handover) or idle
 */
class VDriveWorker implements VDrive.Worker {
    final long T0 = SystemClock.elapsedRealtime();
    /** Build stamp: lets status() prove which dex is actually running. */
    final String BUILD = VDrive.BUILD;
    final long ORPHAN_EMPTY_MS = 20_000;   // task left the display -> release it
    final long IDLE_MS = 300_000;    // screen exists but no commands at all
    final long EMPTY_MS = 20_000;    // display held but no session and no task
    final long EXIT_MS = 0;           // 0 = resident: never exit on idle

    long emptySince = 0;   // when the current display first became empty (attached-display state)

    Context ctx;
    Handler work;
    DisplayManager dm;
    VirtualDisplay vd;
    volatile ImageReader reader;
    int W = 1440, H = 3168, DPI = 560;
    volatile int displayId = -1;

    volatile int frameErr, frameOk;

    /**
     * One capture slot per display.
     *
     * A display's ImageReader listener keeps firing after a newer display is created
     * (the old VirtualDisplay is still alive and owns its surface), so a single set of
     * frame fields let display A's pixels come back as display B's screenshot.
     *
     * No locking is needed: the listener and shot() both run on the `work` handler, so
     * they never overlap. Each frame carries the size it was actually produced at.
     */
    final class Frame {
        Image image;         // newest native buffer; copy pixels only when a screenshot is requested
        Bitmap bmp;          // reused bitmap for this display only
        DisplayGeometry geometry;
        long receivedAt;
        void close() {
            if (image != null) { image.close(); image = null; }
            if (bmp != null) { bmp.recycle(); bmp = null; }
        }
    }

    final java.util.HashMap<Integer, Frame> frames = new java.util.HashMap<>();
    JSONObject lastScreenshot;
    DisplayGeometry lastDisplayGeometry;
    final DisplayManager.DisplayListener geometryListener = new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int did) { }
        public void onDisplayRemoved(int did) { if (did == displayId) lastScreenshot = null; }
        public void onDisplayChanged(int did) {
            if (did != displayId || reader == null || orphanAt > 0) return;
            try {
                DisplayGeometry next = geometry(did);
                if (lastDisplayGeometry != null && next.matches(lastDisplayGeometry.json())) return;
                lastDisplayGeometry = next;
                lastScreenshot = null;
                Frame frame = frames.get(did);
                if (frame != null) frame.close();
                Image pending = reader.acquireLatestImage();
                if (pending != null) pending.close();
            } catch (Throwable unavailable) { log("geometry update: " + unavailable.getClass().getSimpleName()); }
        }
    };

    Object inputManager;
    Method inject;
    Method motionSetDisplay, keySetDisplay;
    Method optSetLaunchDisplay;
    Method atmGetService, atmAllRootTasks, atmMoveRootTask, atmRemoveTask;

    String launchedPkg = "";
    String homeFront = "";   // component that owned the main screen before we borrowed
    String protectedPkg = ""; // app parked on the hidden display, kept out of LMK
    int launchedTaskId = -1;
    int homeDisplay = 0;
    long taskCheckAt = 0;
    boolean taskCheckResult = false;
    long lastCmdAt = SystemClock.elapsedRealtime();
    volatile long orphanAt = 0;
    volatile boolean closed = false;
    String lastError = "";

    /** D2: command bookkeeping so a stuck injection can be interrupted. */
    final java.util.concurrent.atomic.AtomicLong SEQ =
            new java.util.concurrent.atomic.AtomicLong();
    final java.util.Set<Long> CANCELED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    final ThreadLocal<Long> CURRENT_CMD = new ThreadLocal<>();
    volatile long runningCmd = -1;
    final long COMMAND_TIMEOUT_MS = 30_000;

    /** Screenshots are confined here unless a caller explicitly opts out (P2). */
    final String SHOT_DIR = "/data/local/tmp/eta/shots/" + java.util.UUID.randomUUID();

    /**
     * Refuse to write outside SHOT_DIR. A model-supplied path used to be taken
     * verbatim, so a stray value could overwrite any file root can write.
     */
    JSONObject checkShotPath(JSONObject req) throws Exception {
        if (req.optBoolean("allowAnyPath", false)) return null;
        String p = req.optString("path", "");
        if (p.isEmpty()) return null;   // default path is already inside SHOT_DIR
        java.io.File root = new java.io.File(SHOT_DIR).getCanonicalFile();
        if (!root.exists() && !root.mkdirs()) return fail("PATH_NOT_ALLOWED", "cannot create " + root);
        java.io.File f = new java.io.File(p).getCanonicalFile();
        if (!f.getPath().startsWith(root.getPath() + java.io.File.separator))
            return fail("PATH_NOT_ALLOWED", "screenshots must live under " + root
                    + " (pass allowAnyPath=true only while debugging)");
        return null;
    }

    /** One parked app on its own virtual display. */
    final class Session {
        final String pkg;
        VirtualDisplay vd;
        ImageReader reader;
        int displayId = -1;
        int taskId = -1;
        long orphanAt = 0;
        long emptySince = 0;
        long handoffAt = 0;
        long createdAt = SystemClock.elapsedRealtime();
        long lastCmdAt = SystemClock.elapsedRealtime();
        String protectedPkg = "";
        String homeFront = "";
        Session(String p) { pkg = p; }
    }

    /** pkg -> session, in creation order (oldest first). */
    final java.util.LinkedHashMap<String, Session> sessions = new java.util.LinkedHashMap<>();
    final int MAX_SESSIONS = 1;
    Session current;

    void load(Session x) {
        current = x;
        vd = x.vd;
        reader = x.reader;
        displayId = x.displayId;
        launchedTaskId = x.taskId;
        orphanAt = x.orphanAt;
        emptySince = x.emptySince;
        protectedPkg = x.protectedPkg;
        homeFront = x.homeFront;
        launchedPkg = x.pkg;
        taskCheckAt = 0; taskCheckResult = false;
    }

    void save() {
        Session x = current;
        if (x == null) return;
        x.vd = vd;
        x.reader = reader;
        x.displayId = displayId;
        x.taskId = launchedTaskId;
        x.orphanAt = orphanAt;
        x.emptySince = emptySince;
        x.protectedPkg = protectedPkg;
        x.homeFront = homeFront;
        // x.lastCmdAt is stamped in handle(): it means "last command aimed at this
        // session", which is what eviction ordering must use. Overwriting it here with
        // the engine-wide lastCmdAt made every session look equally recently used.
    }

    void detach() {
        vd = null; reader = null; displayId = -1;
        launchedTaskId = -1; orphanAt = 0; emptySince = 0; protectedPkg = ""; homeFront = "";
        launchedPkg = "";
        current = null;
    }

    /**
     * Oldest session that is *parked*: finished (orphanAt set) or holding a display with
     * no task on it. A session that is still in use is never a candidate, so opening a
     * fourth app cannot kill one the caller is working in.
     */
    Session oldestParked(Session keep) {
        Session worst = null;
        for (Session x : sessions.values()) {
            if (x == keep) continue;
            if (x.orphanAt <= 0 && taskOnDisplay(x.displayId)) continue;
            if (worst == null || x.lastCmdAt < worst.lastCmdAt) worst = x;
        }
        return worst;
    }


    void log(String s) {
        System.err.println("[vdrive +" + (SystemClock.elapsedRealtime() - T0) + "ms] " + s);
        System.err.flush();
    }

    int flag(String name) {
        try {
            return DisplayManager.class.getField("VIRTUAL_DISPLAY_FLAG_" + name).getInt(null);
        } catch (Throwable t) { return 0; }
    }

    private HandlerThread workerThread;
    private volatile boolean retired;
    private volatile Runnable releaseListener = () -> { };
    private final java.util.concurrent.atomic.AtomicBoolean reconcilePending = new java.util.concurrent.atomic.AtomicBoolean();
    private boolean hadDisplay;
    private static final int ACTIVE = 0, PARKED = 1, CLAIMED = 2, RETIRED = 3;
    private final java.util.concurrent.atomic.AtomicInteger disposition = new java.util.concurrent.atomic.AtomicInteger(ACTIVE);
    private final Runnable patrol = new Runnable() {
        public void run() {
            if (retired) return;
            watchdog();
            if (!retired) work.postDelayed(this, orphanAt > 0 ? 5000 : 10000);
        }
    };
    private final java.util.concurrent.CountDownLatch released = new java.util.concurrent.CountDownLatch(1);
    private final java.util.concurrent.atomic.AtomicBoolean releaseRequested = new java.util.concurrent.atomic.AtomicBoolean();

    VDriveWorker(Context systemContext) throws Exception {
        ctx = systemContext;
        dm = ctx.getSystemService(DisplayManager.class);

        Class<?> imc = Class.forName("android.hardware.input.InputManager");
        Object imTmp;
        try {
            Method m = imc.getDeclaredMethod("getInstance");
            m.setAccessible(true);
            imTmp = m.invoke(null);
        } catch (Throwable t) {
            imTmp = ctx.getSystemService(Context.INPUT_SERVICE);
        }
        inputManager = imTmp;
        inject = imc.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
        inject.setAccessible(true);
        motionSetDisplay = MotionEvent.class.getDeclaredMethod("setDisplayId", int.class);
        motionSetDisplay.setAccessible(true);
        keySetDisplay = KeyEvent.class.getDeclaredMethod("setDisplayId", int.class);
        keySetDisplay.setAccessible(true);

        optSetLaunchDisplay = reflect(ActivityOptions.class, "setLaunchDisplayId", int.class);
        atmGetService = reflect(Class.forName("android.app.ActivityTaskManager"), "getService");
        Class<?> atmIface = Class.forName("android.app.IActivityTaskManager");
        atmAllRootTasks = reflect(atmIface, "getAllRootTaskInfos");
        atmMoveRootTask = reflect(atmIface, "moveRootTaskToDisplay", int.class, int.class);
        atmRemoveTask = reflect(atmIface, "removeTask", int.class);

        workerThread = new HandlerThread("vdrive-work");
        workerThread.start();
        work = new Handler(workerThread.getLooper());
        dm.registerDisplayListener(geometryListener, work);
        work.postDelayed(patrol, 2000);

    }

    public int displayId() { return displayId; }
    public JSONObject geometrySnapshot() throws Exception { return geometry(displayId).json(); }
    public boolean isParked() { return disposition.get() == PARKED && !retired && orphanAt > 0 && displayId > 0; }
    public boolean claimParked() { return disposition.compareAndSet(PARKED, CLAIMED); }
    public boolean isCapturing() { return !retired && reader != null; }
    public void setReleaseListener(Runnable listener) {
        releaseListener = listener;
        if (released.getCount() == 0) listener.run();
    }
    public void reconcileForeground() {
        if (!isParked() || !reconcilePending.compareAndSet(false, true)) return;
        if (!work.post(() -> {
            try { if (isParked()) watchdog(); }
            finally { reconcilePending.set(false); }
        })) reconcilePending.set(false);
    }

    public void keepAlive() {
        if (!retired) work.post(() -> {
            long now = android.os.SystemClock.elapsedRealtime();
            for (Session session : sessions.values()) session.lastCmdAt = now;
            lastCmdAt = now;
        });
    }

    public boolean closeSession() {
        disposition.set(RETIRED);
        retired = true;
        long command = runningCmd;
        if (command > 0) CANCELED.add(command);
        if (releaseRequested.compareAndSet(false, true)) {
            work.post(() -> {
                try {
                    shutdown();
                    work.removeCallbacks(patrol);
                    workerThread.quitSafely();
                    released.countDown();
                    try { releaseListener.run(); } catch (Throwable callbackError) { log("release callback: " + callbackError); }
                } catch (Throwable error) {
                    log("cleanup incomplete: " + error);
                    releaseRequested.set(false);
                }
            });
        }
        // Acknowledge only actual cleanup, but do not tie up a socket for 32 seconds.
        // The transport and app retry this idempotent request while the worker drains.
        return released.getCount() == 0;
    }

    /** Resident engine: ask the kernel to keep us alive under memory pressure. */
    void lowerOomScore() {
        try (java.io.FileOutputStream f = new java.io.FileOutputStream("/proc/self/oom_score_adj")) {
            f.write("-800".getBytes());
            log("oom_score_adj=-800");
        } catch (Throwable t) {
            log("oom_score_adj not set: " + t);
        }
    }

    Method reflect(Class<?> c, String name, Class<?>... types) {
        try {
            Method m = c.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    void watchdog() {
        try {
            long now = SystemClock.elapsedRealtime();
            for (Session x : new java.util.ArrayList<>(sessions.values())) {
                if (sessions.get(x.pkg) != x) continue;   // removed meanwhile
                try {
                    tickSession(x, now);
                } catch (Throwable t) {
                    log("tick(" + x.pkg + "): " + t);
                }
            }
            tickIdleDisplay(now);
            if (hadDisplay && sessions.isEmpty() && vd == null) { closeSession(); return; }
            if (EXIT_MS > 0 && sessions.isEmpty() && now - lastCmdAt > EXIT_MS) {
                log("idle " + EXIT_MS + "ms with no sessions, exiting");
                shutdown();
                workerThread.quitSafely();
            }
        } catch (Throwable t) {
            log("watchdog: " + t);
        }
    }

    /**
     * Per-session housekeeping. Reads the session record directly, so a patrol can no
     * longer move the "attached session" pointer or reset another session's idle clock.
     */
    void tickSession(Session x, long now) {
        if (disposition.get() == CLAIMED) return; // A new owner is about to resume this exact display.
        if (x.vd == null) { retireFromPatrol(x); return; }
        // One consistent ActivityTaskManager snapshot, instead of several queries and pidof forks per tick.
        List<?> tasks;
        try {
            if (atmGetService == null || atmAllRootTasks == null) return;
            tasks = (List<?>) atmAllRootTasks.invoke(atmGetService.invoke(null));
            if (tasks == null) return; // Unknown placement must never be treated as an empty screen.
        } catch (Throwable unavailable) { return; }
        boolean occupied = false;
        int movedTo = -1;
        for (Object task : tasks) {
            int at = readInt(task.getClass(), task, "displayId");
            int id = readInt(task.getClass(), task, "taskId");
            if (at == x.displayId && id > 0) occupied = true;
            if (id == x.taskId) movedTo = at;
        }
        if (!occupied && movedTo >= 0 && movedTo != x.displayId) {
            // ATM placement can precede WindowManager's new input window. Keep the empty
            // old display through that transition, without resizing or restarting the App.
            if (x.handoffAt > 0 && now - x.handoffAt < 5_000 && !foregroundWindowReady(x.pkg, movedTo)) return;
            // The actual tracked task left; its old display can go without touching its new window.
            retireFromPatrol(x);
            return;
        }
        if (x.orphanAt > 0) {
            long age = now - x.orphanAt;
            if (!occupied && age > ORPHAN_EMPTY_MS) retireFromPatrol(x);
            return;
        }
        if (!occupied) {
            if (x.emptySince == 0) {
                x.emptySince = now;
                if (x == current) emptySince = now;
            } else if (now - x.emptySince >= EMPTY_MS) retireFromPatrol(x);
            return;
        }
        x.emptySince = 0;
        if (x == current) emptySince = 0;
        if (now - x.lastCmdAt > IDLE_MS) retireFromPatrol(x);
    }

    private void retireFromPatrol(Session session) {
        int expected = disposition.get();
        if (expected == CLAIMED || expected == RETIRED || !disposition.compareAndSet(expected, RETIRED)) return;
        try { releaseSessionFully(session, false); }
        finally { closeSession(); }
    }

    /** A display that was created but never got a session (create -> launch failed). */
    void tickIdleDisplay(long now) {
        if (vd == null || current != null) return;
        if (!hasTaskOnDisplay()) {
            if (emptySince == 0) {
                emptySince = now;
            } else if (now - emptySince >= EMPTY_MS) {
                log("empty display " + displayId + " for " + EMPTY_MS + "ms, releasing");
                destroyIdleDisplay();
            }
        } else {
            emptySince = 0;
        }
    }

    void destroyIdleDisplay() {
        Frame frame = frames.get(displayId);
        if (frame != null) frame.close();
        try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
        if (vd != null) vd.release();
        if (displayId >= 0) {
            Frame fr = frames.remove(displayId);
            if (fr != null && fr.bmp != null) fr.bmp.recycle();
            if (fr != null) fr.bmp = null;
        }
        resetGlobals();
    }

    /**
     * The single place where a session gives its resources back. Manual destroy, idle
     * reclaim, eviction and engine shutdown all come through here, so they cannot
     * disagree about which fields still need clearing.
     */
    void releaseSessionFully(Session x, boolean returnTask) {
        if (x == null) return;
        if (!x.protectedPkg.isEmpty()) {
            try { protectApp(x.protectedPkg, false); } catch (Throwable ignored) { }
        }
        if (returnTask && x.vd != null) {
            try {
                Integer tid = x.taskId > 0 ? Integer.valueOf(x.taskId) : findRootTaskSafe(x.displayId);
                if (tid != null && tid > 0) {
                    moveTask(tid, homeDisplay);
                    log("returned task " + tid + " to display " + homeDisplay);
                    Thread.sleep(120);
                }
            } catch (Throwable t) {
                log("return task failed: " + t);
            }
        }
        stopCapture(x);
        if (x.vd != null) x.vd.release();
        int gone = x.displayId;
        x.reader = null;
        x.vd = null;
        x.displayId = -1;
        x.taskId = -1;
        sessions.remove(x.pkg);
        if (x == current) resetGlobals();
        log("session released: " + x.pkg + " display=" + gone + " (remaining " + sessions.size() + ")");
    }

    /** Clear every handle that describes "the session we are working with right now". */
    void resetGlobals() {
        current = null;
        vd = null; reader = null; displayId = -1;
        launchedTaskId = -1; launchedPkg = "";
        orphanAt = 0; emptySince = 0;
        protectedPkg = ""; homeFront = "";
        taskCheckAt = 0; taskCheckResult = false;
    }

    boolean taskOnDisplay(int did) {
        if (did < 0) return false;
        try {
            Integer t = findRootTask(did);
            return t != null && t > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    void startTcp(int port) throws Exception {
        final java.net.ServerSocket server = new java.net.ServerSocket();
        server.setReuseAddress(true);
        server.bind(new java.net.InetSocketAddress("127.0.0.1", port));
        log("tcp listening 127.0.0.1:" + port);
        Thread acceptor = new Thread(() -> {
            while (true) {
                try {
                    java.net.Socket s = server.accept();
                    Thread t = new Thread(() -> serve(s));
                    t.setDaemon(true);
                    t.start();
                } catch (Throwable e) {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) { }
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    void serve(java.net.Socket s) {
        try (java.net.Socket sock = s) {
            sock.setTcpNoDelay(true);
            sock.setSoTimeout(120_000);   // do not hold a thread for a silent client forever
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(sock.getInputStream(), "UTF-8"));
            java.io.PrintStream w = new java.io.PrintStream(sock.getOutputStream(), true, "UTF-8");
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                w.println("RESULT:" + plain(processLine(line).toString()));
            }
        } catch (java.net.SocketTimeoutException te) {
            log("session idle timeout, closing");
        } catch (Throwable e) {
            log("session end: " + e);
        }
    }

    public JSONObject processLine(String payload) {
        return processLine(payload, SEQ.incrementAndGet());
    }

    /**
     * D2: a command must be abortable while it is still injecting.
     *
     * The engine runs every command on one handler thread, so `cancel` cannot be a
     * normal command — it would queue behind the very press it is meant to stop, and a
     * 30s timeout cannot stop anything on its own either: the only thread that can stop
     * the injection loop is the loop itself. Both therefore work through an atomic flag
     * that the loop polls every 50ms.
     *
     * Known limit (documented, not a bug): a client that disconnects mid-command cannot
     * be detected without reading from the socket, and reading would eat the next
     * pipelined request. Cancellation is therefore explicit (`{"cmd":"cancel"}` on a
     * second connection) or by timeout.
     */
    JSONObject processLine(final String payload, final long id) {
        // `cancel` is handled on the calling thread: the work thread is busy injecting
        if (isCancelRequest(payload)) {
            long target = runningCmd;
            if (target <= 0) return fail("NO_RUNNING_CMD", "nothing to cancel");
            CANCELED.add(target);
            log("cancel requested for cmd " + target);
            try {
                return new JSONObject().put("ok", true).put("canceled", target);
            } catch (Throwable t) {
                return fail("INTERNAL", String.valueOf(t));
            }
        }
        if (retired) return fail("SESSION_CLOSED", "screen session has been released");
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final JSONObject[] holder = new JSONObject[1];
        long t = SystemClock.elapsedRealtime();
        work.post(() -> {
            JSONObject res;
            if (retired || CANCELED.remove(id)) {
                // it was already written off by the timeout: do not touch the screen
                try {
                    holder[0] = fail("CANCELED", "command expired before it started");
                } finally {
                    latch.countDown();
                }
                return;
            }
            CURRENT_CMD.set(id);
            runningCmd = id;
            try {
                res = handle(new JSONObject(payload));
            } catch (CanceledException ce) {
                res = failFlag("CANCELED",
                        "command was cancelled; input already injected may still arrive",
                        "cancelRequested");
            } catch (Throwable e) {
                String why = String.valueOf(e);
                if (why.contains("DeadSystem") || why.contains("DeadObjectException")) {
                    // system_server restarted under us: every cached binder is dead now
                    res = fail("SYSTEM_RESTARTED", "Android 系统重启过，引擎里的 binder 已失效；"
                            + "执行 sh /data/local/tmp/eta/vd.sh 重启引擎即可恢复");
                } else {
                    boolean bad = e instanceof org.json.JSONException;
                    res = fail(bad ? "BAD_REQUEST" : "INTERNAL",
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            } finally {
                CURRENT_CMD.remove();
                runningCmd = -1;
                CANCELED.remove(id);
            }
            try {
                res.put("ms", SystemClock.elapsedRealtime() - t);
                if (closed) res.put("closing", true);
            } catch (Throwable ignored) { }
            holder[0] = res;
            if (res.optBoolean("released") && sessions.isEmpty() && vd == null) closeSession();
            latch.countDown();
        });
        boolean done = false;
        try {
            done = latch.await(COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) { }
        if (!done) {
            CANCELED.add(id);   // the injecting loop polls this and stops
            log("cmd " + id + " timed out after " + COMMAND_TIMEOUT_MS + "ms, signaling cancel");
        }
        JSONObject res = holder[0];
        if (res == null) {
            res = failFlag("TIMEOUT", "no reply in " + (COMMAND_TIMEOUT_MS / 1000)
                    + "s; cancellation was requested, but input already injected may still arrive",
                    "cancelRequested", "stillRunning");
        }
        return res;
    }

    /** True only when the JSON command itself is "cancel", not when it is quoted inside. */
    private boolean isCancelRequest(String payload) {
        if (payload == null) return false;
        try {
            return "cancel".equals(new org.json.JSONObject(payload).optString("cmd", ""));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Called from the injection loops: abort the moment the command is written off. */
    void checkCanceled() throws Exception {
        Long id = CURRENT_CMD.get();
        if (id != null && CANCELED.contains(id)) throw new CanceledException();
    }

    final class CanceledException extends Exception {
        CanceledException() { super("CANCELED"); }
    }

    /** Sleep that stays cancellable: long holds used to block the whole engine. */
    void sleepCancellable(long ms) throws Exception {
        long end = SystemClock.elapsedRealtime() + ms;
        while (true) {
            checkCanceled();
            long left = end - SystemClock.elapsedRealtime();
            if (left <= 0) return;
            Thread.sleep(Math.min(50, left));
        }
    }

    JSONObject fail(String code, String detail) {
        try {
            lastError = code + " " + detail;
            return new JSONObject().put("ok", false).put("code", code).put("error", detail);
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    /** fail() plus boolean flags, without letting put()'s checked exception escape. */
    JSONObject failFlag(String code, String detail, String... flags) {
        JSONObject o = fail(code, detail);
        try {
            for (String f : flags) o.put(f, true);
        } catch (Throwable ignored) { }
        return o;
    }

    /** org.json escapes "/" as "\/"; models copy that literally as a path and fail. */
    String plain(String json) {
        return json.replace("\\/", "/");
    }

    void emit(JSONObject o) {
        System.out.println("RESULT:" + plain(o.toString()));
        System.out.flush();
    }

    /** Commands that only look: they must not move the cursor or refresh idle clocks. */
    boolean isPassive(String cmd) {
        return "status".equals(cmd) || "ping".equals(cmd) || "sessions".equals(cmd)
                || "clip".equals(cmd);
    }

    /**
     * One place decides which session a command acts on.
     *
     * Explicit target wins and must exist: an unknown package or display is an error,
     * never "fall back to whatever is attached". Only a command that names no target at
     * all keeps the old behaviour of using the current session.
     *
     * Returns null with err[0]==null for the main screen (displayId 0), which is a valid
     * target without a hidden session.
     */
    Session pickSession(JSONObject req, JSONObject[] err) {
        String pkg = req.optString("package", "").trim();
        boolean hasPkg = !pkg.isEmpty();
        boolean hasDisplay = req.has("displayId");
        int did = hasDisplay ? req.optInt("displayId", -1) : -1;
        if (hasDisplay && did < 0) {
            err[0] = fail("BAD_ARGUMENT", "displayId must be >= 0 (got " + did + ")");
            return null;
        }

        if (!hasPkg && !hasDisplay) return current;

        Session byPkg = hasPkg ? sessions.get(pkg) : null;
        if (hasPkg && byPkg == null) {
            err[0] = fail("NO_SESSION", "no live session for package " + pkg);
            return null;
        }
        Session byDisplay = null;
        if (hasDisplay && did > 0) {
            for (Session s : sessions.values()) {
                if (s.displayId == did) { byDisplay = s; break; }
            }
            if (byDisplay == null) {
                err[0] = fail("NO_SESSION", "display " + did + " does not belong to a live session");
                return null;
            }
            if (byPkg != null && byPkg != byDisplay) {
                err[0] = fail("TARGET_CONFLICT", "package " + pkg + " lives on display "
                        + byPkg.displayId + ", not " + did);
                return null;
            }
        }
        if (hasDisplay && did == 0 && byPkg != null && byPkg.displayId > 0) {
            err[0] = fail("TARGET_CONFLICT", "package " + pkg + " is on display "
                    + byPkg.displayId + ", not the main screen");
            return null;
        }
        return byPkg != null ? byPkg : byDisplay;
    }

    JSONObject handle(JSONObject req) throws Exception {
        String cmd0 = req.optString("cmd", "");
        boolean passive = isPassive(cmd0);
        JSONObject[] err = new JSONObject[1];
        Session target = pickSession(req, err);
        if (err[0] != null) {
            log("cmd=" + cmd0 + " rejected: " + err[0].optString("code"));
            return err[0];
        }
        log("cmd=" + cmd0 + (target == null ? "" : " pkg=" + target.pkg));
        if (passive) {
            // read-only: do not touch the cursor, the idle clock or the orphan flag
            return dispatch(cmd0, req);
        }
        lastCmdAt = SystemClock.elapsedRealtime();
        if (target != null) {
            load(target);
            target.lastCmdAt = lastCmdAt;
            // A command aimed at a parked session brings it back into use. "launch" is
            // excluded: it picks its own package and must not revive (or clear the parked
            // flag of) whatever happens to be the cursor.
            if (!"launch".equals(cmd0) && !"present".equals(cmd0)) { target.orphanAt = 0; orphanAt = 0; }
        } else if (req.has("displayId") && req.optInt("displayId", -1) == 0) {
            // an explicit main-screen command must not inherit a hidden display handle
            save();
            detach();
        }
        try {
            if (isPointerCommand(cmd0)) {
                JSONObject invalid = preparePointerCoordinates(req);
                if (invalid != null) return invalid;
                // Once an action starts it may take effect even if its response fails.
                lastScreenshot = null;
            }
            JSONObject out = dispatch(cmd0, req);
            save();
            return out;
        } catch (Throwable t) {
            save();
            throw t;
        }
    }

    JSONObject dispatch(String cmd0, JSONObject req) throws Exception {
        switch (cmd0) {
            case "ping": return new JSONObject().put("ok", true).put("pong", true);
            case "status": return status();
            case "sessions": return sessionsInfo();
            case "clip": return clipProbe(req);
            case "tree": return treeProxy(req);
            case "click2": return a11yProxy(req);
            case "waittext": return a11yProxy(new JSONObject()
                    .put("cmd", "wait").put("text", req.optString("text"))
                    .put("timeoutMs", req.optLong("timeoutMs", 8000)));
            case "create": return create(req);
            case "destroy": return destroyCurrent();
            case "launch": return launch(req);
            case "tap": return tap(req);
            case "press": return press(req);
            case "drag": return drag(req);
            case "swipe": return swipe(req);
            case "text": return text(req);
            case "key": return key(req);
            case "shot": return shot(req);
            case "handover": return handover(req);
            case "present": return presentCompleted(req);
            case "finish": return finish(req);
            case "orphan":
                if (current != null) {
                    current.orphanAt = SystemClock.elapsedRealtime();
                    orphanAt = current.orphanAt;
                    save();
                }
                return new JSONObject().put("ok", true).put("display", displayId);
            case "notify": return notifyUser(req);
            case "quit":
                closed = true;
                for (Session x : new java.util.ArrayList<>(sessions.values())) {
                    releaseSessionFully(x, false);
                }
                destroyIdleDisplay();
                quitSoon();
                return new JSONObject().put("ok", true).put("bye", true);
            default: return fail("UNKNOWN_CMD", req.optString("cmd"));
        }
    }

    /** Destroy the session this command was resolved to (handle() already loaded it). */
    JSONObject destroyCurrent() throws Exception {
        Session x = current;
        if (x == null) {
            // create() without a session record still owns a display: release it here,
            // otherwise "create then destroy" leaks the screen until the watchdog.
            if (vd != null) {
                int did = displayId;
                destroyIdleDisplay();
                return new JSONObject().put("ok", true).put("destroyed", true)
                        .put("idleDisplay", true).put("displayId", did);
            }
            return new JSONObject().put("ok", true).put("destroyed", false);
        }
        String pkg = x.pkg;
        int did = x.displayId;
        releaseSessionFully(x, false);
        return new JSONObject().put("ok", true).put("destroyed", true)
                .put("package", pkg).put("displayId", did);
    }

    /**
     * Evict the least recently used session to make room for a new one.
     * Releases the display, which may make the system close that task — the app is not
     * force-stopped, and the log says which package lost its window.
     */
    boolean evictParked(Session keep) {
        Session victim = oldestParked(keep);
        if (victim == null) return false;
        log("session limit " + MAX_SESSIONS + " reached, evicting parked " + victim.pkg
                + " (display " + victim.displayId + ")");
        releaseSessionFully(victim, false);
        return true;
    }

    /**
     * The observer lives in whichever Eta build bound its port first. 8738 is the port
     * only the rebuilt bridge uses, so preferring it makes the choice deterministic;
     * 8734 stays as the legacy fallback.
     */
    volatile int obsPort = 0;

    int obsPort() {
        int p = obsPort;
        if (p > 0) return p;
        for (int cand : new int[]{8738, 8734}) {
            if (portOpen(cand)) { obsPort = cand; log("observer on " + cand); return cand; }
        }
        return 8734;
    }

    boolean portOpen(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Send one JSON command to the accessibility observer. */
    JSONObject proxyA11y(JSONObject req) {
        return proxyA11y(req, 20_000);
    }

    JSONObject proxyA11y(JSONObject req, int timeoutMs) {
        try (java.net.Socket sock = new java.net.Socket()) {
            int did = req.optInt("displayId", displayId);
            JSONObject before = geometry(did < 0 ? 0 : did).json();
            JSONObject invalid = ScreenGeometry.check(req.optJSONObject("expectedGeometry"), before);
            if (invalid != null) return invalid;
            req.put("engineGeometry", before);
            sock.connect(new java.net.InetSocketAddress("127.0.0.1", obsPort()), Math.min(2000, timeoutMs));
            sock.setSoTimeout(timeoutMs);
            java.io.PrintStream w = new java.io.PrintStream(sock.getOutputStream(), true, "UTF-8");
            w.println(req.toString());
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(sock.getInputStream(), "UTF-8"));
            String line = r.readLine();
            if (line == null) return null;
            if (line.startsWith("RESULT:")) line = line.substring(7);
            JSONObject result = new JSONObject(line);
            JSONObject after = geometry(did < 0 ? 0 : did).json();
            if (!ScreenGeometry.same(before, after)) return ScreenGeometry.reject(
                    "GEOMETRY_CHANGED_DURING_OPERATION", "GEOMETRY_CHANGED_DURING_OPERATION", before, after)
                    .put("operationMayHaveRun", true);
            return result.put("geometry", after);
        } catch (Throwable t) {
            log("a11y proxy failed on " + obsPort() + ": " + t);
            obsPort = 0;   // port may have moved to the other build; re-probe next call
            return null;
        }
    }

    /** Structured UI read (default observation): list | dump | diag, proxied to a11y. */
    JSONObject treeProxy(JSONObject req) throws Exception {
        String mode = req.optString("mode", "list");
        if (!"list".equals(mode) && !"dump".equals(mode) && !"diag".equals(mode)) mode = "list";
        JSONObject q = new JSONObject().put("cmd", mode).put("displayId", req.optInt("displayId", displayId));
        if (req.has("maxRows")) q.put("maxRows", req.getInt("maxRows"));
        JSONObject r = proxyA11y(q);
        if (a11yDown(r)) return fail("A11Y_UNAVAILABLE", a11yWhy(r));
        // An empty read must never masquerade as success: callers would treat a dead
        // accessibility service as "the page is empty" and start retrying blindly.
        // The helper can be alive-but-powerless (different app instance bound, service
        // unbound but socket held by another process), and in all those cases it still
        // answers ok with zero rows/nodes AND sees no windows on the target display.
        if (r.optBoolean("ok")) {
            int got = mode.equals("list") ? r.optInt("rowCount", -1) : r.optInt("nodeCount", -1);
            if (got == 0) {
                JSONObject probe = proxyA11y(new JSONObject().put("cmd", "diag")
                        .put("displayId", q.optInt("displayId", -1)));
                if (probe != null && probe.optInt("nodeCount", -1) == 0)
                    return fail("A11Y_EMPTY_READ",
                            "zero " + (mode.equals("list") ? "rows" : "nodes") + " and diag confirms the"
                            + " helper sees nothing on this display; it is likely bound to another"
                            + " instance or unbound - restart com.eta.bg accessibility");
            }
        }
        return r;
    }

    /** True when the reply proves the accessibility helper is not actually serving. */
    boolean a11yDown(JSONObject r) {
        if (r == null) return true;
        String code = r.optString("code", "");
        return "UNKNOWN_CMD".equals(code) || "INTERNAL".equals(code);
    }

    String a11yWhy(JSONObject r) {
        if (r == null) return "accessibility observer unreachable on 8738/8734 (not bound or crashed)";
        return "accessibility service replied " + r.optString("code") + ": " + r.optString("error");
    }

    JSONObject a11yProxy(JSONObject req) throws Exception {
        JSONObject q = new JSONObject(req.toString());
        if (!q.has("displayId")) q.put("displayId", displayId);
        JSONObject r = proxyA11y(q);
        if (a11yDown(r)) return fail("A11Y_UNAVAILABLE", a11yWhy(r));
        return r;
    }

    JSONObject clipProbe(JSONObject req) throws Exception {
        String text = req.optString("text", "vdrive-probe");
        boolean set = setClipboardText(text);
        String back = "";
        try {
            Object cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            Object clip = cm.getClass().getMethod("getPrimaryClip").invoke(cm);
            if (clip != null) {
                Object item = clip.getClass().getMethod("getItemAt", int.class).invoke(clip, 0);
                Object cs = item.getClass().getMethod("getText").invoke(item);
                back = cs == null ? "null" : cs.toString();
            }
        } catch (Throwable t) {
            back = "read-error: " + t;
        }
        return new JSONObject().put("ok", true).put("setReturned", set)
                .put("wanted", text).put("got", back).put("match", text.equals(back));
    }

    JSONObject sessionsInfo() throws Exception {
        long now = SystemClock.elapsedRealtime();
        org.json.JSONArray arr = new org.json.JSONArray();
        for (Session x : sessions.values()) {
            arr.put(new JSONObject()
                    .put("pkg", x.pkg)
                    .put("display", x.displayId)
                    .put("taskId", x.taskId)
                    .put("parked", x.orphanAt > 0)
                    .put("idleSec", (now - x.lastCmdAt) / 1000)
                    .put("ageSec", (now - x.createdAt) / 1000));
        }
        return new JSONObject().put("ok", true)
                .put("count", sessions.size())
                .put("limit", MAX_SESSIONS)
                .put("sessions", arr);
    }

    JSONObject status() throws Exception {
        JSONObject o = new JSONObject().put("ok", true);
        o.put("uid", android.os.Process.myUid()).put("build", BUILD);
        o.put("displayId", vd != null ? displayId : -1);
        o.put("active", vd != null);
        o.put("width", W).put("height", H).put("density", DPI);
        o.put("frameReady", displayId >= 0 && frames.containsKey(displayId));
        o.put("frames", frames.size());
        o.put("orphanAt", orphanAt);
        o.put("launchedPkg", launchedPkg);
        o.put("launchedTaskId", launchedTaskId);
        o.put("homeDisplay", homeDisplay);
        o.put("taskOnDisplay", vd != null && hasTaskOnDisplay());
        o.put("focusedPkg", focusedPkg());
        o.put("lastError", lastError).put("lastErrorPkg", launchedPkg);
        if (vd != null) {
            Display d = vd.getDisplay();
            o.put("displayName", d.getName()).put("state", d.getState());
        }
        return o;
    }

    int[] mainSize() {
        try {
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            android.graphics.Point p = new android.graphics.Point();
            d.getRealSize(p);
            if (p.x > 0 && p.y > 0) return new int[]{p.x, p.y, d.getDisplayId()};
        } catch (Throwable ignored) { }
        return new int[]{1440, 3168, 0};
    }

    int mainDensity() {
        try {
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            android.util.DisplayMetrics m = new android.util.DisplayMetrics();
            d.getRealMetrics(m);
            if (m.densityDpi > 0) return m.densityDpi;
        } catch (Throwable ignored) { }
        return 560;
    }

    private Frame createCapture() {
        final Frame fr = new Frame();
        final ImageReader capture = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 3);
        reader = capture;
        capture.setOnImageAvailableListener(r -> {
            if (reader != r || orphanAt > 0 || retired) return;
            Image img = null;
            try {
                img = r.acquireLatestImage();
                if (img == null) return;
                if (fr.image != null) fr.image.close();
                fr.image = img;
                fr.geometry = geometry(displayId);
                fr.receivedAt = SystemClock.elapsedRealtime();
                img = null;
                frameOk++;
            } catch (Throwable t) {
                if (frameErr++ < 3) log("frame error: " + t);
            } finally { if (img != null) img.close(); }
        }, work);
        return fr;
    }

    /** Keep the task container while awaiting the user's next action, without capture buffers. */
    private void stopCapture(Session session) {
        Frame frame = frames.remove(session.displayId);
        if (frame != null) frame.close();
        ImageReader capture = session.reader;
        if (capture == null) return;
        try { capture.setOnImageAvailableListener(null, null); } catch (Throwable ignored) { }
        try { if (session.vd != null) session.vd.setSurface(null); } catch (Throwable t) { log("detach capture surface: " + t); }
        try { capture.close(); } catch (Throwable ignored) { }
        session.reader = null;
        if (session == current) reader = null;
    }

    private void resumeCapture(Session session) {
        load(session);
        if (session.reader != null) return;
        Frame frame = createCapture();
        try {
            session.vd.setSurface(reader.getSurface());
            session.reader = reader;
            frames.put(session.displayId, frame);
        } catch (Throwable error) {
            frame.close();
            reader.close();
            reader = null;
            throw error;
        }
    }

    JSONObject create(JSONObject req) throws Exception {
        // Multi-session: a live session on another display must not block creating a
        // new one. Park it (save state) and detach instead of refusing.
        if (vd != null) {
            if (hasTaskOnDisplay()) {
                if (current != null) {
                    // a live session owns this display: park it and detach
                    log("create: parking live session " + launchedPkg + " and switching away");
                    save();
                    detach();
                } else {
                    // orphaned live display from an earlier failed launch: reclaim it
                    log("create: destroying orphaned display " + displayId);
                    destroyIdleDisplay();
                }
            }
            // else: an idle cursor display is reused below as before
        }
        // Remember what the user had on screen *before* we borrow a display; this is
        // the only reliable moment, later the focus has already moved.
        if (homeFront.isEmpty()) {
            String front = focusedComponent();
            if (!front.isEmpty()) {
                homeFront = front;
                log("home front remembered: " + front);
            }
        }
        if (vd != null) {
            // An idle display (no task on it) is reusable; only refuse when a task is live.
            if (!hasTaskOnDisplay()) {
                // keep the existing display instead of churning a new one (the old
                // release-then-create path leaked one VirtualDisplay per call)
                log("reusing idle display " + displayId);
                return new JSONObject().put("ok", true).put("displayId", displayId)
                        .put("createMs", 0).put("reused", true)
                        .put("width", W).put("height", H);
            }
            return fail("DISPLAY_ALREADY_ACTIVE", "display " + displayId + " still hosts a task");
        }
        int[] main = mainSize();
        W = req.has("width") && req.optInt("width", 0) > 0 ? req.optInt("width") : main[0];
        H = req.has("height") && req.optInt("height", 0) > 0 ? req.optInt("height") : main[1];
        int[] oriented = DisplayGeometry.orient(W, H, req.optString("orientation", "auto"),
                requestedOrientation(req.optString("component")));
        W = oriented[0]; H = oriented[1];
        lastScreenshot = null;
        DPI = req.has("density") && req.optInt("density", 0) > 0
                ? req.optInt("density") : mainDensity();
        final Frame fr = createCapture();
        // Isolate focus from the user's display. Screen-off execution is disabled.
        int flags = flag("PRIVATE") | flag("OWN_CONTENT_ONLY") | flag("SUPPORTS_TOUCH")
                | flag("TRUSTED") | flag("OWN_DISPLAY_GROUP") | flag("OWN_FOCUS")
                | flag("STEAL_TOP_FOCUS_DISABLED") | flag("ROTATES_WITH_CONTENT")
                | flag("DESTROY_CONTENT_ON_REMOVAL");
        long t = SystemClock.elapsedRealtime();
        vd = dm.createVirtualDisplay("eta-bg", W, H, DPI, reader.getSurface(), flags, null, work);
        log("virtual display flags = 0x" + Integer.toHexString(flags));
        displayId = vd.getDisplay().getDisplayId();
        lastDisplayGeometry = geometry(displayId);
        // Some ROMs inherit an orientation-ignore policy on secondary displays.
        try {
            Class<?> global = Class.forName("android.view.WindowManagerGlobal");
            Object wm = global.getMethod("getWindowManagerService").invoke(null);
            Method allow = wm.getClass().getMethod("setIgnoreOrientationRequest", int.class, boolean.class);
            allow.setAccessible(true);
            allow.invoke(wm, displayId, false);
        } catch (Throwable ignored) { } // Framework orientation remains the fallback.
        hadDisplay = true;
        frames.put(displayId, fr);   // keyed by the display id, which only exists now
        return new JSONObject().put("ok", true).put("displayId", displayId)
                .put("createMs", SystemClock.elapsedRealtime() - t)
                .put("width", W).put("height", H);
    }

    /**
     * Open (or re-attach) an app.
     *
     * The order is the whole point:
     *   1. the app is already in front on the main screen -> operate in place
     *   2. a live hidden session exists -> reuse it, moving its task if necessary
     *   3. otherwise -> start it on its own hidden display
     *
     * Nothing here removes tasks or force-stops apps on its own: both destroy the
     * caller's existing window, so they only happen when explicitly allowed.
     */
    JSONObject launch(JSONObject req) throws Exception {
        disposition.set(ACTIVE);
        String comp = req.getString("component");
        boolean allowForceStop = req.optBoolean("allowForceStop", false);
        String pkg = comp.contains("/") ? comp.substring(0, comp.indexOf('/')) : comp;
        long t = SystemClock.elapsedRealtime();

        if (!req.optBoolean("forceHidden", false) && pkg.equals(focusedPkg())) {
            Integer tid = taskOfPackage(pkg);
            return new JSONObject().put("ok", true).put("mode", "onscreen")
                    .put("displayId", 0).put("taskId", tid == null ? -1 : tid)
                    .put("ms", SystemClock.elapsedRealtime() - t);
        }

        Session existing = sessions.get(pkg);
        if (existing != null && existing.vd != null && existing.displayId >= 0) {
            existing.orphanAt = 0;
            resumeCapture(existing);
            return reuseSession(existing, comp);
        }
        if (existing != null) {
            sessions.remove(pkg);   // record without a display: nothing to reuse
            if (existing == current) resetGlobals();
        }
        return startNewSession(req, comp, pkg, allowForceStop, t);
    }

    /**
     * Give the input focus back to whatever was on the real screen.
     *
     * This ROM keeps the virtual display in display group 0, so the moment an app is
     * launched there it becomes the system's focused window / IME client and the user's
     * keyboard disappears. Re-resuming the main-screen activity puts the IME client back
     * on display 0 without touching the hidden app.
     */
    void restoreMainFocus() {
        String front = homeFront;
        if (front == null || front.isEmpty() || !VDrive.mainDisplayOn()) return;
        // Independent display focus normally makes any main-screen intervention unnecessary.
        if (front.equals(focusedComponent())) return;
        try {
            exec("am start -n " + shellQuote(front) + " --activity-single-top");
            Thread.sleep(150);
            log("main focus restored: " + front);
        } catch (Throwable t) {
            log("restoreMainFocus failed: " + t);
        }
    }

    /** Point the command cursor at an existing session without recreating anything. */
    JSONObject reuseSession(Session sess, String comp) throws Exception {
        String pkg = sess.pkg;
        long t = SystemClock.elapsedRealtime();
        Integer tid = taskOfPackage(pkg);

        if (tid != null && displayOfTask(tid) == sess.displayId) {
            log("reusing session " + pkg + " on display " + sess.displayId);
            load(sess);
            launchedTaskId = tid;
            sess.lastCmdAt = SystemClock.elapsedRealtime();
            save();
            protectApp(pkg, true);
            return new JSONObject().put("ok", true).put("mode", "hidden").put("reused", true)
                    .put("taskId", tid).put("displayId", sess.displayId)
                    .put("geometry", geometry(sess.displayId).json())
                    .put("ms", SystemClock.elapsedRealtime() - t);
        }
        if (tid != null) {
            // alive somewhere else: move the task, never kill the app
            moveTask(tid, sess.displayId);
            if (!waitForTaskDisplay(tid, sess.displayId, 30)) {
                return fail("LAUNCH_MISPLACED", "task " + tid + " of " + pkg + " is on display "
                        + displayOfTask(tid) + " and could not be moved to " + sess.displayId
                        + "; nothing was killed");
            }
            load(sess);
            sess.orphanAt = 0; orphanAt = 0;   // parked -> in use again
            launchedTaskId = tid;   // save() copies the global back; keep them in step
            sess.taskId = tid;
            restoreMainFocus();
            sess.lastCmdAt = SystemClock.elapsedRealtime();
            save();
            protectApp(pkg, true);
            return new JSONObject().put("ok", true).put("mode", "hidden").put("reused", true)
                    .put("moved", true).put("taskId", tid).put("displayId", sess.displayId)
                    .put("geometry", geometry(sess.displayId).json())
                    .put("ms", SystemClock.elapsedRealtime() - t);
        }
        // no task at all: cold start onto the same display
        JSONObject r = startOnDisplay(comp, sess.displayId, false);
        if (!r.optBoolean("ok")) return r;
        int newTask = r.optInt("taskId", -1);
        if (newTask <= 0 || !waitForTaskDisplay(newTask, sess.displayId, 30)) {
            return fail("LAUNCH_MISPLACED", "relaunched " + pkg + " but it is not on display "
                    + sess.displayId);
        }
        load(sess);
        sess.orphanAt = 0; orphanAt = 0;
        launchedTaskId = newTask;
        sess.taskId = newTask;
        sess.lastCmdAt = SystemClock.elapsedRealtime();
        save();
        protectApp(pkg, true);
        return new JSONObject().put("ok", true).put("mode", "hidden").put("reused", true)
                .put("relaunched", true).put("taskId", newTask).put("displayId", sess.displayId)
                .put("geometry", geometry(sess.displayId).json())
                .put("ms", SystemClock.elapsedRealtime() - t);
    }

    /** Brand new session on the display that create() just made. */
    JSONObject startNewSession(JSONObject req, String comp, String pkg,
            boolean allowForceStop, long t) throws Exception {
        // Every app gets its own display. If the cursor is still attached to another
        // session, park it (its vd/reader stay alive inside the record) so the globals
        // are free for a fresh create(). This is what keeps two apps off one screen.
        // Park a *live* session so the globals are free. An idle display that nobody
        // owns yet is kept: create() may have just made it at the caller's size, and
        // rebuilding it here would silently drop that size.
        if (vd != null && current != null) {
            save();
            detach();
        }
        if (vd != null && hasTaskOnDisplay() && current == null) {
            // orphaned display with a stray task and no record: reclaim it
            log("startNewSession: reclaiming orphaned display " + displayId);
            destroyIdleDisplay();
        }
        // Make room before starting anything: only a parked session may be dropped, so a
        // running app is never killed just to open a fourth one.
        if (sessions.size() >= MAX_SESSIONS) {
            if (!evictParked(null)) {
                return fail("SESSION_LIMIT", "already " + sessions.size()
                        + " sessions and none is parked; finish one first");
            }
        }
        if (vd == null) {
            JSONObject created = create(new JSONObject()
                    .put("width", req.optInt("width", 0)).put("height", req.optInt("height", 0))
                    .put("density", req.optInt("density", 0)).put("component", comp)
                    .put("orientation", req.optString("orientation", "auto")));
            if (!created.optBoolean("ok")) return created;
        }
        if (homeFront.isEmpty()) {
            String front = focusedComponent();
            if (!front.isEmpty() && !front.startsWith(pkg)) homeFront = front;
        }
        int did = displayId;
        Integer beforeTask = taskOfPackage(pkg);
        int beforeDisplay = beforeTask != null ? displayOfTask(beforeTask) : -1;
        // From here on the fresh display must not leak: every failure path below cleans
        // it up before returning.
        class Cleanup {
            void roll() {
                log("startNewSession failed, rolling back fresh display " + displayId);
                destroyIdleDisplay();
            }
        }
        Cleanup cleanup = new Cleanup();

        // The app already has a window somewhere (typically the main screen, in the
        // background). Move that window onto the hidden display: it keeps the user's
        // state, and it is reversible. Killing it is only for when the move fails and
        // the caller said so explicitly.
        boolean adopted = false;
        if (beforeTask != null) {
            moveTask(beforeTask, did);
            adopted = waitForTaskDisplay(beforeTask, did, 25);
            if (!adopted) {
                if (!allowForceStop) {
                    cleanup.roll();
                    return fail("NEED_FORCE_STOP", "task " + beforeTask + " of " + pkg
                            + " is on display " + displayOfTask(beforeTask)
                            + " and could not be moved to " + did
                            + "; pass allowForceStop=true to kill it instead");
                }
                try {
                    Object atm = atmGetService.invoke(null);
                    if (atmRemoveTask != null) atmRemoveTask.invoke(atm, beforeTask);
                    Thread.sleep(350);
                } catch (Throwable rt) {
                    log("removeTask failed: " + rt);
                    exec("am force-stop " + shellQuote(pkg));
                    Thread.sleep(350);
                }
            }
        }

        int tid;
        String via;
        if (adopted) {
            tid = beforeTask;
            via = "adopted";
        } else {
            JSONObject r = startOnDisplay(comp, did, allowForceStop);
            if (!r.optBoolean("ok")) { cleanup.roll(); return r; }
            tid = r.optInt("taskId", -1);
            if (tid <= 0 || !waitForTaskDisplay(tid, did, 30)) {
                cleanup.roll();
                return fail("LAUNCH_MISPLACED", "task of " + pkg + " did not land on display " + did);
            }
            via = r.optString("via", "atms");
        }

        Session sess = new Session(pkg);
        sessions.put(pkg, sess);
        current = sess;
        launchedTaskId = tid;
        launchedPkg = pkg;
        if (beforeDisplay >= 0) homeDisplay = beforeDisplay;
        taskCheckAt = 0;
        sess.taskId = tid;
        sess.lastCmdAt = SystemClock.elapsedRealtime();
        save();                       // copies vd/reader/displayId/homeFront into the record
        protectedPkg = pkg;
        protectApp(pkg, true);
        restoreMainFocus();   // hand the IME back to the real screen
        log("parked " + pkg + " on display " + did + " via " + via
                + " (sessions=" + sessions.size() + ")");

        JSONObject o = new JSONObject().put("ok", true).put("mode", "hidden")
                .put("via", via).put("taskId", tid).put("displayId", did)
                .put("geometry", geometry(did).json())
                .put("ms", SystemClock.elapsedRealtime() - t);
        if (adopted) o.put("adopted", true);
        if (beforeDisplay >= 0) {
            o.put("borrowed", true).put("fromDisplay", beforeDisplay).put("homeDisplay", homeDisplay);
        }
        return o;
    }

    /** Start the app directly on a display; returns the task id it created. */
    JSONObject startOnDisplay(String comp, int did, boolean allowForceStop) throws Exception {
        String out = "";
        String via = "atms";
        try {
            Intent i;
            if (comp.contains("/")) {
                ComponentName cn = ComponentName.unflattenFromString(comp);
                if (cn == null) throw new IllegalArgumentException("BAD_COMPONENT");
                i = new Intent(Intent.ACTION_MAIN).setComponent(cn);
            } else {
                i = ctx.getPackageManager().getLaunchIntentForPackage(comp);
                if (i == null) return fail("NO_LAUNCH_INTENT", comp);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (optSetLaunchDisplay == null) throw new IllegalStateException("NO_ACTIVITY_OPTIONS");
            ActivityOptions o = ActivityOptions.makeBasic();
            optSetLaunchDisplay.invoke(o, did);
            ctx.startActivity(i, o.toBundle());
        } catch (Throwable direct) {
            log("atms launch failed (" + direct + "), trying am start --display");
            via = "am";
            out = exec(amStartOnDisplay(did, comp));
            if (out.contains("Error") || out.contains("Exception") || out.contains("APP_RESTART_UNSUPPORTED")) {
                if (out.contains("APP_RESTART_UNSUPPORTED") && !allowForceStop)
                    return fail("NEED_FORCE_STOP", "app is alive elsewhere; pass allowForceStop=true to kill it");
                return fail("LAUNCH_FAILED", out.trim());
            }
        }
        String pkg = comp.contains("/") ? comp.substring(0, comp.indexOf('/')) : comp;
        Integer tid = null;
        for (int k = 0; k < 80 && tid == null; k++) {
            checkCanceled();
            tid = taskOfPackage(pkg);
            if (tid == null) sleepCancellable(100);
        }
        if (tid == null) return fail("LAUNCH_FAILED", out.trim());
        return new JSONObject().put("ok", true).put("taskId", tid).put("via", via);
    }

    /** Wait until the task really sits on that display. */
    boolean waitForTaskDisplay(int taskId, int did, int tries) throws Exception {
        for (int k = 0; k < tries; k++) {
            checkCanceled();
            if (displayOfTask(taskId) == did) return true;
            sleepCancellable(120);
        }
        return displayOfTask(taskId) == did;
    }

    class BundleHolder { android.os.Bundle bundle; }

    long clamp(long v, long lo, long hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    JSONObject tap(JSONObject req) throws Exception {
        int did = req.optInt("displayId", displayId);
        if (did != 0 && !hasTaskOnDisplay()) return fail("NO_TASK_ON_DISPLAY", "display " + did + " is empty");
        float x = (float) req.getDouble("x"), y = (float) req.getDouble("y");
        JSONObject bad = checkPoint(did < 0 ? 0 : did, x, y);
        if (bad != null) return bad;
        long hold = clamp(req.optLong("holdMs", 40), 1, 15000);
        // Node click first: injecting touch into the hidden display moves the system input
        // target there and the user's keyboard disappears mid-typing.
        JSONObject a = proxyA11y(new JSONObject().put("cmd", "tapnode")
                .put("displayId", did).put("x", (int) x).put("y", (int) y)
                .put("expectedGeometry", req.optJSONObject("expectedGeometry"))
                .put("holdMs", 0)); // A short injected hold is still a tap, not ACTION_LONG_CLICK.
        if (a != null && a.optBoolean("ok")) {
            return new JSONObject().put("ok", true).put("via", "a11y")
                    .put("changed", a.optBoolean("changed", false))
                    .put("target", a.optString("target", ""))
                    .put("viewId", a.optString("viewId", ""));
        }
        JSONObject stale = stalePointerFallback(req, a, did);
        if (stale != null) return stale;
        injectTouch(did, x, y, x, y, hold, 0);
        return new JSONObject().put("ok", true).put("via", "inject");
    }

    /** Long press: hold the finger down for real, then release. */
    JSONObject press(JSONObject req) throws Exception {
        int did = req.optInt("displayId", displayId);
        if (did != 0 && !hasTaskOnDisplay()) return fail("NO_TASK_ON_DISPLAY", "display " + did + " is empty");
        float x = (float) req.getDouble("x"), y = (float) req.getDouble("y");
        long hold = clamp(req.optLong("holdMs", 800), 1, 15000);
        JSONObject bad = checkPoint(did < 0 ? 0 : did, x, y);
        if (bad != null) return bad;
        JSONObject a = proxyA11y(new JSONObject().put("cmd", "tapnode")
                .put("displayId", did).put("x", (int) x).put("y", (int) y)
                .put("expectedGeometry", req.optJSONObject("expectedGeometry"))
                .put("holdMs", hold));
        if (a != null && a.optBoolean("ok")) {
            return new JSONObject().put("ok", true).put("via", "a11y").put("holdMs", hold)
                    .put("changed", a.optBoolean("changed", false))
                    .put("target", a.optString("target", ""));
        }
        JSONObject stale = stalePointerFallback(req, a, did);
        if (stale != null) return stale;
        injectTouch(did, x, y, x, y, hold, 0);
        return new JSONObject().put("ok", true).put("via", "inject").put("holdMs", hold);
    }

    /**
     * Shared down/hold/move/up driver.
     *
     * D2: UP is always delivered, even when the command is cancelled or throws, because
     * a finger left held down makes every later input look broken. Cancellation is only
     * observed between injections, and the failure is reported as CANCELED/partial so a
     * caller can tell "nothing happened" from "half of it happened".
     */
    void injectTouch(int did, float x1, float y1, float x2, float y2,
                            long holdMs, int moveMs) throws Exception {
        boolean down = false;
        float lastX = x1, lastY = y1;
        long downTime = SystemClock.uptimeMillis();
        try {
            checkCanceled();
            injectMotion(did, downTime, MotionEvent.ACTION_DOWN, x1, y1);
            down = true;
            if (holdMs > 0) sleepCancellable(holdMs);
            if (moveMs > 0) {
                int steps = Math.max(4, moveMs / 16);
                for (int i = 1; i <= steps; i++) {
                    checkCanceled();
                    float f = i / (float) steps;
                    float nx = x1 + (x2 - x1) * f;
                    float ny = y1 + (y2 - y1) * f;
                    // only remember the point once the injection was accepted: a rejected
                    // MOVE must not move the release point
                    injectMotion(did, downTime, MotionEvent.ACTION_MOVE, nx, ny);
                    lastX = nx;
                    lastY = ny;
                    Thread.sleep(Math.max(1, moveMs / steps));
                }
            }
        } finally {
            if (down) {
                try {
                    // release where the finger actually is, not at the planned endpoint
                    injectMotion(did, downTime, MotionEvent.ACTION_UP, lastX, lastY);
                } catch (Throwable t) {
                    log("release after cancel failed: " + t);
                    throw t instanceof Exception ? (Exception) t : new IllegalStateException(String.valueOf(t));
                }
            }
        }
    }

    /** Drag: press, hold to enter drag mode, move, release. */
    JSONObject drag(JSONObject req) throws Exception {
        int did = req.optInt("displayId", displayId);
        if (did != 0 && !hasTaskOnDisplay()) return fail("NO_TASK_ON_DISPLAY", "display " + did + " is empty");
        float x1 = (float) req.getDouble("x1"), y1 = (float) req.getDouble("y1");
        float x2 = (float) req.getDouble("x2"), y2 = (float) req.getDouble("y2");
        long hold = clamp(req.optLong("holdMs", 500), 1, 15000);
        int dur = (int) clamp(req.optInt("durationMs", 600), 1, 15000);
        JSONObject bad = checkPoint(did < 0 ? 0 : did, Math.min(x1, x2), Math.min(y1, y2));
        if (bad != null) return bad;
        bad = checkPoint(did < 0 ? 0 : did, Math.max(x1, x2), Math.max(y1, y2));
        if (bad != null) return bad;
        try {
            injectTouch(did, x1, y1, x2, y2, hold, dur);
        } catch (CanceledException ce) {
            return fail("CANCELED", "drag cancelled while injecting; the finger was released"
                    + " (partial input may have been delivered)");
        }
        return new JSONObject().put("ok", true).put("holdMs", hold).put("durationMs", dur);
    }

    JSONObject swipe(JSONObject req) throws Exception {
        int did = req.optInt("displayId", displayId);
        if (did != 0 && !hasTaskOnDisplay()) return fail("NO_TASK_ON_DISPLAY", "display " + did + " is empty");
        float x1 = (float) req.getDouble("x1"), y1 = (float) req.getDouble("y1");
        float x2 = (float) req.getDouble("x2"), y2 = (float) req.getDouble("y2");
        JSONObject bad = checkPoint(did < 0 ? 0 : did, Math.min(x1, x2), Math.min(y1, y2));
        if (bad != null) return bad;
        bad = checkPoint(did < 0 ? 0 : did, Math.max(x1, x2), Math.max(y1, y2));
        if (bad != null) return bad;
        int dur = (int) clamp(req.optInt("durationMs", 300), 1, 15000);
        try {
            injectTouch(did, x1, y1, x2, y2, 0, dur);
        } catch (CanceledException ce) {
            return fail("CANCELED", "swipe cancelled while injecting; the finger was released"
                    + " (partial input may have been delivered)");
        }
        return new JSONObject().put("ok", true);
    }

    /** One touch event; downTime keeps a gesture's events on a consistent timeline. */
    void injectMotion(int did, long downTime, int action, float x, float y) throws Exception {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(downTime, now, action, x, y, 0);
        try {
            motionSetDisplay.invoke(ev, did);
            Object r = inject.invoke(inputManager, ev, 1);
            if (r instanceof Boolean && !(Boolean) r) {
                throw new IllegalStateException("injectInputEvent returned false for action " + action);
            }
        } finally {
            ev.recycle();
        }
    }

    /** Put text on the system clipboard (uid 0 system context, so no app focus needed). */
    boolean setClipboardText(String s) {
        try {
            Object cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            Class<?> cd = Class.forName("android.content.ClipData");
            Object clip = cd.getMethod("newPlainText", CharSequence.class, CharSequence.class)
                    .invoke(null, "vdrive", s);
            cm.getClass().getMethod("setPrimaryClip", cd).invoke(cm, clip);
            return true;
        } catch (Throwable t) {
            log("clipboard set failed: " + t);
            return false;
        }
    }

    JSONObject key(JSONObject req) throws Exception {
        int did = req.optInt("displayId", displayId);
        if (did != 0 && !hasTaskOnDisplay()) return fail("NO_TASK_ON_DISPLAY", "display " + did + " is empty");
        int code = req.getInt("code");
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0);
        keySetDisplay.invoke(down, did);
        inject.invoke(inputManager, down, 1);
        KeyEvent up = new KeyEvent(now, now + 20, KeyEvent.ACTION_UP, code, 0);
        keySetDisplay.invoke(up, did);
        inject.invoke(inputManager, up, 1);
        return new JSONObject().put("ok", true);
    }

    /**
     * Write text into a field.
     *
     * The accessibility path is always tried first, not only for CJK: it is the only
     * one that (a) honours view_id / index, and (b) can read the value back and say
     * whether the write actually happened. Key injection is kept as a fallback for
     * when the observer is unavailable, because it needs no accessibility service.
     */
    JSONObject text(JSONObject req) throws Exception {
        if (!req.has("text")) return fail("BAD_ARGUMENT", "text is required");
        int did = req.optInt("displayId", displayId);
        // same guard as key(): never type into the user's real screen by accident
        if (did != 0 && !hasTaskOnDisplay()) return fail("NO_TASK_ON_DISPLAY", "display " + did + " is empty");
        String s = req.getString("text");
        if (req.has("index") && req.optInt("index", -1) < 0) {
            return fail("INDEX_OUT_OF_RANGE", "index must be >= 0 (got "
                    + req.optInt("index", -1) + "); omit it to use the only field");
        }

        JSONObject q = new JSONObject().put("cmd", "settext").put("displayId", did).put("text", s);
        if (req.has("viewId")) q.put("viewId", req.optString("viewId", ""));
        if (req.has("index")) q.put("index", req.optInt("index", -1));
        JSONObject a = proxyA11y(q);

        if (a != null && a.optBoolean("ok")) {
            JSONObject r = new JSONObject().put("ok", true).put("chars", s.length()).put("via", "a11y");
            boolean verified = a.optBoolean("verified", false);
            r.put("verified", verified);
            if (!verified) {
                r.put("readback", a.optString("readback", ""));
                r.put("warning", a.optString("warning", "写入已提交但回读不一致"));
            }
            return r;
        }
        if (a == null || a11yDown(a)) {
            if (!req.optBoolean("allowKeyFallback", false)) {
                // Silent fallback used to type into whatever had focus, losing the
                // view_id/index the caller asked for. Refuse unless it is explicit.
                return fail("A11Y_UNAVAILABLE", a == null ? "observer not reachable"
                        : a11yWhy(a))
                        .put("hint", "观察者不可用时不自动回退按键注入；确需回退请显式传 allowKeyFallback=true");
            }
            // explicit fallback: key events, and the reply says the read-back is unproven
            KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                    .getEvents(s.toCharArray());
            if (events == null) {
                return fail("A11Y_UNAVAILABLE", a == null ? "observer not reachable"
                        : a11yWhy(a));
            }
            long now = SystemClock.uptimeMillis();
            for (KeyEvent e : events) {
                KeyEvent copy = new KeyEvent(now, now, e.getAction(), e.getKeyCode(),
                        e.getRepeatCount(), e.getMetaState());
                keySetDisplay.invoke(copy, did);
                inject.invoke(inputManager, copy, 1);
            }
            return new JSONObject().put("ok", true).put("chars", s.length())
                    .put("via", "keys").put("verified", false)
                    .put("warning", "输入法回退：走了按键注入，未校验回读");
        }
        // the observer answered with a real reason: pass it through unchanged
        String code = a.optString("code", "");
        if ("NO_EDITABLE".equals(code)) {
            return new JSONObject().put("ok", false).put("code", "NO_EDITABLE")
                    .put("error", "no input field found; tap the box first, or pass view_id/index")
                    .put("displayId", did);
        }
        if ("AMBIGUOUS".equals(code)) {
            JSONObject r = fail("AMBIGUOUS", "several input fields (" + a.optInt("count")
                    + "); retry with index or view_id");
            r.put("candidates", a.optJSONArray("candidates"));
            r.put("count", a.optInt("count"));
            r.put("displayId", did);
            return r;
        }
        if ("INDEX_OUT_OF_RANGE".equals(code)) {
            return new JSONObject().put("ok", false).put("code", "INDEX_OUT_OF_RANGE")
                    .put("error", "index " + a.optInt("requested")
                            + " but the screen has " + a.optInt("have") + " input fields")
                    .put("have", a.optInt("have")).put("requested", a.optInt("requested"))
                    .put("displayId", did);
        }
        JSONObject r = new JSONObject().put("ok", false)
                .put("code", code.isEmpty() ? "TEXT_FAILED" : code)
                .put("error", a.optString("error", a.optString("hint", "")))
                .put("displayId", did);
        if (a.has("readback")) r.put("readback", a.optString("readback", ""));
        if (a.has("have")) r.put("have", a.optInt("have"));
        if (a.has("requested")) r.put("requested", a.optInt("requested"));
        return r;
    }

    /** Capture the main display (used when working on-screen, no virtual display). */
    JSONObject shotMain(JSONObject req) throws Exception {
        if (!ensureShotDir()) return fail("PATH_NOT_ALLOWED", "cannot create " + SHOT_DIR);
        JSONObject bad = checkShotPath(req);
        if (bad != null) return bad;
        boolean jpeg = req.optString("format", "jpeg").equalsIgnoreCase("jpeg");
        String path = req.optString("path", SHOT_DIR + (jpeg ? "/shot.jpg" : "/shot.png"));
        float scale = clampScale((float) req.optDouble("scale", 0.45));
        int quality = req.optInt("quality", 80);

        String tmp = SHOT_DIR + "/.cap_main.png";
        long t = SystemClock.elapsedRealtime();
        DisplayGeometry geometry = geometry(0);
        exec("screencap -p " + tmp);
        Bitmap b = BitmapFactory.decodeFile(tmp);
        if (b == null) return fail("CAPTURE_FAILED", "screencap produced no image");
        if (!geometry.matches(geometry(0).json()) || b.getWidth() != geometry.width || b.getHeight() != geometry.height) {
            b.recycle(); lastScreenshot = null;
            return fail("FRAME_GEOMETRY_CHANGED", "display changed during capture; take a fresh screenshot");
        }
        Bitmap out = b;
        if (scale < 0.99f) {
            out = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * scale)),
                    Math.max(1, Math.round(b.getHeight() * scale)), true);
        }
        int outW = out.getWidth(), outH = out.getHeight();
        try (FileOutputStream fo = new FileOutputStream(path)) {
            out.compress(jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG,
                    jpeg ? quality : 100, fo);
        } finally {
            if (out != b) out.recycle();
            b.recycle();
        }
        publishShot(new java.io.File(path));
        lastScreenshot = new JSONObject().put("frameId", "0:" + SystemClock.elapsedRealtimeNanos()).put("displayId", 0)
                .put("width", outW).put("height", outH).put("geometry", geometry.json()).put("capturedAt", t);
        return new JSONObject().put("ok", true).put("path", path).put("display", "main")
                .put("format", jpeg ? "jpeg" : "png")
                .put("dims", outW + "x" + outH)
                .put("geometry", geometry.json()).put("frameId", lastScreenshot.getString("frameId"))
                .put("scaleToDisplayX", (double) geometry.width / outW).put("scaleToDisplayY", (double) geometry.height / outH)
                .put("convertMs", SystemClock.elapsedRealtime() - t)
                .put("read_with", readHint(path));
    }

    /**
     * Capture a hidden display from its own frame slot.
     *
     * Nothing waits here on purpose: shot() runs on the same handler that produces the
     * frames, so sleeping would stall the callback it is waiting for. A display without
     * a frame yet says so immediately and the caller retries.
     */
    JSONObject shot(JSONObject req) throws Exception {
        int sdid = req.has("displayId") ? req.optInt("displayId", -1) : displayId;
        if (sdid == 0) {
            if (req.optBoolean("allowMain", false)) return shotMain(req);
            return fail("REAL_SCREEN_REFUSED",
                    "display 0 is the real screen; pass allowMain=true if that is really intended");
        }
        Frame fr = frames.get(sdid);
        if (fr == null) {
            return fail("NO_FRAME", "display " + sdid
                    + " has no capture surface (released, or never created)");
        }
        if (!ensureShotDir()) return fail("PATH_NOT_ALLOWED", "cannot create " + SHOT_DIR);
        JSONObject bad = checkShotPath(req);
        if (bad != null) return bad;
        boolean jpeg = req.optString("format", "jpeg").equalsIgnoreCase("jpeg");
        String path = req.optString("path", SHOT_DIR + (jpeg ? "/shot.jpg" : "/shot.png"));
        float scale = clampScale((float) req.optDouble("scale", 0.5));
        int quality = req.optInt("quality", 80);

        if (fr.image == null) return fail("NO_FRAME", "display has not produced a frame yet; retry in ~100ms");
        DisplayGeometry geometry = geometry(sdid);
        if (fr.geometry == null || !geometry.matches(fr.geometry.json())) {
            fr.close(); lastScreenshot = null;
            return fail("FRAME_GEOMETRY_CHANGED", "display rotated; wait for a new frame and observe again");
        }
        Image.Plane plane = fr.image.getPlanes()[0];
        int fw = fr.image.getWidth(), fh = fr.image.getHeight();
        int stridePx = plane.getRowStride() / 4;
        ByteBuffer pixels = CapturePixels.prepare(plane.getBuffer(), fw, fh,
                plane.getRowStride(), plane.getPixelStride());
        long t = SystemClock.elapsedRealtime();
        Bitmap bmp = fr.bmp;
        if (bmp == null || bmp.getWidth() != stridePx || bmp.getHeight() != fh) {
            Bitmap old = fr.bmp;
            bmp = Bitmap.createBitmap(stridePx, fh, Bitmap.Config.ARGB_8888);
            fr.bmp = bmp;
            if (old != null) old.recycle();     // the cached frame is replaced, not leaked
        }
        bmp.copyPixelsFromBuffer(pixels);
        Bitmap work = bmp;
        if (stridePx != fw) work = Bitmap.createBitmap(bmp, 0, 0, fw, fh);
        Bitmap logical;
        try { logical = geometry.logicalBitmap(work); }
        catch (IllegalStateException mismatch) {
            if (work != bmp) work.recycle();
            return fail("CAPTURE_GEOMETRY_MISMATCH", "capture projection does not match the logical display; do not tap using this frame");
        }
        if (logical != work && work != bmp) work.recycle();
        work = logical;
        int scaledW = Math.max(1, Math.round(geometry.width * scale));
        int scaledH = Math.max(1, Math.round(geometry.height * scale));
        Bitmap out = work;
        if (scale < 0.99f) out = Bitmap.createScaledBitmap(work, scaledW, scaledH, true);
        try (FileOutputStream fo = new FileOutputStream(path)) {
            out.compress(jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG,
                    jpeg ? quality : 100, fo);
        } finally {
            if (out != work) out.recycle();     // scaled copy
            if (work != bmp) work.recycle();    // stride crop
        }
        publishShot(new java.io.File(path));
        lastScreenshot = new JSONObject().put("frameId", sdid + ":" + fr.receivedAt + ":" + frameOk)
                .put("displayId", sdid).put("width", scale < 0.99f ? scaledW : geometry.width)
                .put("height", scale < 0.99f ? scaledH : geometry.height).put("geometry", geometry.json()).put("capturedAt", fr.receivedAt);
        return new JSONObject().put("ok", true).put("path", path)
                .put("displayId", sdid)
                .put("format", jpeg ? "jpeg" : "png")
                .put("convertMs", SystemClock.elapsedRealtime() - t)
                .put("dims", lastScreenshot.getInt("width") + "x" + lastScreenshot.getInt("height"))
                .put("geometry", geometry.json()).put("frameId", lastScreenshot.getString("frameId"))
                .put("scaleToDisplayX", (double) geometry.width / lastScreenshot.getInt("width"))
                .put("scaleToDisplayY", (double) geometry.height / lastScreenshot.getInt("height"))
                .put("frameAgeMs", SystemClock.elapsedRealtime() - fr.receivedAt)
                .put("frameAgeChecked", false)
                .put("inputHint", "截图坐标请传 coordinate_space=screenshot 与本次 frame_id；节点 bounds 使用 display 坐标。旋转后重新观察。")
                // ready-to-copy next call: models kept dropping the required path argument
                .put("read_with", readHint(path));
    }

    boolean ensureShotDir() {
        java.io.File root = new java.io.File(SHOT_DIR);
        if (!root.isDirectory() && !root.mkdirs()) return false;
        // the app process (another uid) has to read these files for read_image
        root.setReadable(true, false);
        root.setExecutable(true, false);
        return true;
    }

    /** Make a shot readable by the app and tell the model exactly how to read it. */
    void publishShot(java.io.File f) {
        try {
            f.setReadable(true, false);
        } catch (Throwable ignored) { }
    }

    JSONObject readHint(String path) throws Exception {
        return new JSONObject().put("tool", "read_image")
                .put("args", new JSONObject().put("path", path));
    }

    float clampScale(float s) {
        if (s <= 0f || Float.isNaN(s)) return 0.5f;
        return s > 1f ? 1f : s;
    }

    /**
     * D3: the single close-out command. Three explicit outcomes:
     *   notify   - task stays parked on its hidden display, user brings it over
     *   handover - task moves back to the main display, hidden display released
     *   destroy  - hidden display released, task left to the system
     * "background" was the old name for handover and is still accepted, so old callers
     * keep working.
     */
    JSONObject finish(JSONObject req) throws Exception {
        String mode = req.optString("mode", "notify");
        if ("background".equals(mode)) mode = "handover";   // old name kept working
        if (!"notify".equals(mode) && !"handover".equals(mode) && !"destroy".equals(mode)) {
            return fail("BAD_ARGUMENT", "mode must be notify, handover or destroy (got " + mode + ")");
        }
        Session s = current;
        int did = s != null ? s.displayId
                : (req.has("displayId") ? req.optInt("displayId", -1) : displayId);
        JSONObject out = new JSONObject().put("mode", mode).put("displayId", did);

        // Nothing to close and nothing named: saying "ok" here is exactly how a failed
        // close-out used to look like success.
        if (s == null && did < 0) {
            return fail("NO_SESSION", "no hidden session to finish");
        }
        if (s == null) {
            if (did > 0) {
                return fail("NO_SESSION", "display " + did + " does not belong to a live session");
            }
            // main-screen session: no hidden display to move or release
            return out.put("ok", true).put("released", false).put("onscreen", true)
                    .put("note", "main screen session has no hidden display");
        }

        if ("handover".equals(mode)) {
            JSONObject h = handover(new JSONObject().put("displayId", did)
                    .put("package", s.pkg)
                    .put("toDisplay", 0)
                    .put("keepFront", req.optBoolean("keepFront", true)));
            out.put("handover", h);
            if (!h.optBoolean("ok", false)) {
                // the task did not get back to the main screen: keep the display so the
                // work is not lost, and say so instead of reporting success
                return out.put("ok", false).put("released", false);
            }
            if (h.optBoolean("moved", false)) {
                releaseSessionFully(s, false);
                out.put("released", true);
            } else {
                out.put("released", false).put("note", "nothing to hand over");
            }
        } else if ("destroy".equals(mode)) {
            releaseSessionFully(s, false);
            out.put("released", true);
        } else {
            s.orphanAt = SystemClock.elapsedRealtime();
            if (s == current) orphanAt = s.orphanAt;
            save();
            stopCapture(s);
            disposition.set(PARKED);
            work.removeCallbacks(patrol);
            work.postDelayed(patrol, 500);
            out.put("parked", true)
                    .put("hint", "本轮操作已结束，页面留待继续或打开；新任务需要屏幕容量时可能回收");
        }
        return out.put("ok", true);
    }

    /** Move the hidden display's root task to another display, and verify it arrived. */
    JSONObject presentCompleted(JSONObject req) throws Exception {
        Session session = current;
        try {
            if (session == null || session.orphanAt <= 0) return fail("NOT_PARKED", "页面尚未交还");
            JSONObject result = handover(new JSONObject().put("package", session.pkg).put("toDisplay", 0));
            if (!result.optBoolean("ok")) return result;
            // Raise the existing task; never deliver a new launcher Intent to a running game.
            try {
                Object atm = atmGetService.invoke(null);
                Method focus = atm.getClass().getMethod("setFocusedTask", int.class);
                focus.setAccessible(true);
                focus.invoke(atm, result.getInt("taskId"));
            } catch (Throwable focusFailure) { log("focus handed-over task: " + focusFailure.getClass().getSimpleName()); }
            // Retain the task identity for the shared occupancy check. A successful move alone
            // does not prove that a login/child task has also left the old display.
            session.taskId = result.getInt("taskId");
            if (session == current) { launchedTaskId = session.taskId; save(); }
            boolean ready = false;
            long readyDeadline = SystemClock.elapsedRealtime() + 1200;
            for (int attempt = 0; attempt < 8 && SystemClock.elapsedRealtime() < readyDeadline; attempt++) {
                if (foregroundWindowReady(session.pkg, 0)) { ready = true; break; }
                sleepCancellable(100);
            }
            disposition.compareAndSet(CLAIMED, PARKED);
            tickSession(session, SystemClock.elapsedRealtime());
            return result.put("released", session.vd == null).put("windowReady", ready)
                    .put("geometry", geometry(0).json());
        } finally {
            // A failed/expired RPC must not make the preserved screen permanently unclaimable.
            disposition.compareAndSet(CLAIMED, PARKED);
        }
    }

    /** Move the hidden display's root task to another display, and verify it arrived. */
    JSONObject handover(JSONObject req) throws Exception {
        String pkg = req.optString("package", "").trim();
        Session sess = null;
        int did;
        Integer taskId;
        if (!pkg.isEmpty()) {
            // a notification names the app it is about: never move whatever happens to be
            // attached right now
            sess = sessions.get(pkg);
            if (sess == null) return fail("NO_SESSION", "no live session for package " + pkg);
            did = sess.displayId;
            taskId = sess.taskId > 0 ? Integer.valueOf(sess.taskId) : findRootTaskSafe(did);
        } else {
            did = req.optInt("displayId", displayId);
            taskId = (sess == current && launchedTaskId > 0)
                    ? Integer.valueOf(launchedTaskId) : findRootTaskSafe(did);
        }
        int to = req.optInt("toDisplay", 0);
        boolean keepFront = req.optBoolean("keepFront", false);
        String previousFront = keepFront ? (sess != null ? sess.homeFront : homeFront) : "";
        long t = SystemClock.elapsedRealtime();
        if (did < 0 || taskId == null) {
            return fail("NO_SESSION", "no task on display " + did + " to hand over");
        }
        try {
            moveTask(taskId, to);
        } catch (Throwable e) {
            return fail("HANDOVER_FAILED", "moveTask(" + taskId + ") threw " + e
                    + "; the hidden display was kept");
        }
        if (!waitForTaskDisplay(taskId, to, 30)) {
            return fail("HANDOVER_FAILED", "task " + taskId + " stayed on display "
                    + displayOfTask(taskId) + " (wanted " + to + "); the hidden display was kept");
        }
        if (sess != null) {
            sess.handoffAt = SystemClock.elapsedRealtime();
            sess.taskId = -1;
            sess.orphanAt = SystemClock.elapsedRealtime();
            sess.homeFront = "";
            if (sess == current) {
                launchedTaskId = -1;
                orphanAt = sess.orphanAt;
                homeFront = "";
                taskCheckAt = 0; taskCheckResult = false;
                save();
            }
        } else {
            launchedTaskId = -1;
            taskCheckAt = 0; taskCheckResult = false;
            orphanAt = SystemClock.elapsedRealtime();
            homeFront = "";
            save();
        }
        if (keepFront && !previousFront.isEmpty()) restoreHome(previousFront);
        return new JSONObject().put("ok", true).put("moved", true)
                .put("taskId", taskId).put("toDisplay", to)
                .put("layoutRestored", false)
                .put("moveMs", SystemClock.elapsedRealtime() - t);
    }

    boolean foregroundWindowReady(String pkg, int did) {
        JSONObject probe;
        try { probe = proxyA11y(new JSONObject().put("cmd", "diag").put("displayId", did), 400); }
        catch (Exception unavailable) { return false; }
        if (probe == null || !probe.optBoolean("ok")) return false;
        org.json.JSONArray windows = probe.optJSONArray("windows");
        if (windows == null) return false;
        for (int index = 0; index < windows.length(); index++) {
            JSONObject window = windows.optJSONObject(index);
            if (window != null && pkg.equals(window.optString("package")) && window.optBoolean("focused")) return true;
        }
        return false;
    }

    /** Bring the app the user was looking at back to the front, a few times if needed. */
    void restoreHome(String component) {
        String wantPkg = component.contains("/")
                ? component.substring(0, component.indexOf('/')) : component;
        for (int i = 0; i < 3; i++) {
            if (wantPkg.equals(focusedPkg())) return;
            restoreFront(component);
            try { Thread.sleep(300); } catch (InterruptedException ignored) { }
        }
    }

    /** Resolve "pkg" to "pkg/cls" via the package manager; empty if unknown. */
    String resolveLauncher(String pkg) {
        try {
            String out = exec("cmd package resolve-activity --brief"
                    + " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "
                    + shellQuote(pkg));
            String[] lines = out.trim().split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String l = lines[i].trim();
                if (l.contains("/") && !l.startsWith("priority")) return l;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** Plain launch (no --display); the caller moves the task afterwards. */
    String amStartPlain(String comp) {
        String c = comp;
        if (!c.contains("/")) {
            String resolved = resolveLauncher(c);
            if (!resolved.isEmpty()) {
                log("resolved " + c + " -> " + resolved);
                c = resolved;
            }
        }
        if (c.contains("/")) return "am start -n " + shellQuote(c);
        return "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "
                + shellQuote(comp);
    }

    /** am start for a display, accepting either "pkg/cls" or a bare package name. */
    String amStartOnDisplay(int did, String comp) {
        String c = comp;
        if (!c.contains("/")) {
            String resolved = resolveLauncher(c);
            if (!resolved.isEmpty()) c = resolved;
        }
        if (c.contains("/")) {
            return "am start --display " + did + " -n " + shellQuote(c);
        }
        return "am start --display " + did
                + " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "
                + shellQuote(comp);
    }

    /** Keep the parked app out of the low-memory killer while it waits on the hidden display. */
    void protectApp(String pkg, boolean on) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            String pids = exec("pidof " + shellQuote(pkg)).trim();
            if (pids.isEmpty()) return;
            for (String p : pids.split("\\s+")) {
                if (p.isEmpty()) continue;
                try (java.io.FileOutputStream f =
                             new java.io.FileOutputStream("/proc/" + p + "/oom_score_adj")) {
                    f.write((on ? "-200" : "0").getBytes());
                }
            }
        } catch (Throwable t) {
            log("protectApp failed: " + t);
        }
    }

    void moveTask(int taskId, int display) throws Exception {
        if (atmGetService != null && atmMoveRootTask != null) {
            try {
                Object atm = atmGetService.invoke(null);
                atmMoveRootTask.invoke(atm, taskId, display);
                return;
            } catch (Throwable ignored) { }
        }
        exec("am display move-stack " + taskId + " " + display);
    }

    /** taskId whose base activity belongs to pkg, or null. */
    Integer taskOfPackage(String pkg) {
        try {
            if (atmGetService == null || atmAllRootTasks == null) return null;
            Object atm = atmGetService.invoke(null);
            List<?> tasks = (List<?>) atmAllRootTasks.invoke(atm);
            if (tasks == null) return null;
            for (Object ti : tasks) {
                Object comp = readField(ti, "baseActivity");
                if (comp == null) comp = readField(ti, "topActivity");
                String pn = null;
                if (comp instanceof ComponentName) pn = ((ComponentName) comp).getPackageName();
                else if (comp != null) pn = String.valueOf(comp);
                if (pn != null && pn.equals(pkg)) return readInt(ti.getClass(), ti, "taskId");
            }
        } catch (Throwable ignored) { }
        return null;
    }

    int displayOfTask(int taskId) {
        try {
            Object atm = atmGetService.invoke(null);
            List<?> tasks = (List<?>) atmAllRootTasks.invoke(atm);
            if (tasks == null) return -1;
            for (Object ti : tasks) {
                if (readInt(ti.getClass(), ti, "taskId") == taskId)
                    return readInt(ti.getClass(), ti, "displayId");
            }
        } catch (Throwable ignored) { }
        return -1;
    }

    Object readField(Object o, String name) {
        try { return o.getClass().getField(name).get(o); } catch (Throwable t) { return null; }
    }

    int[] sizeOf(int did) {
        int[] r = new int[]{W, H};
        try {
            Display d = dm.getDisplay(did);
            if (d != null) {
                android.graphics.Point p = new android.graphics.Point();
                d.getRealSize(p);
                if (p.x > 0 && p.y > 0) { r[0] = p.x; r[1] = p.y; }
            }
        } catch (Throwable ignored) { }
        return r;
    }

    JSONObject checkPoint(int did, float x, float y) {
        DisplayGeometry g = geometry(did);
        if (!g.contains(x, y))
            return fail("COORD_OUT_OF_BOUNDS", x + "," + y + " vs " + g.width + "x" + g.height);
        return null;
    }

    DisplayGeometry geometry(int did) {
        Display display = dm == null ? null : dm.getDisplay(did);
        if (display == null) return new DisplayGeometry(0, 0, -1, did, 0);
        android.graphics.Point size = new android.graphics.Point(); display.getRealSize(size);
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics(); display.getRealMetrics(metrics);
        return new DisplayGeometry(size.x, size.y, display.getRotation(), did, metrics.densityDpi);
    }

    int requestedOrientation(String component) {
        if (component == null || component.isEmpty()) return -1;
        try {
            ComponentName name = ComponentName.unflattenFromString(component);
            if (name == null) {
                Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(component);
                if (launch != null) name = launch.getComponent();
            }
            return name == null ? -1 : ctx.getPackageManager().getActivityInfo(name, 0).screenOrientation;
        } catch (Exception unavailable) { return -1; }
    }

    boolean isPointerCommand(String cmd) {
        return "tap".equals(cmd) || "press".equals(cmd) || "drag".equals(cmd) || "swipe".equals(cmd);
    }

    JSONObject stalePointerFallback(JSONObject req, JSONObject observed, int did) throws Exception {
        String code = observed == null ? "" : observed.optString("code");
        if ("STALE_OBSERVATION".equals(code) || "GEOMETRY_CHANGED_DURING_OPERATION".equals(code)
                || "GEOMETRY_UNAVAILABLE".equals(code) || "INVALID_OBSERVATION".equals(code)) return observed;
        return ScreenGeometry.check(req.optJSONObject("expectedGeometry"), geometry(did).json());
    }

    JSONObject preparePointerCoordinates(JSONObject req) throws Exception {
        int did = req.optInt("displayId", displayId);
        DisplayGeometry g = geometry(did);
        JSONObject invalid = ScreenGeometry.check(req.optJSONObject("expectedGeometry"), g.json());
        if (invalid != null) return invalid;
        req.put("expectedGeometry", g.json());
        String space = req.optString("coordinateSpace", "display");
        if (!"display".equals(space) && !"screenshot".equals(space)) return fail("BAD_ARGUMENT", "unknown coordinate space");
        if (!"screenshot".equals(space)) return null;
        String reason = lastScreenshot == null ? "NO_OBSERVATION" :
                lastScreenshot.optInt("displayId", -1) != did ? "DISPLAY_MISMATCH" :
                !g.matches(lastScreenshot.optJSONObject("geometry")) ? "GEOMETRY_MISMATCH" :
                !req.optString("frameId").equals(lastScreenshot.optString("frameId")) ? "FRAME_ID_MISMATCH" : null;
        if (reason != null) return ScreenGeometry.reject("STALE_OBSERVATION", reason,
                lastScreenshot == null ? null : lastScreenshot.optJSONObject("geometry"), g.json())
                .put("requestedFrameId", req.optString("frameId"))
                .put("availableFrameId", lastScreenshot == null ? JSONObject.NULL : lastScreenshot.optString("frameId"))
                .put("frameAgeMs", lastScreenshot == null ? JSONObject.NULL :
                        SystemClock.elapsedRealtime() - lastScreenshot.optLong("capturedAt", SystemClock.elapsedRealtime()));
        int width = lastScreenshot.getInt("width"), height = lastScreenshot.getInt("height");
        for (String suffix : new String[]{"", "1", "2"}) {
            String x = "x" + suffix, y = "y" + suffix;
            if (!req.has(x) || !req.has(y)) continue;
            double px = req.getDouble(x), py = req.getDouble(y);
            if (!new DisplayGeometry(width, height, 0).contains(px, py)) return fail("COORD_OUT_OF_BOUNDS", "outside screenshot pixels");
            req.put(x, px * g.width / width).put(y, py * g.height / height);
        }
        return null;
    }

    /** Package of the activity currently focused on the main display (fresh, no cache). */
    String focusedPkg() {
        String comp = focusedComponent();
        if (comp.isEmpty()) return "";
        int i = comp.indexOf('/');
        return i > 0 ? comp.substring(0, i) : comp;
    }

    String focusedComponent() {
        try {
            Class<?> iface = Class.forName("android.app.IActivityTaskManager");
            Method m = reflect(iface, "getFocusedRootTaskInfo");
            if (m != null && atmGetService != null) {
                Object info = m.invoke(atmGetService.invoke(null));
                if (info == null) return "";
                int disp = readInt(info.getClass(), info, "displayId");
                if (disp != Display.DEFAULT_DISPLAY) return "";   // focused task is not on the main screen
                Object comp = readField(info, "topActivity");
                if (comp instanceof ComponentName) return ((ComponentName) comp).flattenToString();
            }
        } catch (Throwable ignored) { }
        return "";
    }

    boolean hasTaskOnDisplay() {
        long now = SystemClock.elapsedRealtime();
        if (now - taskCheckAt < 2000) return taskCheckResult;
        Integer t = null;
        try { t = findRootTask(displayId); } catch (Throwable ignored) { }
        taskCheckResult = t != null && t > 0;
        taskCheckAt = now;
        return taskCheckResult;
    }

    /** Bring the app that owned the foreground back on top, so the user is not yanked away. */
    void restoreFront(String component) {
        try {
            Thread.sleep(150);
            exec("am start -n " + shellQuote(component));
            log("restored foreground: " + component);
        } catch (Throwable t) {
            log("restore foreground failed: " + t);
        }
    }

    Integer findRootTaskSafe(int did) {
        try { return findRootTask(did); } catch (Throwable t) { return null; }
    }

    Integer findRootTask(int did) throws Exception {
        if (atmAllRootTasks == null) return null;
        Object atm = atmGetService.invoke(null);
        List<?> tasks = (List<?>) atmAllRootTasks.invoke(atm);
        if (tasks == null) return null;
        for (Object ti : tasks) {
            Class<?> c = ti.getClass();
            int taskId = readInt(c, ti, "taskId");
            int display = readInt(c, ti, "displayId");
            if (display == did && taskId > 0) return taskId;
        }
        return null;
    }

    int readInt(Class<?> c, Object o, String field) {
        try {
            Field f = c.getField(field);
            return f.getInt(o);
        } catch (Throwable t) {
            try {
                Method m = c.getMethod("get" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
                return (Integer) m.invoke(o);
            } catch (Throwable t2) {
                return -1;
            }
        }
    }

    JSONObject notifyUser(JSONObject req) throws Exception {
        String title = req.optString("title", "任务完成");
        String text = req.optString("text", "");
        String open = req.optString("open", "");
        android.app.NotificationManager nm = ctx.getSystemService(android.app.NotificationManager.class);
        String channel = "eta-bg";
        nm.createNotificationChannel(new android.app.NotificationChannel(
                channel, "后台任务", android.app.NotificationManager.IMPORTANCE_DEFAULT));
        android.app.Notification.Builder b = new android.app.Notification.Builder(ctx, channel)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true);
        if (!open.isEmpty()) {
            android.content.Intent intent = android.content.Intent.parseUri(open, 0);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 1, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            b.setContentIntent(pi);
        }
        JSONObject a = proxyA11y(new JSONObject().put("cmd", "notify")
                .put("title", title).put("text", text).put("open", open));
        if (a != null && a.optBoolean("ok") && !a11yDown(a)) {
            // the observer claims success; only trust it once the shade shows the record
            if (notificationVisible(title)) {
                return new JSONObject().put("ok", true).put("title", title).put("via", "a11y");
            }
            log("a11y notify reported ok but nothing is visible; trying the shell path");
        }
        String via = "nm";
        try {
            // ColorOS silently drops notifications posted under the "android" package,
            // so shell's notification command is the fallback
            String out = exec("cmd notification post -S bigtext -t " + shellQuote(title)
                    + " eta-bg " + shellQuote(text)).trim();
            log("notify cmd: " + out);
            if (!out.toLowerCase().contains("error") && !out.toLowerCase().contains("exception")) via = "cmd";
        } catch (Throwable ignored) {
        }
        if ("nm".equals(via)) {
            try { nm.notify("eta-bg", 1, b.build()); } catch (Throwable t) { log("nm.notify failed: " + t); }
        }
        // Neither path is trusted until the notification is actually in the shade: this
        // ROM accepted "cmd notification post" while silently dropping the record.
        if (notificationVisible(title)) {
            return new JSONObject().put("ok", true).put("title", title).put("via", via);
        }
        return new JSONObject().put("ok", false).put("code", "NOTIFY_NOT_DELIVERED")
                .put("title", title).put("via", via)
                .put("error", "no visible notification after posting; the accessibility"
                        + " service that posts under the app uid is probably not connected");
    }

    /** True when a notification with this title is actually listed by the system. */
    boolean notificationVisible(String title) {
        for (int i = 0; i < 3; i++) {
            try {
                String dump = exec("dumpsys notification --noredact | grep -F "
                        + shellQuote("android.title=String (" + title + ")"));
                if (dump != null && dump.contains(title)) return true;
            } catch (Throwable ignored) { }
            try { Thread.sleep(250); } catch (InterruptedException ignored) { }
        }
        return false;
    }

    String exec(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = p.getInputStream().read(tmp)) > 0) b.write(tmp, 0, n);
        p.waitFor();
        return new String(b.toByteArray(), "UTF-8");
    }

    String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    void shutdown() {
        if (dm != null) dm.unregisterDisplayListener(geometryListener);
        for (Session x : new java.util.ArrayList<>(sessions.values())) {
            releaseSessionFully(x, false);
        }
        destroyIdleDisplay();
        // Only this worker's private temporary directory; never follow symlinks or touch custom output paths.
        java.nio.file.Path directory = java.nio.file.Paths.get(SHOT_DIR);
        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { java.nio.file.Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        } catch (java.nio.file.NoSuchFileException ignored) {
        } catch (Exception error) { log("temporary capture cleanup: " + error.getClass().getSimpleName()); }
    }

    /**
     * The --tcp entrypoint parks in Looper.loop() and never reaches the stdin
     * loop, so a plain "closed = true" cannot end the process. Exit explicitly
     * after a short delay so the response still reaches the client.
     */
    void quitSoon() {
        new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(250); } catch (InterruptedException ignored) { }
                log("quit requested, exiting");
                shutdown();
                workerThread.quitSafely();
            }
        }).start();
    }
}
