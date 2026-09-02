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
import com.xiaomi.push.sdk.NotificationClickHandoffPolicy;
import com.xiaomi.push.sdk.TargetSdkClickDispatcher;
import com.xiaomi.push.service.MyMIPushNotificationHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;

import com.nihility.XMPushUtils;

import java.util.List;

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
    private boolean waitingForTargetNavigation;
    private boolean sdkDispatchAttempted;
    private boolean clickFinished;
    private boolean notificationCancelled;
    private boolean stopped;
    private long sdkDispatchStartedAt;

    private final Runnable targetVisibilityProbe = new Runnable() {
        @Override
        public void run() {
            if (!waitingForTargetNavigation || clickFinished) {
                return;
            }
            boolean userPresent = isUserPresent();
            boolean targetVisible = userPresent && (isTargetTaskVisible() || stopped);
            boolean timedOut = SystemClock.uptimeMillis() - sdkDispatchStartedAt
                    >= TARGET_VISIBILITY_TIMEOUT_MS;
            NotificationClickHandoffPolicy.Action action =
                    NotificationClickHandoffPolicy.afterNavigationProbe(
                            targetVisible, timedOut, userPresent);
            if (action == NotificationClickHandoffPolicy.Action.FINISH) {
                finishAfterClick(targetVisible ? "TARGET_UI_VISIBLE" : "USER_NOT_PRESENT");
            } else if (action == NotificationClickHandoffPolicy.Action.START_FALLBACK) {
                startFallbackAndFinish("TARGET_UI_TIMEOUT");
            } else {
                mainHandler.postDelayed(this, TARGET_VISIBILITY_POLL_MS);
            }
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
        // An opaque target Activity stops this transparent trampoline. The
        // process-importance probe covers target Activities that only pause it.
        if (waitingForTargetNavigation && !clickFinished && isUserPresent()) {
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
        if (!hasFocus && waitingForTargetNavigation && !clickFinished) {
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

        NotificationClickHandoffPolicy.Action initialAction =
                NotificationClickHandoffPolicy.initialAction(
                        manualReplay, targetActivityPrivate);
        if (initialAction == NotificationClickHandoffPolicy.Action.DISPATCH_SDK_FIRST) {
            dispatchSdkFirst();
        } else {
            startDirectTargetAndFinish();
        }
    }

    private void dispatchSdkFirst() {
        if (sdkDispatchAttempted || clickFinished) {
            return;
        }
        sdkDispatchAttempted = true;
        TargetSdkClickDispatcher.DispatchResult result =
                TargetSdkClickDispatcher.DispatchResult.UNAVAILABLE;

        try {
            if (pendingManualReplay) {
                result = TargetSdkClickDispatcher.dispatchReplay(this, pendingContainer);
            } else if (pendingContainer != null && pendingPayload != null) {
                result = TargetSdkClickDispatcher.dispatchPayload(
                        this, pendingContainer, pendingPayload);
            }
        } catch (Throwable error) {
            Log.w(TAG, "target SDK click delivery failed", error);
            result = TargetSdkClickDispatcher.DispatchResult.FAILED;
        }

        boolean targetVisible = isUserPresent() && (isTargetTaskVisible() || stopped);
        NotificationClickHandoffPolicy.Action action =
                NotificationClickHandoffPolicy.afterSdkDispatch(
                        result.isAccepted(), targetVisible);
        Log.i(TAG, "SDK-first click delivery: " + result + ", next=" + action);
        if (action == NotificationClickHandoffPolicy.Action.FINISH) {
            finishAfterClick("TARGET_ALREADY_VISIBLE");
        } else if (action == NotificationClickHandoffPolicy.Action.START_FALLBACK) {
            startFallbackAndFinish("SDK_DELIVERY_" + result);
        } else {
            cancelClickedNotification();
            sdkDispatchStartedAt = SystemClock.uptimeMillis();
            waitingForTargetNavigation = true;
            mainHandler.post(targetVisibilityProbe);
        }
    }

    private void startDirectTargetAndFinish() {
        if (clickFinished) {
            return;
        }
        try {
            if (pendingClickIntent != null) {
                startTargetActivity(
                        pendingTargetIntent, pendingClickIntent, pendingContainer,
                        pendingTargetActivityPrivate);
            }
        } catch (Throwable error) {
            Log.w(TAG, "direct notification click route failed", error);
        } finally {
            finishAfterClick("DIRECT_TARGET");
        }
    }

    private void startFallbackAndFinish(String reason) {
        if (clickFinished) {
            return;
        }
        waitingForTargetNavigation = false;
        mainHandler.removeCallbacks(targetVisibilityProbe);
        boolean started = false;
        try {
            if (pendingManualReplay) {
                started = startTargetLauncher(pendingTargetPackage);
            } else if (pendingClickIntent != null) {
                started = startTargetActivity(
                        pendingTargetIntent, pendingClickIntent, pendingContainer,
                        pendingTargetActivityPrivate);
            }
            if (!started) {
                started = startTargetLauncher(pendingTargetPackage);
            }
        } catch (Throwable error) {
            Log.w(TAG, "notification click fallback failed", error);
        }
        Log.w(TAG, reason + ": target fallback started=" + started);
        finishAfterClick(reason);
    }

    private void finishAfterClick(String reason) {
        if (clickFinished) {
            return;
        }
        clickFinished = true;
        waitingForTargetNavigation = false;
        mainHandler.removeCallbacks(targetVisibilityProbe);
        Log.i(TAG, "notification click hand-off complete: " + reason);
        cancelClickedNotification();
        finishClickTask();
    }

    private void cancelClickedNotification() {
        if (notificationCancelled) {
            return;
        }
        notificationCancelled = true;
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
            // XMSF commonly has system-level task visibility. Prefer the actual
            // top Activity because a process executing a broadcast receiver is
            // also reported as IMPORTANCE_FOREGROUND even when it has no UI.
            @SuppressWarnings("deprecation")
            List<ActivityManager.RunningTaskInfo> tasks =
                    activityManager.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty()) {
                ComponentName topActivity = tasks.get(0).topActivity;
                if (topActivity != null
                        && pendingTargetPackage.equals(topActivity.getPackageName())) {
                    return true;
                }
            }
            int importance = ActivityManagerOverride.getPackageImportance(
                    pendingTargetPackage, activityManager);
            // VISIBLE is useful for translucent target Activities. Do not use
            // FOREGROUND here: that may only be the SDK receiver processing the
            // payload and would suppress the bounded launcher fallback.
            return importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
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
        String targetPackage = MyMIPushNotificationHelper
                .getNotificationTargetPackage(container);
        if (container != null && targetPackage != null && !targetPackage.isEmpty()) {
            if (trustedPackage != null && !trustedPackage.isEmpty()
                    && !trustedPackage.equals(targetPackage)) {
                Log.w(TAG, "target package marker does not match payload");
                return null;
            }
            return targetPackage;
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
        // A plain finish keeps the target application's task untouched if its
        // SDK reparented a bridge Activity during this user-initiated hand-off.
        finish();
    }

    private boolean startTargetActivity(
            @Nullable Intent targetIntent,
            Intent clickIntent,
            @Nullable XmPushActionContainer container,
            boolean targetActivityPrivate) {
        Intent launch = targetActivityPrivate
                ? resolveExportedFallback(targetIntent, container)
                : (targetIntent == null ? null : new Intent(targetIntent));
        if (launch == null && container != null) {
            String targetPackage = MyMIPushNotificationHelper
                    .getNotificationTargetPackage(container);
            if (targetPackage != null && !targetPackage.isEmpty()) {
                launch = getPackageManager().getLaunchIntentForPackage(targetPackage);
            }
        }
        if (launch == null) {
            return false;
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
            return true;
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

        String targetPackage = MyMIPushNotificationHelper
                .getNotificationTargetPackage(container);
        ComponentName explicit = targetIntent.getComponent();
        if ((targetPackage == null || targetPackage.isEmpty()) && explicit != null) {
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
