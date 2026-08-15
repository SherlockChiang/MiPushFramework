package top.trumeet.mipushframework.utils;

/**
 * Pure decision model for Android 13+ notification permission UX.
 *
 * <p>Keeping the policy free of Android framework calls makes the first-run and
 * settings behavior deterministic and unit-testable.</p>
 */
public final class NotificationPermissionPolicy {
    public static final int RUNTIME_PERMISSION_SDK = 33;

    private NotificationPermissionPolicy() {
    }

    public enum Status {
        NOT_REQUIRED,
        GRANTED,
        REQUESTABLE,
        DENIED_CAN_ASK_AGAIN,
        BLOCKED
    }

    public static Status evaluate(
            int sdkInt,
            boolean granted,
            boolean requestedBefore,
            boolean shouldShowRationale) {
        if (sdkInt < RUNTIME_PERMISSION_SDK) {
            return Status.NOT_REQUIRED;
        }
        if (granted) {
            return Status.GRANTED;
        }
        if (!requestedBefore) {
            return Status.REQUESTABLE;
        }
        return shouldShowRationale
                ? Status.DENIED_CAN_ASK_AGAIN
                : Status.BLOCKED;
    }

    public static boolean shouldAutoRequest(Status status) {
        return status == Status.REQUESTABLE;
    }
}
