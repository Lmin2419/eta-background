package com.eta.shadow;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import android.os.SystemClock;

/**
 * Hidden-display tools living inside Eta itself.
 *
 * Eta's classes are R8-obfuscated, so everything here talks to the app through
 * reflection and to the engine/observer through their local sockets:
 *   8733 - resident engine (display, input, capture, handover)
 *   8738 - accessibility observer (node tree, text, waits)
 */
public final class ShadowTools {
    static final int ENGINE_PORT = 8733;
    static final int OBSERVER_PORT = 8738;

    private volatile String mode;
    private final String sessionId;
    private volatile boolean closed;
    private volatile boolean parked;
    private String openedPackage;
    private static final ParkedScreenHints parkedHints = new ParkedScreenHints();
    interface LineTransport { String send(int port, String json, int timeoutMs) throws Exception; }
    private final LineTransport transport;
    public ShadowTools(String sessionId) { this(sessionId, null); }
    ShadowTools(String sessionId, LineTransport transport) { this.sessionId = sessionId; this.transport = transport; }
    private volatile int display = -1;
    private JSONObject observedGeometry;
    private JSONObject lastRejection;

    public String sessionInfo() {
        return "{\"mode\":" + (mode == null ? "null" : "\"" + mode + "\"")
                + ",\"display\":" + display + "}";
    }

    // ---------------------------------------------------------------- entry

    /**
     * Called from RoutingToolExecutor.execute before the normal dispatch.
     * Returns the tool result text when the call is ours, otherwise null
     * (the Kotlin side wraps it into ToolResult, no reflection needed).
     */
    public String intercept(String name, String argumentsJson) {
        // S1: 影子入口全程异常隔离——这里出任何未捕获的异常都不许冒回上游宿主。
        try {
            return interceptInner(name, argumentsJson);
        } catch (Throwable t) {
            android.util.Log.w("EtaShadow", "intercept crashed: " + t);
            // screen_* 是我们自己的工具，回一句错误；其它工具返回 null 交还上游正常处理。
            return (name != null && name.startsWith("screen_"))
                    ? "后台操作内部错误：" + t.getClass().getSimpleName()
                    : null;
        }
    }

    private String interceptInner(String name, String argumentsJson) {
        if ("terminal".equals(name)) {
            return guardTerminal(argumentsJson);
        }
        if (name == null || !name.startsWith("screen_")) return null;

        android.content.Context ctx = ShadowSettings.app();
        if (needsEngine(name) && !ShadowSettings.engineReady(ctx)) {
            // 引擎没在跑：用打进包里的 dex 自启（需 root）。冷启动最坏十几秒，这里只有界等待 8s：
            // B — 还在启动就让模型过几秒重试，不把这次调用卡死；明确失败才给修复提示。
            int st = Engine.ensureRunningBounded(ctx, 8000);
            if (st == Engine.STARTING) {
                return "后台虚拟屏引擎正在启动中，请过几秒重试该操作。";
            }
            if (st != Engine.UP) {
                android.util.Log.w("EtaShadow", "engine down, refusing " + name);
                return Engine.reasonHint();
            }
        }
        if (needsObserver(name) && !ShadowSettings.observerReady()) {
            android.util.Log.w("EtaShadow", "observer down, refusing " + name);
            return ShadowSettings.observerFixHint();
        }
        if (closed) return "{\"ok\":false,\"code\":\"SESSION_CLOSED\"}";
        String text;
        try {
            JSONObject args = argumentsOf(argumentsJson);
            text = run(name, args);
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("OBSERVER_DOWN")) {
                text = ShadowSettings.observerFixHint();
            } else if (msg != null && (msg.contains("ConnectException")
                    || msg.contains("Connection refused"))) {
                // #1 自愈：引擎中途掉线——重置失效的虚拟屏会话并后台重启，提示模型重新 screen_open。
                resetSession();
                ShadowSettings.markEngineReady(false);
                Engine.warm(ctx);
                text = "后台虚拟屏引擎连接中断，已在后台尝试重启。请重新 screen_open 后再继续。";
            } else if (msg != null && msg.contains("timed out")) {
                text = ShadowSettings.engineFixHint();
            } else {
                text = "失败：" + e.getClass().getSimpleName() + (msg == null ? "" : "：" + msg);
            }
        }
        return text;
    }

    /**
     * The terminal has root, so it can drive the screen behind our back. Block the
     * handful of shell commands that manipulate the UI and point at screen_* instead;
     * everything else (files, logs, package info) still passes through.
     */
    private String guardTerminal(String argumentsJson) {
        try {
            JSONObject args = argumentsOf(argumentsJson);
            for (java.util.Iterator<String> it = args.keys(); it.hasNext(); ) {
                Object v = args.opt(it.next());
                if (!(v instanceof String)) continue;
                String cmd = ((String) v).toLowerCase();
                String hit = null;
                for (String p : GUI_SHELL) {
                    if (cmd.contains(p)) { hit = p; break; }
                }
                if (hit != null) {
                    return "请改用 screen_* 工具做界面操作（检测到 shell 命令 `"
                            + hit.trim() + "`）。"
                            + "screen_open 会把目标应用绑定到本任务的后台虚拟屏，同一应用被占用时等待。";
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static final String[] GUI_SHELL = {
            "am start", "am force-stop", "input tap", "input swipe", "input text",
            "input keyevent", "screencap", "uiautomator dump",
            "input roll", "monkey -p"};

    /** Bring a parked session back to the main display (used by notification taps). */
    public static void handoverToMain(String pkg) {
        try {
            // finish(handover) rather than a bare handover: it moves the task *and*
            // releases the hidden display instead of leaving the session for the
            // watchdog to clean up ~20s later.
            JSONObject q = new JSONObject()
                    .put("cmd", "finish")
                    .put("mode", "handover")
                    .put("package", pkg)          // which app this notification is about
                    .put("toDisplay", 0)
                    .put("keepFront", false);     // bring the app forward, not the launcher
            String r = new ShadowTools("handover").send(ENGINE_PORT, q.toString(), 8000);
            android.util.Log.i("EtaShadow", "handover " + pkg + " -> " + r);
            // Only clear the bridge handle when the engine really moved something.
            JSONObject ro = new JSONObject(r);
            JSONObject h = ro.optJSONObject("handover");
            boolean moved = ro.optBoolean("ok", false) && h != null && h.optBoolean("moved", false);

        } catch (Throwable t) {
            android.util.Log.w("EtaShadow", "handoverToMain failed: " + t);
        }
    }

    /** Tools that cannot work without the root engine. */
    private boolean needsEngine(String n) {
        switch (n) {
            case "screen_open": case "screen_finish": case "screen_shot":
            case "screen_tap": case "screen_swipe": case "screen_press": case "screen_drag":
                return true;
            default: return false;
        }
    }

    /** Tools that only need the accessibility observer. */
    private boolean needsObserver(String n) {
        switch (n) {
            case "screen_text": case "screen_click": case "screen_wait":
            case "screen_scroll": case "screen_list": case "screen_dump": case "screen_diag":
                return true;
            default: return false;
        }
    }

    private String label(String name) {
        switch (name) {
            case "screen_open": return "正在打开应用";
            case "screen_tap": return "正在点击";
            case "screen_swipe": return "正在滑动";
            case "screen_press": return "正在长按";
            case "screen_drag": return "正在拖拽";
            case "screen_text": return "正在输入";
            case "screen_click": return "正在点击";
            case "screen_wait": return "等待界面";
            case "screen_scroll": return "正在滚动";
            case "screen_list": return "正在读取列表";
            case "screen_dump": return "正在读取界面";
            case "screen_shot": return "正在截图";
            case "screen_diag": return "正在诊断";
            case "screen_finish": return "正在收尾";
            case "screen_state": return "检查状态";
            default: return "处理中";
        }
    }

    private JSONObject argumentsOf(String json) {
        try {
            if (json != null && json.trim().startsWith("{")) return new JSONObject(json);
        } catch (Throwable ignored) { }
        return new JSONObject();
    }

    // ---------------------------------------------------------------- tools

    private String run(String name, JSONObject a) throws Exception {
        switch (name) {
            case "screen_open": return open(a);
            case "screen_state": return state();
            case "screen_finish": return finish(a);
            case "screen_tap": return eng("tap", a);
            case "screen_swipe": return eng("swipe", a);
            case "screen_press": return eng("press", a);
            case "screen_drag": return eng("drag", a);
            case "screen_shot": return eng("shot", a);
            case "screen_text": return obs("settext", a);
            case "screen_click": return obs("click2", a);
            case "screen_wait": return obs(a.has("text") ? "wait" : "idle", a);
            case "screen_scroll": return obs("scroll", a);
            case "screen_list": return obs("list", a);
            case "screen_dump": return obs("dump", a);
            case "screen_diag": return obs("diag", a);
            default: return "{\"ok\":false,\"error\":\"unknown tool\"}";
        }
    }

    /** ④: 用默认显示屏的真实 width/height/densityDpi 补全 launch 请求（显式指定的不覆盖）。 */
    private void fillDeviceMetrics(JSONObject req, JSONObject a) {
        try {
            if (a.has("width") && a.has("height") && a.has("density")) return;
            android.content.Context ctx = ShadowSettings.app();
            if (ctx == null) return;
            android.hardware.display.DisplayManager dm = (android.hardware.display.DisplayManager)
                    ctx.getSystemService(android.content.Context.DISPLAY_SERVICE);
            android.view.Display d = dm == null ? null : dm.getDisplay(android.view.Display.DEFAULT_DISPLAY);
            if (d == null) return;
            android.util.DisplayMetrics m = new android.util.DisplayMetrics();
            d.getRealMetrics(m);
            if (!a.has("width") && m.widthPixels > 0) req.put("width", m.widthPixels);
            if (!a.has("height") && m.heightPixels > 0) req.put("height", m.heightPixels);
            if (!a.has("density") && m.densityDpi > 0) req.put("density", m.densityDpi);
        } catch (Throwable ignored) {
        }
    }

    private String open(JSONObject a) throws Exception {
        String component = a.optString("component", "").trim();
        if (component.isEmpty()) component = a.optString("package", "").trim();
        if (component.isEmpty()) component = a.optString("app_name", "").trim();
        if (component.isEmpty()) {
            return "{\"ok\":false,\"error\":\"缺少 component/package 参数，例如 com.tencent.mm\"}";
        }
        if (!component.contains(".")) {
            return "{\"ok\":false,\"error\":\"需要包名而不是应用显示名，例如 com.tencent.mm"
                    + "（可用 search_apps 或之前对话里的包名）\"}";
        }
        JSONObject req = new JSONObject().put("cmd", "launch").put("component", component);
        req.put("orientation", a.optString("orientation", "auto"));
        if (a.has("allowForceStop")) req.put("allowForceStop", a.optBoolean("allowForceStop", false));
        for (String k : new String[]{"width", "height", "density"}) {
            if (a.has(k)) req.put(k, a.optInt(k, 0));
        }
        // ④: 没显式指定就让隐藏虚拟屏匹配手机当前分辨率/密度，避免引擎用默认值跟实机对不上
        fillDeviceMetrics(req, a);
        // the engine decides: reuse this package's session, or give it a display of its
        // own. Creating a display here is what put two apps on one screen.
        JSONObject launched = new JSONObject(engine(req.toString()));
        if (!launched.optBoolean("ok")) return launched.toString();
        int disp = launched.optInt("displayId", -1);
        if ("hidden".equals(launched.optString("mode")) && disp <= 0) {
            return launched.put("error", "engine reported hidden but no display id").toString();
        }
        parked = false;
        openedPackage = component.split("/")[0];
        parkedHints.forget(openedPackage);
        mode = launched.optString("mode", "hidden");
        display = disp < 0 ? 0 : disp;
        observedGeometry = null;
        rememberGeometry(launched);
        if ("hidden".equals(mode)) {
            for (int i = 0; i < 30; i++) {
                try {
                    JSONObject d = new JSONObject(observer(new JSONObject()
                            .put("cmd", "dump").put("displayId", display).put("limit", 1).toString()));
                    if (d.optBoolean("ok") && d.optInt("count", 0) > 0) break;
                } catch (Throwable ignored) { }
                Thread.sleep(100);
            }
        }
        return launched.put("display", display)
                .put("hint", "hidden".equals(mode)
                        ? "app is on a hidden display; call screen_finish when done"
                        : "app is on the main screen").toString();
    }

    private String state() throws Exception {
        JSONObject o = new JSONObject();
        boolean engineOk = false;
        try {
            JSONObject st = new JSONObject(engine("{\"cmd\":\"status\"}"));
            o.put("engine", st);
            engineOk = st.optBoolean("ok", false);
            if (engineOk && mode != null) {
                boolean found = false;
                JSONArray sessions = st.optJSONArray("sessions");
                if (sessions != null) for (int i = 0; i < sessions.length(); i++) {
                    JSONObject entry = sessions.optJSONObject(i);
                    if (entry != null && sessionId.equals(entry.optString("sessionId")) && !entry.optBoolean("closing")
                            && !entry.optBoolean("parked") && entry.optInt("displayId", display) == display) { found = true; break; }
                }
                if (!found) { resetSession(); o.put("sessionValid", false).put("reason", "SESSION_RELEASED"); }
            }
        } catch (Throwable e) {
            o.put("engine", new JSONObject().put("ok", false).put("code", "ENGINE_DOWN"));
        }
        o.put("session", new JSONObject().put("mode", mode == null ? JSONObject.NULL : mode).put("display", display));
        if (lastRejection != null) o.put("lastInputRejection", lastRejection);
        boolean observerOk = ShadowSettings.observerReady();
        o.put("observer", new JSONObject().put("ok", observerOk));
        o.put("health", ShadowSettings.statusLine(ShadowSettings.app()));
        if (!engineOk) o.put("fix", ShadowSettings.engineFixHint());
        else if (!observerOk) o.put("fix", ShadowSettings.observerFixHint());
        return o.toString();
    }

    private String finish(JSONObject a) throws Exception {
        String out = doFinish(a);
        try {
            // Only a close-out the engine accepted clears the handle: keeping it after a
            // failure is what lets the caller retry instead of losing the display.
            if (new JSONObject(out).optBoolean("ok", false)) {
                JSONObject finish = new JSONObject(out).optJSONObject("finish");
                parked = finish != null && finish.optBoolean("parked", false);
                if (parked) parkedHints.remember(openedPackage, sessionId);
                mode = null;
                display = -1;
                observedGeometry = null;
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private String doFinish(JSONObject a) throws Exception {
        JSONObject out = new JSONObject();
        // Runtime owns each task notification and its chat destination. Tool completion only
        // parks/hands over the screen; it must not post a second app-launch notification.
        out.put("notificationManagedByRuntime", true);
        String mode = a.optString("mode", "notify");
        // Old conversations may still request handover; background completion never opens an app.
        if ("handover".equals(mode) || "background".equals(mode)) mode = "notify";
        boolean finOk = false;
        try {
            JSONObject fin = new JSONObject(engine(new JSONObject()
                    .put("cmd", "finish").put("mode", mode)
                    .put("displayId", display).toString()));
            out.put("finish", fin);
            finOk = fin.optBoolean("ok", false);
            if (!finOk) out.put("finishError", fin.optString("code", "finish failed"));
        } catch (Throwable e) {
            out.put("finishError", String.valueOf(e));
        }
        // ok describes the screen close-out; the runtime publishes the final task result.
        out.put("ok", finOk);
        return out.toString();
    }

    private void requireSession() {
        if (mode == null || display < 0) throw new IllegalStateException("NO_SESSION: call screen_open first");
    }

    /** #1：引擎掉线或收尾后清掉本地会话句柄（旧虚拟屏已随引擎消失）。 */
    void resetSession() {
        mode = null;
        display = -1;
        observedGeometry = null;
    }

    /**
     * A2: the public schema is snake_case, the engine and observer read camelCase.
     * Same table as the MCP endpoint uses; kept as a local copy on purpose (the APK and
     * the engine dex are separate compile units, so a shared class would have to be
     * built into both products).
     */
    private static final String[][] ARG_ALIASES = {
            {"view_id", "viewId"},
            {"hold_ms", "holdMs"},
            {"duration_ms", "durationMs"},
            {"timeout_ms", "timeoutMs"},
            {"quiet_ms", "quietMs"},
            {"max_rows", "maxRows"},
            {"allow_main", "allowMain"},
            {"allow_force_stop", "allowForceStop"},
            {"wait_frame_ms", "waitFrameMs"},
            {"keep_front", "keepFront"},
            {"to_display", "toDisplay"},
            {"coordinate_space", "coordinateSpace"}, {"frame_id", "frameId"}
    };

    private void normalizeArgs(JSONObject q) {
        if (q == null) return;
        for (String[] p : ARG_ALIASES) {
            if (!q.has(p[0])) continue;
            try {
                q.put(p[1], q.remove(p[0]));
            } catch (Throwable ignored) { }
        }
    }

    private String eng(String cmd, JSONObject a) throws Exception {
        requireSession();
        JSONObject q = new JSONObject(a.toString());
        q.put("cmd", cmd);
        normalizeArgs(q);
        if (!q.has("displayId")) q.put("displayId", display);
        // A3: on an on-screen session the user is looking at this very app, so a capture
        // of display 0 is the session's own screen; bind that to the session, not to the
        // caller remembering allowMain.
        if ("shot".equals(cmd) && ("onscreen".equals(mode) || display == 0)) {
            q.put("displayId", 0);
            q.put("allowMain", true);
        }
        if (observedGeometry != null && ("tap".equals(cmd) || "press".equals(cmd) || "drag".equals(cmd) || "swipe".equals(cmd)))
            q.put("expectedGeometry", observedGeometry);
        String response = engine(q.toString());
        JSONObject result = new JSONObject(response);
        if ("shot".equals(cmd) && result.optBoolean("ok")) rememberGeometry(result);
        return response;
    }

    private String obs(String cmd, JSONObject a) throws Exception {
        // Read-only inspection works without an open session: display stays -1 and
        // the observer falls back to the main screen.
        boolean readOnly = "list".equals(cmd) || "dump".equals(cmd) || "diag".equals(cmd) || "wait".equals(cmd) || "idle".equals(cmd);
        requireSession();
        int callTimeout = 25000;
        if ("wait".equals(cmd) || "idle".equals(cmd)) {
            long want = a.optLong("timeout_ms", 8000L);
            callTimeout = (int) Math.min(120000L, want + 8000L);
        }
        JSONObject q = new JSONObject(a.toString());
        q.put("cmd", cmd);
        q.put("displayId", display);
        normalizeArgs(q);
        if (!readOnly && observedGeometry != null) q.put("expectedGeometry", observedGeometry);
        String response = observer(q.toString(), callTimeout);
        JSONObject result = new JSONObject(response);
        if (readOnly && result.optBoolean("ok")) rememberGeometry(result);
        return response;
    }

    /** Called outside registry locks. The engine acknowledges cleanup before the lease is released. */
    public boolean closeSession(boolean keepFinished) {
        closed = true;
        try {
            if (keepFinished && !parked && "hidden".equals(mode) && display > 0) {
                // A model answer (including a question to the user) can end without screen_finish.
                // Preserve that page too; a failed park is retried, never treated as permission to destroy it.
                JSONObject result = new JSONObject(engine(new JSONObject().put("cmd", "finish").put("mode", "notify").toString()));
                if (result.optBoolean("parked")) {
                    parked = true;
                    parkedHints.remember(openedPackage, sessionId);
                } else if (!"NO_SESSION".equals(result.optString("code")) && !"SESSION_CLOSED".equals(result.optString("code"))) {
                    return false;
                }
            }
            // release_session cancels the worker itself. A failed preliminary cancel must never
            // prevent the actual release request from reaching the engine.
            JSONObject reply = new JSONObject(send(ENGINE_PORT,
                    new JSONObject().put("cmd", "release_session").put("sessionId", sessionId)
                            .put("keep", keepFinished && parked).toString(), 5000));
            return reply.optBoolean("ok", false);
        } catch (Exception e) {
            android.util.Log.w("EtaShadow", "screen cleanup not acknowledged: " + e.getClass().getSimpleName());
            if (e instanceof java.net.ConnectException) {
                ShadowSettings.markEngineReady(false);
                Engine.warm(ShadowSettings.app());
            }
            return false;
        }
    }

    /** Explicit notification tap only: reuse the original Android task before considering a launcher. */
    public static String presentCompleted(String runId, String pkg) throws Exception {
        return new ShadowTools("user-handoff").send(ENGINE_PORT,
                new JSONObject().put("cmd", "present_completed").put("runId", runId).put("package", pkg).toString(), 35000);
    }

    public void keepAlive() {
        if (closed) return;
        try { send(ENGINE_PORT, new JSONObject().put("cmd", "keepalive").put("sessionId", sessionId).toString(), 2000); }
        catch (Exception ignored) { }
    }

    /** Accessibility callbacks only enqueue a bounded hint; never wait for Root on the UI thread. */
    public static void onWindowChanged(int displayId, String pkg) {
        parkedHints.onWindow(displayId, pkg, id -> {
            try {
                new ShadowTools("foreground-hint").send(ENGINE_PORT,
                        new JSONObject().put("cmd", "foreground_hint").put("sessionId", id).toString(), 1000);
            } catch (Exception ignored) { } // The engine's own patrol remains the fallback.
        });
    }

    /** Read-only, allowlisted export; never expose raw session IDs, package names or tool payloads. */
    public static JSONObject diagnosticSnapshot() {
        JSONObject out = new JSONObject();
        try {
            JSONObject status = new JSONObject(new ShadowTools("diagnostics").send(ENGINE_PORT,
                    "{\"cmd\":\"status\"}", 2500));
            out.put("reachable", true).put("sessionCount", status.optInt("count"))
                    .put("sessionLimit", status.optInt("limit"));
            String build = status.optString("build");
            out.put("build", build.matches("[A-Za-z0-9._-]{1,80}") ? build : "unknown");
            JSONArray sessions = status.optJSONArray("sessions");
            int closing = 0;
            if (sessions != null) for (int i = 0; i < sessions.length(); i++) {
                JSONObject session = sessions.optJSONObject(i);
                if (session == null) continue;
                if (session.optBoolean("closing")) closing++;
            }
            out.put("closingCount", closing);
        } catch (Exception ignored) {
            try { out.put("reachable", false); } catch (Exception unused) { }
        }
        return out;
    }

    // ---------------------------------------------------------------- transport

    private void rememberGeometry(JSONObject result) throws Exception {
        JSONObject geometry = result.optJSONObject("geometry");
        // An App may see accessibility windows but not the corresponding private Display.
        // Never replace an authoritative snapshot with a partial/empty observer result.
        if (ScreenGeometry.valid(geometry) && (!geometry.has("displayId") || geometry.optInt("displayId") == display))
            observedGeometry = new JSONObject(geometry.toString());
    }

    private String received(String response) throws Exception {
        JSONObject result = new JSONObject(response);
        String code = result.optString("code");
        if ("STALE_OBSERVATION".equals(code) || "INVALID_OBSERVATION".equals(code)
                || "GEOMETRY_UNAVAILABLE".equals(code) || "GEOMETRY_CHANGED_DURING_OPERATION".equals(code)) {
            lastRejection = new JSONObject();
            for (String key : new String[]{"code", "reason", "expected", "actual", "changedFields", "frameAgeMs",
                    "frameAgeChecked", "requestedFrameId", "availableFrameId", "windows", "operationMayHaveRun"})
                if (result.has(key)) lastRejection.put(key, result.opt(key));
            android.util.Log.w("EtaGeometry", lastRejection.toString());
        }
        if ("NO_SESSION".equals(code) || "SESSION_CLOSED".equals(code) || "STALE_SCREEN".equals(code)) resetSession();
        return response;
    }

    private String engine(String json) throws Exception {
        JSONObject request = new JSONObject(json).put("sessionId", sessionId);
        return received(send(ENGINE_PORT, request.toString(), 25000));
    }

    private String observer(String json) throws Exception {
        return observer(json, 25000);
    }

    private String observer(String json, int timeoutMs) throws Exception {
        // Only a refused connection means "observer down". A read timeout means the
        // command may already have run, so it must not be retried anywhere else
        // (the old build re-sent it to a legacy port that nothing binds).
        try {
            return received(send(OBSERVER_PORT, new JSONObject(json).put("sessionId", sessionId).toString(), timeoutMs));
        } catch (java.net.ConnectException e) {
            throw new IllegalStateException("OBSERVER_DOWN: accessibility observer not reachable");
        }
    }

    private String send(int port, String json, int timeoutMs) throws Exception {
        if (transport != null) return transport.send(port, json, timeoutMs);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            s.setSoTimeout(timeoutMs);
            PrintStream w = new PrintStream(s.getOutputStream(), true, "UTF-8");
            w.println(json);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line = r.readLine();
            if (line == null) throw new java.io.IOException("closed");
            return line.startsWith("RESULT:") ? line.substring(7) : line;
        }
    }
}
