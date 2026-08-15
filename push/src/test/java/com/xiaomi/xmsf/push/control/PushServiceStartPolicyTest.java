package com.xiaomi.xmsf.push.control;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PushServiceStartPolicyTest {

    @Test
    public void masterDisabledReturnsSkip() {
        PushServiceStartPolicy.Action action = PushServiceStartPolicy.evaluate(
                false, true, true, true, true);
        assertEquals(PushServiceStartPolicy.Action.SKIP, action);
    }

    @Test
    public void serviceRunningReturnsStartService() {
        PushServiceStartPolicy.Action action = PushServiceStartPolicy.evaluate(
                true, true, false, false, false);
        assertEquals(PushServiceStartPolicy.Action.START_SERVICE, action);
    }

    @Test
    public void userInitiatedAndServiceDeadReturnsStartService() {
        PushServiceStartPolicy.Action action = PushServiceStartPolicy.evaluate(
                true, false, true, false, false);
        assertEquals(PushServiceStartPolicy.Action.START_SERVICE, action);
    }

    @Test
    public void backgroundWithPersistentForegroundAndPlatformAllowedReturnsStartForeground() {
        PushServiceStartPolicy.Action action = PushServiceStartPolicy.evaluate(
                true, false, false, true, true);
        assertEquals(PushServiceStartPolicy.Action.START_FOREGROUND, action);
    }

    @Test
    public void backgroundNotAllowedReturnsSkip() {
        PushServiceStartPolicy.Action action = PushServiceStartPolicy.evaluate(
                true, false, false, true, false);
        assertEquals(PushServiceStartPolicy.Action.SKIP, action);

        PushServiceStartPolicy.Action actionNoForeground = PushServiceStartPolicy.evaluate(
                true, false, false, false, true);
        assertEquals(PushServiceStartPolicy.Action.SKIP, actionNoForeground);
    }
}
