package top.trumeet.common.utils;

import androidx.annotation.Nullable;

public final class NotificationAlertUtils {
    public static final int NOTIFY_TYPE_SOUND = 1;
    public static final int NOTIFY_TYPE_VIBRATE = 2;
    public static final int NOTIFY_TYPE_LIGHTS = 4;

    private NotificationAlertUtils() {
    }

    public static boolean usesPackageResourceSound(
            int notifyType, @Nullable String soundUri, @Nullable String packageName) {
        if ((notifyType & NOTIFY_TYPE_SOUND) == 0
                || soundUri == null || soundUri.isEmpty()
                || packageName == null || packageName.isEmpty()) {
            return false;
        }
        return soundUri.startsWith("android.resource://" + packageName + "/");
    }
}
