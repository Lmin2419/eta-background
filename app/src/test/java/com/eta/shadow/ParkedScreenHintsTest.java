package com.eta.shadow;

import android.app.Application;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37, application = Application.class)
public class ParkedScreenHintsTest {
    @Test public void expiredLeaseDoesNotContactRoot() {
        ParkedScreenHints hints = new ParkedScreenHints();
        hints.remember("app.target", "expired-run");
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(91));
        hints.onWindow(0, "app.target", id -> fail("Expired lease must not contact Root"));
    }

    @Test public void onlyMatchingMainScreenEventsSendCoalescedHints() throws Exception {
        ParkedScreenHints hints = new ParkedScreenHints();
        AtomicInteger sent = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        java.util.function.Consumer<String> sender = id -> { assertEquals("old-run", id); sent.incrementAndGet(); done.countDown(); };
        hints.remember("app.target", "old-run");
        hints.onWindow(8, "app.target", sender);
        hints.onWindow(0, "app.other", sender);
        assertEquals(0, sent.get());
        for (int i = 0; i < 30; i++) hints.onWindow(0, "app.target", sender);
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(1, sent.get());
        hints.forget("app.target");
        hints.onWindow(0, "app.target", id -> fail("New owner must not trigger an old hint"));
    }
}
