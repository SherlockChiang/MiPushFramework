package com.xiaomi.xmsf.push.control;

import static top.trumeet.common.Constants.TAG_CONDOM;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.oasisfeng.condom.CondomContext;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.xmsf.FirstRegister;
import com.xiaomi.xmsf.RetryRegister;
import com.xiaomi.xmsf.push.service.receivers.BootReceiver;
import com.xiaomi.xmsf.push.service.receivers.KeepAliveReceiver;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import top.trumeet.common.Constants;

/**
 * Created by Trumeet on 2017/8/25.
 *
 * @author Trumeet
 */

@SuppressLint("WrongConstant")
public class PushControllerUtils {
    private static Logger logger = XLog.tag(PushControllerUtils.class.getSimpleName()).build();

    private static final Object LIVE_RECEIVER_LOCK = new Object();
    private static BroadcastReceiver liveReceiver;
    private static Context liveReceiverContext;
    private static final AtomicBoolean PUSH_SERVICE_RUNNING = new AtomicBoolean();
    private static final RegistrationRetryCoordinator REGISTRATION_RETRIES =
            new RegistrationRetryCoordinator(new RegistrationRetryCoordinator.Scheduler() {
                private Handler handler;

                private synchronized Handler handler() {
                    if (handler == null) {
                        handler = new Handler(Looper.getMainLooper());
                    }
                    return handler;
                }

                @Override
                public boolean postDelayed(Runnable task, long delayMs) {
                    return handler().postDelayed(task, delayMs);
                }

                @Override
                public void removeCallbacks(Runnable task) {
                    handler().removeCallbacks(task);
                }
            });

    private static final int[] RetryInterval = {3600000, 7200000, 14400000, 28800000, 86400000};

    public static void registerPush(Context context, int i) {
        scheduleRegistrationRetry(context, i, null);
    }

    public static void registerPush(Context context, int i, long generation) {
        scheduleRegistrationRetry(context, i, generation);
    }

    private static void scheduleRegistrationRetry(Context context, int i,
                                                  Long expectedGeneration) {
        Objects.requireNonNull(context);
        int[] retryInterval = RetryInterval;
        int length = retryInterval.length;
        int retryIndex = Math.max(0, i);
        long intervalMs = retryIndex < length
                ? retryInterval[retryIndex] : retryInterval[length - 1];
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }
        Context retryContext = applicationContext;
        boolean scheduled = expectedGeneration == null
                ? REGISTRATION_RETRIES.schedule(intervalMs,
                    generation -> new RetryRegister(retryContext, retryIndex, generation))
                : REGISTRATION_RETRIES.schedule(intervalMs, expectedGeneration,
                    generation -> new RetryRegister(retryContext, retryIndex, generation));
        if (scheduled) {
            MyLog.i("for make sure xmsf register push succ, schedule register after "
                    + intervalMs / 1000 + " sec");
        } else {
            MyLog.i("registration retry already pending or disabled, skip duplicate schedule");
        }
    }

    public static boolean beginRegistrationRetry(RetryRegister retry, long generation) {
        return REGISTRATION_RETRIES.begin(retry, generation);
    }

    public static boolean runRegistrationRetryIfActive(long generation, Runnable action) {
        return REGISTRATION_RETRIES.runIfActive(generation, action);
    }

    public static boolean runInitialRegistrationIfEnabled(Runnable action) {
        return REGISTRATION_RETRIES.runIfEnabled(action);
    }

    public static void cancelRegistrationRetry() {
        REGISTRATION_RETRIES.cancelPending();
    }

    public static boolean isRegistrationRetryEnabled() {
        return REGISTRATION_RETRIES.isEnabled();
    }

    public static boolean pushRegistered(final Context context) {
        return !TextUtils.isEmpty(MiPushClient.getRegId(context));
    }

    private static SharedPreferences getPrefs(Context context) {
        Context applicationContext = context.getApplicationContext();
        return PreferenceManager.getDefaultSharedPreferences(
                applicationContext == null ? context : applicationContext);
    }

    /**
     * Get is user enable push in settings.
     *
     * @param context Context param
     * @return is enable
     * @see Constants#KEY_ENABLE_PUSH
     */
    public static boolean isPrefsEnable(Context context) {
        return getPrefs(context)
                .getBoolean(Constants.KEY_ENABLE_PUSH, true);
    }

    /**
     * Set push enable
     *
     * @param value   is enable
     * @param context Context param
     * @see Constants#KEY_ENABLE_PUSH
     */
    public static void setPrefsEnable(boolean value, Context context) {
        getPrefs(context)
                .edit()
                .putBoolean(Constants.KEY_ENABLE_PUSH, value)
                .apply();
    }

    /**
     * Check is in main app processMIPushMessage
     *
     * @param context Context param
     * @return is in main processMIPushMessage
     */
    public static boolean isAppMainProc(Context context) {
        try {
            ActivityManager activityManager = (ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                return false;
            }
            List<ActivityManager.RunningAppProcessInfo> processes =
                    activityManager.getRunningAppProcesses();
            if (processes == null) {
                return false;
            }
            for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo
                    : processes) {
                if (runningAppProcessInfo != null
                        && runningAppProcessInfo.pid == Process.myPid()
                        && TextUtils.equals(runningAppProcessInfo.processName,
                        context.getPackageName())) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            logger.w("Unable to inspect application process", e);
        }
        return false;
    }

    /**
     * Set XMPush sdk enable
     *
     * @param enable  enable
     * @param context context param
     */
    public static void setServiceEnable(boolean enable, Context context) {
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }
        context = applicationContext;
        if (enable) {
            REGISTRATION_RETRIES.enable();
            logger.d("Starting...");


            if (isAppMainProc(context)) {
                ScheduledJobManager.getInstance(wrapContext(context))
                        .addOneShootJob(new FirstRegister(wrapContext(context)));
            }

            try {
                Intent serviceIntent = new Intent(context,
                        com.xiaomi.push.service.XMPushService.class);
                serviceIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP,
                        System.currentTimeMillis());
                serviceIntent.setAction(PushServiceConstants.ACTION_TIMER);
                ContextCompat.startForegroundService(context, serviceIntent);
            } catch (Throwable e) {
                logger.e(e);
            }

            registerLiveReceiver(context);

        } else {
            REGISTRATION_RETRIES.disable();
            logger.d("Stopping...");

            ScheduledJobManager.getInstance(wrapContext(context))
                    .cancelJob(FirstRegister.JOB_ID);

            unregisterLiveReceiver();

            MiPushClient.unregisterPush(wrapContext(context));
            // Force stop and disable services.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                scheduler.cancelAll();
            }
            context.stopService(new Intent(context, com.xiaomi.push.service.XMPushService.class));
        }
    }

    /**
     * Set SP and XMPush enable
     *
     * @param enable  is enable
     * @param context Context param
     */
    public static void setAllEnable(boolean enable, Context context) {
        setPrefsEnable(enable, context);
        setServiceEnable(enable, context);
        setBootReceiverEnable(enable, context);
    }


    @SuppressLint("WrongConstant")
    private static void setBootReceiverEnable(boolean enable, Context context) {
        context.getPackageManager()
                .setComponentEnabledSetting(new ComponentName(context,
                                BootReceiver.class),
                        enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
    }

    public static Context wrapContext(final Context context) {
        return CondomContext.wrap(context, TAG_CONDOM, XMOutbound.create(context,
                TAG_CONDOM));
    }

    /** Returns lifecycle state reported by the in-process push service hook. */
    public static boolean isPushServiceRunning() {
        return PUSH_SERVICE_RUNNING.get();
    }

    public static void onPushServiceCreated() {
        PUSH_SERVICE_RUNNING.set(true);
    }

    public static void onPushServiceDestroyed() {
        PUSH_SERVICE_RUNNING.set(false);
    }

    static void registerLiveReceiver(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }
        synchronized (LIVE_RECEIVER_LOCK) {
            if (liveReceiverContext != null) {
                return;
            }
            BroadcastReceiver receiver = new KeepAliveReceiver();
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            try {
                applicationContext.registerReceiver(receiver, filter);
                liveReceiver = receiver;
                liveReceiverContext = applicationContext;
            } catch (Throwable e) {
                logger.e("Unable to register screen-on recovery receiver", e);
            }
        }
    }

    static void unregisterLiveReceiver() {
        synchronized (LIVE_RECEIVER_LOCK) {
            if (liveReceiverContext == null || liveReceiver == null) {
                return;
            }
            try {
                liveReceiverContext.unregisterReceiver(liveReceiver);
            } catch (Throwable e) {
                logger.e("Unable to unregister screen-on recovery receiver", e);
            } finally {
                liveReceiver = null;
                liveReceiverContext = null;
            }
        }
    }
}
