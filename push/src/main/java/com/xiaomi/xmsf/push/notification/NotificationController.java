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
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.LruCache;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import top.trumeet.common.utils.CustomConfiguration;
import top.trumeet.common.utils.ImgUtils;
import top.trumeet.common.utils.NotificationMetadata;
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
    private static final long FOCUS_PROTOCOL_CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final FocusProtocolSupportCache FOCUS_PROTOCOL_SUPPORT_CACHE =
            new FocusProtocolSupportCache(FOCUS_PROTOCOL_CACHE_TTL_MILLIS);
    private static final AtomicInteger MOCK_NOTIFICATION_SEQUENCE =
            new AtomicInteger(10_000);
    // The official client permits a much longer network timeout. Holding our
    // notification worker for that long can starve all push notifications, so the
    // native-icon enhancement gets a small global budget while the URL payload stays.
    public static final String CHANNEL_WARN = "warn";

    public static NotificationManagerEx getNotificationManagerEx() {
        return NotificationManagerEx.INSTANCE;
    }

    /** Best-effort preflight used by the settings-page diagnostic actions. */
    public static boolean areNotificationsEnabled(Context context, String packageName) {
        try {
            return getNotificationManagerEx().areNotificationsEnabled(packageName);
        } catch (Throwable error) {
            // A failed hidden-API probe must not suppress a real delivery.
            logger.w("Unable to inspect notification permission", error);
            return true;
        }
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
        notify(context, groupId.hashCode(), packageName, getNotificationTag(packageName),
                builder, metaInfo, false, false);
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
        // Preserve an explicit channel selected by a caller (the settings-page
        // replay uses a dedicated high-importance channel). Older code always
        // overwrote it with the client's derived channel, making the replay
        // appear to do nothing when that channel had been muted.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || hasNoExplicitChannel(notificationBuilder)) {
            notificationBuilder.setChannelId(channelId);
        }

        notificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);

        applyAlertBehavior(metaInfo, packageName, notificationBuilder);
        notificationBuilder.setPriority(Notification.PRIORITY_HIGH);

        boolean attemptFocus = shouldAttachFocusExtras(context, metaInfo);
        if (attemptFocus) {
            // The official group supplied by the client always wins. Debug and
            // other direct callers otherwise get a stable focus-only group so a
            // normal notification from the same app cannot fold it away.
            boolean hasOfficialGroup = hasOfficialNotificationGroup(metaInfo);
            try {
                Notification preview = notificationBuilder.build();
                String existingGroup = preview.getGroup();
                if (!hasOfficialGroup
                        && (TextUtils.isEmpty(existingGroup)
                        || packageName.equals(existingGroup))) {
                    notificationBuilder.setGroup(
                            FocusNotificationSafety.stableFocusGroup(packageName));
                }
            } catch (Throwable error) {
                logger.w("Unable to inspect focus-notification group", error);
            }
        }

        String notificationTag = getNotificationTag(packageName);
        Notification notification = FocusNotificationSafety.deliverWithSingleFallback(
                packageName,
                notificationTag,
                notificationId,
                attemptFocus,
                (deliveryPackage, deliveryTag, deliveryId, includeFocusExtras,
                 focusFailure) -> {
                    if (!includeFocusExtras) {
                        if (focusFailure != null) {
                            logger.w("Focus notification failed; retrying as a standard notification",
                                    focusFailure);
                        }
                        stripFocusNotificationExtras(notificationBuilder);
                    }
                    return notify(context, deliveryId, deliveryPackage, deliveryTag,
                            notificationBuilder, metaInfo, true, includeFocusExtras);
                });

        updateSummaryNotification(context, metaInfo, packageName, notification.getGroup());
    }

    private static boolean hasNoExplicitChannel(NotificationCompat.Builder builder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        try {
            return TextUtils.isEmpty(builder.build().getChannelId());
        } catch (Throwable error) {
            // A partially-built caller notification should still receive the
            // derived channel rather than fail the entire delivery.
            return true;
        }
    }

    private static boolean hasOfficialNotificationGroup(@Nullable PushMetaInfo metaInfo) {
        if (metaInfo == null) {
            return false;
        }
        try {
            CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
            String configuredGroup = configuration.notificationGroup(null);
            return (configuredGroup != null && !configuredGroup.trim().isEmpty())
                    || metaInfo.passThrough == 1;
        } catch (Throwable error) {
            // If metadata cannot be inspected, preserving a caller-supplied
            // group is safer than guessing that it is the SDK default.
            logger.w("Unable to inspect official notification group", error);
            return true;
        }
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
            String notificationTag, NotificationCompat.Builder notificationBuilder,
            PushMetaInfo metaInfo,
            boolean includeOfficialMetadata, boolean includeFocusExtras) {
        // Make the behavior consistent with official MIUI
        Bundle extras = new Bundle();
        extras.putString("target_package", packageName);
        notificationBuilder.addExtras(extras);

        // Set small icon
        processIcon(context, packageName, notificationBuilder);

        CustomConfiguration configuration = null;
        if (includeOfficialMetadata) {
            configuration = XMPushUtils.getConfiguration(metaInfo);
            applyOfficialMetadata(context, packageName, notificationBuilder, configuration);
            String iconUri = configuration.notificationLargeIconUri(null);
            Bitmap largeIcon = getLargeIcon(context, metaInfo, iconUri);
            if (largeIcon != null) {
                notificationBuilder.setLargeIcon(largeIcon);
            }

            String subText = configuration.subText(null);
            buildExtraSubText(context, packageName, notificationBuilder, subText);

        }

        ensureReadableStandardContent(context, packageName, notificationBuilder,
                metaInfo, configuration);

        if (includeFocusExtras && configuration != null) {
            addFocusNotificationExtras(context, notificationBuilder, configuration);
        }

        notificationBuilder.setAutoCancel(true);
        Notification notification = notificationBuilder.build();
        applyTargetPackage(context, notification, packageName);
        getNotificationManagerEx().notify(
                packageName, notificationTag, notificationId, notification);
        return notification;
    }

    private static boolean shouldAttachFocusExtras(Context context, PushMetaInfo metaInfo) {
        try {
            CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
            CustomConfiguration.FocusNotificationPayload payload =
                    configuration.focusNotificationPayload();
            if (!payload.isUsable()) {
                return false;
            }
            // Keep the legacy XMSF contract: the public miui.focus.* payload is
            // forwarded whenever the sender supplied it.  The private protocol
            // setting is only a capability hint for optional native-image
            // enrichment; making it a hard gate caused focus notifications to
            // silently degrade on HyperOS builds which do not expose the setting
            // to third-party/system-app bridges.
            // Do not hand malformed JSON to the private renderer. Valid picture
            // URL fields remain independently useful and are still forwarded.
            return FocusNotificationSafety.isWellFormedParameter(payload.parameter())
                    || !payload.pictureUrls().isEmpty();
        } catch (Throwable error) {
            logger.w("Unable to inspect focus-notification payload", error);
            return false;
        }
    }

    private static void stripFocusNotificationExtras(
            NotificationCompat.Builder notificationBuilder) {
        try {
            stripFocusNotificationExtras(notificationBuilder.getExtras());
        } catch (Throwable error) {
            // A malformed Parcelable in a third-party payload must not prevent
            // the standard retry from being attempted.
            logger.w("Unable to fully strip focus-notification extras", error);
        }
    }

    private static void stripFocusNotificationExtras(@Nullable Bundle extras) {
        if (extras == null) {
            return;
        }
        // NotificationCompat.addExtras() flattens the focus bundle into the
        // builder's top-level extras. Removing those protocol keys directly is
        // sufficient and avoids traversing arbitrary third-party Bundles (which
        // may be self-referential or contain unparcelable values).
        for (String key : new ArrayList<>(extras.keySet())) {
            try {
                if (FocusNotificationSafety.isFocusExtraKey(key)) {
                    extras.remove(key);
                }
            } catch (Throwable error) {
                // Keep walking the remaining keys. Bundle access can throw for
                // a bad parcelable, but no focus key should block delivery.
            }
        }
    }

    private static void ensureReadableStandardContent(
            Context context,
            String packageName,
            NotificationCompat.Builder notificationBuilder,
            PushMetaInfo metaInfo,
            @Nullable CustomConfiguration configuration) {
        String existingTitle = null;
        String existingBody = null;
        try {
            Notification preview = notificationBuilder.build();
            if (preview.extras != null) {
                CharSequence title = preview.extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence body = preview.extras.getCharSequence(Notification.EXTRA_TEXT);
                existingTitle = title == null ? null : title.toString();
                existingBody = body == null ? null : body.toString();
            }
        } catch (Throwable error) {
            logger.w("Unable to inspect standard notification content", error);
        }

        String metaTitle = metaInfo == null ? null : metaInfo.getTitle();
        String metaBody = metaInfo == null ? null : metaInfo.getDescription();
        String focusParameter = null;
        if (configuration != null) {
            try {
                focusParameter = configuration.focusParam(null);
            } catch (Throwable error) {
                logger.w("Unable to read focus-notification parameter", error);
            }
        }
        String fallbackTitle = packageName;
        try {
            CharSequence appName = Global.ApplicationNameCache().getAppName(context, packageName);
            if (appName != null && appName.length() > 0) {
                fallbackTitle = appName.toString();
            }
        } catch (Throwable ignored) {
        }

        FocusNotificationSafety.ResolvedContent resolved =
                FocusNotificationSafety.resolveReadableContent(
                        firstReadable(existingTitle, metaTitle),
                        firstReadable(existingBody, metaBody),
                        focusParameter,
                        fallbackTitle,
                        "New notification");
        if (!hasReadableText(existingTitle)) {
            notificationBuilder.setContentTitle(resolved.title());
        }
        if (!hasReadableText(existingBody)) {
            notificationBuilder.setContentText(resolved.body());
        }
    }

    @Nullable
    private static String firstReadable(@Nullable String first, @Nullable String second) {
        return hasReadableText(first) ? first : second;
    }

    private static boolean hasReadableText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void applyOfficialMetadata(
            Context context,
            String packageName,
            NotificationCompat.Builder builder,
            CustomConfiguration configuration) {
        NotificationMetadata metadata = NotificationMetadata.from(configuration);
        Bundle extras = builder.getExtras();
        String customAppIconUri = configuration.notificationCustomSmallIconUri(null);
        if (!TextUtils.isEmpty(customAppIconUri)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Bitmap customAppIcon = getBitmapFromUri(context, customAppIconUri, 200 * KiB);
            if (customAppIcon != null) {
                extras.putParcelable("miui.appIcon", Icon.createWithBitmap(customAppIcon));
                extras.putString("custom_app_icon", "0");
            }
        }

        String smallIconUri = configuration.notificationSmallIconUri(null);
        if (!TextUtils.isEmpty(smallIconUri) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Bitmap icon = getBitmapFromUri(context, smallIconUri, 200 * KiB);
            if (icon != null) {
                builder.setSmallIcon(IconCompat.createWithBitmap(icon));
            }
        }

        String smallIconColor = configuration.notificationSmallIconColor(null);
        if (!TextUtils.isEmpty(smallIconColor)) {
            try {
                builder.setColor(Color.parseColor(smallIconColor));
            } catch (IllegalArgumentException ignored) {
                try {
                    builder.setColor(Integer.parseInt(smallIconColor));
                } catch (NumberFormatException ignoredToo) {
                }
            }
        }

        if (metadata.timeoutSeconds != null && metadata.timeoutSeconds > 0) {
            builder.setTimeoutAfter(metadata.timeoutSeconds * 1000L);
        }

        Integer backgroundColor = metadata.backgroundColor;
        if (backgroundColor != null) {
            builder.setColor(backgroundColor);
            if (metadata.ongoing == null) builder.setOngoing(true);
            if (metadata.colorized == null) builder.setColorized(true);
        }
        if (metadata.ongoing != null) builder.setOngoing(metadata.ongoing);
        if (metadata.colorized != null) builder.setColorized(metadata.colorized);
        if (metadata.visibility != null) builder.setVisibility(metadata.visibility);
        if (metadata.category != null) builder.setCategory(metadata.category);

        if (configuration.notificationStyle()
                == CustomConfiguration.NotificationStyle.COLORFUL) {
            String colorfulStyleBackground =
                    configuration.notificationColorfulBackgroundColor(null);
            if (!TextUtils.isEmpty(colorfulStyleBackground)) {
                try {
                    // Portable approximation for ROMs without MIUI's private layout.
                    builder.setColor(Color.parseColor(colorfulStyleBackground));
                } catch (IllegalArgumentException ignored) {
                    try {
                        builder.setColor(Integer.parseInt(colorfulStyleBackground));
                    } catch (NumberFormatException ignoredToo) {
                    }
                }
            }
        }

        String imageDescription = configuration.imageDescription(null);
        if (!TextUtils.isEmpty(imageDescription)) {
            extras.putCharSequence("miui.imageDescribe", imageDescription);
        }
        if (metadata.enableKeyguard != null) extras.putBoolean("miui.enableKeyguard", metadata.enableKeyguard);
        if (metadata.enableFloat != null) extras.putBoolean("miui.enableFloat", metadata.enableFloat);
        if (metadata.fold != null) extras.putString("notification_fold", metadata.fold);
        if (metadata.foldTimeoutSeconds != null && metadata.foldTimeoutSeconds > 0) {
            extras.putLong("miui.fold.timeout", metadata.foldTimeoutSeconds * 1000L);
        }

        String styleType = configuration.notificationStyleType(null);
        if (!TextUtils.isEmpty(styleType)) {
            extras.putString("miui.notificationStyleType", styleType);
        }
        String colorfulText = configuration.notificationColorfulButtonText(null);
        if (!TextUtils.isEmpty(colorfulText)) {
            extras.putString("miui.colorfulButtonText", colorfulText);
        }
        String colorfulBackground = configuration.notificationColorfulButtonBackgroundColor(null);
        if (!TextUtils.isEmpty(colorfulBackground)) {
            extras.putString("miui.colorfulButtonBackgroundColor", colorfulBackground);
        }
        if (Boolean.TRUE.equals(metadata.topRepeat)
                && metadata.topPeriodSeconds != null
                && metadata.topPeriodSeconds > 0
                && metadata.topFrequency != null
                && metadata.topFrequency >= 0
                && metadata.topFrequency <= metadata.topPeriodSeconds) {
            builder.setPriority(Notification.PRIORITY_MAX);
            long originalWhen = builder.build().when;
            extras.putLong("mipush_org_when", originalWhen);
            extras.putBoolean("mipush_n_top_flag", true);
            extras.putInt("mipush_n_top_prd", metadata.topPeriodSeconds);
            if (metadata.topFrequency > 0) {
                extras.putInt("mipush_n_top_fre", metadata.topFrequency);
            }
        }
        extras.putString("mipush_target_package", packageName);
        extras.putString("xmsf_target_package", packageName);
    }

    /**
     * HyperOS/MIUI uses a hidden extraNotification target package to attribute a
     * provider-posted notification to its real client. Keep the normal extras as
     * a portable fallback, then use reflection only where the platform exposes
     * the same system API.
     */
    private static void applyTargetPackage(Context context, Notification notification,
                                           String packageName) {
        if (notification == null || TextUtils.isEmpty(packageName)) {
            return;
        }
        boolean targetApplied = false;
        try {
            Field field = Notification.class.getDeclaredField("extraNotification");
            field.setAccessible(true);
            Object extraNotification = field.get(notification);
            if (extraNotification != null) {
                try {
                    Method method = extraNotification.getClass()
                            .getDeclaredMethod("setTargetPkg", String.class);
                    method.setAccessible(true);
                    method.invoke(extraNotification, packageName);
                    targetApplied = true;
                } catch (Throwable ignored) {
                    // Some HyperOS releases expose only part of MiuiNotification.
                }
                // Official XMSF mirrors miui.enableFloat into the hidden
                // MiuiNotification object. The Bundle key alone is ignored by
                // several SystemUI versions, which is why MessagingStyle
                // notifications previously lacked the pull-down mini-window.
                if (notification.extras != null
                        && notification.extras.containsKey("miui.enableFloat")) {
                    try {
                        Method method = extraNotification.getClass()
                                .getDeclaredMethod("setEnableFloat", boolean.class);
                        method.setAccessible(true);
                        method.invoke(extraNotification,
                                notification.extras.getBoolean("miui.enableFloat"));
                    } catch (Throwable ignored) {
                        // AOSP has no MiuiNotification setter.
                    }
                }
            }
        } catch (Throwable ignored) {
            // AOSP and non-MIUI builds do not expose this hidden API.
        }
        if (targetApplied) {
            return;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            CharSequence label = packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0));
            notification.extras.putCharSequence("android.substName", label);
        } catch (Throwable ignored) {
        }
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
        if (FocusNotificationSafety.isWellFormedParameter(payload.parameter())) {
            focusBundle.putString(FOCUS_PARAM, payload.parameter());
        }
        for (Map.Entry<String, String> picture : payload.pictureUrls().entrySet()) {
            // Keep the URL aliases exactly as received.  This is the part of
            // Xiaomi's original protocol that remains useful even when the
            // native focus renderer is unavailable.
            focusBundle.putString(picture.getKey(), picture.getValue());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && isFocusProtocolEnabled(context)
                && !payload.downloadPictureUrls().isEmpty()) {
            // Native Icon enrichment is bounded (count, bytes, executor queue
            // and caller budget). Match official XMSF by doing this optional
            // download only when the ROM advertises notification_focus_protocol;
            // the parameter and URL aliases above remain available as the
            // legacy, no-download compatibility path on AOSP/unsupported ROMs.
            focusBundle.putBundle(FOCUS_PICTURES,
                    FocusIconApi23.downloadPictures(context, payload.downloadPictureUrls()));
        }
        notificationBuilder.addExtras(focusBundle);
    }

    private static boolean isFocusProtocolEnabled(Context context) {
        if (context == null) {
            return false;
        }
        if (!"com.xiaomi.xmsf".equals(context.getPackageName())) {
            return false;
        }
        return FOCUS_PROTOCOL_SUPPORT_CACHE.get(SystemClock.elapsedRealtime(), () -> {
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
        });
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static final class FocusIconApi23 {
        private static final int IMAGE_CACHE_MAX_BYTES = 4 * 1024 * 1024;
        private static final ExecutorService IMAGE_EXECUTOR = createImageExecutor();
        private static final ConcurrentHashMap<String, CompletableFuture<Bitmap>> IN_FLIGHT =
                new ConcurrentHashMap<>();
        private static final LruCache<String, Bitmap> IMAGE_CACHE =
                new LruCache<String, Bitmap>(IMAGE_CACHE_MAX_BYTES) {
                    @Override
                    protected int sizeOf(String key, Bitmap value) {
                        if (value == null || value.isRecycled()) return 1;
                        return Math.max(1, value.getAllocationByteCount());
                    }
                };

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
            List<CompletableFuture<Bitmap>> futures = new ArrayList<>(pictures.size());
            for (Map.Entry<String, String> picture : pictures) {
                futures.add(getOrStartDownload(context, picture.getValue()));
            }

            Bundle result = new Bundle();
            long deadlineNanos = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(
                            FocusNotificationSafety.IMAGE_ENRICHMENT_BUDGET_MILLIS);
            for (int i = 0; i < pictures.size(); i++) {
                Bitmap bitmap = null;
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos > 0L) {
                    try {
                        bitmap = futures.get(i).get(remainingNanos, TimeUnit.NANOSECONDS);
                    } catch (ExecutionException | TimeoutException error) {
                        logger.w("Unable to download focus-notification picture", error);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    } catch (java.util.concurrent.CancellationException error) {
                    }
                }
                Icon icon = null;
                if (bitmap != null && !bitmap.isRecycled()) {
                    try {
                        icon = Icon.createWithBitmap(bitmap);
                    } catch (Throwable error) {
                        logger.w("Unable to create native focus-notification icon", error);
                    }
                }
                // Official XMSF retains the key with a null value on failure.
                result.putParcelable(pictures.get(i).getKey(), icon);
            }
            // Keep the URL keys in the parent focus bundle even when one or
            // more native icons failed. The URL is part of Xiaomi's original
            // protocol; a null/omitted native Icon is the documented safe
            // degradation. Binder/build failures are handled by publish's
            // single focus-stripping retry, while a slow or bad image never
            // blocks the standard notification past the caller budget.
            return result;
        }

        private static CompletableFuture<Bitmap> getOrStartDownload(
                Context context, String url) {
            if (url == null) {
                return CompletableFuture.completedFuture(null);
            }
            Bitmap cached = IMAGE_CACHE.get(url);
            if (cached != null && !cached.isRecycled()) {
                return CompletableFuture.completedFuture(cached);
            }

            CompletableFuture<Bitmap> created = new CompletableFuture<>();
            CompletableFuture<Bitmap> existing = IN_FLIGHT.putIfAbsent(url, created);
            if (existing != null) return existing;

            try {
                IMAGE_EXECUTOR.execute(() -> {
                    try {
                        Bitmap bitmap = downloadPicture(context, url);
                        if (bitmap != null && !bitmap.isRecycled()
                                && url.regionMatches(true, 0, "https://", 0, 8)) {
                            IMAGE_CACHE.put(url, bitmap);
                        }
                        created.complete(bitmap);
                    } catch (Throwable error) {
                        logger.w("Unable to decode focus-notification picture", error);
                        created.complete(null);
                    } finally {
                        IN_FLIGHT.remove(url, created);
                    }
                });
            } catch (RejectedExecutionException error) {
                IN_FLIGHT.remove(url, created);
                created.complete(null);
            }
            return created;
        }

        @Nullable
        private static Bitmap downloadPicture(Context context, String url) {
            // Ask the bounded reader for one extra byte so exactly 100 KiB remains
            // valid while a larger response is rejected.
            MyNotificationIconHelper.GetIconResult result;
            if (url != null && (url.regionMatches(true, 0, "content://", 0, 10)
                    || url.regionMatches(true, 0, "android.resource://", 0, 19))) {
                result = MyNotificationIconHelper.getFocusIconFromUri(context, url,
                        CustomConfiguration.FOCUS_PICTURE_MAX_BYTES);
            } else {
                result = MyNotificationIconHelper.getFocusIconFromUrl(context, url,
                        CustomConfiguration.FOCUS_PICTURE_MAX_BYTES + 1);
            }
            if (result == null || result.bitmap == null
                    || !CustomConfiguration.FocusNotificationPayload
                    .isPictureSizeAllowed(result.downloadSize)) {
                return null;
            }
            return result.bitmap;
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
        test(context, packageName, title, description, new PushMetaInfo(),
                nextMockNotificationId());
    }

    public static void testFocus(Context context, String packageName, String title,
                                 String description) {
        PushMetaInfo metaInfo = new PushMetaInfo();
        Map<String, String> extras = new HashMap<>();
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("ticker", "MiPush Framework");
            json.put("title", title);
            json.put("description", description);
            extras.put(FOCUS_PARAM, json.toString());
        } catch (org.json.JSONException e) {
            logger.e("Failed to construct focus JSON", e);
        }
        extras.put("miui.focus.pic_0",
                "https://raw.githubusercontent.com/SherlockChiang/MiPushFramework/7e2eb27ef86a4ea29d4791a82dd5a557b7f14b62/art/ic_launcher-web.png");
        metaInfo.setExtra(extras);
        test(context, packageName, title, description, metaInfo,
                nextMockNotificationId());
    }

    /**
     * Manual replay is a diagnostic action.  Give every tap a fresh id so a
     * previously dismissed test notification cannot be silently updated without
     * producing a new entry/head-up alert on MIUI/SystemUI.
     */
    private static int nextMockNotificationId() {
        return MOCK_NOTIFICATION_SEQUENCE.updateAndGet(previous ->
                previous == Integer.MAX_VALUE ? 10_000 : previous + 1);
    }

    private static void test(Context context, String packageName, String title,
                             String description, PushMetaInfo metaInfo, int notificationId) {
        NotificationChannelManager.registerDebugChannelIfNeeded(context, packageName);
        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? NotificationChannelManager.DEBUG_CHANNEL_ID
                : getExistsChannelId(context, metaInfo, packageName);
        NotificationCompat.Builder localBuilder = new NotificationCompat.Builder(context, channelId);

        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
        style.bigText(description);
        style.setBigContentTitle(title);
        style.setSummaryText(description);
        localBuilder.setStyle(style);
        // BigTextStyle's expanded title is not guaranteed to populate the
        // standard EXTRA_TITLE/EXTRA_TEXT fields on every AndroidX/MIUI build.
        // Keep the collapsed notification readable as well.
        localBuilder.setContentTitle(title);
        localBuilder.setContentText(description);
        localBuilder.setTicker(title + ": " + description);
        localBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
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

        NotificationController.publish(context, metaInfo, notificationId, packageName, localBuilder);
    }

}
