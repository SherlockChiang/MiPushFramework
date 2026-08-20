package com.xiaomi.xmsf.push.service.receivers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeepAliveReceiverTest {
    @Test
    public void permitsFirstScreenWakeImmediately() {
        assertTrue(KeepAliveReceiver.shouldStart(0L, 1L));
    }

    @Test
    public void suppressesRepeatedScreenWakeWithinInterval() {
        assertFalse(KeepAliveReceiver.shouldStart(1_000L,
                1_000L + KeepAliveReceiver.MIN_START_INTERVAL_MS - 1));
    }

    @Test
    public void permitsRecoveryAtIntervalBoundary() {
        assertTrue(KeepAliveReceiver.shouldStart(1_000L,
                1_000L + KeepAliveReceiver.MIN_START_INTERVAL_MS));
    }

    @Test
    public void runningServiceDoesNotRequireAnotherRecoveryStart() {
        assertFalse(KeepAliveReceiver.shouldAttemptRecoveryForServiceState(true));
        assertTrue(KeepAliveReceiver.shouldAttemptRecoveryForServiceState(false));
    }
}
