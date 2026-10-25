package com.eta.shadow;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import android.os.SystemClock;

/**
 * Hidden-display tools living inside Eta itself.
 *
 * Eta's classes are R8-obfuscated, so everything here talks to the app through
 * reflection and to the engine/observer through their local sockets:
 *   8733 - resident engine (display, input, capture, handover)
 *   8738 - accessibility observer (node tree, text, waits); 8734 is the legacy port
 */
public final class ShadowTools {
    static final int ENGINE_PORT = 8733;
    static final int OBSERVER_PORT = 8738;   // only the rebuilt bridge binds this
    static final int OBSERVER_PORT_LEGACY = 8734;

    private static volatile String mode;
    private static volatile int display = -1;

    public static String sessionInfo() {
        return "{\"mode\":" + (mode == null ? "null" : "\"" + mode + "\"")
                + ",\"display\":" + display + "}";
    }

    // ---------------------------------------------------------------- entry

    /**
     * Called from AgentLocalTools.execute before the normal dispatch.
     * Returns a ToolResult instance when the call is ours, otherwise null.
     */
    public static Object intercept(String name, Object call) {
        if ("terminal".equals(name)) {
            return guardTerminal(call);
        }
        if (name == null || !name.startsWith("screen_")) return null;

        android.content.Context ctx = ShadowSettings.app();
        if (needsEngine(name) && !ShadowSettings.engineReady(ctx)) {
            android.util.Log.w("EtaShadow", "engine down, refusing " + name);
            return toolResult(ShadowSettings.engineFixHint(), name);
        }
        if (needsObserver(name) && !ShadowSettings.observerReady()) {
            android.util.Log.w("EtaShadow", "observer down, refusing " + name);
            return toolResult(ShadowSettings.observerFixHint(), name);
        }
        com.eta.shadow.ShadowNotice.setStep(label(name));
        String text;
        try {
            JSONObject args = argumentsOf(call);
            text = run(name, args);
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("OBSERVER_DOWN")) {
                text = ShadowSettings.observerFixHint();
            } else if (msg != null && (msg.contains("ConnectException")
                    || msg.contains("Connection refused") || msg.contains("timed out"))) {
                text = ShadowSettings.engineFixHint();
            } else {
                text = "失败：" + e.getClass().getSimpleName() + (msg == null ? "" : "：" + msg);
            }
        }
        return toolResult(text, name);
    }

    /**
     * The terminal has root, so it can drive the screen behind our back. Block the
     * handful of shell commands that manipulate the UI and point at screen_* instead;
     * everything else (files, logs, package info) still passes through.
     */
    private static Object guardTerminal(Object call) {
        try {
            JSONObject args = argumentsOf(call);
            for (java.util.Iterator<String> it = args.keys(); it.hasNext(); ) {
                Object v = args.opt(it.next());
                if (!(v instanceof String)) continue;
                String cmd = ((String) v).toLowerCase();
                String hit = null;
                for (String p : GUI_SHELL) {
                    if (cmd.contains(p)) { hit = p; break; }
                }
                if (hit != null) {
                    return toolResult("请改用 screen_* 工具做界面操作（检测到 shell 命令 `"
                            + hit.trim() + "`）。"
                            + "screen_open 会自动判断：目标应用就是用户当前在看的，就在用户屏幕上操作；"
                            + "否则移到后台虚拟屏操作，不打扰用户。", "terminal");
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static final String[] GUI_SHELL = {
            "am start", "am force-stop", "input tap", "input swipe", "input text",
            "input keyevent", "input keyevent", "screencap", "uiautomator dump",
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
            String r = send(ENGINE_PORT, q.toString(), 8000);
            android.util.Log.i("EtaShadow", "handover " + pkg + " -> " + r);
            // Only clear the bridge handle when the engine really moved something.
            JSONObject ro = new JSONObject(r);
            JSONObject h = ro.optJSONObject("handover");
            boolean moved = ro.optBoolean("ok", false) && h != null && h.optBoolean("moved", false);
            if (moved && mode != null) { mode = null; display = -1; }
        } catch (Throwable t) {
            android.util.Log.w("EtaShadow", "handoverToMain failed: " + t);
        }
    }

    /** Tools that cannot work without the root engine. */
    private static boolean needsEngine(String n) {
        switch (n) {
            case "screen_open": case "screen_finish": case "screen_shot":
            case "screen_tap": case "screen_swipe": case "screen_press": case "screen_drag":
                return true;
            default: return false;
        }
    }

    /** Tools that only need the accessibility observer. */
    private static boolean needsObserver(String n) {
        switch (n) {
            case "screen_text": case "screen_click": case "screen_wait":
            case "screen_scroll": case "screen_list": case "screen_dump": case "screen_diag":
                return true;
            default: return false;
        }
    }

    private static String label(String name) {
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

    /** ToolCall is an obfuscated data class; find its JSON argument string. */
    private static JSONObject argumentsOf(Object call) {
        try {
            for (Field f : call.getClass().getFields()) {
                if (f.getType() != String.class) continue;
                Object v = f.get(call);
                if (v instanceof String) {
                    String s = ((String) v).trim();
                    if (s.startsWith("{")) return new JSONObject(s);
                }
            }
        } catch (Throwable ignored) { }
        return new JSONObject();
    }

    /** Build ToolResult(content, [], false). The class is R8-obfuscated to "wj". */
    private static Object toolResult(String content, String name) {
        String[] candidates = {"wj", "io.github.mangi.eta.agent.model.AgentModelClient$ToolResult"};
        for (String candidate : candidates) {
            try {
                Class<?> cls = Class.forName(candidate);
                for (Constructor<?> c : cls.getDeclaredConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    c.setAccessible(true);
                    if (p.length == 3 && p[0] == String.class && List.class.isAssignableFrom(p[1])
                            && (p[2] == boolean.class || p[2] == Boolean.class)) {
                        return c.newInstance(content, new ArrayList<Object>(), false);
                    }
                }
                for (Constructor<?> c : cls.getDeclaredConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    c.setAccessible(true);
                    if (p.length == 1 && p[0] == String.class) return c.newInstance(content);
                }
            } catch (Throwable ignored) { }
        }
        android.util.Log.w("EtaShadow", "toolResult: cannot build ToolResult for " + name);
        return null;
    }

    // ---------------------------------------------------------------- tools

    private static String run(String name, JSONObject a) throws Exception {
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

    private static String open(JSONObject a) throws Exception {
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
        if (a.has("allowForceStop")) req.put("allowForceStop", a.optBoolean("allowForceStop", false));
        for (String k : new String[]{"width", "height", "density"}) {
            if (a.has(k)) req.put(k, a.optInt(k, 0));
        }
        // the engine decides: reuse this package's session, or give it a display of its
        // own. Creating a display here is what put two apps on one screen.
        JSONObject launched = new JSONObject(engine(req.toString()));
        if (!launched.optBoolean("ok")) return launched.toString();
        int disp = launched.optInt("displayId", -1);
        if ("hidden".equals(launched.optString("mode")) && disp <= 0) {
            return launched.put("error", "engine reported hidden but no display id").toString();
        }
        mode = launched.optString("mode", "hidden");
        display = disp < 0 ? 0 : disp;
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

    private static String state() throws Exception {
        JSONObject o = new JSONObject();
        o.put("session", new JSONObject()
                .put("mode", mode == null ? JSONObject.NULL : mode).put("display", display));
        boolean engineOk = false;
        try {
            JSONObject st = new JSONObject(engine("{\"cmd\":\"status\"}"));
            o.put("engine", st);
            engineOk = st.optBoolean("ok", false);
        } catch (Throwable e) {
            o.put("engine", new JSONObject().put("ok", false).put("code", "ENGINE_DOWN"));
        }
        boolean observerOk = ShadowSettings.observerReady();
        o.put("observer", new JSONObject().put("ok", observerOk));
        o.put("health", ShadowSettings.statusLine(ShadowSettings.app()));
        if (!engineOk) o.put("fix", ShadowSettings.engineFixHint());
        else if (!observerOk) o.put("fix", ShadowSettings.observerFixHint());
        return o.toString();
    }

    private static String finish(JSONObject a) throws Exception {
        String out = doFinish(a);
        try {
            // Only a close-out the engine accepted clears the handle: keeping it after a
            // failure is what lets the caller retry instead of losing the display.
            if (new JSONObject(out).optBoolean("ok", false)) {
                mode = null;
                display = -1;
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static String doFinish(JSONObject a) throws Exception {
        JSONObject out = new JSONObject();
        boolean notified = false;
        String notifyError = null;
        for (int i = 0; i < 2 && !notified; i++) {
            try {
                if (i > 0) Thread.sleep(250);
                JSONObject n = new JSONObject().put("cmd", "notify")
                        .put("title", a.optString("title", "任务完成"))
                        .put("text", a.optString("text", ""))
                        .put("open", a.optString("open", ""));
                notified = new JSONObject(observer(n.toString())).optBoolean("ok", false);
                if (!notified) notifyError = "observer returned ok=false";
            } catch (Throwable e) {
                notifyError = String.valueOf(e);
            }
        }
        out.put("notified", notified);
        if (notifyError != null) out.put("notifyError", notifyError);
        // D3: the close-out decision lives in the engine now (notify / handover /
        // destroy), so the built-in bridge and the MCP endpoint cannot drift apart.
        // A failed notification must never block the close-out.
        String mode = a.optString("mode", "notify");
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
        // ok describes the close-out, notified describes the notification: keep them apart
        out.put("ok", finOk);
        return out.toString();
    }

    private static void requireSession() {
        if (mode == null || display < 0) throw new IllegalStateException("NO_SESSION: call screen_open first");
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
            {"to_display", "toDisplay"}
    };

    private static void normalizeArgs(JSONObject q) {
        if (q == null) return;
        for (String[] p : ARG_ALIASES) {
            if (!q.has(p[0])) continue;
            try {
                q.put(p[1], q.remove(p[0]));
            } catch (Throwable ignored) { }
        }
    }

    private static String eng(String cmd, JSONObject a) throws Exception {
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
        return engine(q.toString());
    }

    private static String obs(String cmd, JSONObject a) throws Exception {
        // Read-only inspection works without an open session: display stays -1 and
        // the observer falls back to the main screen.
        boolean readOnly = "list".equals(cmd) || "dump".equals(cmd) || "diag".equals(cmd);
        if (!readOnly) requireSession();
        int callTimeout = 25000;
        if ("wait".equals(cmd) || "idle".equals(cmd)) {
            long want = a.optLong("timeout_ms", 8000L);
            callTimeout = (int) Math.min(120000L, want + 8000L);
        }
        JSONObject q = new JSONObject(a.toString());
        q.put("cmd", cmd);
        q.put("displayId", display);
        normalizeArgs(q);
        return observer(q.toString(), callTimeout);
    }

    // ---------------------------------------------------------------- transport

    private static String engine(String json) throws Exception {
        return send(ENGINE_PORT, json, 25000);
    }

    private static String observer(String json) throws Exception {
        return observer(json, 25000);
    }

    private static String observer(String json, int timeoutMs) throws Exception {
        for (int port : new int[]{OBSERVER_PORT, OBSERVER_PORT_LEGACY}) {
            try {
                return send(port, json, timeoutMs);
            } catch (Exception ignored) { }
        }
        throw new IllegalStateException("OBSERVER_DOWN: accessibility observer not reachable");
    }

    private static String send(int port, String json, int timeoutMs) throws Exception {
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
