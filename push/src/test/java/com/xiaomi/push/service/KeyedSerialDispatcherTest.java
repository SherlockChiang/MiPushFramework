package com.xiaomi.push.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/** JVM coverage for ordering, parallelism, failure isolation and cleanup. */
public class KeyedSerialDispatcherTest {

    @Test
    public void sameKeyRunsInSubmissionOrder() throws Exception {
        ExecutorService backend = Executors.newFixedThreadPool(2);
        try {
            KeyedSerialDispatcher<String> dispatcher =
                    new KeyedSerialDispatcher<>(backend, 4, null);
            List<Integer> events = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondFinished = new CountDownLatch(1);

            dispatcher.execute("same", () -> {
                events.add(1);
                firstStarted.countDown();
                await(releaseFirst);
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            dispatcher.execute("same", () -> {
                events.add(2);
                secondFinished.countDown();
            });

            assertFalse("second command must wait for the first", secondFinished.await(
                    100, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            assertTrue(secondFinished.await(2, TimeUnit.SECONDS));
            assertEquals(Arrays.asList(1, 2), events);
            assertTrue("completed key state must be removed",
                    awaitIdle(dispatcher, 2, TimeUnit.SECONDS));
        } finally {
            backend.shutdownNow();
        }
    }

    @Test
    public void differentKeysCanRunConcurrently() throws Exception {
        ExecutorService backend = Executors.newFixedThreadPool(2);
        try {
            KeyedSerialDispatcher<String> dispatcher =
                    new KeyedSerialDispatcher<>(backend, 4, null);
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(2);

            dispatcher.execute("a", () -> {
                started.countDown();
                await(release);
                finished.countDown();
            });
            dispatcher.execute("b", () -> {
                started.countDown();
                await(release);
                finished.countDown();
            });

            assertTrue("unrelated keys should use separate workers",
                    started.await(2, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
        } finally {
            backend.shutdownNow();
        }
    }

    @Test
    public void commandFailureDoesNotStallTheKey() throws Exception {
        ExecutorService backend = Executors.newSingleThreadExecutor();
        try {
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            KeyedSerialDispatcher<String> dispatcher =
                    new KeyedSerialDispatcher<>(backend, 4,
                            (key, failure) -> failures.add(failure));
            CountDownLatch secondFinished = new CountDownLatch(1);

            dispatcher.execute("same", () -> {
                throw new AssertionError("expected");
            });
            dispatcher.execute("same", secondFinished::countDown);

            assertTrue(secondFinished.await(2, TimeUnit.SECONDS));
            assertEquals(1, failures.size());
            assertTrue(failures.get(0) instanceof AssertionError);
        } finally {
            backend.shutdownNow();
        }
    }

    @Test
    public void rejectingDelegateRunsCommandInlineAndCleansState() {
        KeyedSerialDispatcher<String> dispatcher = new KeyedSerialDispatcher<>(
                command -> {
                    throw new RejectedExecutionException("closed");
                },
                1,
                null);
        List<Integer> events = new ArrayList<>();

        dispatcher.execute("same", () -> events.add(1));
        dispatcher.execute("same", () -> events.add(2));

        assertEquals(Arrays.asList(1, 2), events);
        assertEquals(0, dispatcher.activeKeyCountForTest());
        assertEquals(0, dispatcher.queuedCommandCountForTest());
    }

    @Test
    public void boundedQueuedPermitAppliesBackpressureAndEventuallyReleases() throws Exception {
        ExecutorService backend = Executors.newSingleThreadExecutor();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            KeyedSerialDispatcher<String> dispatcher =
                    new KeyedSerialDispatcher<>(backend, 1, null);
            dispatcher.execute("same", () -> {
                firstStarted.countDown();
                await(releaseFirst);
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            // Active work is intentionally not counted against the bound. The
            // second command occupies the one queued slot, so only the third
            // producer has to apply back-pressure.
            dispatcher.execute("same", () -> { });

            FutureTask<Void> third = new FutureTask<>(() -> {
                dispatcher.execute("same", () -> { });
                return null;
            });
            Thread producer = new Thread(third, "keyed-dispatch-test-producer");
            producer.start();
            // With one queued-command slot, the producer must wait instead of
            // growing an unbounded per-key queue.
            Thread.sleep(50L);
            assertFalse(third.isDone());

            releaseFirst.countDown();
            third.get(2, TimeUnit.SECONDS);
            producer.join(2_000L);
            assertTrue("permit and key state must be released",
                    awaitIdle(dispatcher, 2, TimeUnit.SECONDS));
            assertEquals(1, dispatcher.availableSlotsForTest());
        } finally {
            releaseFirst.countDown();
            backend.shutdownNow();
        }
    }

    @Test
    public void workerReentryNeverWaitsWhenQueuedSlotsAreFull() throws Exception {
        ExecutorService backend = Executors.newSingleThreadExecutor();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch allowReentry = new CountDownLatch(1);
        CountDownLatch reentryReturned = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<Integer> events = Collections.synchronizedList(new ArrayList<>());
        try {
            KeyedSerialDispatcher<String> dispatcher =
                    new KeyedSerialDispatcher<>(backend, 1, null);
            dispatcher.execute("same", () -> {
                events.add(1);
                firstStarted.countDown();
                await(allowReentry);
                // The second command fills the only queue slot. This call is
                // made from a dispatcher worker and must use the emergency
                // no-slot path instead of waiting for itself.
                dispatcher.execute("same", () -> events.add(3));
                reentryReturned.countDown();
                await(releaseFirst);
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            dispatcher.execute("same", () -> events.add(2));
            allowReentry.countDown();
            assertTrue(reentryReturned.await(2, TimeUnit.SECONDS));
            releaseFirst.countDown();

            assertTrue(awaitIdle(dispatcher, 2, TimeUnit.SECONDS));
            assertEquals(Arrays.asList(1, 2, 3), events);
            assertEquals(1, dispatcher.availableSlotsForTest());
        } finally {
            allowReentry.countDown();
            releaseFirst.countDown();
            backend.shutdownNow();
        }
    }

    @Test
    public void ordinaryProducerRunsIdleKeyInlineWhenQueuedSlotsAreFull() {
        List<Integer> events = new ArrayList<>();
        List<Runnable> scheduled = new ArrayList<>();
        KeyedSerialDispatcher<String> dispatcher = new KeyedSerialDispatcher<>(
                scheduled::add, 1, null);

        dispatcher.execute("queued", () -> events.add(1));
        // The only bounded slot is occupied by the queued command. A new,
        // unrelated idle key should use the lossless caller-runs path instead
        // of waiting for the first delegate task to execute.
        dispatcher.execute("inline", () -> events.add(2));

        assertEquals(Arrays.asList(2), events);
        assertEquals(1, scheduled.size());
        assertEquals(1, dispatcher.activeKeyCountForTest());

        scheduled.get(0).run();
        assertEquals(Arrays.asList(2, 1), events);
        assertEquals(1, dispatcher.availableSlotsForTest());
    }

    @Test
    public void inlineKeyRegistrationPreservesOrderForConcurrentSubmission()
            throws Exception {
        List<Runnable> scheduled = Collections.synchronizedList(new ArrayList<>());
        List<Integer> events = Collections.synchronizedList(new ArrayList<>());
        KeyedSerialDispatcher<String> dispatcher = new KeyedSerialDispatcher<>(
                scheduled::add, 1, null);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        FutureTask<Void> first = new FutureTask<>(() -> {
            dispatcher.execute("same", () -> {
                events.add(1);
                firstStarted.countDown();
                await(releaseFirst);
            });
            return null;
        });
        Thread firstProducer = new Thread(first, "keyed-dispatch-inline-first");

        // Occupy the only queued slot so the first "same" submission must use
        // the idle-key CallerRuns path.
        dispatcher.execute("blocker", () -> events.add(0));
        firstProducer.start();
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

        FutureTask<Void> second = new FutureTask<>(() -> {
            dispatcher.execute("same", () -> {
                events.add(2);
                secondFinished.countDown();
            });
            return null;
        });
        Thread secondProducer = new Thread(second, "keyed-dispatch-inline-second");
        secondProducer.start();

        assertFalse("same-key submission must wait behind inline work",
                secondFinished.await(100, TimeUnit.MILLISECONDS));
        // The unrelated queued command releases the only permit; the waiting
        // same-key producer can then append behind the inline command.
        scheduled.get(0).run();
        releaseFirst.countDown();
        second.get(2, TimeUnit.SECONDS);
        first.get(2, TimeUnit.SECONDS);
        secondProducer.join(2_000L);

        assertEquals(Arrays.asList(1, 0, 2), events);
        assertEquals(1, dispatcher.availableSlotsForTest());
    }

    @Test
    public void delegateRuntimeExceptionFallsBackInlineAndCleansState() {
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Integer> events = new ArrayList<>();
        KeyedSerialDispatcher<String> dispatcher = new KeyedSerialDispatcher<>(
                command -> {
                    throw new IllegalStateException("executor failed");
                },
                2,
                (key, failure) -> failures.add(failure));

        dispatcher.execute("same", () -> {
            events.add(1);
            dispatcher.execute("same", () -> events.add(2));
        });

        assertEquals(Arrays.asList(1, 2), events);
        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof IllegalStateException);
        assertEquals(0, dispatcher.activeKeyCountForTest());
        assertEquals(0, dispatcher.queuedCommandCountForTest());
        assertEquals(2, dispatcher.availableSlotsForTest());
    }

    @Test
    public void delegateErrorFallsBackInlineAndCleansState() {
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Integer> events = new ArrayList<>();
        KeyedSerialDispatcher<String> dispatcher = new KeyedSerialDispatcher<>(
                command -> {
                    throw new AssertionError("executor failed");
                },
                1,
                (key, failure) -> failures.add(failure));

        dispatcher.execute("same", () -> events.add(1));

        assertEquals(Arrays.asList(1), events);
        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof AssertionError);
        assertEquals(0, dispatcher.activeKeyCountForTest());
        assertEquals(0, dispatcher.queuedCommandCountForTest());
        assertEquals(1, dispatcher.availableSlotsForTest());
    }

    @Test
    public void shutdownNowReturnsPendingCommandsReleasesPermitsAndRejectsNewWork() {
        List<Runnable> scheduled = Collections.synchronizedList(new ArrayList<>());
        List<Integer> events = new ArrayList<>();
        KeyedSerialDispatcher<String> dispatcher = new KeyedSerialDispatcher<>(
                scheduled::add,
                2,
                null);

        dispatcher.execute("same", () -> events.add(1));
        dispatcher.execute("same", () -> events.add(2));
        assertEquals(2, dispatcher.queuedCommandCountForTest());
        assertEquals(0, dispatcher.availableSlotsForTest());

        List<Runnable> pending = dispatcher.shutdownNow();

        assertEquals(2, pending.size());
        assertEquals(0, events.size());
        assertEquals(0, dispatcher.activeKeyCountForTest());
        assertEquals(0, dispatcher.queuedCommandCountForTest());
        assertEquals(2, dispatcher.availableSlotsForTest());
        try {
            dispatcher.execute("same", () -> events.add(3));
            throw new AssertionError("closed dispatcher accepted a command");
        } catch (RejectedExecutionException expected) {
            // Expected lifecycle contract.
        }

        // A drain wrapper which was already handed to the delegate becomes a
        // no-op after shutdownNow; it cannot resurrect detached commands.
        assertEquals(1, scheduled.size());
        scheduled.get(0).run();
        assertEquals(0, dispatcher.activeKeyCountForTest());
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean awaitIdle(
            KeyedSerialDispatcher<?> dispatcher, long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (dispatcher.activeKeyCountForTest() == 0
                    && dispatcher.queuedCommandCountForTest() == 0) {
                return true;
            }
            Thread.yield();
        }
        return dispatcher.activeKeyCountForTest() == 0
                && dispatcher.queuedCommandCountForTest() == 0;
    }
}
