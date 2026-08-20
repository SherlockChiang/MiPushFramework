package com.xiaomi.push.service;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Small, process-local gate for explicit screen-wake requests.
 *
 * <p>The gate is intentionally independent of Android APIs so that its
 * timing and eviction behaviour can be tested on the JVM.  Callers should
 * only consult it immediately before acquiring a wake lock; it does not
 * gate notification publication or any other dispatch stage.</p>
 */
final class WakeScreenThrottle {
    static final long DEFAULT_MIN_INTERVAL_MILLIS = 5_000L;
    static final int DEFAULT_MAX_ENTRIES = 128;

    private final long minimumIntervalMillis;
    private final int maxEntries;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Long> lastWakeByPackage =
            new LinkedHashMap<>(16, 0.75f, true);

    WakeScreenThrottle(LongSupplier clock) {
        this(clock, DEFAULT_MIN_INTERVAL_MILLIS, DEFAULT_MAX_ENTRIES);
    }

    WakeScreenThrottle(LongSupplier clock, long minimumIntervalMillis, int maxEntries) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (minimumIntervalMillis < 0L) {
            throw new IllegalArgumentException("minimumIntervalMillis must be non-negative");
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.clock = clock;
        this.minimumIntervalMillis = minimumIntervalMillis;
        this.maxEntries = maxEntries;
    }

    /**
     * Claims permission for a wake request from {@code packageName}.
     *
     * <p>The first request for a package is accepted.  A clock rollback is
     * treated as a new elapsed-time epoch and is accepted as well, preventing
     * a wall/elapsed clock anomaly from suppressing wakes indefinitely.</p>
     */
    synchronized boolean tryAcquire(String packageName) {
        return tryAcquireAtLocked(packageName, clock.getAsLong());
    }

    /** Package-private deterministic entry point used by JVM tests. */
    synchronized boolean tryAcquireAt(String packageName, long nowElapsedRealtime) {
        return tryAcquireAtLocked(packageName, nowElapsedRealtime);
    }

    /** Visible to package tests for verifying the bounded-cache contract. */
    synchronized int entryCountForTest() {
        return lastWakeByPackage.size();
    }

    private boolean tryAcquireAtLocked(String packageName, long nowElapsedRealtime) {
        Long previous = lastWakeByPackage.get(packageName);
        if (previous != null && nowElapsedRealtime >= previous) {
            long elapsed = nowElapsedRealtime - previous;
            if (elapsed >= 0L && elapsed < minimumIntervalMillis) {
                return false;
            }
        }

        // A backward jump (now < previous), or an elapsed counter overflow,
        // deliberately reaches this branch and starts a new clock epoch.
        lastWakeByPackage.put(packageName, nowElapsedRealtime);
        trimToBound();
        return true;
    }

    private void trimToBound() {
        if (lastWakeByPackage.size() <= maxEntries) {
            return;
        }
        Iterator<Map.Entry<String, Long>> iterator = lastWakeByPackage.entrySet().iterator();
        iterator.next();
        iterator.remove();
    }
}
