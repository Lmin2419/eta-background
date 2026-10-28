import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * MCP endpoint hosted by the resident engine (uid 0).
 *
 * Why here instead of the accessibility service: the system unbinds accessibility
 * services at will, which used to take the whole MCP endpoint down with it. The engine
 * is a root process with oom protection, so it stays.
 *
 * Display/input tools run in-process. Node tools are forwarded to the accessibility
 * observer on 127.0.0.1:8734 — if that service is gone, only those tools fail.
 */
final class McpEndpoint {
    static final String TAG = "EtaMcpEngine";
    static final int DEFAULT_PORT = 8735;
    static final int OBSERVER_PORT = 8734;
    static final int MAX_HEADER_BYTES = 8192;
    static final int MAX_BODY_BYTES = 1 << 20;
    static final int BODY_READ_TIMEOUT_MS = 10000;

    private static ServerSocket server;
    private static volatile int port = DEFAULT_PORT;
    private static volatile String mode;
    private static volatile int display = -1;

    /** Alias table: public JSON schema uses snake_case, the engine/observer read camelCase. */
    private static final String[][] ARG_ALIASES = {
            {"coordinate_space", "coordinateSpace"},
            {"frame_id", "frameId"},
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

    /**
     * A2: one normalisation point per build product. Kept local on purpose — the
     * engine dex and the Eta APK are separate compile units, so a shared
     * com.eta.shadow.Aliases class would have to be compiled into both.
     */
    private static void normalizeArgs(JSONObject args) {
        if (args == null) return;
        for (String[] p : ARG_ALIASES) {
            if (!args.has(p[0])) continue;
            try {
                args.put(p[1], args.remove(p[0]));
            } catch (Throwable t) {
                VDrive.log("alias " + p[0] + " -> " + p[1] + " failed: " + t);
            }
        }
    }

    static void start() {
        start(DEFAULT_PORT);
    }

    static void start(int listenPort) {
        if (server != null && !server.isClosed()) return;
        port = listenPort;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("127.0.0.1", port));
            Thread t = new Thread(new Runnable() {
                public void run() {
                    while (true) {
                        try {
                            final Socket s = server.accept();
                            Thread c = new Thread(new Runnable() {
                                public void run() { serve(s); }
                            });
                            c.setDaemon(true);
                            c.start();
                        } catch (Throwable e) {
                            if (server.isClosed()) return;
                            try { Thread.sleep(200); } catch (InterruptedException ignored) { }
                        }
                    }
                }
            });
            t.setDaemon(true);
            t.start();
            VDrive.log("mcp listening " + port);
        } catch (Throwable e) {
            VDrive.log("mcp listen failed: " + e);
        }
    }

    /**
     * A1: the body must be read as *bytes*. Content-Length counts bytes, so the old
     * char-based BufferedReader accounting (which is what the previous version did)
     * either stopped short or blocked until the socket timeout whenever the request
     * contained CJK/emoji. Header lines are pure ASCII, the body is decoded once at
     * the end.
     */
    private static void serve(Socket sock) {
        try (Socket s = sock) {
            s.setSoTimeout(30000);
            InputStream in = s.getInputStream();
            // 1) header bytes up to the CRLFCRLF terminator
            java.io.ByteArrayOutputStream hb = new java.io.ByteArrayOutputStream();
            int p3 = -1, p2 = -1, p1 = -1, c;
            while ((c = in.read()) != -1) {
                hb.write(c);
                if (p3 == '\r' && p2 == '\n' && p1 == '\r' && c == '\n') break;
                p3 = p2; p2 = p1; p1 = c;
                if (hb.size() > MAX_HEADER_BYTES) {
                    writeHttp(s, 431, "header too large");
                    return;
                }
            }
            if (hb.size() == 0) return;   // client closed before sending anything
            String head = new String(hb.toByteArray(), "ISO-8859-1");
            String[] lines = head.split("\r\n");
            if (lines.length == 0 || lines[0].trim().isEmpty()) return;
            VDrive.log("mcp " + lines[0].trim());

            boolean hasLength = false;
            int contentLength = 0;
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                if (!line.substring(0, colon).trim().equalsIgnoreCase("content-length")) continue;
                hasLength = true;
                try {
                    contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException nfe) {
                    writeHttp(s, 400, "bad content-length");
                    return;
                }
            }
            if (!hasLength) {
                // JSON-RPC over HTTP always carries a body; treat a missing header as
                // empty rather than "unknown", because the alternative is a deadlock.
                contentLength = 0;
            }
            if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                drain(s, in, 256 * 1024);
                writeHttp(s, 413, "body too large");
                return;
            }

            // 2) read exactly the declared number of bytes. A local client finishes in
            // milliseconds, so a shorter deadline than the header timeout keeps a
            // lying Content-Length from parking this thread for 30s.
            s.setSoTimeout(BODY_READ_TIMEOUT_MS);
            byte[] body = new byte[contentLength];
            int off = 0;
            try {
                while (off < contentLength) {
                    int n = in.read(body, off, contentLength - off);
                    if (n < 0) break;
                    off += n;
                }
            } catch (java.net.SocketTimeoutException ste) {
                // a client that declares more than it sends cannot be detected by EOF
                // (TCP has none), so the read timeout is the only signal: answer 400
                // instead of dropping the connection without a word
                writeHttp(s, 400, "body timeout after " + off + "/" + contentLength + " bytes");
                return;
            }
            if (off < contentLength) {
                writeHttp(s, 400, "truncated body");
                return;
            }

            // 3) decode once, then answer with the byte length of the answer
            String out = handle(new String(body, 0, off, "UTF-8"));
            writeHttp(s, 200, out);
        } catch (Throwable e) {
            VDrive.log("mcp serve: " + e);
        }
    }

    /**
     * Swallow what the client already pushed before answering with an error: closing a
     * socket that still has unread data makes the kernel send RST, which throws away
     * the response we just wrote (the client then sees "connection reset" instead of
     * 413).
     */
    private static void drain(Socket s, InputStream in, int limit) {
        int old = -1;
        try {
            old = s.getSoTimeout();
            s.setSoTimeout(250);
            byte[] junk = new byte[4096];
            int left = limit;
            while (left > 0) {
                int n = in.read(junk, 0, Math.min(junk.length, left));
                if (n < 0) break;
                left -= n;
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (old > 0) s.setSoTimeout(old); } catch (Throwable ignored) { }
        }
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 202: return "Accepted";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 408: return "Request Timeout";
            case 413: return "Payload Too Large";
            case 431: return "Request Header Fields Too Large";
            case 500: return "Internal Server Error";
            default: return "Status";
        }
    }

    private static void writeHttp(Socket s, int code, String json) {
        try {
            byte[] data = json == null ? new byte[0] : json.getBytes("UTF-8");
            OutputStream os = s.getOutputStream();
            os.write(("HTTP/1.1 " + code + " " + reason(code) + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + data.length + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes("UTF-8"));
            if (data.length > 0) os.write(data);
            os.flush();
        } catch (Throwable e) {
            VDrive.log("mcp write failed: " + e);
        }
    }

    private static synchronized String handle(String body) {
        JSONObject req;
        try {
            req = new JSONObject(body);
        } catch (Throwable e) {
            return error(null, -32700, "parse error");
        }
        Object id = req.opt("id");
        String method = req.optString("method");
        JSONObject params = req.optJSONObject("params");
        if (params == null) params = new JSONObject();

        if (method.startsWith("notifications/")) return "";

        if ("initialize".equals(method)) {
            String proto = params.optString("protocolVersion", "2025-06-18");
            JSONObject result = new JSONObject();
            try {
                result.put("protocolVersion", proto);
                result.put("capabilities", new JSONObject().put("tools", new JSONObject()));
                result.put("serverInfo", new JSONObject()
                        .put("name", "eta-hidden-screen").put("version", "1.2.0"));
            } catch (Throwable ignored) { }
            return result(id, result);
        }

        if ("tools/list".equals(method)) {
            try {
                return result(id, new JSONObject().put("tools", tools()));
            } catch (Throwable e) {
                return error(id, -32603, String.valueOf(e));
            }
        }

        if ("tools/call".equals(method)) {
            String name = params.optString("name");
            JSONObject args = params.optJSONObject("arguments");
            if (args == null) args = new JSONObject();
            normalizeArgs(args);   // single entry point: below here only camelCase exists
            String text;
            boolean isError = false;
            try {
                text = call(name, args);
            } catch (Throwable e) {
                text = e.getClass().getSimpleName() + ": " + e.getMessage();
                isError = true;
            }
            try {
                JSONObject content = new JSONObject().put("type", "text").put("text", text);
                JSONArray arr = new JSONArray().put(content);
                return result(id, new JSONObject().put("content", arr).put("isError", isError));
            } catch (Throwable e) {
                return error(id, -32603, String.valueOf(e));
            }
        }

        return error(id, -32601, "method not found: " + method);
    }

    private static String result(Object id, JSONObject res) {
        try {
            // org.json escapes "/" as "\/"; a model copying that as a file path fails
            return VDrive.plain(new JSONObject().put("jsonrpc", "2.0")
                    .put("id", id == null ? JSONObject.NULL : id)
                    .put("result", res).toString());
        } catch (Throwable e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"serialize\"}}";
        }
    }

    private static String error(Object id, int code, String msg) {
        try {
            return new JSONObject().put("jsonrpc", "2.0").put("id", id == null ? JSONObject.NULL : id)
                    .put("error", new JSONObject().put("code", code).put("message", msg)).toString();
        } catch (Throwable e) {
            return "{}";
        }
    }

    // ------------------------------------------------------------ tool dispatch

    private static String call(String name, JSONObject a) throws Exception {
        switch (name) {
            case "screen_open": return open(a);
            case "screen_state": return state();
            case "screen_tap": return eng("tap", a);
            case "screen_swipe": return eng("swipe", a);
            case "screen_press": return eng("press", a);
            case "screen_drag": return eng("drag", a);
            case "screen_shot": return eng("shot", a);
            case "screen_text": return obs("settext", a);
            case "screen_click": return click(a);
            case "screen_wait": return obs(a.has("text") ? "wait" : "idle", a);
            case "screen_scroll": return obs("scroll", a);
            case "screen_list": return obs("list", a);
            case "screen_dump": return obs("dump", a);
            case "screen_diag": return obs("diag", a);
            case "screen_finish": return finish(a);
            default: throw new IllegalArgumentException("unknown tool: " + name);
        }
    }

    private static void requireSession() {
        if (mode == null || display < 0)
            throw new IllegalStateException("NO_SESSION: call screen_open first");
    }

    /** Display / input tools run in the engine process. */
    private static String eng(String cmd, JSONObject a) throws Exception {
        requireSession();
        JSONObject q = new JSONObject(a.toString());
        q.put("cmd", cmd);
        if (!q.has("displayId")) q.put("displayId", display);
        // A3: in an on-screen session the user is already looking at this app, so a
        // capture of display 0 *is* the session's own screen. Bind that permission to
        // the session instead of making the model pass allowMain by hand.
        if ("shot".equals(cmd) && isMainScreenSession()) {
            q.put("displayId", 0);
            q.put("allowMain", true);
        }
        return VDrive.processLine(q.toString()).toString();
    }

    private static boolean isMainScreenSession() {
        return "onscreen".equals(mode) || display == 0;
    }

    /**
     * A precise click needs at least one selector. Without this check an empty call
     * fell through to a full-screen scan and came back as NOT_FOUND, which reads like
     * "the button is gone" instead of "you forgot the argument".
     */
    private static String click(JSONObject a) throws Exception {
        boolean hasViewId = !a.optString("viewId", "").isEmpty();
        boolean hasText = !a.optString("text", "").isEmpty();
        boolean hasDesc = !a.optString("desc", "").isEmpty();
        if (!hasViewId && !hasText && !hasDesc) {
            return new JSONObject().put("ok", false).put("code", "BAD_ARGUMENT")
                    .put("error", "one of view_id, text, desc is required; index only picks "
                            + "among the nodes they match").toString();
        }
        if (a.has("index") && a.optInt("index", -1) < 0) {
            return new JSONObject().put("ok", false).put("code", "BAD_ARGUMENT")
                    .put("error", "index must be >= 0; omit it to take the only match").toString();
        }
        return obs("click2", a);
    }

    /** Node tools go to the accessibility observer. */
    private static String obs(String cmd, JSONObject a) throws Exception {
        requireSession();
        JSONObject q = new JSONObject(a.toString());
        q.put("cmd", cmd);
        q.put("displayId", display);
        return observer(q.toString());
    }

    private static String observer(String json) throws Exception {
        JSONObject request = new JSONObject(json);
        if (request.optInt("displayId", -1) > 0) request.put("sessionId", "legacy-mcp");
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", VDrive.obsPort()), 1500);
            s.setSoTimeout(20000);
            PrintStream w = new PrintStream(s.getOutputStream(), true, "UTF-8");
            w.println(request);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line = r.readLine();
            if (line == null) throw new IOException("observer closed connection");
            return line.startsWith("RESULT:") ? line.substring(7) : line;
        } catch (IOException e) {
            throw new IOException("OBSERVER_DOWN: accessibility service not reachable (" + e.getMessage() + ")");
        }
    }

    private static String open(JSONObject a) throws Exception {
        String component = a.getString("component");
        JSONObject req = new JSONObject().put("cmd", "launch").put("component", component);
        // B4: the engine refuses to force-stop anything unless this is explicitly
        // passed through; forward it so an intentional call still works.
        if (a.has("allowForceStop")) req.put("allowForceStop", a.optBoolean("allowForceStop", false));
        for (String k : new String[]{"width", "height", "density"}) {
            if (a.has(k)) req.put(k, a.optInt(k, 0));
        }
        // the engine decides: reuse this package's session, or give it a display of its
        // own. Probing for NO_DISPLAY and calling create() here is what put two apps on
        // one screen.
        JSONObject launched = VDrive.processLine(req.toString());
        if (!launched.optBoolean("ok")) return launched.toString();
        mode = launched.optString("mode", "hidden");
        display = launched.optInt("displayId", 0);
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
        launched.put("display", display);
        return launched.toString();
    }

    private static String state() throws Exception {
        JSONObject o = new JSONObject();
        o.put("session", new JSONObject()
                .put("mode", mode == null ? JSONObject.NULL : mode)
                .put("display", display));
        try {
            o.put("engine", VDrive.processLine("{\"cmd\":\"status\"}"));
        } catch (Throwable e) {
            o.put("engine", new JSONObject().put("ok", false));
        }
        try {
            o.put("observer", new JSONObject(observer("{\"cmd\":\"ping\"}")));
        } catch (Throwable e) {
            o.put("observer", new JSONObject().put("ok", false).put("code", "OBSERVER_DOWN"));
        }
        return o.toString();
    }

    private static String finish(JSONObject a) throws Exception {
        String how = a.optString("mode", "notify");
        JSONObject out = new JSONObject();
        boolean notified = false;
        for (int i = 0; i < 2 && !notified; i++) {
            try {
                if (i > 0) Thread.sleep(250);
                JSONObject n = new JSONObject().put("cmd", "notify")
                        .put("title", a.optString("title", "任务完成"))
                        .put("text", a.optString("text", ""))
                        .put("open", a.optString("open", ""));
                JSONObject r = new JSONObject(observer(n.toString()));
                notified = r.optBoolean("ok", false);
            } catch (Throwable e) {
                if (i == 1) out.put("notifyError", String.valueOf(e));
            }
        }
        out.put("notified", notified);
        out.put("mode", how);
        // D3: one engine command for the close-out, so the MCP endpoint and the built-in
        // bridge cannot drift apart. A failed notification must not block it.
        JSONObject fin = null;
        try {
            fin = VDrive.processLine(new JSONObject()
                    .put("cmd", "finish").put("mode", how).put("displayId", display).toString());
            out.put("finish", fin);
        } catch (Throwable e) {
            out.put("finishError", String.valueOf(e));
        }
        if (fin == null || !fin.optBoolean("ok", false)) {
            // keep the handle: the app is still on its display and the caller can retry
            out.put("ok", false)
                    .put("code", fin == null ? "FINISH_FAILED" : fin.optString("code", "FINISH_FAILED"));
            return out.toString();
        }
        mode = null;
        display = -1;
        out.put("ok", true);
        return out.toString();
    }

    // ------------------------------------------------------------ schema

    private static JSONObject tool(String name, String desc, String props, String required) {
        try {
            JSONObject schema = new JSONObject().put("type", "object");
            schema.put("properties", new JSONObject(props));
            if (required != null && !required.isEmpty()) {
                JSONArray req = new JSONArray();
                for (String r : required.split(",")) req.put(r.trim());
                schema.put("required", req);
            }
            return new JSONObject().put("name", name).put("description", desc).put("inputSchema", schema);
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    private static JSONArray tools() {
        JSONArray a = new JSONArray();
        a.put(tool("screen_open",
                "Open an app for automation. If it is already in the foreground it is operated in place on "
                + "the main screen (mode=onscreen); otherwise it moves to a hidden display (mode=hidden) and "
                + "screen_finish returns it. If the app already has a task elsewhere it is NOT killed: "
                + "NEED_FORCE_STOP comes back unless allow_force_stop=true.",
                "{\"component\":{\"type\":\"string\",\"description\":\"package/activity\"},"
                + "\"allow_force_stop\":{\"type\":\"boolean\",\"description\":"
                + "\"really kill the app's existing task if it cannot be moved (default false)\"}}",
                "component"));
        a.put(tool("screen_state", "Current session, engine and observer status.", "{}", ""));
        a.put(tool("screen_tap", "Tap a coordinate.",
                "{\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"integer\"}}", "x,y"));
        a.put(tool("screen_swipe", "Swipe from one point to another.",
                "{\"x1\":{\"type\":\"integer\"},\"y1\":{\"type\":\"integer\"},"
                + "\"x2\":{\"type\":\"integer\"},\"y2\":{\"type\":\"integer\"},"
                + "\"duration_ms\":{\"type\":\"integer\"}}", "x1,y1,x2,y2"));
        a.put(tool("screen_press", "Long press a coordinate.",
                "{\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"integer\"},"
                + "\"hold_ms\":{\"type\":\"integer\"}}", "x,y"));
        a.put(tool("screen_drag", "Press, hold, move and release.",
                "{\"x1\":{\"type\":\"integer\"},\"y1\":{\"type\":\"integer\"},"
                + "\"x2\":{\"type\":\"integer\"},\"y2\":{\"type\":\"integer\"},"
                + "\"hold_ms\":{\"type\":\"integer\"},\"duration_ms\":{\"type\":\"integer\"}}", "x1,y1,x2,y2"));
        a.put(tool("screen_text", "Write text into an input field, CJK included.",
                "{\"text\":{\"type\":\"string\"},\"view_id\":{\"type\":\"string\"},"
                + "\"index\":{\"type\":\"integer\"}}", "text"));
        a.put(tool("screen_click", "Precise click by view id, text or description.",
                "{\"view_id\":{\"type\":\"string\"},\"text\":{\"type\":\"string\"},"
                + "\"desc\":{\"type\":\"string\"},\"exact\":{\"type\":\"boolean\"},"
                + "\"index\":{\"type\":\"integer\"}}", ""));
        a.put(tool("screen_wait", "Wait for text to appear, or for the UI to settle.",
                "{\"text\":{\"type\":\"string\"},\"timeout_ms\":{\"type\":\"integer\"},"
                + "\"quiet_ms\":{\"type\":\"integer\"}}", ""));
        a.put(tool("screen_scroll", "Scroll the dominant scrollable container.",
                "{\"direction\":{\"type\":\"string\",\"enum\":[\"down\",\"up\"]}}", ""));
        a.put(tool("screen_list", "Read the screen as rows (title and price come back together).",
                "{\"max_rows\":{\"type\":\"integer\"}}", ""));
        a.put(tool("screen_dump", "Raw node list with bounds and view ids.",
                "{\"limit\":{\"type\":\"integer\"}}", ""));
        a.put(tool("screen_shot", "Screenshot the current screen to a file path. In an on-screen "
                + "session (mode=onscreen) the main display is captured automatically; in a hidden "
                + "session only the hidden display is captured and display 0 stays refused.",
                "{\"path\":{\"type\":\"string\"},\"scale\":{\"type\":\"number\"},"
                + "\"format\":{\"type\":\"string\",\"enum\":[\"jpeg\",\"png\"]},"
                + "\"quality\":{\"type\":\"integer\"},"
                + "\"wait_frame_ms\":{\"type\":\"integer\",\"description\":\"max wait for a fresh frame, default 2500\"},"
                + "\"allow_main\":{\"type\":\"boolean\",\"description\":\"排障用；正常会话不需要传\"}}", ""));
        a.put(tool("screen_diag", "Compact diagnostic snapshot of the current screen.", "{}", ""));
        a.put(tool("screen_finish", "Notify the user and hand the app back.",
                "{\"title\":{\"type\":\"string\"},\"text\":{\"type\":\"string\"},"
                + "\"open\":{\"type\":\"string\"},"
                + "\"mode\":{\"type\":\"string\",\"enum\":[\"notify\",\"background\",\"handover\","
                + "\"destroy\"]}}", ""));
        return a;
    }
}
