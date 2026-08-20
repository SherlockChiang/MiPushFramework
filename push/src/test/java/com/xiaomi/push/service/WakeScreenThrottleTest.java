package com.xiaomi.push.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public class WakeScreenThrottleTest {

    @Test
    public void firstRequestIsAllowedAndBurstIsSuppressed() {
        AtomicLong now = new AtomicLong(10_000L);
        WakeScreenThrottle throttle = new WakeScreenThrottle(now::get);

        assertTrue(throttle.tryAcquire("com.example.chat"));
        now.addAndGet(WakeScreenThrottle.DEFAULT_MIN_INTERVAL_MILLIS - 1L);
        assertFalse(throttle.tryAcquire("com.example.chat"));
        now.incrementAndGet();
        assertTrue(throttle.tryAcquire("com.example.chat"));
    }

    @Test
    public void packagesHaveIndependentIntervals() {
        AtomicLong now = new AtomicLong(1_000L);
        WakeScreenThrottle throttle = new WakeScreenThrottle(now::get);

        assertTrue(throttle.tryAcquire("com.example.one"));
        assertTrue(throttle.tryAcquire("com.example.two"));
        now.addAndGet(1_000L);
        assertFalse(throttle.tryAcquire("com.example.one"));
        assertFalse(throttle.tryAcquire("com.example.two"));
    }

    @Test
    public void suppressedWakeDoesNotSuppressNotificationDispatch() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger wakeLocks = new AtomicInteger();
        AtomicInteger notifications = new AtomicInteger();
        WakeScreenThrottle throttle = new WakeScreenThrottle(now::get);
        NotificationDispatchPipeline.DispatchPlan plan =
                new NotificationDispatchPipeline.DispatchPlan(true, true, false);

        for (int attempt = 0; attempt < 2; attempt++) {
            NotificationDispatchPipeline.dispatch(
                    plan,
                    () -> {
                        if (throttle.tryAcquire("com.example.chat")) {
                            wakeLocks.incrementAndGet();
                        }
                    },
                    notifications::incrementAndGet,
                    null,
                    null);
        }

        assertEquals(1, wakeLocks.get());
        assertEquals(2, notifications.get());
    }

    @Test
    public void clockRollbackStartsANewEpoch() {
        AtomicLong now = new AtomicLong(90_000L);
        WakeScreenThrottle throttle = new WakeScreenThrottle(now::get);

        assertTrue(throttle.tryAcquire("com.example.chat"));
        now.set(100L);
        assertTrue(throttle.tryAcquire("com.example.chat"));
        now.set(100L + WakeScreenThrottle.DEFAULT_MIN_INTERVAL_MILLIS - 1L);
        assertFalse(throttle.tryAcquire("com.example.chat"));
    }

    @Test
    public void cacheRemainsBoundedAndEvictsLeastRecentlyUsedPackage() {
        AtomicLong now = new AtomicLong(1L);
        WakeScreenThrottle throttle = new WakeScreenThrottle(
                now::get, WakeScreenThrottle.DEFAULT_MIN_INTERVAL_MILLIS, 2);

        assertTrue(throttle.tryAcquire("com.example.one"));
        now.incrementAndGet();
        assertTrue(throttle.tryAcquire("com.example.two"));
        assertEquals(2, throttle.entryCountForTest());

        // Touch one so the second package is the eldest entry.
        now.addAndGet(WakeScreenThrottle.DEFAULT_MIN_INTERVAL_MILLIS);
        assertTrue(throttle.tryAcquire("com.example.one"));
        now.incrementAndGet();
        assertTrue(throttle.tryAcquire("com.example.three"));
        assertEquals(2, throttle.entryCountForTest());

        // The evicted package is treated as a first request again.
        assertTrue(throttle.tryAcquire("com.example.two"));
        assertEquals(2, throttle.entryCountForTest());
    }
}
