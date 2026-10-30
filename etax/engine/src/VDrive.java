import android.content.Context;
import android.os.Looper;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.*;
import java.io.*;
import java.util.*;

/** Root transport. Mutable display/input/capture state lives in independent workers. */
public final class VDrive {
    static final String BUILD = "20261030-e40-takeover";
    static java.util.function.BooleanSupplier health = () -> true;
    static volatile boolean systemInvalid;
    private static final String INSTANCE = UUID.randomUUID().toString();
    private static int pid;
    private static String dexHash = "";
    static String credential;
    private static final java.util.concurrent.Semaphore clients = new java.util.concurrent.Semaphore(16);
    static void systemFailed() {
        if (!systemInvalid) log("event=system_connection_lost instance=" + INSTANCE);
        systemInvalid = true;
    }
    static boolean healthy() {
        if (!health.getAsBoolean()) systemFailed();
        return !systemInvalid;
    }
    static JSONObject failure(Throwable error) {
        if (com.eta.shadow.EngineFailures.systemUnavailable(error)) systemFailed();
        if (systemInvalid) {
            JSONObject result = error("SYSTEM_RESTARTED", "Android 系统服务连接已失效，请等待引擎恢复后重新打开屏幕");
            try { result.put("operationMayHaveRun", true); } catch (Exception ignored) { }
            return result;
        }
        return error("ENGINE_ERROR", error.getClass().getSimpleName() + ": " + error.getMessage());
    }
    interface Worker {
        JSONObject processLine(String request);
        boolean closeSession();
        void keepAlive();
        int displayId();
        default JSONObject geometrySnapshot() throws Exception { return null; }
        default boolean isParked() { return false; }
        default boolean claimParked() { return isParked(); }
        default boolean isCapturing() { return false; }
        default boolean isHandingOver() { return false; }
        default boolean isTakenOver() { return false; }
        default JSONObject foregroundGuard() { return null; }
        default String commandState() { return "idle"; }
        default JSONObject observationGuard(boolean mutating) { return null; }
        default void observationAccepted() { }
        default void reconcileForeground() { }
        default void setReleaseListener(Runnable listener) { }
    }
    interface WorkerFactory { Worker create(Context context) throws Exception; }
    static WorkerFactory factory = VDriveWorker::new;
    private static Context context;
    static java.util.function.LongSupplier clock = () -> System.nanoTime() / 1000000L;
    private static long now() { return clock.getAsLong(); }
    private static final Map<String, Slot> workers = new LinkedHashMap<>();
    private static final int LIMIT = 2;
    private static final Set<String> retired = new LinkedHashSet<>();
    private static final Set<String> takenOverIds = new LinkedHashSet<>();
    private static void retire(String id) {
        if (id.equals("legacy-mcp")) return;
        retired.add(id);
        while (retired.size() > 4096) {
            String oldest = retired.iterator().next();
            retired.remove(oldest); takenOverIds.remove(oldest);
        }
    }
    private static final class Slot {
        volatile Worker worker;
        final String pkg;
        volatile boolean closing, parked;
        int inFlight;
        volatile long touched = now();
        Slot(Worker worker, String pkg) { this.worker = worker; this.pkg = pkg; }
    }

    public static void main(String[] args) throws Exception {
        // Fail before attaching to Android services, without exposing credentials or a bare stack trace.
        try {
            credential = com.eta.shadow.EngineWire.readToken(new File("/data/local/tmp/eta/engine.token"));
        } catch (com.eta.shadow.EngineWire.CredentialException error) {
            System.err.println("ETA_ENGINE_ERROR=" + error.code);
            System.exit(78);
            return;
        }
        Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Method systemMain = at.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object activityThread = systemMain.invoke(null);
        java.lang.reflect.Method getContext = at.getDeclaredMethod("getSystemContext");
        getContext.setAccessible(true);
        context = (Context) getContext.invoke(activityThread);
        pid = android.os.Process.myPid();
        health = new SystemServiceHealth();
        try (InputStream input = new FileInputStream("/data/local/tmp/eta/vdrive.dex")) {
            dexHash = com.eta.shadow.EngineWire.sha256(input);
        }
        try (FileOutputStream out = new FileOutputStream("/proc/self/oom_score_adj")) {
            out.write("-800".getBytes("UTF-8"));
        } catch (Exception ignored) { }
        int port = 0, mcpPort = 0;
        for (String arg : args) {
            if (arg.startsWith("--tcp=")) port = Integer.parseInt(arg.substring(6));
            if (arg.startsWith("--mcp=")) {
                String value = arg.substring(6);
                mcpPort = value.matches("[0-9]+") ? Integer.parseInt(value) : 0;
            }
            if (arg.equals("--no-mcp")) mcpPort = 0;
        }
        log("build " + BUILD);
        Thread reaper = new Thread(() -> {
            while (true) {
                try { Thread.sleep(10000); } catch (InterruptedException e) { return; }
                reapExpired();
            }
        }, "vdrive-expiry");
        reaper.setDaemon(true);
        reaper.start();
        if (port > 0) {
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            try { server.bind(new InetSocketAddress("127.0.0.1", port)); }
            catch (java.net.BindException busy) {
                server.close();
                System.err.println("ETA_ENGINE_ERROR=PORT_BUSY");
                System.exit(98);
                return;
            }
            new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket client = server.accept();
                        if (!clients.tryAcquire()) { client.close(); continue; }
                        Thread t = new Thread(() -> { try { serve(client); } finally { clients.release(); } }, "vdrive-client");
                        t.setDaemon(true);
                        t.start();
                    } catch (IOException e) { log(e.getClass().getSimpleName()); }
                }
            }, "vdrive-listen").start();
            if (mcpPort > 0) McpEndpoint.start(mcpPort);
            Looper.loop();
        } else {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) System.out.println("RESULT:" + processLine(line));
        }
    }

    private static void serve(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(10000);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            PrintStream out = new PrintStream(client.getOutputStream(), true, "UTF-8");
            String line;
            while ((line = com.eta.shadow.EngineWire.readLine(in, com.eta.shadow.EngineWire.REQUEST_LIMIT)) != null) {
                JSONObject request = new JSONObject(line);
                if (!com.eta.shadow.EngineWire.authorized(request, credential)) {
                    out.println("RESULT:" + error("UNAUTHORIZED", "engine credential required"));
                    return;
                }
                request.remove("auth");
                out.println("RESULT:" + processLine(request.toString()));
            }
        } catch (Exception e) { log("client: " + e.getClass().getSimpleName()); }
    }

    static JSONObject processLine(String payload) {
        try {
            JSONObject req = new JSONObject(payload);
            String cmd = req.optString("cmd");
            String id = req.optString("sessionId", "legacy-mcp");
            if (cmd.equals("quit")) {
                // Exit stays bounded even when cleanup encounters a dead/hung Binder.
                new Thread(() -> { try { Thread.sleep(1500); } catch (Exception ignored) {} System.exit(0); }, "vdrive-exit").start();
                new Thread(() -> {
                    List<String> ids;
                    synchronized (workers) { ids = new ArrayList<>(workers.keySet()); }
                    for (String key : ids) release(key);
                }, "vdrive-cleanup").start();
                return ok();
            }
            if (cmd.equals("status") || cmd.equals("ping") || cmd.equals("sessions")) {
                JSONArray sessions = new JSONArray();
                Map<String, Slot> snapshot;
                synchronized (workers) {
                    snapshot = new LinkedHashMap<>(workers);
                }
                    for (Map.Entry<String, Slot> e : snapshot.entrySet()) {
                        Worker worker = e.getValue().worker;
                        sessions.put(new JSONObject().put("sessionId", e.getKey()).put("package", e.getValue().pkg)
                                .put("closing", e.getValue().closing).put("parked", e.getValue().parked)
                                .put("initializing", worker == null)
                                .put("capturing", worker != null && worker.isCapturing())
                                .put("handingOver", worker != null && worker.isHandingOver())
                                .put("takenOver", worker != null && worker.isTakenOver())
                                .put("commandState", worker == null ? "initializing" : worker.commandState())
                                .put("displayId", worker == null ? -1 : worker.displayId()));
                    }
                boolean ready = healthy();
                boolean sessionTakenOver;
                synchronized (workers) {
                    Slot selected = workers.get(id);
                    sessionTakenOver = takenOverIds.contains(id) || selected != null && selected.worker != null && selected.worker.isTakenOver();
                }
                return new JSONObject().put("ok", ready).put("pong", ready).put("healthy", ready).put("build", BUILD)
                        .put("sessionTakenOver", sessionTakenOver)
                        .put("code", ready ? "READY" : "SYSTEM_RESTARTED").put("pid", pid).put("instance", INSTANCE)
                        .put("dexSha256", dexHash)
                        .put("sessions", sessions).put("count", sessions.length()).put("limit", LIMIT);
            }
            if (!healthy() && !cmd.equals("release_session"))
                return error("SYSTEM_RESTARTED", "Android 系统服务连接已失效；请等待引擎恢复后重新 screen_open");
            if (cmd.equals("present_completed")) return presentCompleted(req);
            if (cmd.equals("release_session")) {
                if (req.optBoolean("keep", false)) {
                    synchronized (workers) {
                        Slot parked = workers.get(id);
                        if (parked == null) { retire(id); return ok(); }
                        if (parked.worker == null || parked.closing || parked.inFlight != 0) return error("CLEANUP_PENDING", "屏幕仍在收尾");
                        if (!parked.worker.isParked()) return error("NOT_PARKED", "屏幕尚未完成收尾");
                        retire(id);
                        if (!parked.parked) {
                            parked.parked = true;
                            parked.touched = now(); // Idempotent retries must not extend retention.
                        }
                    }
                    return ok();
                }
                return release(id);
            }
            if (cmd.equals("foreground_hint")) {
                Worker target = null;
                synchronized (workers) {
                    Slot candidate = workers.get(id);
                    if (takenOverIds.contains(id)) return takeoverError();
                    if (candidate != null && candidate.worker != null && candidate.worker.isTakenOver()) return takeoverError();
                    if (candidate != null && !candidate.closing) target = candidate.worker;
                }
                // A hint is never proof: the worker checks actual task/display placement.
                if (target != null) {
                    JSONObject moved = target.foregroundGuard();
                    target.reconcileForeground();
                    if (moved != null) return moved;
                }
                return ok();
            }
            synchronized (workers) {
                if (takenOverIds.contains(id)) return takeoverError();
            }
            if (cmd.equals("launch")) {
                String pkg = req.optString("component").split("/")[0];
                String reclaim = null;
                synchronized (workers) {
                    if (retired.contains(id)) return closedSessionError(id);
                    Slot old = workers.get(id);
                    if (id.equals("legacy-mcp") && old != null && !old.pkg.equals(pkg)) reclaim = id;
                    String adopt = null;
                    if (old == null) for (Map.Entry<String, Slot> entry : workers.entrySet()) {
                        Slot candidate = entry.getValue();
                        if (candidate.pkg.equals(pkg) && candidate.parked && !candidate.closing && !candidate.worker.isHandingOver()
                                && candidate.inFlight == 0 && retired.contains(entry.getKey()) && candidate.worker.claimParked()) {
                            adopt = entry.getKey(); break;
                        }
                    }
                    if (adopt != null) {
                        Slot candidate = workers.remove(adopt);
                        candidate.parked = false;
                        candidate.touched = now();
                        workers.put(id, candidate);
                    } else for (Map.Entry<String, Slot> entry : workers.entrySet()) {
                        if (entry.getValue().parked && entry.getValue().inFlight == 0 && entry.getValue().worker.isParked()
                                && !entry.getValue().worker.isHandingOver()
                                && (entry.getValue().pkg.equals(pkg) || workers.size() >= LIMIT)) {
                            reclaim = entry.getKey(); break;
                        }
                    }
                }
                if (reclaim != null && !release(reclaim).optBoolean("ok")) return error("CLEANUP_PENDING", "等待旧屏幕清理");
            }
            Slot slot;
            boolean initialize = false;
            synchronized (workers) {
                // release can race the preliminary launch/reclaim check above.
                if (retired.contains(id) && !cmd.equals("cancel")) return closedSessionError(id);
                slot = workers.get(id);
                if (id.equals("legacy-mcp") && cmd.equals("finish") && req.has("package")) {
                    for (Slot candidate : workers.values()) if (candidate.parked && candidate.pkg.equals(req.optString("package"))) slot = candidate;
                }
                if (slot == null && cmd.equals("launch")) {
                    String pkg = req.optString("component").split("/")[0];
                    for (Slot existing : workers.values()) {
                        if (existing.pkg.equals(pkg)) return error("APP_BUSY", "应用正被另一任务使用");
                    }
                    if (workers.size() >= LIMIT) return error(
                            workers.values().stream().anyMatch(value -> value.closing ||
                                    value.worker != null && value.worker.isHandingOver()) ? "CLEANUP_PENDING" : "SESSION_LIMIT",
                            "等待旧屏幕清理完成");
                    Slot created = new Slot(null, pkg);
                    slot = created;
                    workers.put(id, created);
                    initialize = true;
                }
                if (slot == null) return error("NO_SESSION", "请先打开本任务的屏幕");
                slot.touched = now();
                if (slot.worker != null && slot.worker.isTakenOver() && !cmd.equals("finish") && !cmd.equals("cancel")) return takeoverError();
                if (slot.closing && !cmd.equals("cancel")) return error("SESSION_CLOSED", "屏幕正在释放");
            }
            // Reserve capacity above, but never hold the session monitor across system calls.
            if (initialize) {
                final Slot created = slot;
                try {
                    created.worker = factory.create(context);
                    created.worker.setReleaseListener(() -> workerReleased(created));
                } catch (Throwable error) {
                    synchronized (workers) { if (workers.get(id) == created) workers.remove(id); }
                    return failure(error);
                }
                boolean discarded;
                synchronized (workers) { discarded = workers.get(id) != created || created.closing || retired.contains(id); }
                if (discarded) { created.worker.closeSession(); return error("SESSION_CLOSED", "创建期间任务已结束"); }
            }
            if (slot.worker == null) return error("SESSION_STARTING", "屏幕正在初始化");
            if (cmd.equals("keepalive")) {
                JSONObject moved = slot.worker.foregroundGuard();
                if (moved != null) return moved;
                slot.worker.keepAlive(); return ok();
            }
            if (cmd.equals("validate")) {
                    if (slot.closing || req.optInt("displayId", -1) != slot.worker.displayId()) return error("STALE_SCREEN", "屏幕已失效");
                    JSONObject moved = slot.worker.foregroundGuard();
                    if (moved != null) return moved;
                    JSONObject blocked = slot.worker.observationGuard(VDriveWorker.isMutatingInput(req.optString("action")));
                    if (blocked != null) return blocked;
                    // A direct DisplayManager read, never a queued worker RPC: the worker may
                    // be waiting for this observer callback on its own handler.
                    JSONObject geometry = slot.worker.geometrySnapshot();
                    if (!com.eta.shadow.ScreenGeometry.valid(geometry)) return error("STALE_SCREEN", "目标显示屏已不可用")
                            .put("reason", "DISPLAY_REMOVED").put("displayId", req.optInt("displayId", -1));
                    synchronized (workers) {
                        if (workers.get(id) != slot || slot.closing) return error("STALE_SCREEN", "屏幕已释放");
                    }
                    if (req.optBoolean("observed") && ("dump".equals(req.optString("action")) || "list".equals(req.optString("action")))
                            && com.eta.shadow.ScreenGeometry.same(req.optJSONObject("expectedGeometry"), geometry))
                        slot.worker.observationAccepted();
                    return ok().put("geometry", geometry);
            }
            if (!id.equals("legacy-mcp")) {
                if (cmd.equals("launch")) req.put("forceHidden", true);
                if (cmd.equals("handover")) return error("BACKGROUND_ONLY", "后台任务不会自动切换到主屏，请从桌面打开目标应用");
                if (cmd.equals("finish") && (req.optString("mode").equals("handover") || req.optString("mode").equals("background"))) {
                    req.put("mode", "notify");
                }
            }
            synchronized (workers) {
                if ((!id.equals("legacy-mcp") && (workers.get(id) != slot || retired.contains(id))) || slot.closing) return error("SESSION_CLOSED", "屏幕已释放");
                slot.inFlight++;
            }
            try { return slot.worker.processLine(req.toString()); }
            finally { synchronized (workers) { slot.inFlight--; } }
        } catch (Exception e) { return failure(e); }
    }

    static void reapExpired() {
        List<String> expired = new ArrayList<>();
        synchronized (workers) {
            long time = now();
            for (Map.Entry<String, Slot> entry : workers.entrySet()) {
                Slot slot = entry.getValue();
                // A completed task is not a dead client. Its worker watches actual occupancy;
                // time alone must not destroy a game while the user is deciding what to do.
                if (slot.closing || (!slot.parked && time - slot.touched > 300000)) expired.add(entry.getKey());
            }
        }
        for (String id : expired) release(id);
    }

    private static JSONObject presentCompleted(JSONObject request) throws Exception {
        String run = request.optString("runId"), pkg = request.optString("package");
        if (run.isEmpty() || pkg.isEmpty()) return error("BAD_ARGUMENT", "缺少任务信息");
        Slot target = null;
        synchronized (workers) {
            for (Map.Entry<String, Slot> entry : workers.entrySet()) {
                Slot slot = entry.getValue();
                if (!slot.pkg.equals(pkg)) continue;
                if (!entry.getKey().startsWith(run + ":") || entry.getKey().lastIndexOf(':') != run.length()
                        || !slot.parked || slot.closing || slot.inFlight != 0
                        || !slot.worker.claimParked()) return error("APP_BUSY", "应用正在被其他操作使用，请先查看聊天");
                target = slot;
                target.inFlight++;
                break;
            }
        }
        if (target == null) return error("NOT_FOUND", "已没有保留的后台页面");
        try {
            return target.worker.processLine(new JSONObject().put("cmd", "present").put("package", pkg).toString());
        } finally { synchronized (workers) { target.inFlight--; } }
    }

    private static void workerReleased(Slot released) {
        synchronized (workers) {
            Iterator<Map.Entry<String, Slot>> entries = workers.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<String, Slot> entry = entries.next();
                if (entry.getValue() == released) {
                    if (released.worker != null && released.worker.isTakenOver()) takenOverIds.add(entry.getKey());
                    retire(entry.getKey()); entries.remove();
                }
            }
        }
    }

    private static JSONObject release(String id) {
        Slot slot;
        synchronized (workers) {
            retire(id);
            slot = workers.get(id);
            if (slot == null) return ok();
            slot.closing = true;
        }
        if (slot.worker == null || !slot.worker.closeSession()) return error("CLEANUP_PENDING", "旧操作尚未退出，屏幕容量仍保留");
        synchronized (workers) { if (workers.get(id) == slot) workers.remove(id); }
        return ok();
    }

    static int obsPort() { return 8738; }
    private static JSONObject takeoverError() {
        return error("APP_TAKEN_OVER", "应用已离开后台屏，后台操作已暂停；请由用户决定是否继续，不要自动重新打开或拉回应用。");
    }
    private static JSONObject closedSessionError(String id) {
        Slot slot = workers.get(id); // Called under the workers monitor.
        return takenOverIds.contains(id) || slot != null && slot.worker != null && slot.worker.isTakenOver()
                ? takeoverError() : error("SESSION_CLOSED", "任务已结束");
    }
    static boolean mainDisplayOn() {
        if (context == null) return false;
        android.view.Display main = context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
        return main != null && main.getState() == android.view.Display.STATE_ON;
    }
    static String plain(String value) { return value.replace("\n", " ").replace("\r", " "); }
    static void log(String value) { System.err.println("[vdrive] " + value); }
    private static JSONObject ok() { try { return new JSONObject().put("ok", true); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static JSONObject error(String code, String message) {
        try { return new JSONObject().put("ok", false).put("code", code).put("message", message); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
