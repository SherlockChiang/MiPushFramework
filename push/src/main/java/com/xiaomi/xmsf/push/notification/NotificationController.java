package com.xiaomi.xmsf.push.notification;

import static com.xiaomi.push.service.MyMIPushNotificationHelper.getNotificationTag;
import static com.xiaomi.push.service.MyNotificationIconHelper.KiB;
import static top.trumeet.common.utils.NotificationAlertUtils.NOTIFY_TYPE_SOUND;
import static top.trumeet.common.utils.NotificationAlertUtils.usesPackageResourceSound;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.IconCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.nihility.Global;
import com.nihility.XMPushUtils;
import com.nihility.notification.NotificationManagerEx;
import com.xiaomi.push.service.MyNotificationIconHelper;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.push.utils.Configurations;
import com.xiaomi.xmsf.push.utils.IconConfigurations;
import com.xiaomi.xmsf.utils.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import top.trumeet.common.utils.CustomConfiguration;
import top.trumeet.common.utils.ImgUtils;
import top.trumeet.mipushframework.main.AdvancedSettingsPage;

/**
 * @author Trumeet
 * @date 2018/1/25
 */

public class NotificationController {
    private static final Logger logger = XLog.tag("NotificationController").build();

    private static final String NOTIFICATION_LARGE_ICON = "mipush_notification";
    private static final String NOTIFICATION_SMALL_ICON = "mipush_small_notification";
    private static final String FOCUS_PROTOCOL_SETTING = "notification_focus_protocol";
    private static final String FOCUS_PARAM = "miui.focus.param";
    private static final String FOCUS_PICTURES = "miui.focus.pics";
    // The official client permits a much longer network timeout. Holding our
    // notification worker for that long can starve all push notifications, so the
    // native-icon enhancement gets a small global budget while the URL payload stays.
    private static final long FOCUS_DOWNLOAD_CALLER_BUDGET_MILLIS = 5_000L;

    public static final String CHANNEL_WARN = "warn";

    public static NotificationManagerEx getNotificationManagerEx() {
        return NotificationManagerEx.INSTANCE;
    }


    @TargetApi(Build.VERSION_CODES.N)
    private static void updateSummaryNotification(Context context, PushMetaInfo metaInfo, String packageName, String groupId) {
        if (groupId == null) {
            return;
        }
        if (!needGroupOfNotifications(packageName, groupId)) {
            getNotificationManagerEx().cancel(packageName, null, groupId.hashCode());
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                getExistsChannelId(context, metaInfo, packageName));
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);

        builder.setCategory(Notification.CATEGORY_EVENT)
                .setGroupSummary(true)
                .setGroup(groupId);
        // The summary is an implementation detail of Android notification
        // grouping.  It has no application focus payload of its own; processing
        // the source message again here would duplicate extras and image work.
        notify(context, groupId.hashCode(), packageName, builder, metaInfo, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static boolean needGroupOfNotifications(String packageName, String groupId) {
        int notificationCntInGroup = getNotificationCountOfGroup(packageName, groupId);
        return notificationCntInGroup > 1;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static int getNotificationCountOfGroup(String packageName, String groupId) {
        StatusBarNotification[] activeNotifications =
                getNotificationManagerEx().getActiveNotifications(packageName);

        if (activeNotifications == null) {
            return 0;
        }
        int notificationCntInGroup = 0;
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            if (statusBarNotification != null
                    && statusBarNotification.getNotification() != null
                    && groupId.equals(statusBarNotification.getNotification().getGroup())
                    && (statusBarNotification.getNotification().flags
                    & Notification.FLAG_GROUP_SUMMARY) == 0) {
                notificationCntInGroup++;
            }
        }
        return notificationCntInGroup;
    }

    public static void publish(Context context, PushMetaInfo metaInfo, int notificationId, String packageName, NotificationCompat.Builder notificationBuilder) {
        String channelId = getExistsChannelId(context, metaInfo, packageName);
        notificationBuilder.setChannelId(channelId);

        notificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);

        applyAlertBehavior(metaInfo, packageName, notificationBuilder);
        notificationBuilder.setPriority(Notification.PRIORITY_HIGH);

        Notification notification = notify(context, notificationId, packageName,
                notificationBuilder, metaInfo, true);

        updateSummaryNotification(context, metaInfo, packageName, notification.getGroup());
    }

    @NonNull
    public static String getExistsChannelId(Context context, PushMetaInfo metaInfo, String packageName) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        String channelId = custom.borrowChannelId(null);
        if (TextUtils.isEmpty(channelId) ||
                getNotificationManagerEx().getNotificationChannel(packageName, channelId) == null) {
            // Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName);
            channelId = NotificationChannelManager.getChannelId(metaInfo, packageName);
        }
        return channelId;
    }

    private static Notification notify(
            Context context, int notificationId, String packageName,
            NotificationCompat.Builder notificationBuilder, PushMetaInfo metaInfo,
            boolean includeFocusExtras) {
        // Make the behavior consistent with official MIUI
        Bundle extras = new Bundle();
        extras.putString("target_package", packageName);
        notificationBuilder.addExtras(extras);

        // Set small icon
        processIcon(context, packageName, notificationBuilder);

        if (includeFocusExtras) {
            CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
            String iconUri = configuration.notificationLargeIconUri(null);
            Bitmap largeIcon = getLargeIcon(context, metaInfo, iconUri);
            if (largeIcon != null) {
                notificationBuilder.setLargeIcon(largeIcon);
            }

            String subText = configuration.subText(null);
            buildExtraSubText(context, packageName, notificationBuilder, subText);

            addFocusNotificationExtras(context, notificationBuilder, configuration);
        }

        notificationBuilder.setAutoCancel(true);
        Notification notification = notificationBuilder.build();
        getNotificationManagerEx().notify(
                packageName, getNotificationTag(packageName), notificationId, notification);
        return notification;
    }

    private static void applyAlertBehavior(
            PushMetaInfo metaInfo,
            String packageName,
            NotificationCompat.Builder notificationBuilder) {
        int notifyType = metaInfo == null ? 0 : metaInfo.getNotifyType();
        String soundUri = metaInfo == null
                ? null
                : XMPushUtils.getConfiguration(metaInfo).soundUri(null);

        if (usesPackageResourceSound(notifyType, soundUri, packageName)) {
            notificationBuilder.setDefaults(notifyType & ~NOTIFY_TYPE_SOUND);
            notificationBuilder.setSound(Uri.parse(soundUri));
        } else {
            notificationBuilder.setDefaults(notifyType);
        }
    }

    private static void addFocusNotificationExtras(
            Context context,
            NotificationCompat.Builder notificationBuilder,
            CustomConfiguration configuration) {
        CustomConfiguration.FocusNotificationPayload payload =
                configuration.focusNotificationPayload();
        if (!payload.isUsable()) {
            return;
        }

        Bundle focusBundle = new Bundle();
        focusBundle.putString(FOCUS_PARAM, payload.parameter());
        if (isFocusProtocolEnabled(context)) {
            for (Map.Entry<String, String> picture : payload.pictureUrls().entrySet()) {
                // Supported MIUI SystemUI needs both the URL and the native Icon.
                focusBundle.putString(picture.getKey(), picture.getValue());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !payload.pictureUrls().isEmpty()) {
                focusBundle.putBundle(FOCUS_PICTURES,
                        FocusIconApi23.downloadPictures(context, payload.pictureUrls()));
            }
        }
        notificationBuilder.addExtras(focusBundle);
    }

    private static boolean isFocusProtocolEnabled(Context context) {
        if (context == null || !"com.xiaomi.xmsf".equals(context.getPackageName())) {
            return false;
        }
        int protocolVersion;
        try {
            protocolVersion = Settings.System.getInt(context.getContentResolver(),
                    FOCUS_PROTOCOL_SETTING, 0);
        } catch (Throwable error) {
            logger.w("Unable to read focus-notification protocol setting", error);
            return false;
        }
        return CustomConfiguration.FocusNotificationPayload
                .isSupportedProtocolVersion(protocolVersion);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static final class FocusIconApi23 {
        private static final ExecutorService IMAGE_EXECUTOR = createImageExecutor();

        private FocusIconApi23() {
        }

        private static ExecutorService createImageExecutor() {
            AtomicInteger threadNumber = new AtomicInteger();
            ThreadFactory threadFactory = runnable -> {
                Thread thread = new Thread(runnable,
                        "mipush-focus-image-" + threadNumber.incrementAndGet());
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            };
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    3, 3, 30L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(30), threadFactory);
            executor.allowCoreThreadTimeOut(true);
            return executor;
        }

        static Bundle downloadPictures(
                Context context, Map<String, String> pictureUrls) {
            List<Map.Entry<String, String>> pictures =
                    new ArrayList<>(pictureUrls.entrySet());
            List<Callable<Icon>> tasks = new ArrayList<>(pictures.size());
            for (Map.Entry<String, String> picture : pictures) {
                tasks.add(() -> downloadPicture(context, picture.getValue()));
            }

            Bundle result = new Bundle();
            try {
                List<Future<Icon>> futures = IMAGE_EXECUTOR.invokeAll(
                        tasks, FOCUS_DOWNLOAD_CALLER_BUDGET_MILLIS, TimeUnit.MILLISECONDS);
                for (int i = 0; i < pictures.size(); i++) {
                    Icon icon = null;
                    Future<Icon> future = futures.get(i);
                    if (!future.isCancelled()) {
                        try {
                            icon = future.get();
                        } catch (ExecutionException | CancellationException error) {
                            logger.w("Unable to download focus-notification picture", error);
                        }
                    }
                    // Official XMSF retains the key with a null value on failure.
                    result.putParcelable(pictures.get(i).getKey(), icon);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                addNullPictures(result, pictures);
            } catch (RuntimeException error) {
                logger.w("Unable to schedule focus-notification pictures", error);
                addNullPictures(result, pictures);
            }
            return result;
        }

        private static void addNullPictures(
                Bundle result, List<Map.Entry<String, String>> pictures) {
            for (Map.Entry<String, String> picture : pictures) {
                result.putParcelable(picture.getKey(), null);
            }
        }

        @Nullable
        private static Icon downloadPicture(Context context, String url) {
            // Ask the bounded reader for one extra byte so exactly 100 KiB remains
            // valid while a larger response is rejected.
            MyNotificationIconHelper.GetIconResult result =
                    MyNotificationIconHelper.getIconFromUrl(context, url,
                            CustomConfiguration.FOCUS_PICTURE_MAX_BYTES + 1);
            if (result == null || result.bitmap == null
                    || !CustomConfiguration.FocusNotificationPayload
                    .isPictureSizeAllowed(result.downloadSize)) {
                return null;
            }
            return Icon.createWithBitmap(result.bitmap);
        }
    }

    @Nullable
    public static Bitmap getLargeIcon(Context context, PushMetaInfo metaInfo, String iconUri) {
        Bitmap largeIcon = Global.IconCache().getBitmap(context, iconUri,
                (context1, iconUri1) -> getBitmapFromUri(context1, iconUri1, 200 * KiB));
        if (largeIcon != null) {
            largeIcon = roundLargeIconIfConfigured(metaInfo, largeIcon);
        }
        return largeIcon;
    }

    public static Bitmap roundLargeIconIfConfigured(PushMetaInfo metaInfo, Bitmap largeIcon) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        if (custom.roundLargeIcon(false)) {
            largeIcon = ImgUtils.trimImgToCircle(largeIcon, Color.TRANSPARENT);
        }
        return largeIcon;
    }

    @Nullable
    public static Bitmap getBitmapFromUri(Context context, String iconUri, int maxDownloadBytes) {
        Bitmap bitmap = null;
        if (iconUri != null) {
            if (iconUri.startsWith("http")) {
                MyNotificationIconHelper.GetIconResult result =
                        MyNotificationIconHelper.getIconFromUrl(context, iconUri, maxDownloadBytes);
                if (result != null) {
                    bitmap = result.bitmap;
                }
            } else {
                bitmap = MyNotificationIconHelper.getIconFromUri(context, iconUri);
            }
        }
        return bitmap;
    }

    public static void cancel(Context context, XmPushActionContainer container,
                              int notificationId, String notificationGroup, boolean clearGroup) {
        getNotificationManagerEx().cancel(container.getPackageName(),
                getNotificationTag(container), notificationId);

        if (clearGroup) {
            if (notificationGroup != null) {
                getNotificationManagerEx().cancel(container.getPackageName(),
                        getNotificationTag(container), notificationGroup.hashCode());
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationGroup != null) {
                XmPushActionContainer copy = container.deepCopy();
                try {
                    Configurations.getInstance().handle(container.packageName, copy);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                updateSummaryNotification(context, copy.metaInfo, container.getPackageName(), notificationGroup);
            }
        }
    }


    /**
     * @param ctx context
     * @param pkg packageName
     * @return 0 if not processed
     */
    public static int getIconColor(final Context ctx, final String pkg) {
        return Global.IconCache().getAppColor(ctx, pkg, (ctx1, iconBitmap) -> {
            if (iconBitmap == null) {
                return Notification.COLOR_DEFAULT;
            }
            int color = ColorUtil.getIconColor(iconBitmap);
            if (color != Notification.COLOR_DEFAULT) {
                final float[] hsl = new float[3];
                ColorUtils.colorToHSL(color, hsl);
                hsl[1] = 0.94f;
                hsl[2] = Math.min(hsl[2] * 0.6f, 0.31f);
                return ColorUtils.HSLToColor(hsl);
            } else {
                return Notification.COLOR_DEFAULT;
            }
        });
    }


    public static void processIcon(Context context, String packageName, NotificationCompat.Builder notificationBuilder) {
        notificationBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);

        // refer: https://dev.mi.com/console/doc/detail?pId=2625#_5_0
        Context pkgContext = null;
        try {
            pkgContext = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        int largeIconId = getIconId(context, packageName, NOTIFICATION_LARGE_ICON);
        int smallIconId = getIconId(context, packageName, NOTIFICATION_SMALL_ICON);

        if (largeIconId > 0) {
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(pkgContext.getResources(), largeIconId));
        }

        notificationBuilder.setColor(getIconColor(context, packageName));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IconConfigurations.IconConfig iconConfig = Global.IconConfigurations().get(packageName);
            if (iconConfig != null && iconConfig.isEnabled && iconConfig.isEnabledAll) {
                Bitmap iconBitmap = iconConfig.bitmap();
                if (iconBitmap != null) {
                    notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap));
                    notificationBuilder.setColor(iconConfig.color());
                    return;
                }
            }

            if (smallIconId > 0) {
                notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, smallIconId));
                return;
            }
            if (largeIconId > 0) {
                notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, largeIconId));
                return;
            }

            Bitmap iconBitmap = iconConfig == null ? null : iconConfig.bitmap();
            if (iconBitmap != null && iconConfig.isEnabled) {
                notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap));
                notificationBuilder.setColor(iconConfig.color());
                return;
            }

            IconCompat iconCache = Global.IconCache().getIconCache(context, packageName, (ctx, b) -> IconCompat.createWithBitmap(b));
            if (iconCache != null) {
                notificationBuilder.setSmallIcon(iconCache);
                return;
            }
        }
    }

    public static void buildExtraSubText(Context context, String packageName, NotificationCompat.Builder localBuilder, CharSequence text) {
        if ("".equals(text)) {
            localBuilder.setSubText(null);
            return;
        }
        if (text == null) {
            text = Global.ApplicationNameCache().getAppName(context, packageName);
        }
        int color = localBuilder.getColor();
        if (color == Notification.COLOR_DEFAULT) {
            localBuilder.setSubText(text);
            return;
        }
        CharSequence subText = ColorUtil.createColorSubtext(text, color);
        localBuilder.setSubText(subText);
    }

    private static int getIconId(Context context, String packageName, String resourceName) {
        return context.getResources().getIdentifier(resourceName, "drawable", packageName);
    }


    public static void test(Context context, String packageName, String title, String description) {
        NotificationChannelManager.registerChannelIfNeeded(context, new PushMetaInfo(), packageName);

        int id = (int) (System.currentTimeMillis() / 1000L);

        NotificationCompat.Builder localBuilder = new NotificationCompat.Builder(context);

        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
        style.bigText(description);
        style.setBigContentTitle(title);
        style.setSummaryText(description);
        localBuilder.setStyle(style);
        localBuilder.setWhen(System.currentTimeMillis());
        localBuilder.setShowWhen(true);

        Intent notifyIntent = new Intent(context, AdvancedSettingsPage.class);
        // Set the Activity to start in a new, empty task
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Create the PendingIntent
        PendingIntent notifyPendingIntent = PendingIntent.getActivity(
                context, 0, notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        localBuilder.setContentIntent(notifyPendingIntent);

        NotificationController.publish(context, new PushMetaInfo(), id, packageName, localBuilder);
    }

}
