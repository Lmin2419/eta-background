package com.eta.shadow;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Health of the 后台操作 dependency chain.
 *
 * There is no user-facing switch: the tools carry their own preflight. When the
 * root engine or the accessibility observer is missing, the tool call itself
 * returns an explicit, actionable message so the user always learns that the
 * capability is down instead of seeing a silent failure.
 */
public final class ShadowSettings {
    public static final String NAME = "后台操作";
    /** Row id used by the earlier switch design; cleaned up on boot. */
    private static final String LEGACY_ROW_ID = "eta-shadow-background";

    private static final int ENGINE_PORT = 8733;
    private static final int OBSERVER_PORT = 8738;

    private static volatile Boolean engineReady = null;
    private static volatile long lastProbe = 0L;
    private static volatile Boolean observerReady = null;
    private static volatile long lastObserverProbe = 0L;

    public static Context app() {
        try {
            Object a = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            return a instanceof Context ? (Context) a : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Remove the obsolete switch row if a previous build created one. */
    public static void cleanupLegacyRow(Context ctx) {
        if (ctx == null) return;
        SQLiteDatabase d = null;
        try {
            File f = ctx.getDatabasePath("eta.db");
            if (!f.exists()) return;
            d = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE);
            d.delete("mcp_servers", "id=?", new String[]{LEGACY_ROW_ID});
        } catch (Throwable ignored) {
        } finally {
            if (d != null) try { d.close(); } catch (Throwable ignored) { }
        }
    }

    private static boolean portOpen(int port, int timeoutMs) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", port), timeoutMs);
            s.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Root engine (creates the hidden display, injects input, captures). */
    public static boolean engineReady(Context ctx) {
        long now = SystemClock.elapsedRealtime();
        Boolean cached = engineReady;
        if (cached != null && now - lastProbe < 3000L) return cached;
        lastProbe = now;
        boolean ok = portOpen(ENGINE_PORT, 1200);
        engineReady = ok;
        return ok;
    }

    /**
     * Accessibility observer running inside the Eta process. It lives in this very
     * process, so ask it directly instead of opening a socket to ourselves.
     */
    public static boolean observerReady() {
        if (Boot.serving()) return true;
        long now = SystemClock.elapsedRealtime();
        Boolean cached = observerReady;
        if (cached != null && now - lastObserverProbe < 3000L) return cached;
        lastObserverProbe = now;
        boolean ok = portOpen(OBSERVER_PORT, 800);
        observerReady = ok;
        return ok;
    }

    public static String engineFixHint() {
        return "后台操作当前不可用：root 引擎没有运行（连不上 127.0.0.1:" + ENGINE_PORT + "）。"
                + "引擎已打包在 APK 里，会在授予 root 后自动启动。"
                + "修复方法：确认手机已 root，并在 Magisk/KernelSU 里允许 io.github.mangi.etax 使用 root，然后重试。"
                + "如果手机刚重启过，临时 root 已经失效，需要先重新获取 root 再重试。";
    }

    public static String observerFixHint() {
        return "后台操作不可用：Eta 的无障碍观察服务没有连上（127.0.0.1:" + OBSERVER_PORT + "）。"
                + "修复方法：到系统设置 → 无障碍 → 把 Eta 的服务关掉再打开，然后重试。"
                + "注意每次覆盖安装 Eta 后这个开关都会被系统重置。";
    }

    /** Human readable state, also returned by screen_state. */
    public static String statusLine(Context ctx) {
        boolean e = engineReady(ctx);
        boolean o = observerReady();
        return NAME + "：引擎=" + (e ? "就绪" : "未运行")
                + "，观察者=" + (o ? "就绪" : "未连接")
                + (e && o ? "" : "（能力不完整，工具会给出修复提示）");
    }
}
