package com.eta.shadow;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded snapshots of visible application content; excludes status-bar/notification changes. */
final class BackgroundObservation {
    static List<AccessibilityNodeInfo> nodes(AccessibilityNodeInfo root, int limit) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        if (root != null) queue.add(root);
        while (!queue.isEmpty() && nodes.size() < limit) {
            AccessibilityNodeInfo node = queue.removeFirst(); nodes.add(node);
            for (int i = 0; i < node.getChildCount() && queue.size() + nodes.size() < limit; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return nodes;
    }

    static JSONObject target(AccessibilityNodeInfo node) throws Exception {
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        return new JSONObject().put("viewId", value(node.getViewIdResourceName()))
                .put("class", value(node.getClassName())).put("windowId", node.getWindowId())
                .put("bounds", rect(bounds));
    }

    static String targetKey(AccessibilityNodeInfo node) throws Exception {
        JSONObject target = target(node);
        return node.getWindowId() + ":" + value(node.getPackageName()) + ":"
                + value(node.getViewIdResourceName()) + ":" + value(node.getClassName())
                + ":" + target.getJSONArray("bounds");
    }

    static JSONObject capture(AccessibilityNodeInfo root) throws Exception {
        if (root == null) return new JSONObject().put("observable", false);
        Rect viewport = new Rect(); root.getBoundsInScreen(viewport);
        JSONArray evidence = new JSONArray(), preview = new JSONArray();
        int meaningful = 0;
        List<AccessibilityNodeInfo> all = nodes(root, 240);
        for (AccessibilityNodeInfo node : all) {
            Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
            if (!node.isVisibleToUser() || bounds.isEmpty() || !Rect.intersects(viewport, bounds)) continue;
            // Positions are relative to the target, so moving an outer container alone isn't scrolling it.
            bounds.offset(-viewport.left, -viewport.top);
            String text = value(node.getText()), desc = value(node.getContentDescription());
            String cls = value(node.getClassName());
            boolean canvas = cls.contains("SurfaceView") || cls.contains("TextureView") || cls.contains("UnityPlayer");
            boolean semantic = !canvas && (!text.isEmpty() || !desc.isEmpty() || node.isEditable() || node.isCheckable());
            if (semantic) meaningful++;
            JSONObject item = new JSONObject().put("id", value(node.getViewIdResourceName()))
                    .put("cls", value(node.getClassName())).put("text", text).put("desc", desc)
                    .put("bounds", rect(bounds)).put("checked", node.isChecked())
                    .put("selected", node.isSelected()).put("enabled", node.isEnabled());
            evidence.put(item);
            if (semantic && preview.length() < 12) preview.put(new JSONObject(item.toString())
                    .put("text", text.substring(0, Math.min(text.length(), 160)))
                    .put("desc", desc.substring(0, Math.min(desc.length(), 160))));
        }
        return new JSONObject().put("observable", meaningful > 0 && !viewport.isEmpty())
                .put("targetKey", targetKey(root)).put("target", target(root))
                .put("fingerprint", digest(evidence.toString())).put("visibleNodeCount", evidence.length())
                .put("nodes", preview).put("previewOnly", true).put("scanLimited", all.size() >= 240);
    }

    static JSONObject scene(List<AccessibilityWindowInfo> windows, JSONObject request) throws Exception {
        if (windows == null) return new JSONObject().put("observable", false);
        AccessibilityNodeInfo root = null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo candidate = window.getRoot();
            if (candidate == null) continue;
            if (request.has("x") && request.has("y")) {
                Rect bounds = new Rect(); candidate.getBoundsInScreen(bounds);
                if (!bounds.contains(request.optInt("x"), request.optInt("y"))) continue;
            }
            if (root == null || window.isFocused()) root = candidate;
            if (window.isFocused()) break;
        }
        // Anchor a gesture to the smallest scrollable region containing its start point.
        if (root != null && request.has("x") && request.has("y")) {
            Rect bounds = new Rect();
            AccessibilityNodeInfo selected = null;
            long area = Long.MAX_VALUE;
            for (AccessibilityNodeInfo node : nodes(root, 240)) {
                if (!node.isScrollable() || !node.isVisibleToUser()) continue;
                node.getBoundsInScreen(bounds);
                long size = (long) bounds.width() * bounds.height();
                if (bounds.contains(request.optInt("x"), request.optInt("y")) && size > 0 && size < area) {
                    selected = node; area = size;
                }
            }
            if (selected != null) root = selected;
        }
        return capture(root);
    }

    static JSONArray rect(Rect bounds) { return new JSONArray().put(bounds.left).put(bounds.top).put(bounds.right).put(bounds.bottom); }
    static String value(CharSequence value) { return value == null ? "" : value.toString(); }
    static String digest(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        char[] out = new char[hash.length * 2];
        char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < hash.length; i++) { out[i * 2] = hex[(hash[i] & 255) >>> 4]; out[i * 2 + 1] = hex[hash[i] & 15]; }
        return new String(out);
    }
}
