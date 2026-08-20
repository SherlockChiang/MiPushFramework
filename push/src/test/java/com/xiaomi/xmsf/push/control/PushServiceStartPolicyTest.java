package com.xiaomi.xmsf.push.control;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PushServiceStartPolicyTest {

    @Test
    public void masterDisabledAlwaysReturnsSkip() {
        assertAction(PushServiceStartPolicy.Action.SKIP,
                false, true, true, true, true);
        assertAction(PushServiceStartPolicy.Action.SKIP,
                false, false, false, false, true);
    }

    @Test
    public void serviceRunningWithMasterEnabledReturnsStartService() {
        assertAction(PushServiceStartPolicy.Action.START_SERVICE,
                true, true, false, false, false);
        assertAction(PushServiceStartPolicy.Action.START_SERVICE,
                true, true, false, true, true);
    }

    @Test
    public void userInitiatedWithMasterEnabledReturnsStartService() {
        assertAction(PushServiceStartPolicy.Action.START_SERVICE,
                true, false, true, false, false);
        assertAction(PushServiceStartPolicy.Action.START_SERVICE,
                true, false, true, true, true);
    }

    @Test
    public void backgroundStartUsesConfiguredModeWhenPlatformAllowed() {
        assertAction(PushServiceStartPolicy.Action.START_FOREGROUND,
                true, false, false, true, true);
        assertAction(PushServiceStartPolicy.Action.START_SERVICE,
                true, false, false, false, true);
    }

    @Test
    public void backgroundStartReturnsSkipWhenPlatformNotAllowed() {
        assertAction(PushServiceStartPolicy.Action.SKIP,
                true, false, false, true, false);
        assertAction(PushServiceStartPolicy.Action.SKIP,
                true, false, false, false, false);
    }

    private static void assertAction(
            PushServiceStartPolicy.Action expected,
            boolean isMasterEnabled,
            boolean isServiceRunning,
            boolean isUserInitiated,
            boolean isPersistentForegroundEnabled,
            boolean isPlatformAllowed) {
        assertEquals(expected, PushServiceStartPolicy.evaluate(
                isMasterEnabled,
                isServiceRunning,
                isUserInitiated,
                isPersistentForegroundEnabled,
                isPlatformAllowed));
    }
}
