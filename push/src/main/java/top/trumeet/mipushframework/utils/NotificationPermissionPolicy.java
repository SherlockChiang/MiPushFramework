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

    /**
     * Destination used when the user needs to repair notification access from settings.
     *
     * <p>This is deliberately a pure value so the Android intent construction and launch
     * fallback can be tested without creating an Android {@code Context}.</p>
     */
    public enum SettingsRoute {
        APP_NOTIFICATION_SETTINGS,
        APPLICATION_DETAILS_SETTINGS
    }

    /**
     * Selects the most specific settings destination available on the device.
     */
    public static SettingsRoute chooseSettingsRoute(boolean notificationSettingsResolvable) {
        return notificationSettingsResolvable
                ? SettingsRoute.APP_NOTIFICATION_SETTINGS
                : SettingsRoute.APPLICATION_DETAILS_SETTINGS;
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
