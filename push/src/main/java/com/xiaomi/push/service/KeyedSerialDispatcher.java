package com.xiaomi.push.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Dispatches commands serially for each logical key while allowing unrelated
 * keys to use the delegate executor concurrently.
 *
 * <p>The dispatcher owns the per-key queues and a bounded number of queued
 * commands. A command releases its queue permit before it starts running, so a
 * notification worker never waits for a permit held by its own active command.
 * When no permit is available, a producer for an idle key uses a lossless
 * caller-runs path; a producer for a key that is already draining waits for a
 * permit, while dispatcher-worker re-entry uses an unmetered emergency entry
 * to preserve ordering without deadlocking the worker.</p>
 *
 * <p>The delegate is not owned by this class. Call {@link #shutdown()} or
 * {@link #shutdownNow()} before shutting down the delegate. A generic
 * {@link Executor} cannot report a drain runnable discarded by a direct
 * {@code shutdownNow()}, so bypassing this lifecycle contract may strand a
 * queued key.</p>
 */
final class KeyedSerialDispatcher<K> {
    private static final long PRODUCER_WAIT_MILLIS = 100L;

    private final Executor delegate;
    /** Counts queued (not active) commands that own a bounded slot. */
    private final Semaphore slots;
    private final FailureHandler<K> failureHandler;
    private final Object stateLock = new Object();
    private final Map<K, State> states = new HashMap<>();
    private final ThreadLocal<Integer> drainDepth = new ThreadLocal<>();
    private volatile boolean closed;

    KeyedSerialDispatcher(
            Executor delegate,
            int maxRetainedCommands,
            FailureHandler<K> failureHandler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxRetainedCommands <= 0) {
            throw new IllegalArgumentException("maxRetainedCommands must be positive");
        }
        this.slots = new Semaphore(maxRetainedCommands, true);
        this.failureHandler = failureHandler;
    }

    /**
     * Enqueue a command for {@code key}. For queued calls, order is the order
     * in which the bounded slot is acquired and the command enters the
     * dispatcher. An idle key may execute inline when the queue is full;
     * dispatcher-worker re-entry is ordered under the key lock without
     * waiting in that case.
     *
     * @throws RejectedExecutionException after {@link #shutdown()} or
     *         {@link #shutdownNow()} has been called
     */
    void execute(K key, Runnable command) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(command, "command");

        // Acquire the first slot and register an idle key atomically. Without
        // this critical section, producer A could take the last slot and pause
        // before inserting its state while producer B observes an apparently
        // idle key and runs inline, reversing the same-key FIFO order.
        boolean reservedSlot = false;
        while (true) {
            State state;
            boolean schedule = false;
            boolean runInline = false;
            boolean retryForSlot = false;
            synchronized (stateLock) {
                if (closed) {
                    if (reservedSlot) {
                        slots.release();
                    }
                    throw new RejectedExecutionException("dispatcher is shut down");
                }

                state = states.get(key);
                if (state == null) {
                    if (reservedSlot || slots.tryAcquire()) {
                        state = new State();
                        states.put(key, state);
                        state.commands.addLast(new Entry(command, true));
                        state.scheduled = true;
                        reservedSlot = false;
                        schedule = true;
                    } else {
                        // Match ThreadPoolExecutor's lossless CallerRunsPolicy
                        // for an idle key: execute directly instead of parking
                        // the push ingress thread behind unrelated work. The
                        // key is marked draining before the command starts, so
                        // concurrent submissions still queue behind it in FIFO.
                        state = new State();
                        state.scheduled = true;
                        state.drainClaimed = true;
                        state.draining = true;
                        state.commands.addLast(new Entry(command, false));
                        states.put(key, state);
                        runInline = true;
                    }
                } else {
                    boolean entryOwnsSlot = reservedSlot;
                    if (!entryOwnsSlot) {
                        entryOwnsSlot = slots.tryAcquire();
                    }
                    if (!entryOwnsSlot && !isDispatcherWorker()) {
                        // A producer for an already-draining key waits for a
                        // bounded queue permit, but never while holding the
                        // state lock. Dispatcher-worker re-entry below uses an
                        // unmetered emergency entry to avoid self-deadlock.
                        retryForSlot = true;
                    } else {
                        state.commands.addLast(new Entry(command, entryOwnsSlot));
                        reservedSlot = false;
                        if (!state.scheduled) {
                            state.scheduled = true;
                            schedule = true;
                        }
                    }
                }
            }

            if (runInline) {
                drainClaimed(key, state);
                return;
            }
            if (schedule) {
                submitDrain(key, state);
                return;
            }
            if (!retryForSlot) {
                return;
            }
            // Only the non-worker path can reach here. Keep the acquired
            // permit across the next state-lock acquisition so another
            // producer cannot steal it and overtake this submission.
            reservedSlot = acquireSlotForCaller();
        }
    }

    /**
     * Stop accepting new commands while allowing all already queued commands
     * to finish on the delegate. This method does not shut down the delegate.
     */
    void shutdown() {
        closed = true;
    }

    /**
     * Stop accepting new commands and detach commands that have not started.
     * Active commands are allowed to finish. The returned list contains the
     * detached commands in per-key FIFO order; callers own whether/how to
     * retry them. Their queue permits are released before this method returns.
     */
    List<Runnable> shutdownNow() {
        List<Runnable> pending = new ArrayList<>();
        synchronized (stateLock) {
            closed = true;
            for (State state : states.values()) {
                state.cancelled = true;
                Entry entry;
                while ((entry = state.commands.pollFirst()) != null) {
                    pending.add(entry.command);
                    releaseSlot(entry);
                }
                if (!state.draining) {
                    // The delegate may still have the drain wrapper queued; it
                    // will observe cancelled and return without touching state.
                    state.scheduled = false;
                }
            }
            states.entrySet().removeIf(entry -> !entry.getValue().draining);
        }
        return pending;
    }

    private boolean acquireSlotForCaller() {
        if (closed) {
            throw new RejectedExecutionException("dispatcher is shut down");
        }

        // A worker must never wait for a permit: active notification work may
        // be the only thing capable of releasing one. If no queue slot is
        // available, retain this re-entrant command as an emergency entry.
        if (isDispatcherWorker()) {
            return slots.tryAcquire();
        }

        boolean interrupted = false;
        while (true) {
            if (closed) {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                throw new RejectedExecutionException("dispatcher is shut down");
            }
            try {
                if (slots.tryAcquire(PRODUCER_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return true;
                }
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
    }

    private boolean isDispatcherWorker() {
        Integer depth = drainDepth.get();
        return depth != null && depth > 0;
    }

    private void submitDrain(K key, State state) {
        try {
            delegate.execute(() -> drain(key, state));
        } catch (Throwable schedulingFailure) {
            // Executor implementations normally throw before accepting the
            // runnable. claimDrain() also protects against a broken executor
            // which throws after accepting it, so a task is never run twice.
            reportFailure(key, schedulingFailure);
            if (claimDrain(state)) {
                drainClaimed(key, state);
            }
        }
    }

    private void drain(K key, State state) {
        if (claimDrain(state)) {
            drainClaimed(key, state);
        }
    }

    private boolean claimDrain(State state) {
        synchronized (stateLock) {
            if (state.cancelled || state.drainClaimed) {
                return false;
            }
            state.drainClaimed = true;
            state.draining = true;
            return true;
        }
    }

    private void drainClaimed(K key, State state) {
        incrementDrainDepth();
        try {
            // Drain the whole currently-available queue in one worker
            // invocation. New entries remain FIFO and are handled without a
            // recursive hand-off, including when CallerRunsPolicy is active.
            while (true) {
                Entry entry;
                synchronized (stateLock) {
                    entry = state.cancelled ? null : state.commands.pollFirst();
                    if (entry == null) {
                        state.draining = false;
                        state.scheduled = false;
                        states.remove(key, state);
                        return;
                    }
                }

                // Active work is deliberately not counted against the queue
                // bound. This prevents a worker from waiting for its own
                // completion before it can enqueue a re-entrant command.
                releaseSlot(entry);
                try {
                    entry.command.run();
                } catch (Throwable failure) {
                    // One malformed payload must not prevent later states for
                    // the same notification key from being delivered.
                    reportFailure(key, failure);
                }
            }
        } finally {
            decrementDrainDepth();
            // Keep state/queue cleanup defensive if an unexpected failure
            // occurs outside command.run() (for example a VM-level Error).
            synchronized (stateLock) {
                if (state.draining) {
                    state.draining = false;
                    state.scheduled = false;
                    clearPendingLocked(state);
                    states.remove(key, state);
                }
            }
        }
    }

    private void clearPendingLocked(State state) {
        Entry entry;
        while ((entry = state.commands.pollFirst()) != null) {
            releaseSlot(entry);
        }
    }

    private void incrementDrainDepth() {
        Integer depth = drainDepth.get();
        drainDepth.set(depth == null ? 1 : depth + 1);
    }

    private void decrementDrainDepth() {
        Integer depth = drainDepth.get();
        if (depth == null || depth <= 1) {
            drainDepth.remove();
        } else {
            drainDepth.set(depth - 1);
        }
    }

    private void releaseSlot(Entry entry) {
        if (entry.ownsSlot) {
            entry.ownsSlot = false;
            slots.release();
        }
    }

    private void reportFailure(K key, Throwable failure) {
        if (failureHandler == null) {
            return;
        }
        try {
            failureHandler.onFailure(key, failure);
        } catch (Throwable ignored) {
            // Failure reporting must not break queue progress.
        }
    }

    // Package-private test visibility keeps production API surface small.
    int activeKeyCountForTest() {
        synchronized (stateLock) {
            return states.size();
        }
    }

    // Package-private test visibility. Running commands have already been
    // removed from their queue, so this is primarily useful for leak checks.
    int queuedCommandCountForTest() {
        synchronized (stateLock) {
            int count = 0;
            for (State state : states.values()) {
                count += state.commands.size();
            }
            return count;
        }
    }

    // Package-private test visibility for shutdown permit-leak checks.
    int availableSlotsForTest() {
        return slots.availablePermits();
    }

    private static final class Entry {
        final Runnable command;
        boolean ownsSlot;

        Entry(Runnable command, boolean ownsSlot) {
            this.command = command;
            this.ownsSlot = ownsSlot;
        }
    }

    private static final class State {
        final ArrayDeque<Entry> commands = new ArrayDeque<>();
        boolean scheduled;
        boolean drainClaimed;
        boolean draining;
        boolean cancelled;
    }

    interface FailureHandler<K> {
        void onFailure(K key, Throwable failure);
    }
}
