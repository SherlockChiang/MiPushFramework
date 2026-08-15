package com.xiaomi.xmsf.push.control;

/**
 * Pure policy class to evaluate push service starting decisions without Android UI dependencies.
 */
public class PushServiceStartPolicy {

    public enum Action {
        SKIP,
        START_SERVICE,
        START_FOREGROUND
    }

    public static Action evaluate(
            boolean isMasterEnabled,
            boolean isServiceRunning,
            boolean isUserInitiated,
            boolean isPersistentForegroundEnabled,
            boolean isPlatformAllowed) {
        if (!isMasterEnabled) {
            return Action.SKIP;
        }
        if (isServiceRunning) {
            return Action.START_SERVICE;
        }
        if (isUserInitiated) {
            return Action.START_SERVICE;
        }
        if (isPersistentForegroundEnabled && isPlatformAllowed) {
            return Action.START_FOREGROUND;
        }
        return Action.SKIP;
    }
}
