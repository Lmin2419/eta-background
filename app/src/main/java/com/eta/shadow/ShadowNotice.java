package com.eta.shadow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 功能一：通知栏流式输出。
 *
 * 运行中把回答按句切成短行喂给 Eta 自己的前台通知；运行结束时由
 * AgentRuntimeService 显式调用 {@link #onRunFinished}，按成功 / 出错发一条收尾通知。
 *
 * 与旧版（逆向注入版）的区别：
 *  - 不再有常驻看门狗线程，也不再每 3 秒查询一次通知列表；收尾完全由显式钩子驱动，
 *    所以出错、终端会话仍在跑等情况下也能准确收尾。
 *  - 收尾通知设置了锁屏公开版本：锁屏选「隐藏敏感内容」时只显示「任务完成 / 任务出错」。
 *  - 用户主动停止的任务不再发收尾通知。
 */
public final class ShadowNotice {
    private static final String TAG = "EtaShadow";
    private static final int MAX_LINE = 80;
    private static final int MAX_HISTORY = 6;
    private static final int FLUSH_MIN_GAP = 1200;
    private static final int LONG_RUN = 40;
    private static final long STEP_SHOW_MS = 1500L;
    private static final long STREAM_STALE_MS = 5000L;
    private static final String STOPPED = "已停止";

    // Eta 自己的通道是 IMPORTANCE_LOW，状态栏不显示图标；收尾通知用独立的 DEFAULT 静音通道。
    private static final String CHANNEL = "eta_shadow_done";
    private static final int DONE_ID = 0x4D5A;

    private static final StringBuilder PENDING = new StringBuilder();
    private static final LinkedList<String> HISTORY = new LinkedList<>();

    private static volatile boolean active = false;
    private static volatile boolean sawStep = false;
    private static volatile String lastLine = "";
    private static volatile long lastKick = 0L;
    private static volatile long lastAppend = 0L;
    private static volatile String step = "";
    private static volatile long stepAt = 0L;

    private static final char[] BREAKS = {'。', '！', '？', '；', '\n', '.', '!', '?', ';'};

    private ShadowNotice() {}

    // ------------------------------------------------------------- lifecycle

    /** 每次对话任务开始时调用（AgentRuntimeService 的运行线程）。 */
    public static void onRunStarted() {
        synchronized (ShadowNotice.class) {
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
    public static void onRunFinished(Context ctx, boolean ok, String error) {
        List<String> lines;
        boolean didSomething;
        synchronized (ShadowNotice.class) {
            if (!ok && PENDING.length() > 0) {
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
    public static void append(String chunk) {
        if (!active || chunk == null || chunk.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        lastAppend = now;

        boolean updated = false;
        synchronized (ShadowNotice.class) {
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

        if (updated && now - lastKick >= FLUSH_MIN_GAP) {
            lastKick = now;
            kick();
        }
    }

    /** 调用方需持有 ShadowNotice.class 锁。 */
    private static void pushLine(String line) {
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

    private static int lastBreak(StringBuilder sb) {
        for (int i = sb.length() - 1; i >= 0; i--) {
            char c = sb.charAt(i);
            for (char b : BREAKS) {
                if (c == b) return i;
            }
        }
        return -1;
    }

    private static String clean(String s) {
        return s.replace("**", "")
                .replace("`", "")
                .replace("##", "")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();
    }

    /** 保留句尾，从自然断点开始截，避免截在词中间。 */
    private static String clip(String s) {
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
    public static String decorate(Object original) {
        String base = original == null ? "" : original.toString();
        if (!active) return base;
        long now = SystemClock.elapsedRealtime();
        String s = step;
        if (!s.isEmpty() && now - stepAt < STEP_SHOW_MS) return s;
        if (now - lastAppend > STREAM_STALE_MS) return base;
        String line = lastLine;
        return line.isEmpty() ? base : line;
    }

    /** 前台通知的多行展开视图（每句一行）。 */
    public static void applyStyle(Notification.Builder builder) {
        if (builder == null || !active) return;
        try {
            List<String> lines;
            synchronized (ShadowNotice.class) {
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

    private static void post(Context ctx, String status, List<String> lines) {
        if (ctx == null || lines.isEmpty()) return;
        try {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (!nm.areNotificationsEnabled()) {
                android.util.Log.w(TAG, "notifications disabled, completion notice dropped");
                return;
            }
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL, "任务完成", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setSound(null, null);
                ch.enableVibration(false);
                ch.setShowBadge(true);
                nm.createNotificationChannel(ch);
            }

            String title = "Eta 助手 · " + status;
            int icon = ctx.getApplicationInfo().icon;
            if (icon == 0) icon = android.R.drawable.ic_dialog_info;

            Notification.InboxStyle style = new Notification.InboxStyle();
            for (String line : lines) style.addLine(line);

            // 锁屏「隐藏敏感内容」时显示这个版本：只有状态，没有回答正文
            Notification publicVersion = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(icon)
                    .setContentTitle(title)
                    .setContentText("任务" + status)
                    .build();

            Notification.Builder b = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(icon)
                    .setContentTitle(title)
                    .setContentText(lines.get(lines.size() - 1))
                    .setStyle(style)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion)
                    .setAutoCancel(false)   // 保留到用户手动划掉（与原版一致）
                    .setOnlyAlertOnce(true);

            Intent open = new Intent();
            open.setComponent(new ComponentName(ctx.getPackageName(), "io.github.mangi.eta.ui.MainActivity"));
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            b.setContentIntent(PendingIntent.getActivity(ctx, 1, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

            nm.notify(DONE_ID, b.build());
        } catch (Throwable t) {
            android.util.Log.w(TAG, "completion notice failed: " + t);
        }
    }

    // ----------------------------------------------------------------- utils

    /** 让 Eta 的前台通知立即重绘（上游只在状态文字变化时才刷新）。 */
    private static void kick() {
        try {
            Context ctx = ShadowSettings.app();
            if (ctx == null) return;
            Intent i = new Intent();
            i.setComponent(new ComponentName(ctx.getPackageName(),
                    "io.github.mangi.eta.agent.runtime.AgentExecutionService"));
            ctx.startService(i);
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
    public static void setStep(String s) {
        step = s == null ? "" : s;
        stepAt = SystemClock.elapsedRealtime();
        lastAppend = stepAt;
        sawStep = true;
    }
}
