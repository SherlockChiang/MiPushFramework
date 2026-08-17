package com.xiaomi.xmsf.push.notification;

import java.util.function.BooleanSupplier;

/**
 * Small process-local cache for HyperOS' focus protocol capability setting.
 * The value changes very rarely, but a bounded lifetime lets SystemUI updates
 * take effect without restarting this process.
 */
final class FocusProtocolSupportCache {
    static final long NO_CACHED_VALUE = Long.MIN_VALUE;

    private final long ttlMillis;
    private volatile long cachedAtElapsedRealtime = NO_CACHED_VALUE;
    private volatile boolean cachedValue;

    FocusProtocolSupportCache(long ttlMillis) {
        if (ttlMillis <= 0L) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
        this.ttlMillis = ttlMillis;
    }

    boolean get(long nowElapsedRealtime, BooleanSupplier resolver) {
        long cachedAt = cachedAtElapsedRealtime;
        if (isFresh(cachedAt, nowElapsedRealtime, ttlMillis)) {
            return cachedValue;
        }
        synchronized (this) {
            cachedAt = cachedAtElapsedRealtime;
            if (isFresh(cachedAt, nowElapsedRealtime, ttlMillis)) {
                return cachedValue;
            }
            boolean resolved = resolver.getAsBoolean();
            cachedValue = resolved;
            cachedAtElapsedRealtime = nowElapsedRealtime;
            return resolved;
        }
    }

    static boolean isFresh(
            long cachedAtElapsedRealtime,
            long nowElapsedRealtime,
            long ttlMillis) {
        if (cachedAtElapsedRealtime == NO_CACHED_VALUE) {
            return false;
        }
        long elapsed = nowElapsedRealtime - cachedAtElapsedRealtime;
        return elapsed >= 0L && elapsed < ttlMillis;
    }
}
