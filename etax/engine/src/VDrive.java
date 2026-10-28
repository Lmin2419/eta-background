import android.content.Context;
import android.os.Looper;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.*;
import java.io.*;
import java.util.*;

/** Root transport. Mutable display/input/capture state lives in independent workers. */
public final class VDrive {
    static final String BUILD = "20261027-e34-authority";
    interface Worker {
        JSONObject processLine(String request);
        boolean closeSession();
        void keepAlive();
        int displayId();
        default JSONObject geometrySnapshot() throws Exception { return null; }
        default boolean isParked() { return false; }
        default boolean claimParked() { return isParked(); }
        default boolean isCapturing() { return false; }
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
    private static void retire(String id) {
        if (id.equals("legacy-mcp")) return;
        retired.add(id);
        while (retired.size() > 4096) retired.remove(retired.iterator().next());
    }
    private static final class Slot {
        final Worker worker;
        final String pkg;
        boolean closing, parked;
        int inFlight;
        volatile long touched = now();
        Slot(Worker worker, String pkg) { this.worker = worker; this.pkg = pkg; }
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Method systemMain = at.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object activityThread = systemMain.invoke(null);
        java.lang.reflect.Method getContext = at.getDeclaredMethod("getSystemContext");
        getContext.setAccessible(true);
        context = (Context) getContext.invoke(activityThread);
        try (FileOutputStream out = new FileOutputStream("/proc/self/oom_score_adj")) {
            out.write("-800".getBytes("UTF-8"));
        } catch (Exception ignored) { }
        int port = 0, mcpPort = 8735;
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
            server.bind(new InetSocketAddress("127.0.0.1", port));
            new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket client = server.accept();
                        Thread t = new Thread(() -> serve(client), "vdrive-client");
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
            client.setSoTimeout(120000);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            PrintStream out = new PrintStream(client.getOutputStream(), true, "UTF-8");
            String line;
            while ((line = in.readLine()) != null) out.println("RESULT:" + processLine(line));
        } catch (Exception e) { log("client: " + e.getClass().getSimpleName()); }
    }

    static JSONObject processLine(String payload) {
        try {
            JSONObject req = new JSONObject(payload);
            String cmd = req.optString("cmd");
            String id = req.optString("sessionId", "legacy-mcp");
            if (cmd.equals("status") || cmd.equals("ping") || cmd.equals("sessions")) {
                JSONArray sessions = new JSONArray();
                synchronized (workers) {
                    for (Map.Entry<String, Slot> e : workers.entrySet()) {
                        sessions.put(new JSONObject().put("sessionId", e.getKey()).put("package", e.getValue().pkg)
                                .put("closing", e.getValue().closing).put("parked", e.getValue().parked)
                                .put("capturing", e.getValue().worker.isCapturing()).put("displayId", e.getValue().worker.displayId()));
                    }
                }
                return new JSONObject().put("ok", true).put("pong", true).put("build", BUILD)
                        .put("sessions", sessions).put("count", sessions.length()).put("limit", LIMIT);
            }
            if (cmd.equals("quit")) {
                List<String> ids;
                synchronized (workers) { ids = new ArrayList<>(workers.keySet()); }
                for (String key : ids) release(key);
                new Thread(() -> { try { Thread.sleep(300); } catch (Exception ignored) {} System.exit(0); }).start();
                return ok();
            }
            if (cmd.equals("present_completed")) return presentCompleted(req);
            if (cmd.equals("release_session")) {
                if (req.optBoolean("keep", false)) {
                    synchronized (workers) {
                        Slot parked = workers.get(id);
                        if (parked == null) { retire(id); return ok(); }
                        if (parked.closing || parked.inFlight != 0) return error("CLEANUP_PENDING", "屏幕仍在收尾");
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
                    if (candidate != null && candidate.parked && !candidate.closing) target = candidate.worker;
                }
                // A hint is never proof: the worker checks actual task/display placement.
                if (target != null) target.reconcileForeground();
                return ok();
            }
            if (cmd.equals("launch")) {
                String pkg = req.optString("component").split("/")[0];
                String reclaim = null;
                synchronized (workers) {
                    if (retired.contains(id)) return error("SESSION_CLOSED", "任务已结束");
                    Slot old = workers.get(id);
                    if (id.equals("legacy-mcp") && old != null && !old.pkg.equals(pkg)) reclaim = id;
                    String adopt = null;
                    if (old == null) for (Map.Entry<String, Slot> entry : workers.entrySet()) {
                        Slot candidate = entry.getValue();
                        if (candidate.pkg.equals(pkg) && candidate.parked && !candidate.closing
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
                                && (entry.getValue().pkg.equals(pkg) || workers.size() >= LIMIT)) {
                            reclaim = entry.getKey(); break;
                        }
                    }
                }
                if (reclaim != null && !release(reclaim).optBoolean("ok")) return error("CLEANUP_PENDING", "等待旧屏幕清理");
            }
            Slot slot;
            synchronized (workers) {
                // release can race the preliminary launch/reclaim check above.
                if (retired.contains(id) && !cmd.equals("cancel")) return error("SESSION_CLOSED", "任务已结束");
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
                            workers.values().stream().anyMatch(value -> value.closing) ? "CLEANUP_PENDING" : "SESSION_LIMIT",
                            "等待旧屏幕清理完成");
                    Slot created = new Slot(factory.create(context), pkg);
                    slot = created;
                    workers.put(id, created);
                    created.worker.setReleaseListener(() -> workerReleased(created));
                }
                if (slot == null) return error("NO_SESSION", "请先打开本任务的屏幕");
                slot.touched = now();
                if (cmd.equals("keepalive")) { slot.worker.keepAlive(); return ok(); }
                if (cmd.equals("validate")) {
                    if (slot.closing || req.optInt("displayId", -1) != slot.worker.displayId()) return error("STALE_SCREEN", "屏幕已失效");
                    // A direct DisplayManager read, never a queued worker RPC: the worker may
                    // be waiting for this observer callback on its own handler.
                    JSONObject geometry = slot.worker.geometrySnapshot();
                    if (!com.eta.shadow.ScreenGeometry.valid(geometry)) return error("STALE_SCREEN", "目标显示屏已不可用")
                            .put("reason", "DISPLAY_REMOVED").put("displayId", req.optInt("displayId", -1));
                    return ok().put("geometry", geometry);
                }
                if (slot.closing && !cmd.equals("cancel")) return error("SESSION_CLOSED", "屏幕正在释放");
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
        } catch (Exception e) { return error("ENGINE_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage()); }
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
                if (entry.getValue() == released) { retire(entry.getKey()); entries.remove(); }
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
        if (!slot.worker.closeSession()) return error("CLEANUP_PENDING", "旧操作尚未退出，屏幕容量仍保留");
        synchronized (workers) { if (workers.get(id) == slot) workers.remove(id); }
        return ok();
    }

    static int obsPort() { return 8738; }
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
