import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.IntConsumer;

/** Only the resident engine owns this guard; it never terminates an Android service. */
final class EngineShutdown {
    interface Scheduler { void after(long delayMs, Runnable action); }
    private final Scheduler scheduler;
    private final IntConsumer exit;
    private ServerSocket listener;
    private boolean requested;

    EngineShutdown() {
        this((delay, action) -> {
            Thread thread = new Thread(() -> {
                long deadline = System.nanoTime() + delay * 1000000L;
                long remaining;
                // An interrupt must not make a dead engine live forever.
                while ((remaining = deadline - System.nanoTime()) > 0) {
                    try { Thread.sleep(Math.max(1, remaining / 1000000L)); }
                    catch (InterruptedException ignored) { }
                }
                action.run();
            }, "vdrive-exit");
            thread.setDaemon(true);
            thread.start();
        }, code -> Runtime.getRuntime().halt(code));
    }

    EngineShutdown(Scheduler scheduler, IntConsumer exit) {
        this.scheduler = scheduler;
        this.exit = exit;
    }

    synchronized boolean attach(ServerSocket socket) {
        if (requested) { close(socket); return false; }
        listener = socket;
        return true;
    }

    synchronized boolean isRequested() { return requested; }

    void request(int code) {
        ServerSocket socket;
        synchronized (this) {
            if (requested) return;
            requested = true;
            socket = listener;
            listener = null;
        }
        // Arm the deadline before cleanup: a shutdown hook or a dead Binder must
        // not hold the process alive. Existing clients have time to receive the
        // failure/quit reply, but no new client can enter the invalid engine.
        scheduler.after(1500, () -> exit.accept(code));
        close(socket);
    }

    private static void close(ServerSocket socket) {
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }
}
