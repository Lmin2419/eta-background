package com.eta.shadow;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Appends the hidden-display tools to Eta's tool schema array. */
public final class ShadowSchema {

    /**
     * Native GUI tools superseded by screen_*. They are removed from the model's
     * tool list while the engine is healthy, otherwise the model keeps picking
     * launch_app/observe_screen and drives the user's own screen.
     */
    private static final Set<String> REPLACED = new HashSet<>(Arrays.asList(
            "launch_app", "observe_screen", "tap", "tap_element",
            "long_press", "long_press_element", "swipe", "scroll", "scroll_element",
            "type_text", "press_key", "wait_for_text", "wait_for_package"));

    public static void append(JSONArray tools) {
        android.content.Context ctx = ShadowSettings.app();
        boolean engine = ctx != null && ShadowSettings.engineReady(ctx);
        if (ctx != null) android.util.Log.i("EtaShadow", "schema append engine=" + engine + " n=" + tools.length());
        if (engine) {
            removeReplaced(tools);
        }
        try {
            for (int i = 0; i < tools.length(); i++) {
                JSONObject o = tools.optJSONObject(i);
                if (o == null) continue;
                JSONObject fn = o.optJSONObject("function");
                if (fn != null && "screen_open".equals(fn.optString("name"))) return;
            }
        } catch (Throwable ignored) { }
        try {
            tools.put(tool("screen_open",
                    "在本任务独占的后台虚拟屏打开应用。同一应用被其他任务占用时等待释放。"
                    + "不同应用最多使用两个虚拟屏；额外任务等待空闲位置，不会抢占正在执行的任务。"
                    + "所以凡是涉及界面的操作都走这个工具，不要用 terminal 执行 am/input/screencap。"
                    + "只传包名即可（如 com.tencent.mm）。暂停恢复后必须先重新观察页面再操作；操作结束后用 screen_finish 收尾。",
                    props3("component", "string", "包名或 package/activity，例如 com.tencent.mm",
                            "package", "string", "包名（与 component 二选一）",
                            "app_name", "string", "应用显示名（前两者都没有时才用）")
                            .put("orientation", property("string", "auto 默认按应用方向；横屏游戏请选 landscape，竖屏应用 portrait。仅新建屏生效，不重建已有 App。").put("enum", new JSONArray().put("auto").put("landscape").put("portrait"))),
                    ""));
            tools.put(tool("screen_state", "查看后台操作会话与引擎状态（含 root 引擎是否就绪）。", new JSONObject(), ""));
            tools.put(tool("screen_tap", "在当前屏幕点按坐标。截图坐标必须携带 coordinate_space=screenshot 和该截图 frame_id；节点 bounds 用 display。",
                    coordinates(props3("x", "integer", "横坐标", "y", "integer", "纵坐标")), "x,y"));
            tools.put(tool("screen_swipe", "从一点滑到另一点。",
                    coordinates(props7("x1", "y1", "x2", "y2", "duration_ms")), "x1,y1,x2,y2"));
            tools.put(tool("screen_press", "长按坐标（上下文菜单、拖拽把手）。",
                    coordinates(props3("x", "integer", "横坐标", "y", "integer", "纵坐标", "hold_ms", "integer", "按住毫秒")),
                    "x,y"));
            tools.put(tool("screen_drag", "按住后拖动再松开（排序、滑块）。",
                    coordinates(props7("x1", "y1", "x2", "y2", "duration_ms", "hold_ms")), "x1,y1,x2,y2"));
            tools.put(tool("screen_text", "往输入框写文本，中文可直接写入。有多个输入框时用 view_id 或 index 指定。",
                    props3("text", "string", "要写入的文本",
                            "view_id", "string", "输入框的 view id（可只写后半段）",
                            "index", "integer", "第几个输入框，从 0 开始"),
                    "text"));
            tools.put(tool("screen_click", "按 view id、文本或描述精确点击；多个匹配时会返回 AMBIGUOUS 和候选，"
                    + "并报告点击后界面是否真的发生变化。",
                    props3("text", "string", "要点击的文本",
                            "desc", "string", "contentDescription",
                            "index", "integer", "多个匹配时的序号")
                            .put("view_id", property("string", "view id（可只写后半段）"))
                            .put("exact", property("boolean", "是否精确匹配文本/描述，默认 true")),
                    ""));
            tools.put(tool("screen_wait", "等待某段文字出现，或等待界面停止变化。",
                    props3("text", "string", "等待出现的文字",
                            "timeout_ms", "integer", "超时毫秒",
                            "quiet_ms", "integer", "静止阈值毫秒"),
                    ""));
            tools.put(tool("screen_scroll", "滚动当前屏幕的主要可滚动容器。",
                    enumProp("direction", "down 或 up"), ""));
            tools.put(tool("screen_list", "把当前屏幕按行读出：同一水平位置的文字归为一行，"
                    + "标题和价格会落在同一行。",
                    props1("max_rows", "integer", "最多行数"), ""));
            tools.put(tool("screen_dump", "读取当前屏幕的原始节点列表（含 bounds 与 view id）。",
                    props1("limit", "integer", "最多节点数"), ""));
            tools.put(tool("screen_shot", "截取当前显示方向的画面，返回逻辑尺寸、缩放比例和 frame_id。用 read_image 查看后，截图坐标操作请带 coordinate_space=screenshot 和 frame_id。游戏/画布通常没有按钮节点，应使用截图。转屏后必须重新截图。",
                    props3("path", "string", "保存路径", "scale", "number", "缩放比例"), ""));
            tools.put(tool("screen_diag", "当前屏幕的诊断快照（包名、节点数、最近文本）。", new JSONObject(), ""));
            tools.put(tool("screen_finish", "结束后台操作，由该任务的运行通知展示完成结果。"
                    + "应用会留在后台虚拟屏里继续待命，用户的屏幕完全不动，"
                    + "原通知显示本轮结果，用户可继续指令或打开目标应用。等待用户决定也应保留页面，不应当作目标已全部达成。"
                    + "保留的页面不会因一分钟超时被销毁；新任务需要屏幕容量时可能回收。"
                    + "后台收尾不会自动打开目标应用或切换用户当前页面。",
                    props3("title", "string", "通知标题",
                            "text", "string", "结果摘要（点卡片打开目标应用，另有会话入口）",
                            "open", "string", "结果涉及的应用包名（兼容旧参数；以本任务实际操作的应用为准）")
                            .put("mode", property("string",
                                    "收尾方式：notify（默认，留在后台虚拟屏并发通知）、"
                                    + "destroy（直接释放虚拟屏）")),
                    ""));
        } catch (Throwable ignored) { }
    }

    /** Drop native GUI tools so screen_* is the only way to drive an app. */
    private static void removeReplaced(JSONArray tools) {
        try {
            List<JSONObject> keep = new ArrayList<>();
            for (int i = 0; i < tools.length(); i++) {
                JSONObject o = tools.optJSONObject(i);
                if (o == null) continue;
                JSONObject fn = o.optJSONObject("function");
                String name = fn != null ? fn.optString("name") : o.optString("name");
                if (!REPLACED.contains(name)) keep.add(o);
            }
            int before = tools.length();
            while (tools.length() > 0) tools.remove(tools.length() - 1);
            for (JSONObject o : keep) tools.put(o);
            android.util.Log.i("EtaShadow", "tools " + before + " -> " + tools.length());
        } catch (Throwable t) {
            android.util.Log.w("EtaShadow", "removeReplaced failed: " + t);
        }
    }

    private static JSONObject tool(String name, String desc, JSONObject properties, String required) {
        try {
            JSONObject schema = new JSONObject().put("type", "object").put("properties", properties);
            if (required != null && !required.isEmpty()) {
                JSONArray req = new JSONArray();
                for (String r : required.split(",")) req.put(r.trim());
                schema.put("required", req);
            }
            JSONObject fn = new JSONObject()
                    .put("name", name)
                    .put("description", desc)
                    .put("parameters", schema);
            return new JSONObject().put("type", "function").put("function", fn);
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    private static JSONObject props(String name, String type, String desc) {
        try {
            return new JSONObject().put(name, new JSONObject().put("type", type).put("description", desc));
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    private static JSONObject props1(String n, String t, String d) {
        return props(n, t, d);
    }

    private static JSONObject props3(String n1, String t1, String d1,
                                     String n2, String t2, String d2) {
        try {
            JSONObject o = new JSONObject();
            if (!n1.isEmpty()) o.put(n1, new JSONObject().put("type", t1).put("description", d1));
            if (!n2.isEmpty()) o.put(n2, new JSONObject().put("type", t2).put("description", d2));
            return o;
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    private static JSONObject props3(String n1, String t1, String d1,
                                     String n2, String t2, String d2,
                                     String n3, String t3, String d3) {
        try {
            JSONObject o = props3(n1, t1, d1, n2, t2, d2);
            if (!n3.isEmpty()) o.put(n3, new JSONObject().put("type", t3).put("description", d3));
            return o;
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    private static JSONObject props7(String a, String b, String c, String d, String e) {
        return props7(a, b, c, d, e, null);
    }

    private static JSONObject props7(String a, String b, String c, String d, String e, String f) {
        try {
            JSONObject o = new JSONObject();
            for (String n : new String[]{a, b, c, d}) {
                o.put(n, new JSONObject().put("type", "integer"));
            }
            if (e != null) o.put(e, new JSONObject().put("type", "integer").put("description", "毫秒"));
            if (f != null) o.put(f, new JSONObject().put("type", "integer").put("description", "按住毫秒"));
            return o;
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    private static JSONObject property(String type, String description) throws Exception {
        return new JSONObject().put("type", type).put("description", description);
    }

    private static JSONObject coordinates(JSONObject properties) throws Exception {
        return properties.put("coordinate_space", new JSONObject().put("type", "string")
                        .put("enum", new JSONArray().put("display").put("screenshot"))
                        .put("description", "display 为节点/原始显示屏像素；screenshot 为最新截图中的像素，会自动换算"))
                .put("frame_id", property("string", "截图坐标必须带最新 screen_shot 的 frameId；每次动作后重新观察"));
    }

    private static JSONObject enumProp(String name, String desc) {
        try {
            JSONArray arr = new JSONArray().put("down").put("up");
            return new JSONObject().put(name,
                    new JSONObject().put("type", "string").put("enum", arr).put("description", desc));
        } catch (Throwable e) {
            return new JSONObject();
        }
    }
}
