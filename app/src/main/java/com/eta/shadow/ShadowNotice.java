package com.eta.shadow;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Per-run streaming summary. Completion is handed to the shared execution notification,
 * which replaces its ongoing card after all foreground leases have been released.
 * Explicit user cancellation does not post a completion card.
 */
public final class ShadowNotice {
    private static final String TAG = "ShadowNotice";
    private static final int MAX_LINE = 80;
    private static final int MAX_HISTORY = 6;
    private static final int FLUSH_MIN_GAP = 1200;
    private static final int LONG_RUN = 40;
    private static final long STEP_SHOW_MS = 1500L;
    private static final long STREAM_STALE_MS = 5000L;
    private static final String STOPPED = "已停止";


    private final StringBuilder PENDING = new StringBuilder();
    private final LinkedList<String> HISTORY = new LinkedList<>();

    private volatile boolean active = false;
    private volatile boolean sawStep = false;
    private volatile String lastLine = "";
    private volatile long lastKick = 0L;
    private volatile long lastAppend = 0L;
    private volatile String step = "";
    private volatile long stepAt = 0L;

    private static final char[] BREAKS = {'。', '！', '？', '；', '\n', '.', '!', '?', ';'};

    private final String runId;
    private final String conversationId;
    public ShadowNotice(String runId, String conversationId) { this.runId = runId; this.conversationId = conversationId; }

    // ------------------------------------------------------------- lifecycle

    /** 每次对话任务开始时调用（AgentRuntimeService 的运行线程）。 */
    public void onRunStarted() {
        synchronized (this) {
            PENDING.setLength(0);
            HISTORY.clear();
        }
        lastLine = "";
        step = "";
        stepAt = 0L;
        lastAppend = 0L;
        sawStep = false;
        active = true;
    }

    /**
     * 每次对话任务结束时调用，成功、出错、被停止、异常中断都会走到这里。
     *
     * @param ok    任务是否成功
     * @param error 失败原因（成功时为 null）
     */
    public void onRunFinished(Context ctx, boolean ok, String error) {
        List<String> lines;
        boolean didSomething;
        synchronized (this) {
            if (PENDING.length() > 0) {
                // 出错时把半句也带上，用户能看到停在了哪里
                String tailText = clean(PENDING.toString());
                if (!tailText.isEmpty()) pushLine(clip(tailText));
            }
            lines = new ArrayList<>(HISTORY);
            didSomething = !lines.isEmpty() || sawStep;
            PENDING.setLength(0);
            HISTORY.clear();
        }
        active = false;
        lastLine = "";
        step = "";

        if (STOPPED.equals(error)) return;          // 用户自己点的停止，不打扰
        if (ok && lines.isEmpty()) return;          // 成功但没有可展示的文字
        if (!ok && !didSomething && error == null) return;

        String status = ok ? "已完成" : "出错";
        if (!ok) {
            String reason = error == null || error.trim().isEmpty() ? "任务未完成" : error.trim();
            lines.add(clip("❗ " + reason));
        }
        post(ctx, status, lines);
    }

    // ---------------------------------------------------------------- stream

    /** 回答正文的流式增量（AgentLoop 中 TEXT 块）。 */
    public void append(String chunk) {
        if (!active || chunk == null || chunk.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        lastAppend = now;

        boolean updated = false;
        synchronized (this) {
            PENDING.append(chunk);
            int cut = lastBreak(PENDING);
            if (cut >= 0) {
                String clean = clean(PENDING.substring(0, cut + 1));
                PENDING.delete(0, cut + 1);
                if (!clean.isEmpty()) {
                    pushLine(clip(clean));
                    updated = true;
                }
            }
            if (PENDING.length() > 400) PENDING.delete(0, PENDING.length() - 400);
            if (!updated && PENDING.length() >= LONG_RUN) {
                String clean = clean(PENDING.toString());
                if (!clean.isEmpty()) {
                    pushLine(clip(clean));
                    updated = true;
                }
            }
        }

        // AgentRuntimeService refreshes the selected task through one shared throttle.
    }

    /** 调用方需持有本实例的锁。 */
    private void pushLine(String line) {
        if (line.isEmpty()) return;
        // 流式重试会从头重放回答：已经显示过的句子直接丢弃，并清掉半句缓冲。
        for (String seen : HISTORY) {
            if (seen.equals(line) || line.contains(seen) || seen.contains(line)) {
                lastLine = line;
                PENDING.setLength(0);
                return;
            }
        }
        lastLine = line;
        HISTORY.addLast(line);
        while (HISTORY.size() > MAX_HISTORY) HISTORY.removeFirst();
    }

    private int lastBreak(StringBuilder sb) {
        for (int i = sb.length() - 1; i >= 0; i--) {
            char c = sb.charAt(i);
            for (char b : BREAKS) {
                if (c == b) return i;
            }
        }
        return -1;
    }

    /** A: 把模型回答里的常见 markdown 去干净，通知栏里更像人话。 */
    private String clean(String s) {
        if (s == null) return "";
        String t = s;
        t = t.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");   // 链接 [文字](url) -> 文字
        t = t.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");          // 行首标题 #/##/###
        t = t.replaceAll("(?m)^\\s*[-*+]\\s+", "");               // 行首列表符 - / * / +
        t = t.replaceAll("(?m)^\\s*>\\s*", "");                   // 行首引用 >
        t = t.replace("**", "").replace("__", "").replace("~~", "")
                .replace("*", "").replace("`", "");               // 加粗/斜体/行内代码标记
        t = t.replace("\n", " ").replace("\r", " ");
        t = t.replaceAll("\\s{2,}", " ");                         // 压掉多余空白
        return t.trim();
    }

    /** 保留句尾，从自然断点开始截，避免截在词中间。 */
    private String clip(String s) {
        if (s.length() <= MAX_LINE) return s;
        int start = s.length() - (MAX_LINE - 1);
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '，' || c == ',' || c == '、' || c == ' ' || c == '。') {
                start = i + 1;
                break;
            }
        }
        if (start >= s.length()) start = s.length() - (MAX_LINE - 1);
        return "…" + s.substring(start);
    }

    // --------------------------------------------------------------- display

    /** 前台通知正文：刚执行的工具步骤优先，否则显示最新一句。 */
    public String decorate(Object original) {
        String base = original == null ? "" : original.toString();
        // S4: 这是上游构建前台通知时调的热路径，出任何异常都退回原文，绝不往上游抛。
        try {
            if (!active) return base;
            long now = SystemClock.elapsedRealtime();
            String s = step;
            if (!s.isEmpty() && now - stepAt < STEP_SHOW_MS) return s;
            if (now - lastAppend > STREAM_STALE_MS) return base;
            String line = lastLine;
            return line.isEmpty() ? base : line;
        } catch (Throwable t) {
            return base;
        }
    }

    /** 前台通知的多行展开视图（每句一行）。 */
    public void applyStyle(Notification.Builder builder) {
        if (builder == null || !active) return;
        try {
            List<String> lines;
            synchronized (this) {
                if (HISTORY.size() < 2) return;
                lines = new ArrayList<>(HISTORY);
            }
            Notification.InboxStyle style = new Notification.InboxStyle();
            for (String line : lines) style.addLine(line);
            builder.setStyle(style);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ completion

    private void post(Context ctx, String status, List<String> lines) {
        if (ctx == null || lines.isEmpty()) return;
        io.github.mangi.eta.agent.runtime.AgentExecutionService.publishRunCompletion(
                ctx, runId, conversationId, status, android.text.TextUtils.join("\n", lines));
    }

    // ----------------------------------------------------------------- utils

    /**
     * 让 Eta 的前台通知立即重绘（上游只在状态文字变化时才刷新）。
     * 直接请服务用自己的 NotificationManager 刷新；不再用 startService——
     * App 退到后台后后台启动服务会被系统拦，导致回桌面后流式不再更新。
     */
    private void kick() {
        try {
            io.github.mangi.eta.agent.runtime.AgentExecutionService.pokeStreaming();
        } catch (Throwable ignored) {
        }
    }

    /**
     * MainActivity.onCreate / onNewIntent 调用：完成通知带着目标包名，
     * 在这里把隐藏屏上的任务移回主屏（单纯 am start 只会在隐藏屏上恢复它）。
     */
    public static void handleHandoverIntent(Intent intent) {
        if (intent == null) return;
        try {
            final String pkg = intent.getStringExtra("eta_shadow_handover");
            if (pkg == null || pkg.isEmpty()) return;
            intent.removeExtra("eta_shadow_handover");
            new Thread(new Runnable() {
                public void run() {
                    try { ShadowTools.handoverToMain(pkg); }
                    catch (Throwable t) { android.util.Log.w(TAG, "handover: " + t); }
                }
            }, "eta-shadow-handover").start();
        } catch (Throwable ignored) {
        }
    }

    /** 工具步骤提示（ShadowTools 在执行 screen_* 前调用）。 */
    public void setStep(String s) {
        step = s == null ? "" : s;
        stepAt = SystemClock.elapsedRealtime();
        lastAppend = stepAt;
        sawStep = true;
    }
}
