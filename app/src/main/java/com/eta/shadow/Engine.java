package com.eta.shadow;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自启后台虚拟屏引擎（etax）。
 *
 * 引擎是一个需要 root 的常驻进程（uid 0 的 app_process），监听 127.0.0.1:8733。
 * 它的 dex 和启动脚本随 APK 打包在 assets 里；这个类负责：把 assets 释放到
 * /data/local/tmp/eta/，再用 su 跑 vd.sh 把引擎拉起来。装完 APK、授一次 root，
 * 就不用再手动 `sh vd.sh`。
 *
 * 普通（未 root）手机上 su 会直接失败，这里静默吞掉，只把失败原因记进 lastHint()，
 * 通知栏流式输出那条链路不受影响。
 */
public final class Engine {

    static final int ENGINE_PORT = 8733;

    /** assets 里的路径 → 释放到 /data/local/tmp/eta/ 下的文件名。 */
    private static final String[][] PAYLOAD = {
            {"vdrive.dex", "vdrive.dex"},
            {"vdrive.version", "vdrive.version"},
            {"eta-engine/vd.sh", "vd.sh"},
            {"eta-engine/install_vdrive.sh", "install_vdrive.sh"},
            {"eta-engine/check_pair.sh", "check_pair.sh"},
    };

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static volatile long lastAttemptAt = 0L;
    private static volatile String lastReason = null;

    private Engine() {}

    /**
     * 确保引擎在跑。已在跑直接返回 true；否则释放 assets 并用 su 启动。
     * 可能阻塞最多 ~45s（vd.sh 自身会轮询等待），调用方应在工作线程里用。
     *
     * @return 调用结束时 127.0.0.1:8733 是否连得上。
     */
    public static boolean ensureRunning(Context ctx) {
        if (portOpen(1000)) return true;
        if (ctx == null) { lastReason = "NO_CONTEXT"; return false; }

        // 节流：一次尝试最多 ~45s，失败后 8s 内不重复触发（避免反复弹 root 授权框）。
        long now = System.currentTimeMillis();
        if (now - lastAttemptAt < 8000L && !LOCK.isLocked()) {
            return portOpen(1000);
        }
        if (!LOCK.tryLock()) {
            // 另一个线程正在启动：等它一会，然后看端口。
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            return portOpen(1000);
        }
        try {
            if (portOpen(1000)) return true;          // 拿到锁期间别人已经起好了
            lastAttemptAt = System.currentTimeMillis();
            File stage = stageAssets(ctx);
            if (stage == null) { lastReason = "NOT_BUNDLED"; return false; }
            runSu(buildScript(stage.getAbsolutePath()));
            boolean up = portOpen(1500);
            if (up) lastReason = null;
            return up;
        } finally {
            LOCK.unlock();
        }
    }

    /** 失败时给工具调用返回的可读提示。 */
    public static String reasonHint() {
        String r = lastReason;
        String base = ShadowSettings.engineFixHint();
        if ("NOT_BUNDLED".equals(r)) {
            return "后台虚拟屏引擎没有打进这个 APK（assets 里没有 vdrive.dex）。"
                    + "请把引擎放进 etax/engine/ 再重新编译。";
        }
        if ("NO_ROOT".equals(r)) {
            return "后台虚拟屏需要 root：没能拿到 root 权限。"
                    + "请确认手机已 root，并在 Magisk/KernelSU 里允许 io.github.mangi.etax 使用 root，然后重试。"
                    + "（刚重启过的话临时 root 已失效，需要先重新获取 root。）";
        }
        return base;
    }

    // ------------------------------------------------------------- internals

    private static boolean portOpen(int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", ENGINE_PORT), timeoutMs);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 把 assets 里的引擎释放到应用私有目录（无需 root，总能写）。root 脚本随后从这里
     * 复制到 /data/local/tmp/eta/。只在文件缺失或大小变化时重写，省去无谓 IO。
     *
     * @return 释放目录；assets 里缺少引擎 dex 时返回 null。
     */
    private static File stageAssets(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "eta-engine-stage");
        if (!dir.exists() && !dir.mkdirs()) return null;
        boolean haveDex = false;
        for (String[] p : PAYLOAD) {
            File out = new File(dir, p[1]);
            try (InputStream in = ctx.getAssets().open(p[0])) {
                if (out.exists() && out.length() == available(ctx, p[0])) {
                    if ("vdrive.dex".equals(p[1])) haveDex = true;
                    continue;
                }
                copy(in, out);
                if ("vdrive.dex".equals(p[1])) haveDex = true;
            } catch (Throwable t) {
                // vd.sh/version 缺失是致命的；个别可选文件缺失先忽略。
                android.util.Log.w("EtaShadow", "stage " + p[0] + " failed: " + t);
            }
        }
        return haveDex ? dir : null;
    }

    private static long available(Context ctx, String asset) {
        try (InputStream in = ctx.getAssets().open(asset)) {
            long n = 0; byte[] buf = new byte[8192]; int r;
            while ((r = in.read(buf)) > 0) n += r;
            return n;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void copy(InputStream in, File out) throws Exception {
        try (FileOutputStream fo = new FileOutputStream(out)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            fo.getFD().sync();
        }
    }

    /**
     * root 脚本：dex 变了就重铺并重启（vd.sh 无参=停旧起新），没变就只在没跑时启动
     * （vd.sh status 在跑 exit 0 / 没跑 exit 1）。stage 目录路径无空格，直接内插安全。
     */
    private static String buildScript(String stage) {
        return "set -u\n"
                + "D=/data/local/tmp/eta\n"
                + "SRC=" + stage + "\n"
                + "[ \"$(id -u 2>/dev/null)\" = 0 ] || { echo ETA_NO_ROOT; exit 97; }\n"
                + "mkdir -p \"$D\" || { echo ETA_MKDIR_FAIL; exit 20; }\n"
                + "restage=0\n"
                + "if [ ! -f \"$D/vdrive.dex\" ]; then restage=1; else\n"
                + "  a=$(sha256sum \"$SRC/vdrive.dex\" 2>/dev/null | awk '{print $1}')\n"
                + "  b=$(sha256sum \"$D/vdrive.dex\" 2>/dev/null | awk '{print $1}')\n"
                + "  [ -n \"$a\" ] && [ \"$a\" = \"$b\" ] || restage=1\n"
                + "fi\n"
                + "if [ \"$restage\" = 1 ]; then\n"
                + "  for f in vdrive.dex vdrive.version vd.sh install_vdrive.sh check_pair.sh; do\n"
                + "    [ -f \"$SRC/$f\" ] && cp -f \"$SRC/$f\" \"$D/$f\"\n"
                + "  done\n"
                + "  chmod 700 \"$D\"/*.sh 2>/dev/null\n"
                + "  echo ETA_STAGED\n"
                + "  sh \"$D/vd.sh\"; rc=$?; echo \"ETA_VD_RC=$rc\"; exit $rc\n"
                + "fi\n"
                + "if sh \"$D/vd.sh\" status >/dev/null 2>&1; then echo ETA_ALREADY; exit 0; fi\n"
                + "sh \"$D/vd.sh\"; rc=$?; echo \"ETA_VD_RC=$rc\"; exit $rc\n";
    }

    /** 用 su 开一个 root shell，把脚本喂给它的 stdin 执行。 */
    private static void runSu(String script) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("su");
            pb.redirectErrorStream(true);   // 合并 stderr，免得错误输出撑满管道把 shell 卡住
            p = pb.start();
            try (OutputStream os = p.getOutputStream()) {
                os.write(script.getBytes("UTF-8"));
                os.write("\nexit $?\n".getBytes("UTF-8"));
                os.flush();
            }
            final Process proc = p;
            Thread reader = new Thread(new Runnable() {
                public void run() { drain(proc); }
            });
            reader.setDaemon(true);
            reader.start();
            boolean done = p.waitFor(45, TimeUnit.SECONDS);
            if (!done) {
                lastReason = "TIMEOUT";
                p.destroy();
            }
        } catch (java.io.IOException e) {
            // 没有 su 二进制：这台机器没 root。
            lastReason = "NO_ROOT";
            android.util.Log.w("EtaShadow", "su not available: " + e);
        } catch (Throwable t) {
            android.util.Log.w("EtaShadow", "engine start failed: " + t);
        } finally {
            if (p != null) try { p.destroy(); } catch (Throwable ignored) { }
        }
    }

    private static void drain(Process p) {
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[1024];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0 && sb.length() < 4096) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            String out = sb.toString();
            if (out.contains("ETA_NO_ROOT")) lastReason = "NO_ROOT";
            if (!out.trim().isEmpty()) android.util.Log.i("EtaShadow", "vd.sh: " + out.trim());
        } catch (Throwable ignored) { }
    }

    /** 开机/服务连上时预热一次，让第一条 screen_* 调用不用等启动。 */
    public static void warm(final Context ctx) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try { ensureRunning(ctx); } catch (Throwable ignored) { }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
