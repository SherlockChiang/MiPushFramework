package com.xiaomi.xmsf.push.control;

/** Pure policy for process-start background work and its elapsed-time throttle. */
public final class StartupWorkPolicy {
    private StartupWorkPolicy() {
    }

    public static boolean shouldRunAppStartup(
            boolean mainProcess, boolean masterEnabled) {
        return mainProcess && masterEnabled;
    }

    public static boolean shouldRunThrottled(
            long previousElapsedRealtime,
            long nowElapsedRealtime,
            long minimumIntervalMs) {
        if (previousElapsedRealtime == 0L) {
            return true;
        }
        long elapsed = nowElapsedRealtime - previousElapsedRealtime;
        return elapsed < 0L || elapsed >= minimumIntervalMs;
    }
}
