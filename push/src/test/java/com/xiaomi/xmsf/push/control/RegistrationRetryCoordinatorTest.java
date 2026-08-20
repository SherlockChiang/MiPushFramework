package com.xiaomi.xmsf.push.control;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RegistrationRetryCoordinatorTest {
    @Test
    public void keepsOnlyOnePendingRetry() {
        FakeScheduler scheduler = new FakeScheduler();
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(scheduler);
        coordinator.enable();

        assertTrue(coordinator.schedule(1_000L, generation -> () -> { }));
        assertFalse(coordinator.schedule(2_000L, generation -> () -> { }));

        assertEquals(1, scheduler.posted.size());
        assertEquals(1_000L, scheduler.delays.get(0).longValue());
    }

    @Test
    public void acceptsRetryBeforeExplicitEnableForDefaultEnabledStartup() {
        FakeScheduler scheduler = new FakeScheduler();
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(scheduler);

        assertTrue(coordinator.schedule(1_000L, generation -> () -> { }));
    }

    @Test
    public void disableAndEnableUpdateVisibleState() {
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(
                new FakeScheduler());

        assertTrue(coordinator.isEnabled());
        coordinator.disable();
        assertFalse(coordinator.isEnabled());
        coordinator.enable();
        assertTrue(coordinator.isEnabled());
    }

    @Test
    public void disableCancelsPendingRetry() {
        FakeScheduler scheduler = new FakeScheduler();
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(scheduler);
        coordinator.enable();
        coordinator.schedule(1_000L, generation -> () -> { });
        Runnable pending = scheduler.posted.get(0);

        coordinator.disable();

        assertSame(pending, scheduler.removed);
        assertFalse(coordinator.begin(pending, scheduler.generation));
    }

    @Test
    public void staleRunningRetryCannotJoinLaterEnableGeneration() {
        FakeScheduler scheduler = new FakeScheduler();
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(scheduler);
        coordinator.enable();
        coordinator.schedule(1_000L, generation -> {
            scheduler.generation = generation;
            return () -> { };
        });
        Runnable running = scheduler.posted.get(0);
        long oldGeneration = scheduler.generation;
        assertTrue(coordinator.begin(running, oldGeneration));

        coordinator.disable();
        coordinator.enable();

        assertFalse(coordinator.runIfActive(oldGeneration, () -> { }));
        assertFalse(coordinator.schedule(2_000L, oldGeneration, generation -> () -> { }));
    }

    @Test
    public void failedPostDoesNotPermanentlyBlockRetries() {
        FakeScheduler scheduler = new FakeScheduler();
        scheduler.acceptPosts = false;
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(scheduler);
        coordinator.enable();

        assertFalse(coordinator.schedule(1_000L, generation -> () -> { }));
        assertSame(scheduler.posted.get(0), scheduler.removed);
        scheduler.acceptPosts = true;
        assertTrue(coordinator.schedule(2_000L, generation -> () -> { }));
    }

    @Test
    public void nullTaskDoesNotBlockLaterRetry() {
        FakeScheduler scheduler = new FakeScheduler();
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(scheduler);

        assertFalse(coordinator.schedule(1_000L, generation -> null));
        assertTrue(coordinator.schedule(2_000L, generation -> () -> { }));
    }

    @Test
    public void disabledCoordinatorRejectsInitialAndRetrySideEffects() {
        FakeScheduler scheduler = new FakeScheduler();
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(scheduler);
        coordinator.schedule(1_000L, generation -> {
            scheduler.generation = generation;
            return () -> { };
        });
        long generation = scheduler.generation;
        coordinator.disable();
        int[] calls = {0};

        assertFalse(coordinator.runIfActive(generation, () -> calls[0]++));
        assertFalse(coordinator.runIfEnabled(() -> calls[0]++));
        assertEquals(0, calls[0]);
    }

    @Test
    public void activeGenerationRunsRegistrationSideEffectOnce() {
        FakeScheduler scheduler = new FakeScheduler();
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(scheduler);
        coordinator.schedule(1_000L, generation -> {
            scheduler.generation = generation;
            return () -> { };
        });
        int[] calls = {0};

        assertTrue(coordinator.runIfActive(scheduler.generation, () -> calls[0]++));
        assertEquals(1, calls[0]);
    }

    @Test
    public void successCanCancelAnOlderPendingRetry() {
        FakeScheduler scheduler = new FakeScheduler();
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(scheduler);
        coordinator.schedule(1_000L, generation -> () -> { });
        Runnable pending = scheduler.posted.get(0);

        coordinator.cancelPending();

        assertSame(pending, scheduler.removed);
        assertFalse(coordinator.begin(pending, 1L));
    }

    @Test
    public void disableWaitsForRunningRegistrationThenWinsOrdering() throws Exception {
        RegistrationRetryCoordinator coordinator = new RegistrationRetryCoordinator(
                new FakeScheduler());
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        List<String> order = new ArrayList<>();
        Thread registration = new Thread(() -> coordinator.runIfActive(1L, () -> {
            actionStarted.countDown();
            try {
                releaseAction.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            order.add("register");
        }));
        Thread disable = new Thread(() -> {
            coordinator.disable();
            order.add("disable");
        });

        registration.start();
        assertTrue(actionStarted.await(1, TimeUnit.SECONDS));
        disable.start();
        releaseAction.countDown();
        registration.join(1_000L);
        disable.join(1_000L);

        assertEquals(java.util.Arrays.asList("register", "disable"), order);
        assertFalse(coordinator.isEnabled());
    }

    private static final class FakeScheduler implements RegistrationRetryCoordinator.Scheduler {
        final List<Runnable> posted = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        Runnable removed;
        long generation;
        boolean acceptPosts = true;

        @Override
        public boolean postDelayed(Runnable task, long delayMs) {
            posted.add(task);
            delays.add(delayMs);
            return acceptPosts;
        }

        @Override
        public void removeCallbacks(Runnable task) {
            removed = task;
        }
    }
}
