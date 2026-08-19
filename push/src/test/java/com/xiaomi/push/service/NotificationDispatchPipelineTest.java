package com.xiaomi.push.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xiaomi.xmsf.push.utils.PackageConfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationDispatchPipelineTest {

    @Test
    public void configurationFailureFallsBackToNotificationOnly() {
        NotificationDispatchPipeline.DispatchPlan plan =
                NotificationDispatchPipeline.planFromOperations(null);

        assertFalse(plan.wake);
        assertTrue(plan.notify);
        assertFalse(plan.open);
    }

    @Test
    public void ignoreStillSuppressesNotificationButPreservesOptionalStages() {
        Set<String> operations = new HashSet<>();
        operations.add(PackageConfig.OPERATION_WAKE);
        operations.add(PackageConfig.OPERATION_IGNORE);
        operations.add(PackageConfig.OPERATION_OPEN);

        NotificationDispatchPipeline.DispatchPlan plan =
                NotificationDispatchPipeline.planFromOperations(operations);

        assertTrue(plan.wake);
        assertFalse(plan.notify);
        assertTrue(plan.open);
    }

    @Test
    public void wakeFailureDoesNotPreventNotificationOrOpen() {
        NotificationDispatchPipeline.DispatchPlan plan =
                NotificationDispatchPipeline.planFromOperations(withOperations(
                        PackageConfig.OPERATION_WAKE, PackageConfig.OPERATION_OPEN));
        List<String> stages = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        NotificationDispatchPipeline.dispatch(
                plan,
                () -> {
                    stages.add(NotificationDispatchPipeline.STAGE_WAKE);
                    throw new IllegalStateException("wake failed");
                },
                () -> stages.add(NotificationDispatchPipeline.STAGE_NOTIFY),
                () -> stages.add(NotificationDispatchPipeline.STAGE_OPEN),
                (stage, exception) -> failures.add(stage + ":" + exception.getMessage()));

        assertEquals(java.util.Arrays.asList("wake", "notify", "open"), stages);
        assertEquals(java.util.Arrays.asList("wake:wake failed"), failures);
    }

    @Test
    public void notificationFailureDoesNotPreventOpen() {
        NotificationDispatchPipeline.DispatchPlan plan =
                NotificationDispatchPipeline.planFromOperations(withOperations(
                        PackageConfig.OPERATION_OPEN));
        List<String> stages = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        NotificationDispatchPipeline.dispatch(
                plan,
                () -> stages.add(NotificationDispatchPipeline.STAGE_WAKE),
                () -> {
                    stages.add(NotificationDispatchPipeline.STAGE_NOTIFY);
                    throw new IllegalStateException("notify failed");
                },
                () -> stages.add(NotificationDispatchPipeline.STAGE_OPEN),
                (stage, exception) -> failures.add(stage + ":" + exception.getMessage()));

        assertEquals(java.util.Arrays.asList("notify", "open"), stages);
        assertEquals(java.util.Arrays.asList("notify:notify failed"), failures);
    }

    @Test
    public void openFailureIsIsolatedAfterNotificationSubmission() {
        NotificationDispatchPipeline.DispatchPlan plan =
                NotificationDispatchPipeline.planFromOperations(withOperations(
                        PackageConfig.OPERATION_OPEN));
        List<String> stages = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        NotificationDispatchPipeline.dispatch(
                plan,
                () -> stages.add(NotificationDispatchPipeline.STAGE_WAKE),
                () -> stages.add(NotificationDispatchPipeline.STAGE_NOTIFY),
                () -> {
                    stages.add(NotificationDispatchPipeline.STAGE_OPEN);
                    throw new IllegalStateException("open failed");
                },
                (stage, exception) -> failures.add(stage + ":" + exception.getMessage()));

        assertEquals(java.util.Arrays.asList("notify", "open"), stages);
        assertEquals(java.util.Arrays.asList("open:open failed"), failures);
    }

    @Test
    public void failureReporterCannotBreakLaterStages() {
        NotificationDispatchPipeline.DispatchPlan plan =
                NotificationDispatchPipeline.planFromOperations(withOperations(
                        PackageConfig.OPERATION_WAKE, PackageConfig.OPERATION_OPEN));
        List<String> stages = new ArrayList<>();

        NotificationDispatchPipeline.dispatch(
                plan,
                () -> {
                    stages.add(NotificationDispatchPipeline.STAGE_WAKE);
                    throw new IllegalStateException("wake failed");
                },
                () -> stages.add(NotificationDispatchPipeline.STAGE_NOTIFY),
                () -> stages.add(NotificationDispatchPipeline.STAGE_OPEN),
                (stage, exception) -> {
                    throw new IllegalStateException("logger failed");
                });

        assertEquals(java.util.Arrays.asList("wake", "notify", "open"), stages);
    }

    private static Set<String> withOperations(String... values) {
        Set<String> operations = new HashSet<>();
        java.util.Collections.addAll(operations, values);
        return operations;
    }
}
