package com.xiaomi.xmsf;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.xiaomi.push.sdk.MyPushMessageHandler;
import com.xiaomi.push.sdk.TargetSdkClickDispatcher;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;

import com.nihility.XMPushUtils;

import top.trumeet.common.override.ActivityManagerOverride;

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
    public static final String EXTRA_TARGET_PACKAGE =
            "com.xiaomi.xmsf.extra.NOTIFICATION_TARGET_PACKAGE";

    private static final String TAG = "MiPushClick";
    private static final long TARGET_VISIBILITY_TIMEOUT_MS = 2_000L;
    private static final long TARGET_VISIBILITY_POLL_MS = 50L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable private Intent pendingClickIntent;
    @Nullable private Intent pendingServiceIntent;
    @Nullable private Intent pendingTargetIntent;
    @Nullable private byte[] pendingPayload;
    @Nullable private XmPushActionContainer pendingContainer;
    @Nullable private String pendingTargetPackage;
    private boolean pendingTargetActivityPrivate;
    private boolean pendingManualReplay;
    private boolean dispatchAfterTargetVisible;
    private boolean targetTaskPrimed;
    private boolean dispatched;
    private boolean stopped;
    private long targetLaunchStartedAt;

    private final Runnable targetVisibilityProbe = new Runnable() {
        @Override
        public void run() {
            if (!dispatchAfterTargetVisible || dispatched) {
                return;
            }
            if ((isTargetTaskVisible() || targetTaskPrimed && stopped)
                    && isUserPresent()) {
                completeClick();
                return;
            }
            if (SystemClock.uptimeMillis() - targetLaunchStartedAt
                    >= TARGET_VISIBILITY_TIMEOUT_MS) {
                abandonAfterTargetLaunch(isUserPresent()
                        ? "TARGET_UI_TIMEOUT" : "USER_NOT_PRESENT");
                return;
            }
            mainHandler.postDelayed(this, TARGET_VISIBILITY_POLL_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dispatchClick(getIntent());
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopped = true;
        // For an ordinary opaque launcher, Android stops this trampoline only
        // after the target Activity has become visible. This is the fastest and
        // deterministic hand-off point; the bounded probe above covers
        // translucent launchers that only pause us.
        if (dispatchAfterTargetVisible && !dispatched && isUserPresent()) {
            mainHandler.post(targetVisibilityProbe);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        stopped = false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && dispatchAfterTargetVisible && !dispatched) {
            mainHandler.post(targetVisibilityProbe);
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(targetVisibilityProbe);
        super.onDestroy();
    }

    private void dispatchClick(@Nullable Intent clickIntent) {
        if (clickIntent == null) {
            finishClickTask();
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

        pendingClickIntent = clickIntent;
        pendingServiceIntent = serviceIntent;
        pendingTargetIntent = targetIntent;
        pendingPayload = payload;
        pendingContainer = container;
        pendingTargetActivityPrivate = targetActivityPrivate;
        pendingManualReplay = manualReplay;
        pendingTargetPackage = resolveTargetPackage(clickIntent, container, targetIntent);

        if (TargetSdkClickDispatcher.shouldPrimeTargetTask(
                manualReplay, targetActivityPrivate)) {
            if (isTargetTaskVisible() && isUserPresent()) {
                completeClick();
                return;
            }
            targetLaunchStartedAt = SystemClock.uptimeMillis();
            if (!startTargetLauncher(pendingTargetPackage)) {
                completeClickWithoutConfirmedTarget("TARGET_LAUNCH_UNAVAILABLE");
                return;
            }
            targetTaskPrimed = true;
            dispatchAfterTargetVisible = true;
            mainHandler.post(targetVisibilityProbe);
            return;
        }

        completeClick();
    }

    private void completeClick() {
        if (dispatched) {
            return;
        }
        dispatched = true;
        dispatchAfterTargetVisible = false;
        mainHandler.removeCallbacks(targetVisibilityProbe);

        Intent clickIntent = pendingClickIntent;
        Intent targetIntent = pendingTargetIntent;
        byte[] payload = pendingPayload;
        XmPushActionContainer container = pendingContainer;

        try {
            if (pendingManualReplay) {
                TargetSdkClickDispatcher.DispatchResult result =
                        TargetSdkClickDispatcher.dispatchReplay(this, container);
                if (!result.isAccepted()) {
                    Log.w(TAG, "manual replay SDK delivery not accepted: " + result);
                }
            } else if (pendingTargetActivityPrivate && container != null && payload != null) {
                TargetSdkClickDispatcher.DispatchResult result =
                        TargetSdkClickDispatcher.dispatchPayload(this, container, payload);
                if (!result.isAccepted()) {
                    Log.w(TAG, "target SDK click delivery not accepted: " + result);
                    if (!targetTaskPrimed && clickIntent != null) {
                        startTargetActivity(targetIntent, clickIntent, container, true);
                    }
                }
            } else if (!targetTaskPrimed && clickIntent != null) {
                // A malformed/stale click must still try the validated target route.
                startTargetActivity(
                        targetIntent, clickIntent, container, pendingTargetActivityPrivate);
            } else if (!pendingTargetActivityPrivate && clickIntent != null) {
                // Defensive compatibility for an exported route that was wrapped
                // by an older notification already present in the shade.
                startTargetActivity(targetIntent, clickIntent, container, false);
            }
        } catch (Throwable error) {
            Log.w(TAG, "notification click hand-off failed", error);
            try {
                if (!targetTaskPrimed) {
                    if (pendingManualReplay) {
                        startTargetLauncher(pendingTargetPackage);
                    } else if (clickIntent != null) {
                        startTargetActivity(
                                targetIntent, clickIntent, container,
                                pendingTargetActivityPrivate);
                    }
                }
            } catch (Throwable fallbackError) {
                Log.w(TAG, "notification click Activity fallback failed", fallbackError);
            }
        } finally {
            cancelClickedNotification();
            finishClickTask();
        }
    }

    private void completeClickWithoutConfirmedTarget(String reason) {
        if (dispatched) {
            return;
        }
        Log.w(TAG, reason + ": delivering payload with isolated-task fallback");
        completeClick();
    }

    private void abandonAfterTargetLaunch(String reason) {
        if (dispatched) {
            return;
        }
        dispatched = true;
        dispatchAfterTargetVisible = false;
        mainHandler.removeCallbacks(targetVisibilityProbe);
        Log.w(TAG, reason + ": retaining target launcher fallback without SDK delivery");
        cancelClickedNotification();
        finishClickTask();
    }

    private void cancelClickedNotification() {
        Intent clickIntent = pendingClickIntent;
        Intent serviceIntent = pendingServiceIntent;
        XmPushActionContainer container = pendingContainer;
        Bundle notificationExtras = serviceIntent != null
                ? serviceIntent.getExtras()
                : (clickIntent == null ? null : clickIntent.getExtras());
        if (container != null && notificationExtras != null) {
            try {
                MyPushMessageHandler.cancelNotification(this, notificationExtras, container);
            } catch (Throwable error) {
                Log.w(TAG, "unable to cancel clicked notification", error);
            }
        }
    }

    /** Replay payloads may contain stale vendor bridge tokens; failure opens only the app root. */
    private boolean startTargetLauncher(@Nullable String targetPackage) {
        if (targetPackage == null || targetPackage.equals(getPackageName())) {
            return false;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launch == null) {
            return false;
        }
        ResolveInfo resolved = getPackageManager().resolveActivity(
                launch, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.activityInfo == null
                || !resolved.activityInfo.exported
                || !resolved.activityInfo.enabled
                || (resolved.activityInfo.applicationInfo != null
                && !resolved.activityInfo.applicationInfo.enabled)
                || !targetPackage.equals(resolved.activityInfo.packageName)) {
            return false;
        }
        launch.setComponent(new ComponentName(
                resolved.activityInfo.packageName, resolved.activityInfo.name));
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(launch);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            Log.w(TAG, "unable to prime target launcher: " + targetPackage, error);
            return false;
        }
    }

    private boolean isTargetTaskVisible() {
        if (pendingTargetPackage == null) {
            return false;
        }
        ActivityManager activityManager = getSystemService(ActivityManager.class);
        if (activityManager == null) {
            return false;
        }
        try {
            int importance = ActivityManagerOverride.getPackageImportance(
                    pendingTargetPackage, activityManager);
            return importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    || importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
        } catch (Throwable unavailable) {
            // Usage access and hidden-API availability differ across third-party
            // ROMs. onStop remains the portable opaque-Activity readiness signal.
            return false;
        }
    }

    private boolean isUserPresent() {
        PowerManager powerManager = getSystemService(PowerManager.class);
        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        return powerManager != null && powerManager.isInteractive()
                && (keyguardManager == null || !keyguardManager.isKeyguardLocked());
    }

    @Nullable
    private String resolveTargetPackage(
            Intent clickIntent,
            @Nullable XmPushActionContainer container,
            @Nullable Intent targetIntent) {
        String trustedPackage = clickIntent.getStringExtra(EXTRA_TARGET_PACKAGE);
        if (container != null && container.getPackageName() != null) {
            if (trustedPackage != null && !trustedPackage.isEmpty()
                    && !trustedPackage.equals(container.getPackageName())) {
                Log.w(TAG, "target package marker does not match payload");
                return null;
            }
            return container.getPackageName();
        }
        if (trustedPackage != null && !trustedPackage.isEmpty()) {
            return trustedPackage;
        }
        ComponentName targetComponent = targetIntent == null ? null : targetIntent.getComponent();
        if (targetComponent != null) {
            return targetComponent.getPackageName();
        }
        return targetIntent == null ? null : targetIntent.getPackage();
    }

    private void finishClickTask() {
        if (isTaskRoot()) {
            finishAndRemoveTask();
        } else {
            finish();
        }
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
