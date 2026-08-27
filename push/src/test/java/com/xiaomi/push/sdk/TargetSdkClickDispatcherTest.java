package com.xiaomi.push.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xiaomi.push.service.PushConstants;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TargetSdkClickDispatcherTest {
    private static final String PACKAGE = "example.target";

    @Test
    public void enabledExportedServiceWinsBeforeReceiver() {
        FakeSource source = new FakeSource(
                candidate(PACKAGE, "SdkService", true, true),
                Collections.singletonList(candidate(PACKAGE, "Receiver", true, true)));

        TargetSdkClickDispatcher.Capability selected =
                TargetSdkClickDispatcher.selectCapability(PACKAGE, source);

        assertEquals(TargetSdkClickDispatcher.Kind.SERVICE, selected.kind);
        assertEquals("SdkService", selected.candidate.className);
    }

    @Test
    public void receiverIsUsedWhenServiceIsDisabledOrPrivate() {
        for (TargetSdkClickDispatcher.Candidate service : Arrays.asList(
                candidate(PACKAGE, "Disabled", false, true),
                candidate(PACKAGE, "Private", true, false),
                null)) {
            FakeSource source = new FakeSource(service,
                    Collections.singletonList(candidate(PACKAGE, "Receiver", true, true)));

            TargetSdkClickDispatcher.Capability selected =
                    TargetSdkClickDispatcher.selectCapability(PACKAGE, source);

            assertEquals(TargetSdkClickDispatcher.Kind.RECEIVER, selected.kind);
            assertEquals("Receiver", selected.candidate.className);
            assertEquals(PushConstants.MIPUSH_ACTION_NEW_MESSAGE, source.requestedAction);
        }
    }

    @Test
    public void selectorRejectsDisabledPrivateAndCrossPackageReceivers() {
        FakeSource source = new FakeSource(null, Arrays.asList(
                candidate(PACKAGE, "Disabled", false, true),
                candidate(PACKAGE, "Private", true, false),
                candidate("other.package", "Foreign", true, true)));

        assertNull(TargetSdkClickDispatcher.selectCapability(PACKAGE, source));
    }

    @Test
    public void receiverChoiceIsDeterministic() {
        FakeSource source = new FakeSource(null, Arrays.asList(
                candidate(PACKAGE, "z.Last", true, true),
                candidate(PACKAGE, "a.First", true, true)));

        TargetSdkClickDispatcher.Capability selected =
                TargetSdkClickDispatcher.selectCapability(PACKAGE, source);

        assertEquals("a.First", selected.candidate.className);
    }

    @Test
    public void deliveryAcceptanceDoesNotUseNavigationTerminology() {
        assertTrue(TargetSdkClickDispatcher.DispatchResult.SERVICE_DELIVERY_ACCEPTED
                .isAccepted());
        assertTrue(TargetSdkClickDispatcher.DispatchResult.BROADCAST_DELIVERY_ACCEPTED
                .isAccepted());
        assertFalse(TargetSdkClickDispatcher.DispatchResult.UNAVAILABLE.isAccepted());
        assertFalse(TargetSdkClickDispatcher.DispatchResult.FAILED.isAccepted());
    }

    @Test
    public void privateAndReplayRoutesPrimeTargetTask() {
        assertTrue(TargetSdkClickDispatcher.shouldPrimeTargetTask(false, true));
        assertTrue(TargetSdkClickDispatcher.shouldPrimeTargetTask(true, false));
        assertTrue(TargetSdkClickDispatcher.shouldPrimeTargetTask(true, true));
        assertFalse(TargetSdkClickDispatcher.shouldPrimeTargetTask(false, false));
    }

    @Test
    public void oneShotDeliveryIdentitySeparatesPackageNotificationAndCapability() {
        int baseline = TargetSdkClickDispatcher.deliveryRequestCode(
                "example.a", 42, TargetSdkClickDispatcher.Kind.SERVICE);
        assertFalse(baseline == TargetSdkClickDispatcher.deliveryRequestCode(
                "example.b", 42, TargetSdkClickDispatcher.Kind.SERVICE));
        assertFalse(baseline == TargetSdkClickDispatcher.deliveryRequestCode(
                "example.a", 43, TargetSdkClickDispatcher.Kind.SERVICE));
        assertFalse(baseline == TargetSdkClickDispatcher.deliveryRequestCode(
                "example.a", 42, TargetSdkClickDispatcher.Kind.RECEIVER));
    }

    @Test
    public void receiverPermissionUsesTargetPackageContract() {
        assertEquals("example.target.permission.MIPUSH_RECEIVE",
                TargetSdkClickDispatcher.receiverPermission(PACKAGE));
    }

    private static TargetSdkClickDispatcher.Candidate candidate(
            String packageName, String className, boolean enabled, boolean exported) {
        return new TargetSdkClickDispatcher.Candidate(
                packageName, className, enabled, exported);
    }

    private static final class FakeSource
            implements TargetSdkClickDispatcher.CapabilitySource {
        final TargetSdkClickDispatcher.Candidate service;
        final List<TargetSdkClickDispatcher.Candidate> receivers;
        String requestedAction;

        FakeSource(TargetSdkClickDispatcher.Candidate service,
                   List<TargetSdkClickDispatcher.Candidate> receivers) {
            this.service = service;
            this.receivers = receivers;
        }

        @Override
        public TargetSdkClickDispatcher.Candidate service(
                String targetPackage, String serviceClass) {
            return service;
        }

        @Override
        public List<TargetSdkClickDispatcher.Candidate> receivers(
                String targetPackage, String action) {
            requestedAction = action;
            return receivers;
        }
    }
}
