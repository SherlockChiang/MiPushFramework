package com.xiaomi.xmsf.push.notification;

import static top.trumeet.common.utils.NotificationAlertUtils.NOTIFY_TYPE_LIGHTS;
import static top.trumeet.common.utils.NotificationAlertUtils.NOTIFY_TYPE_SOUND;
import static top.trumeet.common.utils.NotificationAlertUtils.NOTIFY_TYPE_VIBRATE;
import static top.trumeet.common.utils.NotificationAlertUtils.usesPackageResourceSound;
import static top.trumeet.common.utils.NotificationUtils.getChannelIdByPkg;
import static top.trumeet.common.utils.NotificationUtils.getGroupIdByPkg;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.nihility.Global;
import com.nihility.XMPushUtils;
import com.nihility.notification.NotificationManagerEx;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmsf.R;

import java.util.Arrays;

import top.trumeet.common.utils.CustomConfiguration;

public class NotificationChannelManager {

    /** Dedicated channel for the settings-page replay actions. */
    public static final String DEBUG_CHANNEL_ID = "mipush_debug_test_v2";
    /** Channel suffix for portable focus notifications that should heads-up. */
    private static final String PORTABLE_FOCUS_CHANNEL_SUFFIX = "_mipush_focus_v1";
    static final int PORTABLE_FOCUS_CHANNEL_IMPORTANCE =
            NotificationManager.IMPORTANCE_HIGH;

    public static NotificationManagerEx getNotificationManagerEx() {
        return NotificationManagerEx.INSTANCE;
    }

    @TargetApi(26)
    private static NotificationChannelGroup createGroupWithPackage(@NonNull String packageName,
                                                                   @NonNull CharSequence appName) {
        return new NotificationChannelGroup(getGroupIdByPkg(packageName), appName);
    }

    private static NotificationChannel createChannelWithPackage(@NonNull PushMetaInfo metaInfo,
                                                                @NonNull String packageName) {
        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        String channelName = configuration.channelName("未分类");
        String channelDescription = configuration.channelDescription(null);
        String sound = configuration.soundUri(null);

        NotificationChannel channel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            int importance = configuration.channelImportance(
                    NotificationManager.IMPORTANCE_DEFAULT);
            int notifyType = metaInfo.getNotifyType();
            channel = new NotificationChannel(
                    getChannelId(metaInfo, packageName), channelName, importance);
            channel.setDescription(channelDescription);
            channel.enableVibration(
                    (notifyType & NOTIFY_TYPE_VIBRATE) != 0);
            channel.enableLights(
                    (notifyType & NOTIFY_TYPE_LIGHTS) != 0);
            if ((notifyType & NOTIFY_TYPE_SOUND) == 0) {
                channel.setSound(null, null);
            } else if (usesPackageResourceSound(notifyType, sound, packageName)) {
                AudioAttributes attr = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(Uri.parse(sound), attr);
            }
        }
        return channel;
    }

    public static String getChannelId(@NonNull PushMetaInfo metaInfo,
                                      @NonNull String packageName) {
        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        return getChannelIdByPkg(packageName) + "_" + configuration.channelId("");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static boolean isNotificationChannelEnabled(@Nullable NotificationChannel channel){
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    public static boolean isNotificationChannelEnabled(@NonNull String packageName, @Nullable String channelId){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(!TextUtils.isEmpty(channelId)) {
                NotificationChannel channel =
                        NotificationManagerEx.INSTANCE.getNotificationChannel(packageName, channelId);
                return isNotificationChannelEnabled(channel);
            }
            return false;
        } else {
            return NotificationManagerEx.INSTANCE.areNotificationsEnabled(packageName);
        }
    }

    public static NotificationChannel registerChannelIfNeeded(Context context, PushMetaInfo metaInfo, String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }

        CharSequence appName = Global.ApplicationNameCache().getAppName(context, packageName);
        if (appName == null) {
            return null;
        }

        return createNotificationChannel(metaInfo, packageName, appName);

    }

    /**
     * Manual replay must remain observable even when a user's normal/default
     * channel was muted by an earlier configuration. Keep it isolated from
     * client channels and request a heads-up-capable importance once.
     */
    public static void registerDebugChannelIfNeeded(Context context, String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel existing =
                getNotificationManagerEx().getNotificationChannel(packageName, DEBUG_CHANNEL_ID);
        if (existing != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                DEBUG_CHANNEL_ID, "MiPush Framework test", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Manual notification replay");
        channel.enableVibration(true);
        channel.enableLights(true);
        getNotificationManagerEx().createNotificationChannels(
                packageName, Arrays.asList(channel));
    }

    /**
     * Create the user-visible high-importance channel used by the portable
     * three-stage focus renderer. A dedicated channel keeps ordinary message
     * channel preferences intact while giving focus updates a heads-up-capable
     * default on AOSP and other non-Xiaomi SystemUI implementations.
     */
    @Nullable
    public static NotificationChannel ensurePortableFocusChannel(
            Context context, String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context == null
                || TextUtils.isEmpty(packageName)) {
            return null;
        }

        try {
            String channelId = getPortableFocusChannelId(packageName);
            NotificationChannel existing =
                    getNotificationManagerEx().getNotificationChannel(packageName, channelId);
            if (existing != null) {
                return existing;
            }

            CharSequence appName = null;
            try {
                appName = Global.ApplicationNameCache().getAppName(context, packageName);
            } catch (Throwable ignored) {
            }
            if (TextUtils.isEmpty(appName)) {
                appName = packageName;
            }

            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    context.getString(R.string.notification_focus_channel_name),
                    PORTABLE_FOCUS_CHANNEL_IMPORTANCE);
            channel.setDescription(
                    context.getString(R.string.notification_focus_channel_description));
            channel.enableVibration(true);
            channel.enableLights(true);

            try {
                NotificationChannelGroup group = createGroupWithPackage(packageName, appName);
                getNotificationManagerEx().createNotificationChannelGroups(
                        packageName, Arrays.asList(group));
                channel.setGroup(group.getId());
            } catch (Throwable ignored) {
                // A group is cosmetic. Channel creation remains authoritative.
            }

            getNotificationManagerEx().createNotificationChannels(
                    packageName, Arrays.asList(channel));
            return getNotificationManagerEx().getNotificationChannel(packageName, channelId);
        } catch (Throwable ignored) {
            // The caller preserves its original channel when package-attributed
            // channel creation is unavailable on this ROM.
            return null;
        }
    }

    public static String getPortableFocusChannelId(String packageName) {
        return getChannelIdByPkg(packageName) + PORTABLE_FOCUS_CHANNEL_SUFFIX;
    }

    private static NotificationChannel createNotificationChannel(PushMetaInfo metaInfo, String packageName, CharSequence appName) {
        NotificationChannelGroup notificationChannelGroup = createGroupWithPackage(packageName, appName);
        getNotificationManagerEx().createNotificationChannelGroups(
                packageName, Arrays.asList(notificationChannelGroup));

        NotificationChannel notificationChannel = createChannelWithPackage(metaInfo, packageName);
        if (notificationChannel == null) {
            return null;
        }
        notificationChannel.setGroup(notificationChannelGroup.getId());

        getNotificationManagerEx().createNotificationChannels(
                packageName, Arrays.asList(notificationChannel));
        return notificationChannel;
    }

}
