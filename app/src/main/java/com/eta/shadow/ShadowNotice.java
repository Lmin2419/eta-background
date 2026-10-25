package com.eta.shadow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Feeds Eta's foreground notification with short per-sentence lines, keeps a
 * small history for the expanded view, and posts a final "已完成" notification
 * once the run is over (Eta itself cancels its own notification at that point).
 */
public final class ShadowNotice {
    private static final int MAX_LINE = 80;
    private static final int MAX_HISTORY = 6;
    private static final int FLUSH_MIN_GAP = 1200;
    private static final int LONG_RUN = 40;
    private static final int RESET_AFTER = 15000;

    // Eta's own channel is IMPORTANCE_LOW, and Android hides LOW channels from the
    // status bar. Use a dedicated DEFAULT channel (silent) so the completion icon
    // is actually visible up top.
    private static final String CHANNEL = "eta_shadow_done";
    private static final int DONE_ID = 0x4D5A;

    private static final StringBuilder PENDING = new StringBuilder();
    private static final LinkedList<String> HISTORY = new LinkedList<>();

    private static volatile String lastLine = "";
    private static volatile long lastKick = 0L;
    private static volatile long lastDoneAt = 0L;
    private static volatile long lastAppend = 0L;

    private static volatile String step = "";
    private static volatile long stepAt = 0L;

    private static volatile String title = "";

    private static final char[] BREAKS = {'。', '！', '？', '；', '\n', '.', '!', '?', ';'};

    /**
     * Eta cancels its notification when the run ends, and we deliberately do not
     * hook that (it also fires at run start). Instead a tiny watchdog posts the
     * summary once the stream and tool steps have both been quiet for a while.
     */
    static {
        Thread dog = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try { Thread.sleep(3000L); } catch (InterruptedException ignored) { }
                    try {
                        Context ctx = currentApp();
                        long quietFrom = Math.max(lastAppend, stepAt);
                        if (ctx != null
                                && SystemClock.elapsedRealtime() - quietFrom > 10000L
                                && !etaStillRunning(ctx)) {
                            notifyDone();
                        }
                    } catch (Throwable ignored) { }
                }
            }
        });
        dog.setDaemon(true);
        dog.start();
    }

    // ------------------------------------------------------------------ stream

    public static void append(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastAppend > RESET_AFTER) {
            synchronized (ShadowNotice.class) {
                PENDING.setLength(0);
                HISTORY.clear();
            }
            lastLine = "";
        }
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

    /** Reasoning is internal and often in another language: keep it out. */
    public static void appendReasoning(String chunk) {
        // intentionally ignored
    }

    private static void pushLine(String line) {
        if (line.isEmpty()) return;
        // Eta retries failed streams and replays the answer from the start.
        // Drop anything we already showed, and clear the half-finished buffer so
        // the replay cannot be glued onto the interrupted sentence.
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

    /** Keep the tail, but start on a natural boundary instead of mid-word. */
    private static String clip(String s) {
        if (s.length() <= MAX_LINE) return s;
        int start = s.length() - (MAX_LINE - 1);
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\uFF0C' || c == ',' || c == '\u3001' || c == ' ' || c == '\u3002') {
                start = i + 1;
                break;
            }
        }
        if (start >= s.length()) start = s.length() - (MAX_LINE - 1);
        return "\u2026" + s.substring(start);
    }

    // ----------------------------------------------------------------- display

    /** Notification body: current tool step briefly, else the latest sentence. */
    public static String decorate(Object original) {
        long now = SystemClock.elapsedRealtime();
        if (!step.isEmpty() && now - stepAt < 1500L) return step;
        String base = original == null ? "" : original.toString();
        if (now - lastAppend > 5000L) {
            return base;
        }
        String line = lastLine;
        return line.isEmpty() ? base : line;
    }

    /** Give the notification an expanded multi-line view (one line per sentence). */
    public static void applyStyle(Notification.Builder builder) {
        if (builder == null) return;
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

    // -------------------------------------------------------------- completion

    /**
     * Eta cancels its own foreground notification when the run ends, so post a
     * normal one that keeps the last few lines visible.
     */
    public static void notifyDone() {
        long now = SystemClock.elapsedRealtime();
        List<String> lines;
        // Cheap check first: nothing to report, or we just posted one.
        synchronized (ShadowNotice.class) {
            if (HISTORY.isEmpty()) return;
            if (now - lastDoneAt < 30000L) return;
            lines = new ArrayList<>(HISTORY);
        }

        try {
            Context ctx = currentApp();
            if (ctx == null) return;

            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL, "任务完成", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setSound(null, null);
                ch.enableVibration(false);
                ch.setShowBadge(true);
                nm.createNotificationChannel(ch);
            }

            Notification.InboxStyle style = new Notification.InboxStyle();
            for (String line : lines) style.addLine(line);

            Notification.Builder b = new Notification.Builder(ctx, CHANNEL);
            try {
                b.setSmallIcon(ctx.getApplicationInfo().icon);
            } catch (Throwable t) {
                b.setSmallIcon(android.R.drawable.ic_dialog_info);
            }
            b.setContentTitle("Eta 助手");
            b.setContentText(lines.get(lines.size() - 1));
            b.setStyle(style);
            b.setAutoCancel(false);   // stay until the user swipes it away
            b.setOnlyAlertOnce(true);

            try {
                Intent open = new Intent();
                open.setComponent(new ComponentName(
                        ctx.getPackageName(), "io.github.mangi.eta.ui.MainActivity"));
                open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                b.setContentIntent(PendingIntent.getActivity(ctx, 1, open,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } catch (Throwable ignored) {
            }

            nm.notify(DONE_ID, b.build());
        } catch (Throwable ignored) {
        } finally {
            // Always reset, even if posting failed: the watchdog calls this every
            // few seconds and must not spam the same notification.
            synchronized (ShadowNotice.class) {
                HISTORY.clear();
                PENDING.setLength(0);
            }
            lastLine = "";
            step = "";
            lastDoneAt = now;
        }
    }

    // ------------------------------------------------------------------ utils

    private static void kick() {
        try {
            Context ctx = currentApp();
            if (ctx == null) return;
            Class<?> cls = Class.forName("io.github.mangi.eta.agent.runtime.AgentExecutionService");
            Intent i = new Intent();
            i.setComponent(new ComponentName(ctx, cls));
            ctx.startService(i);
        } catch (Throwable ignored) {
        }
    }

    /** True while Eta still shows its own "正在运行" foreground notification. */
    private static boolean etaStillRunning(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return false;
            for (android.service.notification.StatusBarNotification sbn : nm.getActiveNotifications()) {
                if (!ctx.getPackageName().equals(sbn.getPackageName())) continue;
                if (sbn.getId() == DONE_ID) continue;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Context currentApp() {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            return app instanceof Context ? (Context) app : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static synchronized void reset() {
        PENDING.setLength(0);
        HISTORY.clear();
        lastLine = "";
        step = "";
    }

    /**
     * Called from MainActivity.onCreate. The completion notification carries the
     * target package; we hand that session back to the main display here, because
     * a plain am-start only resumes the task on the hidden display.
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
                    catch (Throwable t) { android.util.Log.w("EtaShadow", "handover: " + t); }
                }
            }).start();
        } catch (Throwable ignored) {
        }
    }

    public static void setStep(String s) {
        step = s == null ? "" : s;
        stepAt = SystemClock.elapsedRealtime();
        lastAppend = stepAt;
    }

    public static String stepTail() {
        return step;
    }

    public static void setTitle(String t) {
        title = t == null ? "" : t;
    }

    public static String getTitle() {
        return title;
    }

    public static String tail(int chars) {
        String line = lastLine;
        return line.length() <= chars ? line : line.substring(line.length() - chars);
    }
}
