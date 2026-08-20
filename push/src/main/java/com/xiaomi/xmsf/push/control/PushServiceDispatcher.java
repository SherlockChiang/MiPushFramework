package com.xiaomi.xmsf.push.control;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.nihility.Global;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.xmsf.utils.ConfigCenter;

import java.util.List;

public class PushServiceDispatcher {
    private static final Logger logger = XLog.tag(PushServiceDispatcher.class.getSimpleName()).build();

    public static PushServiceStartPolicy.Action dispatchStart(Context context, boolean userInitiated) {
        return dispatchIntent(context, null, userInitiated);
    }

    /**
     * Dispatch a recovery/start request while preserving the SDK's action
     * contract (for example network-status and check-alive are not timers).
     */
    public static PushServiceStartPolicy.Action dispatchStart(
            Context context, String action, boolean userInitiated) {
        Intent sourceIntent = new Intent();
        sourceIntent.setAction(action);
        return dispatchIntent(context, sourceIntent, userInitiated);
    }

    /**
     * Start the transport while preserving the SDK action and all extras. This
     * is the single gate used by recovery receivers and the bridge service.
     */
    public static PushServiceStartPolicy.Action dispatchIntent(
            Context context, Intent sourceIntent, boolean userInitiated) {
        if (context == null) {
            return PushServiceStartPolicy.Action.SKIP;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        boolean masterEnabled = PushControllerUtils.isPrefsEnable(appContext);
        boolean serviceRunning = PushControllerUtils.isPushServiceRunning();
        boolean persistentForeground = isPersistentForegroundEnabled(
                appContext, Global.ConfigCenter());
        boolean platformAllowed = isPlatformStartAllowed(appContext);

        PushServiceStartPolicy.Action action = PushServiceStartPolicy.evaluate(
                masterEnabled,
                serviceRunning,
                userInitiated,
                persistentForeground,
                platformAllowed
        );

        logger.i("PushServiceDispatcher evaluated action: " + action + " (master=" + masterEnabled
                + ", running=" + serviceRunning + ", userInit=" + userInitiated
                + ", fgsPref=" + persistentForeground + ", platformAllowed=" + platformAllowed + ")");

        switch (action) {
            case START_FOREGROUND:
                startForegroundServiceSafely(appContext, sourceIntent);
                break;
            case START_SERVICE:
                startServiceSafely(appContext, sourceIntent);
                break;
            case SKIP:
            default:
                break;
        }
        return action;
    }

    static boolean isPersistentForegroundEnabled(Context context, ConfigCenter configCenter) {
        return configCenter.isStartForegroundService(context);
    }

    private static boolean isPlatformStartAllowed(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return true;
            }
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs != null) {
                int pid = Process.myPid();
                for (ActivityManager.RunningAppProcessInfo info : procs) {
                    if (info.pid == pid) {
                        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
                    }
                }
            }
        } catch (Throwable e) {
            logger.w("Unable to determine process importance", e);
        }
        return false;
    }

    static Intent createPushServiceIntent(Context context, Intent sourceIntent) {
        Intent intent = sourceIntent == null
                ? new Intent(PushServiceConstants.ACTION_TIMER)
                : new Intent(sourceIntent);
        intent.setComponent(new ComponentName(context, com.xiaomi.push.service.XMPushService.class));
        if (TextUtils.isEmpty(intent.getAction())) {
            intent.setAction(PushServiceConstants.ACTION_TIMER);
        }
        if (!intent.hasExtra(PushServiceConstants.EXTRA_TIME_STAMP)) {
            intent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, System.currentTimeMillis());
        }
        return intent;
    }

    private static void startForegroundServiceSafely(Context context, Intent sourceIntent) {
        try {
            Intent intent = createPushServiceIntent(context, sourceIntent);
            ContextCompat.startForegroundService(context, intent);
            PushControllerUtils.registerLiveReceiver(context);
        } catch (Throwable e) {
            logger.e("Failed to start XMPushService as foreground", e);
        }
    }

    private static void startServiceSafely(Context context, Intent sourceIntent) {
        try {
            Intent intent = createPushServiceIntent(context, sourceIntent);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startService(intent);
                } catch (Throwable e) {
                    logger.w("startService not allowed in background, falling back safely", e);
                }
            } else {
                context.startService(intent);
            }
            PushControllerUtils.registerLiveReceiver(context);
        } catch (Throwable e) {
            logger.e("Failed to start XMPushService", e);
        }
    }
}
