package com.xiaomi.push.sdk;

/**
 * Pure decision policy for notification click hand-off.
 *
 * <p>Private target Activities cannot be started by XMSF, but opening the
 * target launcher before handing the payload to its SDK creates a visible
 * launcher-to-deep-link task gap. The policy therefore gives the target SDK
 * the original user-initiated click first and uses an exported route or the
 * launcher only when delivery is rejected or produces no visible UI.</p>
 */
public final class NotificationClickHandoffPolicy {
    public enum Action {
        DISPATCH_SDK_FIRST,
        START_DIRECT_TARGET,
        WAIT_FOR_TARGET,
        START_FALLBACK,
        FINISH
    }

    private NotificationClickHandoffPolicy() {
    }

    public static Action initialAction(
            boolean manualReplay, boolean targetActivityPrivate) {
        return manualReplay || targetActivityPrivate
                ? Action.DISPATCH_SDK_FIRST
                : Action.START_DIRECT_TARGET;
    }

    public static Action afterSdkDispatch(boolean accepted, boolean targetVisible) {
        if (!accepted) {
            return Action.START_FALLBACK;
        }
        return targetVisible ? Action.FINISH : Action.WAIT_FOR_TARGET;
    }

    public static Action afterNavigationProbe(
            boolean targetVisible, boolean timedOut, boolean canLaunchFallback) {
        if (targetVisible) {
            return Action.FINISH;
        }
        if (!timedOut) {
            return Action.WAIT_FOR_TARGET;
        }
        return canLaunchFallback ? Action.START_FALLBACK : Action.FINISH;
    }
}
