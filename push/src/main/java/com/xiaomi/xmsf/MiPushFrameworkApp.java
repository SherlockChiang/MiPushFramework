package com.xiaomi.xmsf;

import static com.xiaomi.xmsf.push.control.PushControllerUtils.isAppMainProc;
import static com.xiaomi.xmsf.push.notification.NotificationController.CHANNEL_WARN;
import static top.trumeet.common.Constants.TAG_CONDOM;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.elvishew.xlog.XLog;
import com.nihility.Global;
import com.nihility.notification.NotificationManagerEx;
import com.nihility.utils.Hooker;
import com.nihility.utils.PrivilegeElevator;
import com.oasisfeng.condom.CondomOptions;
import com.oasisfeng.condom.CondomProcess;
import com.xiaomi.xmsf.push.control.PushControllerUtils;
import com.xiaomi.xmsf.push.control.StartupWorkPolicy;
import com.xiaomi.xmsf.push.control.XMOutbound;
import com.xiaomi.xmsf.push.notification.NotificationController;
import com.xiaomi.xmsf.push.service.MiuiPushActivateService;
import com.xiaomi.xmsf.utils.LogUtils;

import top.trumeet.common.Constants;
import top.trumeet.common.push.PushServiceAccessibility;
import top.trumeet.common.utils.Utils;
import top.trumeet.mipush.provider.DatabaseUtils;
import top.trumeet.mipushframework.component.AppIconKt;


public class MiPushFrameworkApp extends Application {
    private com.elvishew.xlog.Logger logger;

    private static final String MIPUSH_EXTRA = "mipush_extra";


    @Override
    public void attachBaseContext(Context context) {
        super.attachBaseContext(context);
        DatabaseUtils.init(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        PrivilegeElevator.tryToElevate();

        Utils.setApplicationContext(this);
        initBasicLogger();
        CrashHandler.installCrashLogger();

        Hooker.setLogger(PushControllerUtils.wrapContext(this));
        Hooker.hook(this);

        NotificationManagerEx.init(getApplicationContext());

        installCondom();

        // Follow the master switch so opening a disabled installation does not wake
        // scanners or post keep-alive prompts.
        if (StartupWorkPolicy.shouldRunAppStartup(
                isAppMainProc(this),
                PushControllerUtils.isPrefsEnable(this))) {
            awakePushActivateService(PushControllerUtils.wrapContext(this));
            requestDozeWhiteList();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // All caches are accelerators. Apply the same pressure signal to the
        // notification and settings layers without cancelling active delivery.
        try {
            Global.IconCache().trimMemory(level);
        } catch (Throwable error) {
            logCacheFailure("Unable to trim shared icon cache", error);
        }
        try {
            Global.ApplicationNameCache().trimMemory(level);
        } catch (Throwable error) {
            logCacheFailure("Unable to trim application-name cache", error);
        }
        try {
            NotificationController.trimMemory(level);
        } catch (Throwable error) {
            logCacheFailure("Unable to trim focus-notification image cache", error);
        }
        try {
            AppIconKt.trimIconCache(level);
        } catch (Throwable error) {
            logCacheFailure("Unable to trim settings UI icon cache", error);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        try {
            Global.IconCache().clearMemory();
            Global.ApplicationNameCache().clearMemory();
            NotificationController.trimMemory(
                    android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL);
            AppIconKt.clearIconCache();
        } catch (Throwable error) {
            logCacheFailure("Unable to clear process caches", error);
        }
    }

    private void logCacheFailure(String message, Throwable error) {
        if (logger != null) {
            logger.w(message, error);
        }
    }

    private void requestDozeWhiteList() {
        try {
            if (!PushServiceAccessibility.isInDozeWhiteList(this)) {
                NotificationManagerCompat manager = NotificationManagerCompat.from(this);
                notifyDozeWhiteListRequest(manager);
            }
        } catch (RuntimeException e) {
            logger.e(e.getMessage(), e);
        }
    }

    private void awakePushActivateService(Context context) {
        long nowElapsed = SystemClock.elapsedRealtime();
        long previousElapsed = getLastStartupElapsed();
        int fiveMinutesMs = 300_000;
        if (StartupWorkPolicy.shouldRunThrottled(
                previousElapsed, nowElapsed, fiveMinutesMs)) {
            setStartupElapsed(nowElapsed);
            MiuiPushActivateService.awakePushActivateService(
                    context, "com.xiaomi.xmsf.push.SCAN");
        }
    }

    private void installCondom() {
        CondomOptions options = XMOutbound.create(this, TAG_CONDOM + "_PROCESS",
                false);
        CondomProcess.installExceptDefaultProcess(this, options);
    }


    private void initBasicLogger() {
        LogUtils.init(this);
        logger = XLog.tag(MiPushFrameworkApp.class.getSimpleName()).build();
        logger.i("App starts: " + BuildConfig.VERSION_NAME);
    }

    private void notifyDozeWhiteListRequest(NotificationManagerCompat manager) {
        createWarnChannel(manager);

        Intent removeDozeActivityIntent = new Intent().setComponent(
                new ComponentName(getPackageName(), RemoveDozeActivity.class.getName()));
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                removeDozeActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_WARN)
                .setContentInfo(getString(R.string.wizard_title_doze_whitelist))
                .setContentTitle(getString(R.string.wizard_title_doze_whitelist))
                .setContentText(getString(R.string.wizard_descr_doze_whitelist))
                .setTicker(getString(R.string.wizard_descr_doze_whitelist))
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .setShowWhen(true)
                .setAutoCancel(true)
                .build();
        manager.notify(getClass().getSimpleName(), 100, notification);  // Use tag to avoid conflict with push notifications.
    }

    private void createWarnChannel(NotificationManagerCompat manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelCompat.Builder channel = new NotificationChannelCompat
                .Builder(CHANNEL_WARN, NotificationManager.IMPORTANCE_HIGH)
                .setName(getString(R.string.wizard_title_doze_whitelist));

            NotificationChannelGroupCompat notificationChannelGroup =
                new NotificationChannelGroupCompat.Builder(CHANNEL_WARN).setName(CHANNEL_WARN).build();
            manager.createNotificationChannelGroup(notificationChannelGroup);
            channel.setGroup(notificationChannelGroup.getId());
            manager.createNotificationChannel(channel.build());
        }
    }


    private long getLastStartupElapsed() {
        return getDefaultPreferences().getLong("xmsf_startup_elapsed", 0);
    }

    private void setStartupElapsed(long elapsedRealtime) {
        getDefaultPreferences().edit()
                .putLong("xmsf_startup_elapsed", elapsedRealtime)
                .remove("xmsf_startup")
                .apply();
    }

    private SharedPreferences getDefaultPreferences() {
        return getSharedPreferences(MIPUSH_EXTRA, 0);
    }

}
