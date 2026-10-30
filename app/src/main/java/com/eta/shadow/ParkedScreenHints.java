package com.eta.shadow;

import android.os.SystemClock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Bounded window hints for active and completed screens; only Root proves task movement. */
final class ParkedScreenHints {
    private static final class Lease {
        final String sessionId;
        final long expires;
        long lastHint = -1000;
        Lease(String id, long now, boolean active) { sessionId = id; expires = active ? Long.MAX_VALUE : now + 90000; }
    }
    private final ConcurrentHashMap<String, Lease> leases = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "eta-display-handoff");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    void remember(String pkg, String sessionId) {
        remember(pkg, sessionId, false);
    }

    void rememberActive(String pkg, String sessionId) { remember(pkg, sessionId, true); }

    private void remember(String pkg, String sessionId, boolean active) {
        if (pkg == null || pkg.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        leases.entrySet().removeIf(entry -> entry.getValue().expires < now);
        if (leases.size() >= 8 && !leases.containsKey(pkg)) {
            leases.entrySet().stream().min(java.util.Comparator.comparingLong(entry -> entry.getValue().expires))
                    .ifPresent(old -> leases.remove(old.getKey(), old.getValue()));
        }
        leases.put(pkg, new Lease(sessionId, now, active));
    }

    void forget(String pkg) { if (pkg != null) leases.remove(pkg); }
    void forget(String pkg, String sessionId) {
        if (pkg != null) leases.computeIfPresent(pkg, (key, lease) -> lease.sessionId.equals(sessionId) ? null : lease);
    }

    void onWindow(int displayId, String pkg, Consumer<String> sender) {
        if (displayId != 0 || pkg == null) return;
        Lease lease = leases.get(pkg);
        if (lease == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now > lease.expires) { leases.remove(pkg, lease); return; }
        synchronized (lease) {
            if (now - lease.lastHint < 300) return;
            lease.lastHint = now;
        }
        executor.execute(() -> {
            if (leases.get(pkg) == lease && SystemClock.elapsedRealtime() <= lease.expires) sender.accept(lease.sessionId);
        });
    }
}
