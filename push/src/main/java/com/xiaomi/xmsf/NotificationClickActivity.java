package com.xiaomi.xmsf;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.xiaomi.push.sdk.MyPushMessageHandler;
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
        XmPushActionContainer container = XMPushUtils.packToContainer(payload);
        Intent targetIntent = getParcelable(clickIntent, EXTRA_TARGET_INTENT);

        try {
            if (container != null && payload != null) {
                // This is the official generic click contract, now executed from
                // a user-initiated Activity instead of a background Service. Send
                // the complete payload through the target SDK first, then open the
                // sender-provided route (or the validated Launcher fallback).
                MyPushMessageHandler.forwardToTargetApplication(this, payload);
                startTargetActivity(targetIntent, clickIntent, container);
            } else {
                // A malformed/stale click must still try the validated target route.
                startTargetActivity(targetIntent, clickIntent, null);
            }
        } catch (Throwable error) {
            Log.w(TAG, "notification click hand-off failed", error);
            try {
                // If a target does not expose the MiPush service, the explicit route
                // or launcher remains a safe user-visible fallback.
                startTargetActivity(targetIntent, clickIntent, container);
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

    private void startTargetActivity(
            @Nullable Intent targetIntent,
            Intent clickIntent,
            @Nullable XmPushActionContainer container) {
        Intent launch = targetIntent == null ? null : new Intent(targetIntent);
        if (launch == null && container != null && container.getPackageName() != null) {
            launch = getPackageManager().getLaunchIntentForPackage(container.getPackageName());
        }
        if (launch == null) {
            return;
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

    @SuppressWarnings("deprecation")
    @Nullable
    private static Intent getParcelable(Intent source, String key) {
        return source.getParcelableExtra(key);
    }
}
