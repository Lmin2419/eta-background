import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.MotionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.json.JSONObject;

/**
 * 息屏时后台屏（eta-bg）的电源处理（e52，2026-11-24 息屏排查报告）。
 *
 * 后台屏带 OWN_DISPLAY_GROUP，有自己的电源状态。手机息屏（Dozing）时它可能跟着睡下：
 * 系统不让应用在上面启动（atms 抛 SecurityException），已有窗口没有画面（package ""、无 surface）。
 * 这里在给后台屏发命令前看它是否醒着；没醒先只针对后台屏试几种唤醒（不碰主屏），
 * 都不行、并且 App 允许（外观设置「息屏时后台屏醒不过来」）时才点亮主屏，并在任务期间保持亮着。
 * 每一步都写进 vdrive.log：哪种方式起作用、主屏有没有被连带点亮，真机上一看便知。
 */
final class BgPower {
    /** 往某块屏注入一个触摸动作（引擎已有的注入通道）。 */
    interface Toucher { void touch(int displayId, long downTime, int action, float x, float y) throws Exception; }

    private final Context ctx;
    private final Toucher toucher;
    private final java.util.function.Consumer<String> logger;
    private PowerManager.WakeLock mainLock;
    private long mainLockUntil;
    private long lastKeepAlive;
    private boolean methodsLogged;
    /** 上一次唤醒用到的方式（诊断用）。 */
    String lastMethod = "";

    BgPower(Context ctx, Toucher toucher, java.util.function.Consumer<String> logger) {
        this.ctx = ctx;
        this.toucher = toucher;
        this.logger = logger;
    }

    private PowerManager pm() { return ctx.getSystemService(PowerManager.class); }
    private DisplayManager dm() { return ctx.getSystemService(DisplayManager.class); }

    static String stateName(int state) {
        switch (state) {
            case Display.STATE_ON: return "ON";
            case Display.STATE_OFF: return "OFF";
            case Display.STATE_DOZE: return "DOZE";
            case Display.STATE_DOZE_SUSPEND: return "DOZE_SUSPEND";
            case Display.STATE_ON_SUSPEND: return "ON_SUSPEND";
            case Display.STATE_VR: return "VR";
            case -1: return "MISSING";
            default: return "UNKNOWN(" + state + ")";
        }
    }

    int state(int displayId) {
        try {
            Display display = dm().getDisplay(displayId);
            return display == null ? -1 : display.getState();
        } catch (Throwable error) { return -1; }
    }

    /** 这块屏所在的电源组是否醒着（隐藏接口 PowerManager.isInteractive(int)；没有这个接口时为 null）。 */
    Boolean displayInteractive(int displayId) {
        try {
            Method m = PowerManager.class.getMethod("isInteractive", int.class);
            Object value = m.invoke(pm(), displayId);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Throwable error) { return null; }
    }

    boolean deviceInteractive() {
        try { return pm().isInteractive(); } catch (Throwable error) { return true; }
    }

    String snapshot(int displayId) {
        return "main=" + stateName(state(Display.DEFAULT_DISPLAY)) + " bg=" + stateName(state(displayId))
                + " interactive=" + deviceInteractive() + " bgInteractive=" + displayInteractive(displayId);
    }

    /** 后台屏醒着：显示状态 ON，且（能查到时）它的电源组醒着。 */
    boolean awake(int displayId) {
        return state(displayId) == Display.STATE_ON && !Boolean.FALSE.equals(displayInteractive(displayId));
    }

    /**
     * 确保后台屏醒着。[force] 为真时不看当前状态直接试（启动被拒、窗口没画面时用）。
     * 返回 null 表示醒着（或已唤醒）；否则返回诊断 JSON（code BG_SCREEN_ASLEEP）。
     */
    JSONObject ensureAwake(VirtualDisplay vd, int displayId, boolean allowMainWake, String why, boolean force) {
        if (displayId <= 0) return null;
        if (!force && awake(displayId)) {
            keepAlive(displayId);
            renewMainLock();
            return null;
        }
        String before = snapshot(displayId);
        log("bg screen " + displayId + " asleep (" + why + "): " + before);
        logPowerApisOnce();
        int mainBefore = state(Display.DEFAULT_DISPLAY);
        // 1. 后台屏自己的「开显示」（隐藏接口 VirtualDisplay.setDisplayState，主屏不受影响）。
        if (vd != null && attempt("setDisplayState", () -> {
            Method m = vd.getClass().getMethod("setDisplayState", boolean.class);
            m.setAccessible(true);
            m.invoke(vd, true);
        }) && waitAwake(displayId, 800)) return woke("setDisplayState", displayId, mainBefore);
        // 2. 按屏幕编号开显示（部分系统有 DisplayManager.requestDisplayPower）。
        if (attempt("requestDisplayPower", () -> requestDisplayPower(displayId)) && waitAwake(displayId, 800))
            return woke("requestDisplayPower", displayId, mainBefore);
        // 3. 在后台屏上按下再取消一次（不会点到东西）：支持「按屏唤醒」的系统会只唤醒这块屏的电源组。
        if (toucher != null && attempt("touchWake", () -> {
            long down = SystemClock.uptimeMillis();
            toucher.touch(displayId, down, MotionEvent.ACTION_DOWN, 1, 1);
            toucher.touch(displayId, down, MotionEvent.ACTION_CANCEL, 1, 1);
        }) && waitAwake(displayId, 800)) return woke("touchWake", displayId, mainBefore);
        // 4. 兜底：点亮主屏（只有 App 允许时），任务期间保持亮着，结束后交还系统按时熄屏。
        if (allowMainWake) {
            if (attempt("wakeMain", this::wakeMain)) {
                holdMainLock();
                // 主屏醒了以后，后台屏的电源组可能要再推一下。
                if (waitAwake(displayId, 1500)) return woke("wakeMain", displayId, mainBefore);
                if (toucher != null && attempt("touchWakeAfterMain", () -> {
                    long down = SystemClock.uptimeMillis();
                    toucher.touch(displayId, down, MotionEvent.ACTION_DOWN, 1, 1);
                    toucher.touch(displayId, down, MotionEvent.ACTION_CANCEL, 1, 1);
                }) && waitAwake(displayId, 1000)) return woke("wakeMain+touch", displayId, mainBefore);
            }
        }
        String after = snapshot(displayId);
        log("bg screen " + displayId + " still asleep after wake attempts (allowMainWake=" + allowMainWake + "): " + after);
        try {
            return new JSONObject().put("ok", false).put("code", "BG_SCREEN_ASLEEP")
                    .put("before", before).put("after", after).put("allowMainWake", allowMainWake)
                    .put("message", allowMainWake
                            ? "手机息屏，后台屏唤醒失败（点亮主屏后也没醒），见 vdrive.log"
                            : "手机息屏，后台屏没能唤醒；可以点亮屏幕后重试，或在设置里允许这类任务息屏时点亮屏幕");
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private JSONObject woke(String method, int displayId, int mainBefore) {
        lastMethod = method;
        int mainAfter = state(Display.DEFAULT_DISPLAY);
        boolean mainLit = mainBefore != Display.STATE_ON && mainAfter == Display.STATE_ON;
        log("bg screen " + displayId + " awake via " + method + (mainLit ? " (main screen lit)" : " (main screen untouched)")
                + ": " + snapshot(displayId));
        keepAlive(displayId);
        return null;
    }

    private interface Step { void run() throws Exception; }

    private boolean attempt(String name, Step step) {
        try {
            step.run();
            return true;
        } catch (Throwable error) {
            Throwable cause = error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null ? error.getCause() : error;
            log("bg wake " + name + " unavailable: " + cause.getClass().getSimpleName()
                    + (cause.getMessage() == null ? "" : " " + VDrive.plain(cause.getMessage())));
            return false;
        }
    }

    private boolean waitAwake(int displayId, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            if (awake(displayId)) return true;
            SystemClock.sleep(100);
        }
        return awake(displayId);
    }

    private void requestDisplayPower(int displayId) throws Exception {
        Object target = dm();
        Method found = null;
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals("requestDisplayPower") && m.getParameterTypes().length == 2
                    && m.getParameterTypes()[0] == int.class) { found = m; break; }
        }
        if (found == null) throw new NoSuchMethodException("DisplayManager.requestDisplayPower");
        Class<?> second = found.getParameterTypes()[1];
        found.setAccessible(true);
        if (second == boolean.class) found.invoke(target, displayId, true);
        else if (second == int.class) found.invoke(target, displayId, Display.STATE_ON);
        else throw new NoSuchMethodException("requestDisplayPower(" + second + ")");
    }

    private void wakeMain() throws Exception {
        PowerManager pm = pm();
        try {
            Method m = PowerManager.class.getMethod("wakeUp", long.class, int.class, String.class);
            m.invoke(pm, SystemClock.uptimeMillis(), 2 /* WAKE_REASON_APPLICATION */, "eta:bg-screen");
        } catch (NoSuchMethodException old) {
            Method m = PowerManager.class.getMethod("wakeUp", long.class);
            m.invoke(pm, SystemClock.uptimeMillis());
        }
    }

    /** 点亮主屏后在任务期间保持亮着（暗亮度），命令不断时续期；结束或 5 分钟没有命令就放开。 */
    @SuppressWarnings("deprecation")
    private void holdMainLock() {
        try {
            if (mainLock == null) {
                mainLock = pm().newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "eta:bg-screen");
                mainLock.setReferenceCounted(false);
            }
            mainLock.acquire(MAIN_LOCK_MS);
            mainLockUntil = SystemClock.uptimeMillis() + MAIN_LOCK_MS;
        } catch (Throwable error) {
            log("bg wake main lock unavailable: " + error.getClass().getSimpleName());
        }
    }

    private void renewMainLock() {
        if (mainLock != null && mainLock.isHeld() && SystemClock.uptimeMillis() < mainLockUntil) holdMainLock();
    }

    /** 任务结束：放开为后台屏点亮主屏的锁，屏幕按系统设置自己熄灭。 */
    void releaseMain() {
        try {
            if (mainLock != null && mainLock.isHeld()) {
                mainLock.release();
                log("bg screen main-screen hold released");
            }
        } catch (Throwable ignored) { }
        mainLockUntil = 0;
    }

    /** 任务期间给后台屏的电源组记一次「有操作」，防止它中途按超时睡下（最多 5 秒一次）。 */
    void keepAlive(int displayId) {
        long now = SystemClock.uptimeMillis();
        if (now - lastKeepAlive < 5000) return;
        lastKeepAlive = now;
        try {
            Field field = PowerManager.class.getDeclaredField("mService");
            field.setAccessible(true);
            Object service = field.get(pm());
            for (Method m : service.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("userActivity") && p.length == 4 && p[0] == int.class && p[1] == long.class) {
                    m.invoke(service, displayId, now, 0 /* USER_ACTIVITY_EVENT_OTHER */, 0);
                    return;
                }
            }
        } catch (Throwable ignored) { }
    }

    /** 第一次需要唤醒时，把系统里和电源 / 显示开关有关的隐藏接口名字记进日志，方便下一轮对症。 */
    private void logPowerApisOnce() {
        if (methodsLogged) return;
        methodsLogged = true;
        try {
            Field field = PowerManager.class.getDeclaredField("mService");
            field.setAccessible(true);
            Object service = field.get(pm());
            StringBuilder names = new StringBuilder();
            for (Method m : service.getClass().getMethods()) {
                String n = m.getName();
                if (n.contains("wake") || n.contains("Wake") || n.contains("Sleep") || n.contains("userActivity") || n.contains("Interactive")) {
                    names.append(n).append('(').append(m.getParameterTypes().length).append(") ");
                }
            }
            log("power apis: " + names.toString().trim());
        } catch (Throwable error) { log("power apis: unavailable " + error.getClass().getSimpleName()); }
        try {
            StringBuilder names = new StringBuilder();
            for (Method m : dm().getClass().getMethods()) {
                String n = m.getName();
                if (n.contains("Power") || n.contains("DisplayState")) names.append(n).append(' ');
            }
            for (Method m : VirtualDisplay.class.getMethods()) {
                if (m.getName().contains("State")) names.append("VirtualDisplay.").append(m.getName()).append(' ');
            }
            log("display power apis: " + names.toString().trim());
        } catch (Throwable error) { log("display power apis: unavailable " + error.getClass().getSimpleName()); }
    }

    private void log(String message) { logger.accept(message); }

    static final long MAIN_LOCK_MS = 5 * 60 * 1000L;
}
