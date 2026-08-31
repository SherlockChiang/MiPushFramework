package com.xiaomi.xmsf.push.service.receivers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetworkStatusReceiverTest {
    @Test
    public void offlineBroadcastNeverStartsRecovery() {
        assertFalse(NetworkStatusReceiver.shouldAttemptRecovery(
                false,
                false,
                NetworkStatusReceiver.NO_RECOVERY_ATTEMPT,
                1L));
    }

    @Test
    public void runningServiceNeverStartsRecovery() {
        assertFalse(NetworkStatusReceiver.shouldAttemptRecovery(
                true,
                true,
                NetworkStatusReceiver.NO_RECOVERY_ATTEMPT,
                1L));
    }

    @Test
    public void firstOnlineBroadcastCanRecoverDeadService() {
        assertTrue(NetworkStatusReceiver.shouldAttemptRecovery(
                true,
                false,
                NetworkStatusReceiver.NO_RECOVERY_ATTEMPT,
                1L));
    }

    @Test
    public void repeatedBroadcastInsideIntervalIsSuppressed() {
        assertFalse(NetworkStatusReceiver.shouldAttemptRecovery(
                true,
                false,
                1_000L,
                1_000L + NetworkStatusReceiver.MIN_RECOVERY_INTERVAL_MS - 1L));
    }

    @Test
    public void recoveryIsAllowedAtIntervalBoundary() {
        assertTrue(NetworkStatusReceiver.shouldAttemptRecovery(
                true,
                false,
                1_000L,
                1_000L + NetworkStatusReceiver.MIN_RECOVERY_INTERVAL_MS));
    }

    @Test
    public void elapsedRealtimeRollbackAllowsRecovery() {
        assertTrue(NetworkStatusReceiver.shouldAttemptRecovery(
                true,
                false,
                90_000L,
                100L));
    }

    @Test
    public void registrationRefreshIsSuppressedDuringConnectivityBurst() {
        assertFalse(NetworkStatusReceiver.shouldProcessRegistration(
                true,
                1_000L,
                1_000L + NetworkStatusReceiver.MIN_REGISTRATION_PROCESS_INTERVAL_MS - 1L));
    }

    @Test
    public void registrationRefreshRunsAtIntervalBoundary() {
        assertTrue(NetworkStatusReceiver.shouldProcessRegistration(
                true,
                1_000L,
                1_000L + NetworkStatusReceiver.MIN_REGISTRATION_PROCESS_INTERVAL_MS));
    }

    @Test
    public void registrationRefreshRequiresNetwork() {
        assertFalse(NetworkStatusReceiver.shouldProcessRegistration(false,
                NetworkStatusReceiver.NO_RECOVERY_ATTEMPT,
                1L));
    }
}
