package com.eta.shadow;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Scroll an identified container, and verify only that container's visible content. */
final class BackgroundScroll {
    private static final ActionEvidence.Tracker progress = new ActionEvidence.Tracker();

    static JSONObject execute(JSONObject request, Supplier<List<AccessibilityWindowInfo>> windows) throws Exception {
        String direction = request.optString("direction", request.optString("dir", "down"));
        if (!"up".equals(direction) && !"down".equals(direction)) return failure("BAD_ARGUMENT", "direction 必须为 up 或 down");
        if (request.has("x") != request.has("y")) return failure("BAD_ARGUMENT", "x/y 必须一起提供（显示屏像素）");
        List<AccessibilityNodeInfo> candidates = candidates(windows.get(), direction);
        if (candidates.isEmpty()) return failure("NO_SCROLLABLE", "没有可识别的竖向滚动容器；请截图确认目标，不能推断历史已到底");
        JSONArray choices = new JSONArray();
        for (int i = 0; i < candidates.size(); i++) choices.put(BackgroundObservation.target(candidates.get(i)).put("index", i));
        List<AccessibilityNodeInfo> matching = new ArrayList<>();
        String id = request.optString("viewId", request.optString("view_id"));
        for (int i = 0; i < candidates.size(); i++) {
            AccessibilityNodeInfo node = candidates.get(i);
            if (request.has("index") && request.optInt("index", -1) != i) continue;
            String actual = BackgroundObservation.value(node.getViewIdResourceName());
            if (!id.isEmpty() && !actual.equals(id) && !actual.endsWith("/" + id)) continue;
            Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
            if (request.has("x") && !bounds.contains(request.optInt("x"), request.optInt("y"))) continue;
            matching.add(node);
        }
        if (matching.isEmpty()) return failure("TARGET_NOT_FOUND", "指定的滚动容器不存在，请重新观察").put("candidates", choices);
        // With a point, choose the deepest containing container; without a selector, do not guess.
        if (request.has("x")) {
            List<AccessibilityNodeInfo> leaves = new ArrayList<>(matching);
            for (AccessibilityNodeInfo node : matching) for (AccessibilityNodeInfo other : matching) {
                if (!node.equals(other) && ancestor(node, other)) { leaves.remove(node); break; }
            }
            matching = leaves;
        }
        if (matching.size() != 1) return failure("AMBIGUOUS_SCROLL_TARGET", "存在多个滚动容器，请用 view_id、候选 index 或 x/y 选择目标列表")
                .put("candidates", choices);
        AccessibilityNodeInfo target = matching.get(0);
        JSONObject before = BackgroundObservation.capture(target);
        String key = request.optString("sessionId") + ":" + request.optInt("displayId", -1) + ":" + direction + ":" + before.optString("targetKey");
        JSONObject blocked = progress.blocked(key, before, SystemClock.elapsedRealtime());
        if (blocked != null) { android.util.Log.i("EtaBackgroundInput", "scroll display=" + request.optInt("displayId") + " code=NO_PROGRESS"); return blocked; }
        int action = action(target, direction);
        boolean accepted = target.performAction(action);
        JSONObject after = before;
        if (accepted) {
            long deadline = System.nanoTime() + 800_000_000L;
            do {
                Thread.sleep(100);
                AccessibilityNodeInfo fresh = findAgain(windows.get(), target);
                after = BackgroundObservation.capture(fresh);
                if (!ActionEvidence.comparable(before, after) || ActionEvidence.changed(before, after)) break;
            } while (System.nanoTime() < deadline);
        }
        progress.record(key, before, after, accepted, SystemClock.elapsedRealtime());
        JSONObject result = ActionEvidence.attach(new JSONObject().put("ok", accepted), before, after)
                .put("moved", accepted && ActionEvidence.changed(before, after))
                .put("target", BackgroundObservation.target(target)).put("action", action);
        if (!accepted) result.put("code", "ACTION_REJECTED");
        android.util.Log.i("EtaBackgroundInput", "scroll display=" + request.optInt("displayId")
                + " target=" + BackgroundObservation.value(target.getViewIdResourceName())
                + " accepted=" + accepted + " outcome=" + result.optString("outcome"));
        return result;
    }

    private static List<AccessibilityNodeInfo> candidates(List<AccessibilityWindowInfo> windows, String direction) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        if (windows == null) return result;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            for (AccessibilityNodeInfo node : BackgroundObservation.nodes(window.getRoot(), 500)) {
                Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                if (node.isVisibleToUser() && node.isEnabled() && node.isScrollable() && !bounds.isEmpty()
                        && action(node, direction) != 0) result.add(node);
            }
        }
        return result;
    }

    private static int action(AccessibilityNodeInfo node, String direction) {
        int vertical = "up".equals(direction) ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId()
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId();
        int generic = "up".equals(direction) ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        boolean horizontal = false, anyScroll = false, genericSupported = false;
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            int id = action.getId();
            if (id == vertical) return vertical;
            if (id == generic) genericSupported = true;
            if (id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId()
                    || id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId()) horizontal = true;
            if (id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    || id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId()
                    || id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()) anyScroll = true;
        }
        if (horizontal && !anyScroll) return 0;
        return genericSupported || (!horizontal && !anyScroll) ? generic : 0;
    }

    private static boolean ancestor(AccessibilityNodeInfo parent, AccessibilityNodeInfo node) {
        for (int depth = 0; depth < 32 && node != null; depth++) {
            node = node.getParent();
            if (parent.equals(node)) return true;
        }
        return false;
    }

    private static AccessibilityNodeInfo findAgain(List<AccessibilityWindowInfo> windows, AccessibilityNodeInfo target) throws Exception {
        if (windows == null) return null;
        String key = BackgroundObservation.targetKey(target);
        AccessibilityNodeInfo match = null;
        for (AccessibilityWindowInfo window : windows) {
            for (AccessibilityNodeInfo node : BackgroundObservation.nodes(window.getRoot(), 500)) {
                if (!node.isVisibleToUser()) continue;
                if (!key.equals(BackgroundObservation.targetKey(node))) continue;
                if (match != null) return null; // A replaced/ambiguous tree is not evidence of success.
                match = node;
            }
        }
        return match;
    }

    private static JSONObject failure(String code, String message) throws Exception {
        return new JSONObject().put("ok", false).put("accepted", false).put("code", code)
                .put("error", message).put("operationMayHaveRun", false);
    }
}
