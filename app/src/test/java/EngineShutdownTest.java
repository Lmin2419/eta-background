import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class EngineShutdownTest {
    @Test public void stopsAcceptingImmediatelyButAllowsInFlightFailureReply() throws Exception {
        List<Runnable> deadlines = new ArrayList<>();
        AtomicInteger exit = new AtomicInteger(-1);
        EngineShutdown guard = new EngineShutdown((delay, action) -> {
            assertEquals(1500, delay); deadlines.add(action);
        }, exit::set);
        try (ServerSocket listener = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", listener.getLocalPort());
             Socket accepted = listener.accept()) {
            client.setSoTimeout(1000);
            assertTrue(guard.attach(listener));
            guard.request(75);
            assertTrue(listener.isClosed());
            accepted.getOutputStream().write(42);
            assertEquals(42, client.getInputStream().read());
            assertEquals(-1, exit.get());
            deadlines.get(0).run();
            assertEquals(75, exit.get());
        }
    }

    @Test public void deathBeforeListenPreventsNewListenerAndConcurrentDeathsScheduleOnce() throws Exception {
        AtomicInteger schedules = new AtomicInteger();
        EngineShutdown guard = new EngineShutdown((delay, action) -> schedules.incrementAndGet(), code -> {});
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> attempts = new ArrayList<>();
            for (int i = 0; i < 20; i++) attempts.add(pool.submit(() -> guard.request(75)));
            for (Future<?> future : attempts) future.get(1, TimeUnit.SECONDS);
            assertEquals(1, schedules.get());
            try (ServerSocket late = new ServerSocket()) {
                assertFalse(guard.attach(late));
                assertTrue(late.isClosed());
            }
        } finally { pool.shutdownNow(); }
    }
}
