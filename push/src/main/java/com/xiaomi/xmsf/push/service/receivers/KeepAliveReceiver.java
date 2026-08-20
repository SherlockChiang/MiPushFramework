package com.xiaomi.xmsf.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.xmsf.push.control.PushControllerUtils;
import com.xiaomi.xmsf.push.control.PushServiceDispatcher;

/**
 * @author zts
 */
public class KeepAliveReceiver extends BroadcastReceiver {
    static final long MIN_START_INTERVAL_MS = 2 * 60 * 1000L;
    private final Logger logger = XLog.tag(KeepAliveReceiver.class.getSimpleName()).build();

    /* Zero means no recovery attempt has been made yet; the first valid screen-on is allowed. */
    private long lastActiveElapsedRealtime;

    public KeepAliveReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            return;
        }
        if (!PushControllerUtils.isRegistrationRetryEnabled()) {
            return;
        }
        // A live transport does not need a second start command on every screen
        // wake. Avoid needless binder/service churn on HyperOS and third-party ROMs.
        if (!shouldAttemptRecoveryForServiceState(
                PushControllerUtils.isPushServiceRunning())) {
            return;
        }
        try {
            long nowElapsedRealtime = SystemClock.elapsedRealtime();

            if (!shouldStart(lastActiveElapsedRealtime, nowElapsedRealtime)) {
                return;
            }

            lastActiveElapsedRealtime = nowElapsedRealtime;
            logger.d("start service when " + intent.getAction());
            PushServiceDispatcher.dispatchStart(
                    context, PushServiceConstants.ACTION_CHECK_ALIVE, false);
        } catch (Exception localException) {
            MyLog.e(localException);
        }
    }

    static boolean shouldStart(long lastElapsedRealtime, long nowElapsedRealtime) {
        return lastElapsedRealtime == 0L
                || nowElapsedRealtime - lastElapsedRealtime >= MIN_START_INTERVAL_MS;
    }

    static boolean shouldAttemptRecoveryForServiceState(boolean serviceRunning) {
        return !serviceRunning;
    }
}
