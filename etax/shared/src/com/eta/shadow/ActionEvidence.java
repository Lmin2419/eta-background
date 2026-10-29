package com.eta.shadow;

import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Input acceptance and observed UI change are different facts. No screenshot-age heuristic. */
public final class ActionEvidence {
    private ActionEvidence() { }

    public static boolean comparable(JSONObject before, JSONObject after) {
        return before != null && after != null && before.optBoolean("observable")
                && after.optBoolean("observable") && !before.optBoolean("scanLimited") && !after.optBoolean("scanLimited")
                && !before.optString("fingerprint").isEmpty()
                && before.optString("targetKey").equals(after.optString("targetKey"));
    }

    public static boolean changed(JSONObject before, JSONObject after) {
        return comparable(before, after)
                && !before.optString("fingerprint").equals(after.optString("fingerprint"));
    }

    public static JSONObject attach(JSONObject result, JSONObject before, JSONObject after) throws Exception {
        boolean accepted = result.optBoolean("ok");
        boolean comparable = comparable(before, after);
        boolean changed = accepted && changed(before, after);
        result.put("accepted", accepted).put("verified", changed)
                .put("outcome", !accepted ? "REJECTED" : changed ? "CHANGED"
                        : comparable ? "NO_OBSERVED_CHANGE" : "UNKNOWN");
        if (before != null) result.put("beforeFingerprint", before.optString("fingerprint"));
        if (after != null) result.put("after", new JSONObject(after.toString()).put("screen_changed",
                comparable ? Boolean.valueOf(changed) : JSONObject.NULL));
        if (accepted && !changed) {
            result.put("code", "ACTION_OUTCOME_UNKNOWN").put("operationMayHaveRun", true)
                    .put("hint", "动作已提交，但未验证目标变化；先重新观察，不要直接重放，也不能据此判断内容不存在或已到底。");
        }
        return result;
    }

    /** Bounded per-display/target history, used only with meaningful, comparable node evidence. */
    public static final class Tracker {
        private static final int LIMIT = 3;
        private final Map<String, ProgressEntry> entries = new LinkedHashMap<String, ProgressEntry>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, ProgressEntry> eldest) { return size() > 64; }
        };
        private static final class ProgressEntry {
            String fingerprint;
            int count;
            long at;
        }

        public synchronized JSONObject blocked(String key, JSONObject before, long now) throws Exception {
            ProgressEntry entry = entries.get(key);
            if (entry == null) return null;
            if (now - entry.at > 120_000 || before == null || !before.optBoolean("observable") || before.optBoolean("scanLimited")
                    || !entry.fingerprint.equals(before.optString("fingerprint"))) {
                entries.remove(key);
                return null;
            }
            if (entry.count < LIMIT) return null;
            return new JSONObject().put("ok", false).put("accepted", false).put("verified", false)
                    .put("code", "NO_PROGRESS").put("outcome", "REJECTED")
                    .put("operationMayHaveRun", false).put("unchangedAttempts", entry.count)
                    .put("after", before)
                    .put("hint", "同一目标连续三次没有可验证变化，本次未执行；请诊断输入状态或换目标/方式，不要反复重建应用，也不能推断历史消息不存在。");
        }

        public synchronized void record(String key, JSONObject before, JSONObject after, boolean accepted, long now) {
            if (!accepted || !comparable(before, after) || changed(before, after)) {
                entries.remove(key);
                return;
            }
            String fingerprint = after.optString("fingerprint");
            ProgressEntry entry = entries.get(key);
            if (entry == null || !fingerprint.equals(entry.fingerprint) || now - entry.at > 120_000) {
                entry = new ProgressEntry(); entries.put(key, entry);
            }
            entry.fingerprint = fingerprint;
            entry.count++;
            entry.at = now;
        }

        public synchronized void clear() { entries.clear(); }
    }
}
