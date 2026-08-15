package com.xiaomi.xmsf.push.control;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StartupWorkPolicyTest {
    @Test
    public void qaAndDisabledInstallsNeverRunAutomaticStartupWork() {
        assertFalse(StartupWorkPolicy.shouldRunAppStartup(true, true, true));
        assertFalse(StartupWorkPolicy.shouldRunAppStartup(false, true, false));
        assertFalse(StartupWorkPolicy.shouldRunAppStartup(false, false, true));
        assertTrue(StartupWorkPolicy.shouldRunAppStartup(false, true, true));
    }

    @Test
    public void elapsedThrottleHandlesFirstRunBoundaryAndReboot() {
        assertTrue(StartupWorkPolicy.shouldRunThrottled(0L, 1L, 300_000L));
        assertFalse(StartupWorkPolicy.shouldRunThrottled(1_000L, 300_999L, 300_000L));
        assertTrue(StartupWorkPolicy.shouldRunThrottled(1_000L, 301_000L, 300_000L));
        assertTrue(StartupWorkPolicy.shouldRunThrottled(900_000L, 100L, 300_000L));
    }
}
