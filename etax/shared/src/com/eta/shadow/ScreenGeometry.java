package com.eta.shadow;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/** The same wire contract is compiled into the App observer and the Root engine. */
public final class ScreenGeometry {
    private ScreenGeometry() { }

    public static boolean valid(JSONObject value) {
        return value != null && value.optInt("width", 0) > 0 && value.optInt("height", 0) > 0
                && value.optInt("rotation", -1) >= 0 && value.optInt("rotation", -1) <= 3
                && "display".equals(value.optString("coordinateSpace", "display"));
    }

    public static boolean same(JSONObject expected, JSONObject actual) {
        if (!valid(expected) || !valid(actual)) return false;
        for (String key : new String[]{"width", "height", "rotation"})
            if (expected.optInt(key, -1) != actual.optInt(key, -1)) return false;
        return !expected.has("displayId") || expected.optInt("displayId", -1) == actual.optInt("displayId", -1);
    }

    public static JSONObject check(JSONObject expected, JSONObject actual) throws Exception {
        if (!valid(actual)) return reject("GEOMETRY_UNAVAILABLE", "ACTUAL_GEOMETRY_MISSING", expected, actual);
        if (expected == null) return null;
        if (!valid(expected)) return reject("INVALID_OBSERVATION", "INCOMPLETE_GEOMETRY", expected, actual);
        return same(expected, actual) ? null : reject("STALE_OBSERVATION", "GEOMETRY_MISMATCH", expected, actual);
    }

    private static JSONObject fields(JSONObject value) throws Exception {
        JSONObject out = new JSONObject();
        if (value != null) for (String key : new String[]{"displayId", "width", "height", "rotation", "density", "coordinateSpace", "source"})
            if (value.has(key)) out.put(key, value.opt(key));
        return out;
    }

    public static JSONObject reject(String code, String reason, JSONObject expected, JSONObject actual) throws Exception {
        JSONArray changed = new JSONArray();
        if (expected != null) for (String key : new String[]{"displayId", "width", "height", "rotation"})
            if (expected.has(key) && (actual == null || expected.optInt(key, -1) != actual.optInt(key, -1))) changed.put(key);
        JSONObject result = new JSONObject().put("ok", false).put("code", code).put("reason", reason)
                .put("expected", fields(expected)).put("actual", fields(actual)).put("changedFields", changed)
                .put("frameAgeChecked", false)
                .put("error", "观察数据不可用于本次操作；请根据 reason 和 expected/actual 重新观察，不要重复发送同一动作");
        if (actual != null) result.put("geometry", fields(actual));
        // Geometry only: never log user text, node text, tool arguments or images.
        Log.w("EtaGeometry", result.toString());
        return result;
    }
}
