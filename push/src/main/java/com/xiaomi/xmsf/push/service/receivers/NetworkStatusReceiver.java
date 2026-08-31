package com.xiaomi.xmsf.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.mipush.sdk.PushServiceClient;
import com.xiaomi.smack.util.TrafficUtils;
import com.xiaomi.xmsf.push.control.PushControllerUtils;
import com.xiaomi.xmsf.push.control.PushServiceDispatcher;

import java.util.concurrent.atomic.AtomicLong;

public class NetworkStatusReceiver extends BroadcastReceiver {
    private static final String ACTION_NETWORK_STATUS_CHANGED =
            "com.xiaomi.push.network_status_changed";
    static final long MIN_RECOVERY_INTERVAL_MS = 60_000L;
    /** Registration refresh is useful after reconnect, but connectivity broadcasts can burst. */
    static final long MIN_REGISTRATION_PROCESS_INTERVAL_MS = 5 * 60 * 1000L;
    static final long NO_RECOVERY_ATTEMPT = Long.MIN_VALUE;

    private static final AtomicLong LAST_RECOVERY_ELAPSED_REALTIME =
            new AtomicLong(NO_RECOVERY_ATTEMPT);
    private static final AtomicLong LAST_REGISTRATION_PROCESS_ELAPSED_REALTIME =
            new AtomicLong(NO_RECOVERY_ATTEMPT);

    public void onReceive(Context context, Intent intent) {
        if (context == null || !PushControllerUtils.isPrefsEnable(context)) {
            return;
        }

        boolean hasNetwork = false;
        try {
            // CONNECTIVITY_CHANGE can be noisy on vendor ROMs. Resolve the
            // state once so recovery and registration make the same decision.
            hasNetwork = Network.hasNetwork(context);
        } catch (Throwable ignored) {
        }

        try {
            TrafficUtils.notifyNetworkChanage(context);
        } catch (Throwable ignored) {
        }

        if (hasNetwork && !PushControllerUtils.isPushServiceRunning()
                && claimRecovery(SystemClock.elapsedRealtime())) {
            PushServiceDispatcher.dispatchStart(
                    context, ACTION_NETWORK_STATUS_CHANGED, false);
        }

        try {
            if (hasNetwork) {
                PushServiceClient client = PushServiceClient.getInstance(context);
                if (client.isProvisioned()
                        && claimRegistrationProcessing(SystemClock.elapsedRealtime())) {
                    client.processRegisterTask();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean claimRegistrationProcessing(long nowElapsedRealtime) {
        while (true) {
            long previous = LAST_REGISTRATION_PROCESS_ELAPSED_REALTIME.get();
            if (!shouldProcessRegistration(true, previous, nowElapsedRealtime)) {
                return false;
            }
            if (LAST_REGISTRATION_PROCESS_ELAPSED_REALTIME.compareAndSet(previous,
                    nowElapsedRealtime)) {
                return true;
            }
        }
    }

    static boolean shouldProcessRegistration(
            boolean hasNetwork, long previousElapsedRealtime, long nowElapsedRealtime) {
        if (!hasNetwork) {
            return false;
        }
        if (previousElapsedRealtime == NO_RECOVERY_ATTEMPT) {
            return true;
        }
        long elapsed = nowElapsedRealtime - previousElapsedRealtime;
        return elapsed < 0L || elapsed >= MIN_REGISTRATION_PROCESS_INTERVAL_MS;
    }

    private static boolean claimRecovery(long nowElapsedRealtime) {
        while (true) {
            long previous = LAST_RECOVERY_ELAPSED_REALTIME.get();
            if (!shouldAttemptRecovery(true, false, previous, nowElapsedRealtime)) {
                return false;
            }
            if (LAST_RECOVERY_ELAPSED_REALTIME.compareAndSet(previous, nowElapsedRealtime)) {
                return true;
            }
        }
    }

    static boolean shouldAttemptRecovery(
            boolean hasNetwork,
            boolean serviceRunning,
            long previousElapsedRealtime,
            long nowElapsedRealtime) {
        if (!hasNetwork || serviceRunning) {
            return false;
        }
        if (previousElapsedRealtime == NO_RECOVERY_ATTEMPT) {
            return true;
        }
        long elapsed = nowElapsedRealtime - previousElapsedRealtime;
        return elapsed < 0L || elapsed >= MIN_RECOVERY_INTERVAL_MS;
    }
}
