package com.xiaomi.xmsf.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.xmsf.push.control.PushControllerUtils;



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
        try {
            long nowElapsedRealtime = SystemClock.elapsedRealtime();

            if (!shouldStart(lastActiveElapsedRealtime, nowElapsedRealtime)) {
                return;
            }

            lastActiveElapsedRealtime = nowElapsedRealtime;
            long now = System.currentTimeMillis();

            logger.d("start service when " + intent.getAction());
            Intent localIntent = new Intent(context, com.xiaomi.push.service.XMPushService.class);
            localIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, now);
            localIntent.setAction(PushServiceConstants.ACTION_CHECK_ALIVE);
            if (!shouldUseForegroundStart(PushControllerUtils.isPushServiceRunning())) {
                // The existing foreground service can receive a normal start command. Avoid
                // asking Android to promote it again on every screen-on recovery check.
                context.startService(localIntent);
            } else {
                ContextCompat.startForegroundService(context, localIntent);
            }
        } catch (Exception localException) {
            MyLog.e(localException);
        }
    }

    static boolean shouldStart(long lastElapsedRealtime, long nowElapsedRealtime) {
        return lastElapsedRealtime == 0L
                || nowElapsedRealtime - lastElapsedRealtime >= MIN_START_INTERVAL_MS;
    }

    static boolean shouldUseForegroundStart(boolean serviceRunning) {
        return !serviceRunning;
    }
}
