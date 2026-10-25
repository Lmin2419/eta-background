package com.eta.shadow;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SparseArray;
import android.util.SparseLongArray;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Accessibility observer living inside Eta's own accessibility service.
 *
 * Eta already runs AgentAccessibilityService; instead of shipping a second APK with its
 * own service, Boot rides on that one. It exposes the node-tree commands on 8738 so the
 * engine (or the built-in tools) can read the screen and write text.
 */
public final class Boot {
    static final int PORT = 8738;   // the original Eta build keeps its own listener on 8734

    private static volatile AccessibilityService service;
    private static ServerSocket server;
    private static volatile long lastEventAt = SystemClock.elapsedRealtime();
    private static volatile int eventCount = 0;
    private static final SparseLongArray lastEventByDisplay = new SparseLongArray();

    public static void init(AccessibilityService svc) {
        android.util.Log.i("EtaShadow", "Boot.init called, svc=" + svc);
        if (svc == null) return;
        service = svc;
        try {
            startServer();
        } catch (Throwable ignored) { }
        reportEngine();
        // Room may still be opening eta.db at this point, so drop the obsolete
        // switch row from a worker thread once the process has settled.
        new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(3000L); } catch (InterruptedException ignored) { }
                try { ShadowSettings.cleanupLegacyRow(service); } catch (Throwable ignored) { }
            }
        }).start();
        android.util.Log.i("EtaShadow", "server started, engine released");
    }

    /**
     * The engine is deployed by the operator (`install_vdrive.sh`): one dex, one place.
     * The app only reports what is on disk, so reinstalling the APK cannot silently put
     * an older engine back in charge of a newer one.
     */
    private static void reportEngine() {
        try {
            java.io.File dex = new java.io.File(service.getFilesDir(), "vdrive.dex");
            java.io.File verFile = new java.io.File(service.getFilesDir(), "vdrive.version");
            android.util.Log.i("EtaShadow", "engine on disk: "
                    + (dex.exists() ? dex.length() + "B" : "missing")
                    + " stamp=" + readText(verFile).trim());
        } catch (Throwable t) {
            android.util.Log.w("EtaShadow", "reportEngine failed: " + t);
        }
    }

    private static String readAsset(String name) {
        try (java.io.InputStream in = service.getAssets().open(name)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static void copyAsset(String name, java.io.File out) throws Exception {
        try (java.io.InputStream in = service.getAssets().open(name);
             java.io.FileOutputStream fo = new java.io.FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            fo.getFD().sync();
        }
    }

    private static String readText(java.io.File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static void writeText(java.io.File f, String s) {
        try (java.io.FileOutputStream fo = new java.io.FileOutputStream(f)) {
            fo.write(s.getBytes("UTF-8"));
            fo.getFD().sync();
        } catch (Throwable t) {
            android.util.Log.w("EtaShadow", "write " + f + " failed: " + t);
        }
    }

    /** Called from the service's onAccessibilityEvent so waits can see UI changes. */
    private static volatile long lastRebindAt = 0;

    public static void onEvent(Object event) {
        eventCount++;
        long now = SystemClock.elapsedRealtime();
        lastEventAt = now;
        // The accept thread dies silently if the server socket breaks (force-stop of a
        // related process, fd exhaustion...). Events keep flowing, so this is the one
        // place that notices: rebind at most every 10s.
        if ((server == null || server.isClosed()) && now - lastRebindAt > 10_000) {
            lastRebindAt = now;
            startServer();
        }
        try {
            int d = (Integer) event.getClass().getMethod("getDisplayId").invoke(event);
            synchronized (lastEventByDisplay) { lastEventByDisplay.put(d, now); }
        } catch (Throwable t) {
            android.util.Log.w("EtaShadow", "startServer failed: " + t);
        }
    }

    private static synchronized void startServer() {
        if (server != null && !server.isClosed()) return;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (startServerOnce()) return;
            try { Thread.sleep(300); } catch (InterruptedException ignored) { }
        }
    }

    private static synchronized boolean startServerOnce() {
        if (server != null && !server.isClosed()) return true;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("127.0.0.1", PORT));
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
            return true;
        } catch (Throwable ignored) {
            try { if (server != null) server.close(); } catch (Throwable ignored2) { }
            server = null;
            return false;
        }
    }

    private static void serve(Socket sock) {
        try (Socket s = sock) {
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line = r.readLine();
            if (line == null) return;
            PrintStream w = new PrintStream(s.getOutputStream(), true, "UTF-8");
            while (line != null) {
                line = line.trim();
                if (line.isEmpty()) { line = r.readLine(); continue; }
                JSONObject res;
                long t0 = SystemClock.elapsedRealtime();
                try {
                    res = handle(new JSONObject(line));
                } catch (Throwable e) {
                    res = new JSONObject().put("ok", false)
                            .put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                res.put("ms", SystemClock.elapsedRealtime() - t0);
                w.println("RESULT:" + res);
                line = r.readLine();
            }
        } catch (Throwable ignored) { }
    }

    private static JSONObject handle(JSONObject req) throws Exception {
        if (service == null) return new JSONObject().put("ok", false).put("code", "SERVICE_NOT_READY");
        switch (req.optString("cmd")) {
            case "ping": return new JSONObject().put("ok", true).put("pong", true);
            case "dump": return dump(req);
            case "settext": return setText(req);
            case "click2": return click(req);
            case "tapnode": return tapNode(req);
            case "wait": return waitText(req);
            case "idle": return waitIdle(req);
            case "scroll": return scroll(req);
            case "list": return list(req);
            case "diag": return diag(req);
            case "notify": return notifyUser(req);
            default: return new JSONObject().put("ok", false).put("code", "UNKNOWN_CMD");
        }
    }

    private static List<AccessibilityWindowInfo> windowsOf(int did) {
        SparseArray<List<AccessibilityWindowInfo>> all = service.getWindowsOnAllDisplays();
        if (all == null) return null;
        int idx = did < 0 ? (all.size() > 0 ? 0 : -1) : all.indexOfKey(did);
        return idx < 0 ? null : all.valueAt(idx);
    }

    private static List<AccessibilityNodeInfo> flatten(List<AccessibilityWindowInfo> list, int limit) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        if (list == null) return out;
        for (AccessibilityWindowInfo w : list) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
            q.add(root);
            while (!q.isEmpty() && out.size() < limit) {
                AccessibilityNodeInfo n = q.poll();
                if (n == null) continue;
                out.add(n);
                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo ch = n.getChild(i);
                    if (ch != null) q.add(ch);
                }
            }
        }
        return out;
    }

    private static String textOf(AccessibilityNodeInfo n) {
        String t = n.getText() == null ? "" : n.getText().toString();
        if (t.isEmpty() && n.getContentDescription() != null) t = n.getContentDescription().toString();
        return t;
    }

    private static JSONObject dump(JSONObject req) throws Exception {
        int did = req.optInt("displayId", -1);
        int limit = req.optInt("limit", 250);
        List<AccessibilityWindowInfo> list = windowsOf(did);
        if (list == null) return new JSONObject().put("ok", false).put("code", "DISPLAY_NOT_FOUND");
        JSONArray nodes = new JSONArray();
        int count = 0;
        for (AccessibilityNodeInfo n : flatten(list, limit)) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.isEmpty()) continue;
            JSONObject o = new JSONObject();
            o.put("cls", String.valueOf(n.getClassName()));
            o.put("pkg", String.valueOf(n.getPackageName()));
            o.put("text", n.getText() == null ? "" : n.getText().toString());
            o.put("desc", n.getContentDescription() == null ? "" : n.getContentDescription().toString());
            o.put("vid", n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName());
            o.put("edit", n.isEditable());
            o.put("click", n.isClickable());
            o.put("enabled", n.isEnabled());
            o.put("bounds", new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom));
            nodes.put(o);
            count++;
        }
        return new JSONObject().put("ok", true).put("displayId", did).put("count", count).put("nodes", nodes);
    }

    /**
     * D1: a text field is picked deliberately, not "the first one we found".
     *
     * Old behaviour: several editable fields + no viewId silently wrote into field 0,
     * `index` past the end was clamped to the last field, and a successful
     * performAction() was reported as success even when the field never changed.
     */
    private static JSONObject setText(JSONObject req) throws Exception {
        int did = req.optInt("displayId", -1);
        String text = req.optString("text", "");
        String viewId = req.optString("viewId", "");
        int index = req.optInt("index", -1);
        List<AccessibilityNodeInfo> fields = new ArrayList<>();
        for (AccessibilityNodeInfo n : flatten(windowsOf(did), 400)) {
            if (!n.isEditable() || !n.isEnabled()) continue;
            if (!viewId.isEmpty()) {
                String v = n.getViewIdResourceName();
                if (v == null || !v.contains(viewId)) continue;
            }
            fields.add(n);
        }
        if (fields.isEmpty())
            return new JSONObject().put("ok", false).put("code", "NO_EDITABLE").put("displayId", did);

        if (req.has("index") && index < 0) {
            return new JSONObject().put("ok", false).put("code", "INDEX_OUT_OF_RANGE")
                    .put("have", fields.size()).put("requested", index)
                    .put("hint", "index starts at 0; omit it entirely to use the only field");
        }
        if (index >= 0) {
            if (index >= fields.size())
                return new JSONObject().put("ok", false).put("code", "INDEX_OUT_OF_RANGE")
                        .put("have", fields.size()).put("requested", index);
        } else if (fields.size() > 1) {
            // even with a view_id: two matches are still two different fields
            JSONArray cand = new JSONArray();
            for (int i = 0; i < fields.size(); i++) {
                AccessibilityNodeInfo f = fields.get(i);
                Rect r = new Rect();
                f.getBoundsInScreen(r);
                CharSequence hint = f.getHintText();
                cand.put(new JSONObject().put("index", i)
                        .put("hint", hint == null ? "" : hint.toString())
                        .put("vid", f.getViewIdResourceName() == null ? "" : f.getViewIdResourceName())
                        .put("text", f.getText() == null ? "" : f.getText().toString())
                        .put("bounds", new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom)));
            }
            return new JSONObject().put("ok", false).put("code", "AMBIGUOUS")
                    .put("count", fields.size()).put("candidates", cand)
                    .put("hint", "pass index or view_id to pick one of the candidates");
        }

        AccessibilityNodeInfo target = fields.get(index >= 0 ? index : 0);
        // Deliberately no ACTION_FOCUS: SET_TEXT does not need it, and focusing a field
        // on the hidden display pulls the system input focus (and the user's keyboard)
        // over to that display, which shows up as "typing keeps getting interrupted".
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);

        // performAction()==true only means the framework accepted the request; several
        // ROMs clamp or reformat the value afterwards, so read it back
        boolean verified = false;
        String readback = "";
        boolean refreshed = false;
        long deadline = SystemClock.elapsedRealtime() + 600;
        while (true) {
            Thread.sleep(60);
            refreshed = target.refresh();
            if (!refreshed) break;          // stale node: cannot prove anything
            CharSequence rb = target.getText();
            readback = rb == null ? "" : rb.toString();
            verified = text.contentEquals(rb == null ? "" : rb);
            if (verified || SystemClock.elapsedRealtime() >= deadline) break;
        }
        JSONObject out = new JSONObject().put("ok", ok).put("verified", verified)
                .put("readback", readback);
        if (!refreshed) out.put("warning", "节点刷新失败，无法确认写入结果");
        else if (!verified) out.put("warning", "写入已提交但回读不一致，输入框可能改写了内容");
        return out;
    }

    /**
     * Tap by coordinate without injecting touch.
     *
     * Injected MotionEvents make the hidden display's window the system input target,
     * which drags the input focus (and the user's keyboard) over to that display. A node
     * action clicks the same widget without touching the input pipeline.
     */
    private static JSONObject tapNode(JSONObject req) throws Exception {
        int did = req.optInt("displayId", -1);
        int x = req.optInt("x", -1), y = req.optInt("y", -1);
        long hold = req.optLong("holdMs", 0);
        if (x < 0 || y < 0) return new JSONObject().put("ok", false).put("code", "BAD_ARGUMENT");
        AccessibilityNodeInfo hit = null;
        AccessibilityNodeInfo clickable = null;
        for (AccessibilityNodeInfo n : flatten(windowsOf(did), 800)) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (!r.contains(x, y)) continue;
            if (hit == null) hit = n;
            if (n.isClickable() || (hold > 0 && n.isLongClickable())) {
                clickable = n;
                break;
            }
        }
        if (hit == null) return new JSONObject().put("ok", false).put("code", "NO_NODE");
        AccessibilityNodeInfo target = nearestClickable(clickable == null ? hit : clickable);
        if (target == null) return new JSONObject().put("ok", false).put("code", "NO_NODE");
        String before = signature(did);
        int evBefore = eventCount;
        boolean ok = target.performAction(hold > 0
                ? AccessibilityNodeInfo.ACTION_LONG_CLICK
                : AccessibilityNodeInfo.ACTION_CLICK);
        if (!ok && hold > 0) ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        boolean changed = false;
        long deadline = SystemClock.elapsedRealtime() + 250;
        while (SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(20);
            if (eventCount != evBefore || !signature(did).equals(before)) { changed = true; break; }
        }
        if (changed && target.isEditable()) {
            // Clicking a text field makes that app request the IME. On this ROM the hidden
            // display shares display group 0 with the real screen, so the keyboard is taken
            // away from whatever the user is typing in. Dropping focus right away keeps the
            // click but releases the IME.
            try {
                Thread.sleep(120);
                target.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
            } catch (Throwable ignored) { }
        }
        Rect r = new Rect();
        target.getBoundsInScreen(r);
        return new JSONObject().put("ok", ok).put("changed", changed)
                .put("target", textOf(target))
                .put("viewId", target.getViewIdResourceName() == null ? "" : target.getViewIdResourceName())
                .put("bounds", new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom));
    }

    private static AccessibilityNodeInfo nearestClickable(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 6 && cur != null; i++) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
        }
        return n;
    }

    private static JSONObject click(JSONObject req) throws Exception {
        int did = req.optInt("displayId", -1);
        String viewId = req.optString("viewId", "");
        String text = req.optString("text", "");
        String desc = req.optString("desc", "");
        boolean exact = req.optBoolean("exact", true);
        int index = req.optInt("index", -1);
        List<AccessibilityNodeInfo> hits = new ArrayList<>();
        for (AccessibilityNodeInfo n : flatten(windowsOf(did), 500)) {
            boolean ok;
            if (!viewId.isEmpty()) {
                String v = n.getViewIdResourceName();
                ok = v != null && (exact ? v.equals(viewId) : v.contains(viewId));
            } else {
                String nt = n.getText() == null ? "" : n.getText().toString();
                String nd = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
                boolean t = !text.isEmpty() && (exact ? nt.equals(text) : nt.contains(text));
                boolean d = !desc.isEmpty() && (exact ? nd.equals(desc) : nd.contains(desc));
                ok = t || d;
            }
            if (ok) hits.add(n);
        }
        if (hits.isEmpty()) {
            // "not found" alone makes the model dump the screen and guess. Hand back the
            // clickable things that ARE on this screen so it can pick one and move on.
            JSONArray cand = new JSONArray();
            for (AccessibilityNodeInfo n : flatten(windowsOf(did), 400)) {
                if (cand.length() >= 12) break;
                String t = n.getText() == null ? "" : n.getText().toString().trim();
                String d = n.getContentDescription() == null ? ""
                        : n.getContentDescription().toString().trim();
                String v = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName();
                if (!n.isClickable() && t.isEmpty() && d.isEmpty()) continue;
                if (t.isEmpty() && d.isEmpty() && v.isEmpty()) continue;
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                cand.put(new JSONObject().put("text", t.length() > 24 ? t.substring(0, 24) : t)
                        .put("desc", d.length() > 24 ? d.substring(0, 24) : d)
                        .put("vid", v)
                        .put("click", n.isClickable())
                        .put("bounds", new JSONArray().put(r.left).put(r.top)
                                .put(r.right).put(r.bottom)));
            }
            return new JSONObject().put("ok", false).put("code", "NOT_FOUND")
                    .put("candidates", cand)
                    .put("hint", "use one of the candidates (text/desc/vid) or tap a coordinate");
        }
        if (req.has("index") && index < 0) {
            return new JSONObject().put("ok", false).put("code", "INDEX_OUT_OF_RANGE")
                    .put("have", hits.size()).put("requested", index);
        }
        if (hits.size() > 1 && index < 0) {
            return new JSONObject().put("ok", false).put("code", "AMBIGUOUS").put("count", hits.size());
        }
        if (index >= hits.size()) {
            // clamping to the last hit used to turn a bad index into a click somewhere else
            return new JSONObject().put("ok", false).put("code", "INDEX_OUT_OF_RANGE")
                    .put("have", hits.size()).put("requested", index);
        }
        AccessibilityNodeInfo picked = hits.get(index < 0 ? 0 : index);
        AccessibilityNodeInfo clickable = nearestClickable(picked);
        boolean editableTarget = picked.isEditable();
        String before = signature(did);
        int evBefore = eventCount;
        long t0 = SystemClock.elapsedRealtime();
        boolean ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        boolean changed = false;
        long deadline = t0 + 250;
        long lastSample = 0;
        while (SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(20);
            if (eventCount != evBefore) { changed = true; break; }
            long now = SystemClock.elapsedRealtime();
            if (lastSample == 0 || now - lastSample >= 100) {
                lastSample = now;
                if (!signature(did).equals(before)) { changed = true; break; }
            }
        }
        if (changed && editableTarget) {
            // same reason as tapNode: a clicked text field would otherwise pull the IME
            // onto the hidden display and hide the user's keyboard
            try {
                Thread.sleep(120);
                picked.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
            } catch (Throwable ignored) { }
        }
        Rect r = new Rect();
        clickable.getBoundsInScreen(r);
        return new JSONObject().put("ok", ok).put("changed", changed)
                .put("bounds", new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom));
    }

    private static String signature(int did) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (AccessibilityNodeInfo node : flatten(windowsOf(did), 160)) {
            String t = textOf(node);
            if (!t.isEmpty()) { sb.append(t).append('|'); n++; }
            if (n > 60) break;
        }
        return Integer.toHexString(sb.toString().hashCode()) + ":" + n;
    }

    private static long lastEventFor(int did) {
        synchronized (lastEventByDisplay) {
            if (did < 0) return lastEventAt;
            int i = lastEventByDisplay.indexOfKey(did);
            if (i < 0) {
                long now = SystemClock.elapsedRealtime();
                lastEventByDisplay.put(did, now);
                return now;
            }
            return lastEventByDisplay.valueAt(i);
        }
    }

    private static JSONObject waitText(JSONObject req) throws Exception {
        int did = req.optInt("displayId", -1);
        String needle = req.optString("text", "");
        long timeout = req.optLong("timeoutMs", 8000);
        long t0 = SystemClock.elapsedRealtime();
        int checks = 0;
        while (SystemClock.elapsedRealtime() - t0 < timeout) {
            checks++;
            for (AccessibilityNodeInfo n : flatten(windowsOf(did), 400)) {
                if (textOf(n).contains(needle)) {
                    Rect r = new Rect();
                    n.getBoundsInScreen(r);
                    return new JSONObject().put("ok", true)
                            .put("elapsedMs", SystemClock.elapsedRealtime() - t0).put("checks", checks)
                            .put("bounds", new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom));
                }
            }
            int before = eventCount;
            for (int i = 0; i < 20 && eventCount == before; i++) Thread.sleep(10);
        }
        return new JSONObject().put("ok", false).put("code", "TIMEOUT");
    }

    private static JSONObject waitIdle(JSONObject req) throws Exception {
        int did = req.optInt("displayId", -1);
        long quiet = req.optLong("quietMs", 400);
        long timeout = req.optLong("timeoutMs", 8000);
        int checkMs = req.optInt("checkMs", 200);
        long t0 = SystemClock.elapsedRealtime();
        String prev = signature(did);
        long lastChange = t0;
        while (SystemClock.elapsedRealtime() - t0 < timeout) {
            Thread.sleep(checkMs);
            long now = SystemClock.elapsedRealtime();
            String cur = signature(did);
            if (!cur.equals(prev)) { prev = cur; lastChange = now; }
            long eq = now - lastEventFor(did);
            long sq = now - lastChange;
            if (eq >= quiet && sq >= quiet) {
                return new JSONObject().put("ok", true).put("elapsedMs", now - t0)
                        .put("quietMs", Math.min(eq, sq));
            }
        }
        return new JSONObject().put("ok", false).put("code", "TIMEOUT");
    }

    private static JSONObject scroll(JSONObject req) throws Exception {
        int did = req.optInt("displayId", -1);
        // the tool schema exposes "direction"; "dir" is the old internal name
        String dir = req.optString("direction", req.optString("dir", "down"));
        boolean forward = !"up".equals(dir);
        AccessibilityNodeInfo best = null;
        long bestArea = 0;
        for (AccessibilityNodeInfo n : flatten(windowsOf(did), 500)) {
            if (!n.isScrollable() || !n.isEnabled()) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            long area = (long) r.width() * r.height();
            if (area > bestArea) { bestArea = area; best = n; }
        }
        if (best == null) return new JSONObject().put("ok", false).put("code", "NO_SCROLLABLE");
        String before = signature(did);
        boolean ok = best.performAction(forward
                ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        // the list animates: sample for a short while instead of one fixed 350ms guess
        boolean moved = false;
        long deadline = SystemClock.elapsedRealtime() + 1200;
        while (SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(120);
            if (!before.equals(signature(did))) { moved = true; break; }
        }
        return new JSONObject().put("ok", ok).put("moved", moved);
    }

    private static JSONObject list(JSONObject req) throws Exception {
        int did = req.optInt("displayId", -1);
        int maxRows = req.optInt("maxRows", 40);
        int tol = req.optInt("tolerance", 40);
        List<Object[]> items = new ArrayList<>();
        for (AccessibilityNodeInfo n : flatten(windowsOf(did), 500)) {
            String t = textOf(n).trim();
            if (t.isEmpty()) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.isEmpty()) continue;
            items.add(new Object[]{(r.top + r.bottom) / 2, r.left, t});
        }
        items.sort(new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) {
                int ya = (Integer) a[0], yb = (Integer) b[0];
                if (Math.abs(ya - yb) > tol) return Integer.compare(ya, yb);
                return Integer.compare((Integer) a[1], (Integer) b[1]);
            }
        });
        JSONArray rows = new JSONArray();
        JSONArray cur = null;
        int curY = Integer.MIN_VALUE;
        for (Object[] it : items) {
            int y = (Integer) it[0];
            if (cur == null || Math.abs(y - curY) > tol) {
                if (cur != null && rows.length() < maxRows) rows.put(cur);
                if (rows.length() >= maxRows) break;
                cur = new JSONArray();
                curY = y;
            }
            cur.put(new JSONObject().put("t", it[2]));
        }
        if (cur != null && cur.length() > 0 && rows.length() < maxRows) rows.put(cur);
        return new JSONObject().put("ok", true).put("rows", rows).put("rowCount", rows.length());
    }

    private static JSONObject diag(JSONObject req) throws Exception {
        int did = req.optInt("displayId", -1);
        List<AccessibilityNodeInfo> all = flatten(windowsOf(did), 400);
        List<String> texts = new ArrayList<>();
        int edit = 0, click = 0;
        String pkg = "";
        for (AccessibilityNodeInfo n : all) {
            if (n.isEditable()) edit++;
            if (n.isClickable()) click++;
            String t = textOf(n).trim();
            if (!t.isEmpty() && texts.size() < 20) texts.add(t.length() > 40 ? t.substring(0, 40) : t);
            if (pkg.isEmpty() && n.getPackageName() != null) pkg = n.getPackageName().toString();
        }
        return new JSONObject().put("ok", true).put("displayId", did).put("package", pkg)
                .put("nodeCount", all.size()).put("editables", edit).put("clickables", click)
                .put("texts", new JSONArray(texts)).put("eventCount", eventCount)
                .put("sinceLastEventMs", SystemClock.elapsedRealtime() - lastEventAt);
    }

    private static JSONObject notifyUser(JSONObject req) throws Exception {
        String title = req.optString("title", "任务完成");
        String text = req.optString("text", "");
        String component = req.optString("open", "");
        android.app.NotificationManager nm = service.getSystemService(android.app.NotificationManager.class);
        nm.createNotificationChannel(new android.app.NotificationChannel(
                "eta-bg", "后台任务", android.app.NotificationManager.IMPORTANCE_DEFAULT));
        // A plain am-start only *resumes* a task that sits on the hidden display; it
        // never moves it to the main screen. So we open Eta itself and let it run the
        // handover engine command instead.
        Intent open = new Intent();
        open.setClassName(service.getPackageName(), "io.github.mangi.eta.ui.MainActivity");
        String target = component.contains("/")
                ? component.substring(0, component.indexOf('/')) : component;
        if (!target.isEmpty()) open.putExtra("eta_shadow_handover", target);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(service, 2, open,
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification n = new android.app.Notification.Builder(service, "eta-bg")
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title).setContentText(text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(pi).build();
        nm.notify("eta-bg", 2, n);
        return new JSONObject().put("ok", true);
    }
}
