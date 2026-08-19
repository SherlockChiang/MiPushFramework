package top.trumeet.mipushframework.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Android adapter around {@link NotificationPermissionPolicy}. */
public final class NotificationPermissionController {
    private static final String PREFS_NAME = "notification_permission_state";
    private static final String KEY_REQUESTED = "post_notifications_requested";

    private NotificationPermissionController() {
    }

    @NonNull
    public static NotificationPermissionPolicy.Status status(@NonNull Activity activity) {
        boolean granted = Build.VERSION.SDK_INT < NotificationPermissionPolicy.RUNTIME_PERMISSION_SDK
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean requestedBefore = preferences(activity).getBoolean(KEY_REQUESTED, false);
        boolean shouldShowRationale = Build.VERSION.SDK_INT
                >= NotificationPermissionPolicy.RUNTIME_PERMISSION_SDK
                && ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.POST_NOTIFICATIONS);
        return NotificationPermissionPolicy.evaluate(
                Build.VERSION.SDK_INT, granted, requestedBefore, shouldShowRationale);
    }

    public static boolean shouldAutoRequest(@NonNull Activity activity) {
        return NotificationPermissionPolicy.shouldAutoRequest(status(activity));
    }

    public static void markRequested(@NonNull Context context) {
        preferences(context).edit().putBoolean(KEY_REQUESTED, true).apply();
    }

    public static void openNotificationSettings(@NonNull Context context) {
        String packageName = context.getPackageName();
        Intent notificationSettings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent applicationDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PackageManager packageManager;
        try {
            packageManager = context.getPackageManager();
        } catch (SecurityException ignored) {
            return;
        }

        boolean notificationSettingsResolvable;
        try {
            notificationSettingsResolvable = notificationSettings.resolveActivity(packageManager) != null;
        } catch (SecurityException ignored) {
            notificationSettingsResolvable = false;
        }

        Intent preferred = NotificationPermissionPolicy.chooseSettingsRoute(notificationSettingsResolvable)
                == NotificationPermissionPolicy.SettingsRoute.APP_NOTIFICATION_SETTINGS
                ? notificationSettings
                : applicationDetails;
        if (tryStartActivity(context, packageManager, preferred)) {
            return;
        }

        // HyperOS variants can report a resolver and still reject the launch. Keep a
        // package-details fallback so tapping the row can never crash or strand the user.
        if (preferred != applicationDetails) {
            tryStartActivity(context, packageManager, applicationDetails);
        }
    }

    private static boolean tryStartActivity(
            @NonNull Context context,
            @NonNull PackageManager packageManager,
            @NonNull Intent intent) {
        try {
            if (intent.resolveActivity(packageManager) == null) {
                return false;
            }
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
