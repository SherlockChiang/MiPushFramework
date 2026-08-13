package com.xiaomi.xmsf.push.control;

/**
 * Owns the single delayed registration retry for this process.
 *
 * <p>The generation token prevents a retry which was already running during disable from
 * scheduling itself again after a later re-enable.</p>
 */
final class RegistrationRetryCoordinator {
    interface TaskFactory {
        Runnable create(long generation);
    }

    interface Scheduler {
        boolean postDelayed(Runnable task, long delayMs);

        void removeCallbacks(Runnable task);
    }

    private final Scheduler scheduler;
    /*
     * Push is enabled by default in preferences. Starting enabled also makes retries requested
     * by alternate process/service startup paths reliable before setServiceEnable(true) has had
     * a chance to run. An explicit disable still invalidates the generation immediately.
     */
    private boolean enabled = true;
    private long generation = 1L;
    private Runnable pending;

    RegistrationRetryCoordinator(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    synchronized void enable() {
        if (!enabled) {
            enabled = true;
            generation++;
        }
    }

    synchronized boolean isEnabled() {
        return enabled;
    }

    synchronized void disable() {
        if (enabled) {
            enabled = false;
            generation++;
        }
        if (pending != null) {
            scheduler.removeCallbacks(pending);
            pending = null;
        }
    }

    synchronized boolean schedule(long delayMs, TaskFactory taskFactory) {
        return schedule(delayMs, generation, taskFactory);
    }

    synchronized boolean schedule(long delayMs, long expectedGeneration,
                                  TaskFactory taskFactory) {
        if (!enabled || generation != expectedGeneration || pending != null) {
            return false;
        }
        Runnable task = taskFactory.create(generation);
        if (task == null) {
            return false;
        }
        pending = task;
        if (!scheduler.postDelayed(task, delayMs)) {
            pending = null;
            scheduler.removeCallbacks(task);
            return false;
        }
        return true;
    }

    synchronized boolean begin(Runnable task, long taskGeneration) {
        if (pending != task) {
            return false;
        }
        pending = null;
        return enabled && generation == taskGeneration;
    }

    /**
     * Runs a registration side effect only while this retry generation is still current.
     *
     * <p>The action intentionally executes while holding the coordinator monitor. This closes
     * the otherwise unavoidable check-then-act race with {@link #disable()}: if disable wins,
     * the action is skipped; if the action already started, disable waits and the caller's
     * unregister operation happens afterwards.</p>
     */
    synchronized boolean runIfActive(long taskGeneration, Runnable action) {
        if (!enabled || generation != taskGeneration) {
            return false;
        }
        action.run();
        return true;
    }

    /** Applies the same ordering guarantee to the initial registration job. */
    synchronized boolean runIfEnabled(Runnable action) {
        if (!enabled) {
            return false;
        }
        action.run();
        return true;
    }

    synchronized void cancelPending() {
        if (pending != null) {
            scheduler.removeCallbacks(pending);
            pending = null;
        }
    }
}
