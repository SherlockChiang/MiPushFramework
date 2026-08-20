package com.nihility.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.os.Build;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import com.nihility.Global;
import com.xiaomi.xmsf.R;

public class ForegroundHelper {
    public static final String CHANNEL_STATUS = "status";
    public static final int NOTIFICATION_ALIVE_ID = 1;
    private final Service service;

    public ForegroundHelper(Service service) {
        this.service = service;
    }

    public void startForeground() {
        createNotificationGroupForPushStatus();
        // A service reached through startForegroundService() must call
        // startForeground() even when the user does not want a persistent status
        // notification. Promote first to satisfy Android's five-second contract,
        // then leave foreground state immediately for the non-persistent mode.
        showForegroundNotificationToKeepAlive();
        if (!Global.ConfigCenter().isStartForegroundService()) {
            stopForegroundNotification();
        }
    }

    public void stopForegroundNotification() {
        try {
            ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE);
        } catch (Throwable ignored) {
        }
    }

    void showForegroundNotificationToKeepAlive() {
        Notification notification = new NotificationCompat.Builder(service,
                CHANNEL_STATUS)
                .setContentTitle(service.getString(R.string.notification_alive))
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(true)
                .build();

        try {
            int foregroundServiceType = 0;
            if (Build.VERSION.SDK_INT >= 34) {
                // ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 0x40000000 (1 << 30)
                foregroundServiceType = 0x40000000;
            }
            ServiceCompat.startForeground(service, NOTIFICATION_ALIVE_ID, notification, foregroundServiceType);
        } catch (Throwable e) {
            // Catches android.app.ForegroundServiceStartNotAllowedException on API 31+
            // and SecurityException / IllegalStateException
        }
    }

    void createNotificationGroupForPushStatus() {
        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(service.getApplicationContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String groupId = "status_group";
                NotificationChannelGroupCompat.Builder group =
                        new NotificationChannelGroupCompat.Builder(groupId)
                                .setName(CHANNEL_STATUS);
                manager.createNotificationChannelGroup(group.build());

                NotificationChannelCompat.Builder channel = new NotificationChannelCompat.Builder(
                        CHANNEL_STATUS, NotificationManager.IMPORTANCE_MIN)
                        .setName(service.getString(R.string.notification_category_alive)).setGroup(groupId);
                manager.createNotificationChannel(channel.build());
            }
        } catch (Throwable ignored) {
        }
    }
}
