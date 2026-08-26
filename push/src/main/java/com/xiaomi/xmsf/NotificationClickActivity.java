package com.xiaomi.xmsf;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.xiaomi.push.sdk.MyPushMessageHandler;
import com.xiaomi.push.sdk.TargetSdkClickDispatcher;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;

import com.nihility.XMPushUtils;

/**
 * User-initiated notification click hand-off.
 *
 * <p>The notification is posted by XMSF, but the original MiPush SDK click
 * contract has two parts: it wakes the target application and forwards the
 * complete message to that application's {@code PushMessageHandler}. Starting
 * the target Activity directly skips the second part and breaks SDK bridge
 * Activities (for example a vendor's notification proxy). This transparent
 * Activity is the common hand-off point for every package; it contains no
 * package-specific routing.</p>
 */
public final class NotificationClickActivity extends Activity {
    public static final String EXTRA_TARGET_INTENT =
            "com.xiaomi.xmsf.extra.NOTIFICATION_TARGET_INTENT";
    public static final String EXTRA_SERVICE_INTENT =
            "com.xiaomi.xmsf.extra.NOTIFICATION_SERVICE_INTENT";
    public static final String EXTRA_TARGET_ACTIVITY_PRIVATE =
            "com.xiaomi.xmsf.extra.NOTIFICATION_TARGET_ACTIVITY_PRIVATE";
    public static final String EXTRA_MANUAL_REPLAY =
            "com.xiaomi.xmsf.extra.NOTIFICATION_MANUAL_REPLAY";

    private static final String TAG = "MiPushClick";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dispatchClick(getIntent());
    }

    private void dispatchClick(@Nullable Intent clickIntent) {
        if (clickIntent == null) {
            finish();
            return;
        }

        Intent serviceIntent = getParcelable(clickIntent, EXTRA_SERVICE_INTENT);
        if (serviceIntent == null) {
            serviceIntent = clickIntent.getParcelableExtra("mipush_serviceIntent");
        }
        byte[] payload = clickIntent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
        if (payload == null && serviceIntent != null) {
            payload = serviceIntent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
        }
        XmPushActionContainer container = null;
        if (payload != null && payload.length > 0) {
            try {
                container = XMPushUtils.packToContainer(payload);
            } catch (Throwable decodeError) {
                Log.w(TAG, "unable to decode notification click payload", decodeError);
            }
        }
        Intent targetIntent = getParcelable(clickIntent, EXTRA_TARGET_INTENT);
        boolean targetActivityPrivate = clickIntent.getBooleanExtra(
                EXTRA_TARGET_ACTIVITY_PRIVATE, false);
        boolean manualReplay = clickIntent.getBooleanExtra(EXTRA_MANUAL_REPLAY, false);

        try {
            if (manualReplay) {
                TargetSdkClickDispatcher.DispatchResult result =
                        TargetSdkClickDispatcher.dispatchReplay(this, container);
                if (TargetSdkClickDispatcher.shouldLaunchReplayFallback(result)) {
                    Log.w(TAG, "manual replay SDK hand-off failed: " + result);
                    startTargetLauncher(container);
                }
            } else if (container != null && payload != null) {
                // This is the official generic click contract, now executed from
                // a user-initiated Activity instead of a background Service. Send
                // the complete payload through the target SDK first, then open the
                // sender-provided route (or the validated Launcher fallback).
                boolean forwarded = false;
                try {
                    forwarded = MyPushMessageHandler.forwardToTargetApplication(this, payload)
                            != null;
                    if (!forwarded) {
                        Log.w(TAG, "target SDK click bridge returned no component");
                    }
                } catch (Throwable forwardError) {
                    // A private proxy may not expose the MiPush bridge service.
                    // Continue with the exported route/launcher fallback below.
                    Log.w(TAG, "target SDK click bridge unavailable", forwardError);
                }
                if (!targetActivityPrivate || !forwarded) {
                    startTargetActivity(targetIntent, clickIntent, container, targetActivityPrivate);
                }
            } else {
                // A malformed/stale click must still try the validated target route.
                startTargetActivity(targetIntent, clickIntent, null, targetActivityPrivate);
            }
        } catch (Throwable error) {
            Log.w(TAG, "notification click hand-off failed", error);
            try {
                if (manualReplay) {
                    startTargetLauncher(container);
                } else {
                    // Live notifications retain their existing validated route.
                    startTargetActivity(
                            targetIntent, clickIntent, container, targetActivityPrivate);
                }
            } catch (Throwable fallbackError) {
                Log.w(TAG, "notification click Activity fallback failed", fallbackError);
            }
        } finally {
            Bundle notificationExtras = serviceIntent == null
                    ? clickIntent.getExtras() : serviceIntent.getExtras();
            if (container != null && notificationExtras != null) {
                try {
                    MyPushMessageHandler.cancelNotification(this, notificationExtras, container);
                } catch (Throwable error) {
                    Log.w(TAG, "unable to cancel clicked notification", error);
                }
            }
            finish();
        }
    }

    /** Replay payloads may contain stale vendor bridge tokens; failure opens only the app root. */
    private void startTargetLauncher(@Nullable XmPushActionContainer container) {
        String targetPackage = container == null ? null : container.getPackageName();
        if (targetPackage == null) {
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launch == null) {
            return;
        }
        ResolveInfo resolved = getPackageManager().resolveActivity(
                launch, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.activityInfo == null
                || !resolved.activityInfo.exported
                || !targetPackage.equals(resolved.activityInfo.packageName)) {
            return;
        }
        launch.setComponent(new ComponentName(
                resolved.activityInfo.packageName, resolved.activityInfo.name));
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private void startTargetActivity(
            @Nullable Intent targetIntent,
            Intent clickIntent,
            @Nullable XmPushActionContainer container,
            boolean targetActivityPrivate) {
        Intent launch = targetActivityPrivate
                ? resolveExportedFallback(targetIntent, container)
                : (targetIntent == null ? null : new Intent(targetIntent));
        if (launch == null && container != null && container.getPackageName() != null) {
            launch = getPackageManager().getLaunchIntentForPackage(container.getPackageName());
        }
        if (launch == null) {
            return;
        }

        if (targetActivityPrivate) {
            Log.i(TAG, "private notification route replaced with exported target: "
                    + launch.getComponent());
        }

        Intent serviceIntent = getParcelable(clickIntent, EXTRA_SERVICE_INTENT);
        if (serviceIntent == null) {
            serviceIntent = clickIntent.getParcelableExtra("mipush_serviceIntent");
        }
        if (serviceIntent != null) {
            // Preserve the SDK's historical bridge extras for target proxy
            // Activities. They are opaque to XMSF and therefore work for every
            // client without an application-specific adapter.
            launch.putExtra("mipush_serviceIntent", serviceIntent);
            launch.putExtras(serviceIntent);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launch);
        } catch (ActivityNotFoundException error) {
            ComponentName component = launch.getComponent();
            Log.w(TAG, "target Activity not found: " + component, error);
            throw error;
        }
    }

    /**
     * Resolve a user-visible route without ever attempting to start the
     * sender's private proxy Activity.  Clearing an explicit component keeps
     * its action/data/extras (for example a vendor deep link) and lets the
     * package manager select an exported handler in the same target package.
     * If no such handler exists, the caller falls back to the package launcher.
     */
    @Nullable
    private Intent resolveExportedFallback(
            @Nullable Intent targetIntent,
            @Nullable XmPushActionContainer container) {
        if (targetIntent == null) {
            return null;
        }

        String targetPackage = container == null ? null : container.getPackageName();
        ComponentName explicit = targetIntent.getComponent();
        if (targetPackage == null && explicit != null) {
            targetPackage = explicit.getPackageName();
        }

        Intent candidate = new Intent(targetIntent);
        if (explicit != null) {
            // Do not leak a cross-package component from malformed payloads.
            if (targetPackage == null || !targetPackage.equals(explicit.getPackageName())) {
                return null;
            }
            candidate.setComponent(null);
            candidate.setPackage(targetPackage);
        }

        ResolveInfo resolved = getPackageManager().resolveActivity(
                candidate, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.activityInfo == null
                || !resolved.activityInfo.exported
                || (targetPackage != null
                && !targetPackage.equals(resolved.activityInfo.packageName))) {
            return null;
        }
        candidate.setComponent(new ComponentName(
                resolved.activityInfo.packageName, resolved.activityInfo.name));
        return candidate;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static Intent getParcelable(Intent source, String key) {
        return source.getParcelableExtra(key);
    }
}
