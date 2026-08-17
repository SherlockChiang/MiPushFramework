package com.xiaomi.xmsf.push.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class FocusProtocolSupportCacheTest {
    private static final long TTL_MS = 300_000L;

    @Test
    public void cachesResolvedCapabilityInsideTtl() {
        FocusProtocolSupportCache cache = new FocusProtocolSupportCache(TTL_MS);
        AtomicInteger resolutions = new AtomicInteger();

        assertTrue(cache.get(1_000L, () -> {
            resolutions.incrementAndGet();
            return true;
        }));
        assertTrue(cache.get(1_000L + TTL_MS - 1L, () -> {
            resolutions.incrementAndGet();
            return false;
        }));
        assertEquals(1, resolutions.get());
    }

    @Test
    public void refreshesAtBoundaryAndAfterElapsedRealtimeRollback() {
        FocusProtocolSupportCache cache = new FocusProtocolSupportCache(TTL_MS);

        assertFalse(cache.get(10_000L, () -> false));
        assertTrue(cache.get(10_000L + TTL_MS, () -> true));
        assertFalse(cache.get(100L, () -> false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveTtl() {
        new FocusProtocolSupportCache(0L);
    }

    @Test
    public void sentinelIsNeverFresh() {
        assertFalse(FocusProtocolSupportCache.isFresh(
                FocusProtocolSupportCache.NO_CACHED_VALUE,
                Long.MAX_VALUE,
                TTL_MS));
    }
}
