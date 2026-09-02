package com.xiaomi.push.service;

import static com.xiaomi.push.service.MIPushNotificationHelper.FROM_NOTIFICATION;
import static com.xiaomi.push.service.MIPushNotificationHelper.getTargetPackage;
import static com.xiaomi.push.service.MIPushNotificationHelper.isBusinessMessage;
import static com.xiaomi.push.service.MyNotificationIconHelper.KiB;
import static com.xiaomi.push.service.MyNotificationIconHelper.MiB;
import static com.xiaomi.xmsf.push.notification.NotificationController.getBitmapFromUri;
import static com.xiaomi.xmsf.push.notification.NotificationController.getLargeIcon;
import static com.xiaomi.xmsf.push.notification.NotificationController.getNotificationManagerEx;
import static com.xiaomi.xmsf.push.notification.NotificationController.prepareLargeIconForNotification;
import static com.xiaomi.xmsf.push.notification.NotificationController.roundLargeIconIfConfigured;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.nihility.Global;
import com.nihility.XMPushUtils;
import com.nihility.notification.NotificationManagerEx;
import com.nihility.utils.NotificationReplayMarker;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.mipush.sdk.PushMessageProcessor;
import com.xiaomi.push.sdk.MyPushMessageHandler;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.PushMessage;
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.push.notification.FocusNotificationSafety;
import com.xiaomi.xmsf.push.notification.NotificationController;
import com.xiaomi.xmsf.push.utils.Configurations;
import com.xiaomi.xmsf.push.utils.IconConfigurations;
import com.xiaomi.xmsf.push.utils.RegSecUtils;
import com.xiaomi.xmsf.utils.ConfigCenter;
import com.xiaomi.xmsf.utils.ConvertUtils;

import org.apache.thrift.TBase;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.trumeet.common.Constants;
import top.trumeet.common.utils.CustomConfiguration;
import top.trumeet.common.utils.Utils;
import top.trumeet.mipush.provider.db.RegisteredApplicationDb;
import top.trumeet.mipush.provider.entities.RegisteredApplication;

/**
 * @author zts1993
 * @date 2018/2/8
 */

public class MyMIPushNotificationHelper {

    public static final String CLASS_NAME_PUSH_MESSAGE_HANDLER = "com.xiaomi.mipush.sdk.PushMessageHandler";
    private static Logger logger = XLog.tag("MyNotificationHelper").build();

    private static final int NOTIFICATION_BIG_STYLE_MIN_LEN = 25;

    private static final String GROUP_TYPE_MIPUSH_GROUP = "#group#";
    private static final String GROUP_TYPE_SAME_TITLE = "#title#";
    private static final String GROUP_TYPE_SAME_NOTIFICATION_ID = "#id#";
    private static final String GROUP_TYPE_PASS_THROUGH = "#pass_through#";

    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_LEFT = 1;
    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_MID = 2;
    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT = 3;
    private static final int NOTIFICATION_ACTION_BUTTON_PLACE_COLORFUL = 4;
    private static final String NOTIFICATION_COLORFUL_BUTTON_INTENT_CLASS = "notification_colorful_button_intent_class";
    private static final String NOTIFICATION_COLORFUL_BUTTON_INTENT_URI = "notification_colorful_button_intent_uri";
    private static final String NOTIFICATION_COLORFUL_BUTTON_NOTIFY_EFFECT = "notification_colorful_button_notify_effect";
    private static final String NOTIFICATION_COLORFUL_BUTTON_TEXT = "notification_colorful_button_text";
    private static final String NOTIFICATION_COLORFUL_BUTTON_WEB_URI = "notification_colorful_button_web_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS = "notification_style_button_left_intent_class";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI = "notification_style_button_left_intent_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_NAME = "notification_style_button_left_name";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT = "notification_style_button_left_notify_effect";
    private static final String NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI = "notification_style_button_left_web_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS = "notification_style_button_mid_intent_class";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI = "notification_style_button_mid_intent_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_NAME = "notification_style_button_mid_name";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT = "notification_style_button_mid_notify_effect";
    private static final String NOTIFICATION_STYLE_BUTTON_MID_WEB_URI = "notification_style_button_mid_web_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS = "notification_style_button_right_intent_class";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI = "notification_style_button_right_intent_uri";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_NAME = "notification_style_button_right_name";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT = "notification_style_button_right_notify_effect";
    private static final String NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI = "notification_style_button_right_web_uri";
    private static final String NOTIFICATION_STYLE_TYPE = "notification_style_type";
    /**
     * Configuration files may intentionally replace a sender-declared click
     * route by setting this key to the literal value {@code true}. Without this
     * opt-in, a rewritten route is treated as presentation-only configuration
     * whenever the original sender route is still safe and usable.
     */
    static final String ALLOW_CLICK_ROUTE_REWRITE =
            "__mi_push_allow_click_route_rewrite";
    private static final String[] CLICK_ROUTE_EXTRA_KEYS = {
            PushConstants.EXTRA_PARAM_NOTIFY_EFFECT,
            PushConstants.EXTRA_PARAM_INTENT_URI,
            PushConstants.EXTRA_PARAM_CLASS_NAME,
            PushConstants.EXTRA_PARAM_WEB_URI,
            PushConstants.EXTRA_PARAM_INTENT_FLAG
    };
    private static final StyleActionKeys LEFT_ACTION_KEYS = new StyleActionKeys(
            NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT,
            NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI,
            NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS,
            NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI);
    private static final StyleActionKeys MID_ACTION_KEYS = new StyleActionKeys(
            NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT,
            NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI,
            NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS,
            NOTIFICATION_STYLE_BUTTON_MID_WEB_URI);
    private static final StyleActionKeys RIGHT_ACTION_KEYS = new StyleActionKeys(
            NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT,
            NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI,
            NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS,
            NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI);
    private static final StyleActionKeys COLORFUL_ACTION_KEYS = new StyleActionKeys(
            NOTIFICATION_COLORFUL_BUTTON_NOTIFY_EFFECT,
            NOTIFICATION_COLORFUL_BUTTON_INTENT_URI,
            NOTIFICATION_COLORFUL_BUTTON_INTENT_CLASS,
            NOTIFICATION_COLORFUL_BUTTON_WEB_URI);

    private static boolean tryLoadConfigurations = false;

    private static final java.util.concurrent.atomic.AtomicInteger NOTIFICATION_THREAD_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(1);
    /**
     * Keep the hand-off queue deliberately small because each queued task retains a decrypted
     * push payload.  CallerRunsPolicy remains the lossless back-pressure mechanism: when this
     * bound is reached the producer performs the notification work itself instead of dropping it.
     */
    static final int NOTIFICATION_QUEUE_CAPACITY = 16;
    private static final java.util.concurrent.ThreadPoolExecutor executorService = createNotificationExecutor();
    /**
     * Keep updates for one Android notification identity ordered while still
     * allowing unrelated applications/ids to use the three worker threads in
     * parallel.  The dispatcher bounds payload retention to the same queue
     * budget as the underlying executor.
     */
    private static final KeyedSerialDispatcher<NotificationKey> notificationDispatcher =
            new KeyedSerialDispatcher<>(
                    executorService,
                    NOTIFICATION_QUEUE_CAPACITY,
                    (key, failure) -> logger.e(
                            "Notification task failed for " + key,
                            failure));

    /**
     * Explicit wake operations are optional side effects.  Keep a short
     * per-package gate so bursty pushes cannot repeatedly reacquire a
     * screen-bright wake lock, while notification publication continues
     * independently in the dispatch pipeline.
     */
    private static final WakeScreenThrottle WAKE_SCREEN_THROTTLE =
            new WakeScreenThrottle(SystemClock::elapsedRealtime);

    public static java.util.concurrent.ThreadPoolExecutor getNotificationExecutor() {
        return executorService;
    }

    private static java.util.concurrent.ThreadPoolExecutor createNotificationExecutor() {
        java.util.concurrent.ThreadPoolExecutor executor = new java.util.concurrent.ThreadPoolExecutor(
                3,
                3,
                30L,
                java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(NOTIFICATION_QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(() -> {
                        try {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        } catch (Throwable ignored) {
                        }
                        r.run();
                    }, "mipush-notification-" + NOTIFICATION_THREAD_COUNT.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                },
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * @see `MIPushNotificationHelper`#notifyPushMessage
     */
    public static void notifyPushMessage(Context context, byte[] decryptedContent) {
        XmPushActionContainer container = XMPushUtils.packToContainer(decryptedContent);
        String targetPackage = publishPackageName(container);
        AppInfoUtils.AppNotificationOp notificationOp =
                AppInfoUtils.getAppNotificationOp(context, targetPackage, true);
        if (notificationOp == AppInfoUtils.AppNotificationOp.NOT_ALLOWED) {
            logger.w("Do not notify because user block " + targetPackage + "'s notification");
        } else {
            loadConfigurationsOnce(context);
            // The SDK uses the wrapper package for some system-delivered
            // messages and carries the real client in miui_package_name.
            // Configuration matching must use the same target identity that
            // will later own the published notification.
            handleNotificationByConfigurations(
                    context, decryptedContent, publishPackageName(container), container);
        }
    }

    private static void handleNotificationByConfigurations(Context context, byte[] decryptedContent, String packageName, XmPushActionContainer container) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Set<String> operations = null;
        try {
            operations = Configurations.getInstance().handle(packageName, container);
        } catch (Exception e) {
            logger.e(e.getLocalizedMessage(), e);
        }

        NotificationDispatchPipeline.DispatchPlan plan =
                NotificationDispatchPipeline.planFromOperations(operations);
        NotificationDispatchPipeline.dispatch(
                plan,
                () -> wakeScreen(appContext, packageName),
                // Configuration rules may rewrite the package or notify id.
                // Derive the Android identity only after those rewrites have
                // completed so every update reaches the queue that will
                // actually publish it.
                () -> notificationDispatcher.execute(notificationKeyFor(container), () -> {
                    try {
                        doNotifyPushMessage(appContext, container, decryptedContent);
                    } catch (Exception e) {
                        logger.e(e.getLocalizedMessage(), e);
                    }
                }),
                () -> MyPushMessageHandler.startService(appContext, container, decryptedContent),
                (stage, exception) -> logger.e(
                        "Notification dispatch stage failed: " + stage,
                        exception));
    }

    /**
     * Build the key from the exact package/tag/id tuple used by the current
     * publish path.  Keeping this derivation in one place prevents a future
     * target-package attribution change from silently creating a second queue
     * for the same Android notification.
     */
    private static NotificationKey notificationKeyFor(XmPushActionContainer container) {
        String packageName = publishPackageName(container);
        return new NotificationKey(
                packageName,
                getNotificationTag(packageName),
                getNotificationId(container));
    }

    private static String publishPackageName(XmPushActionContainer container) {
        if (container == null) {
            return "";
        }
        // System-wrapper messages carry the real client in this public MiPush
        // field. Read it before the SDK helper so a hook/aspect failure cannot
        // misattribute the notification to com.xiaomi.xmsf.
        if ("com.xiaomi.xmsf".equals(container.getPackageName())
                && container.getMetaInfo() != null
                && container.getMetaInfo().getExtra() != null) {
            String wrappedTarget = container.getMetaInfo().getExtra().get("miui_package_name");
            if (wrappedTarget != null && !wrappedTarget.trim().isEmpty()) {
                return wrappedTarget.trim();
            }
        }
        try {
            String targetPackage = getTargetPackage(container);
            if (targetPackage != null && !targetPackage.isEmpty()) {
                return targetPackage;
            }
        } catch (Throwable error) {
            logger.w("Unable to derive notification publish package", error);
        }
        if (container.getPackageName() != null && !container.getPackageName().isEmpty()) {
            return container.getPackageName();
        }
        return "";
    }

    /**
     * Return the package that owns the rendered notification, including the
     * miui_package_name target carried by system-wrapper messages.
     */
    public static String getNotificationTargetPackage(
            @Nullable XmPushActionContainer container) {
        return publishPackageName(container);
    }

    private static void loadConfigurationsOnce(Context context) {
        if (!tryLoadConfigurations) {
            tryLoadConfigurations = true;
            try {
                ConfigCenter configCenter = Global.ConfigCenter();
                Uri configurationDirectory = configCenter.getConfigurationDirectory(context);
                loadConfigurations(context, configurationDirectory);
            } catch (Exception e) {
                Utils.makeText(context, e.toString(), Toast.LENGTH_LONG);
            }
        }
    }

    private static void loadConfigurations(Context context, Uri configurationDirectory) {
        Configurations configurations = Configurations.getInstance();
        if (configurations.init(context, configurationDirectory)) {
            IconConfigurations iconConfigurations = Global.IconConfigurations();
            iconConfigurations.init(context, configurationDirectory);
        }
    }

    private static void wakeScreen(Context context, String sourcePackage) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null || powerManager.isInteractive()) {
            return;
        }
        if (!WAKE_SCREEN_THROTTLE.tryAcquire(sourcePackage)) {
            return;
        }
        PowerManager.WakeLock fullWakeLock = powerManager.newWakeLock((
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                        PowerManager.FULL_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP
        ), "xmsf: configurations of " + sourcePackage);
        // Waking the panel is an explicit per-app configuration.  Keep the
        // pulse short so a notification cannot hold a third-party ROM awake
        // for ten seconds after SystemUI has already rendered it.
        fullWakeLock.acquire(5_000L);
    }

    private static Notification findActiveNotification(String packageName, int notificationId) {
        StatusBarNotification[] notifications = getNotificationManagerEx().getActiveNotifications(packageName);
        if (notifications == null) {
            return null;
        }
        for (StatusBarNotification notification : notifications) {
            if (notification != null && notification.getId() == notificationId) {
                Notification activeNotification = notification.getNotification();
                if (activeNotification != null) {
                    return activeNotification;
                }
            }
        }
        return null;
    }

    private static NotificationCompat.Builder addToExistingMessageNotification(
            Context context, String packageName, int notificationId,
            NotificationCompat.MessagingStyle.Message message) {
        try {
            Notification activeNotification = findActiveNotification(packageName, notificationId);
            if (activeNotification != null) {
                NotificationCompat.MessagingStyle activeStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(activeNotification); // todo: try remove
                if (activeStyle != null) {
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, activeNotification);
                    activeStyle.addMessage(message);
                    builder.setStyle(activeStyle); // todo: try remove
                    return builder;
                }
            }
            return null;
        } catch (Exception e) {
            logger.e(e.getLocalizedMessage(), e);
            return null;
        }
    }

    private static void doNotifyPushMessage(Context context, XmPushActionContainer container, byte[] decryptedContent) {
        PushMetaInfo metaInfo = container.getMetaInfo();
        logPushMessage(metaInfo);

        NotificationInfo result = getNotificationFor(context, container, decryptedContent);

        NotificationController.publish(context, metaInfo, result.notificationId,
                publishPackageName(container), result.notificationBuilder);
    }

    private static void logPushMessage(PushMetaInfo metaInfo) {
        String title = metaInfo.getTitle();
        String description = metaInfo.getDescription();

        logger.i("title:" + title + "  description:" + description);
    }

    @NonNull
    private static NotificationInfo getNotificationFor(Context context, XmPushActionContainer container, byte[] decryptedContent) {
        PushMetaInfo metaInfo = container.getMetaInfo();
        String packageName = publishPackageName(container);

        Context pkgCtx = getPackageContext(context, packageName);
        NotificationCompat.MessagingStyle.Message message = createMessage(
                context, container, pkgCtx, packageName);
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        boolean useMessagingStyle = message != null && custom.useMessagingStyle(false);

        int notificationId = getNotificationId(container);

        NotificationCompat.Builder notificationBuilder;

        if (useMessagingStyle) {
            notificationBuilder = messagingStyleNotificationBuilder(context, container, notificationId, message, pkgCtx);
        } else {
            notificationBuilder = normalStyleNotificationBuilder(
                    context, packageName, container.getMetaInfo());
        }

        if (metaInfo.getExtra() != null) {
            setNotificationStyleAction(notificationBuilder, context, packageName, metaInfo.getExtra());
        }
        addDebugAction(context, container, decryptedContent, metaInfo, packageName, notificationBuilder);

        if (useMessagingStyle) {
            // Xiaomi's SystemUI uses this documented MiPush hint for the heads-up
            // affordance. A conversation with a validated Activity click can then
            // be dragged into the target application's small window.
            notificationBuilder.getExtras().putBoolean("miui.enableFloat", true);
            notificationBuilder.setCategory(Notification.CATEGORY_MESSAGE);
        }

        notificationBuilder.setWhen(metaInfo.getMessageTs());
        notificationBuilder.setShowWhen(custom.notificationShowWhen(true));

        String group = getGroupName(context, container);
        notificationBuilder.setGroup(group);

        Intent intentExtra = new Intent();
        intentExtra.putExtra(Constants.INTENT_NOTIFICATION_ID, notificationId);
        intentExtra.putExtra(Constants.INTENT_NOTIFICATION_GROUP, notificationBuilder.build().getGroup());

        ClickPendingIntent clickPendingIntent = getClickedPendingIntent(
                context, container, decryptedContent, notificationId, intentExtra.getExtras(),
                useMessagingStyle);

        if (clickPendingIntent != null) {
            notificationBuilder.setContentIntent(clickPendingIntent.pendingIntent);
            // The temporary-whitelist service PendingIntent is only needed for
            // the legacy Service click path. Carrying it alongside an Activity
            // click can make HyperOS wake the target service and the target
            // Activity together, producing a visible hand-off pause.
            if (shouldCarryTemporaryWhitelist(clickPendingIntent.activity)) {
                carryPendingIntentForTemporarilyWhitelisted(context, container, notificationBuilder);
            }
        }
        return new NotificationInfo(notificationId, notificationBuilder);
    }

    private static class NotificationInfo {
        public final int notificationId;
        public final NotificationCompat.Builder notificationBuilder;

        public NotificationInfo(int notificationId, NotificationCompat.Builder notificationBuilder) {
            this.notificationId = notificationId;
            this.notificationBuilder = notificationBuilder;
        }
    }

    /**
     * Immutable Android notification identity used by the keyed dispatcher.
     * The tuple mirrors NotificationManagerEx.notify(package, tag, id).
     */
    static final class NotificationKey {
        final String packageName;
        final String tag;
        final int id;

        NotificationKey(String packageName, String tag, int id) {
            this.packageName = packageName == null ? "" : packageName;
            this.tag = tag == null ? "" : tag;
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof NotificationKey)) {
                return false;
            }
            NotificationKey that = (NotificationKey) other;
            return id == that.id
                    && packageName.equals(that.packageName)
                    && tag.equals(that.tag);
        }

        @Override
        public int hashCode() {
            int result = packageName.hashCode();
            result = 31 * result + tag.hashCode();
            result = 31 * result + id;
            return result;
        }

        @Override
        public String toString() {
            return packageName + "/" + tag + "/" + id;
        }
    }

    private static Context getPackageContext(Context context, String packageName) {
        Context pkgCtx = context;
        try {
            // Shortcut/person icons and resource lookup must use the client
            // package even when the optional MIUI notification hook is absent.
            pkgCtx = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return pkgCtx;
    }

    @NonNull
    private static NotificationCompat.Builder normalStyleNotificationBuilder(
            Context context, String packageName, PushMetaInfo metaInfo) {
        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        String focusParameter = configuration.focusParam(null);
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
                        metaInfo.getTitle(), metaInfo.getDescription(), focusParameter,
                        fallbackTitle, "New notification");
        String title = resolved.title();
        String description = resolved.body();
        CustomConfiguration.NotificationStyle notificationStyle =
                configuration.notificationStyle();
        // On non-Xiaomi ROMs the private miui.focus.* renderer is deliberately
        // disabled. Keep the original, portable focus presentation visible by
        // expanding the readable body whenever a valid focus payload exists,
        // even when the text is shorter than the ordinary BigText threshold.
        boolean hasPortableFocusPayload = configuration.focusNotificationPayload().isUsable();

        Bitmap bigPic = getBigPic(context, metaInfo);
        if (notificationStyle == CustomConfiguration.NotificationStyle.COLORFUL) {
            bigPic = getBitmapFromUri(context,
                    configuration.notificationColorfulBackgroundImageUri(null), 1 * MiB);
        } else if (notificationStyle == CustomConfiguration.NotificationStyle.BANNER) {
            bigPic = getBitmapFromUri(context,
                    configuration.notificationBannerImageUri(null), 1 * MiB);
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
        if (notificationStyle == CustomConfiguration.NotificationStyle.BANNER) {
            Bitmap bannerIcon = getBitmapFromUri(context,
                    configuration.notificationBannerIconUri(null), 200 * KiB);
            if (bannerIcon != null) {
                // MIUI renders this inside its private banner layout. A large
                // icon is the closest portable representation on other ROMs.
                notificationBuilder.setLargeIcon(bannerIcon);
            }
        }
        // Colorful and Banner are private MIUI layouts. On other ROMs, their
        // published background image is represented with the portable style.
        if (bigPic != null) {
            NotificationCompat.BigPictureStyle style = new NotificationCompat.BigPictureStyle();
            style.bigPicture(bigPic);
            style.setBigContentTitle(title);
            String imageDescription = configuration.imageDescription(null);
            if (!TextUtils.isEmpty(imageDescription)) {
                style.setContentDescription(imageDescription);
            }
            notificationBuilder.setStyle(style);
        } else if (notificationStyle == CustomConfiguration.NotificationStyle.BIG_TEXT
                || description.length() > NOTIFICATION_BIG_STYLE_MIN_LEN
                || hasPortableFocusPayload) {
            NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
            style.bigText(description);
            style.setBigContentTitle(title);
            notificationBuilder.setStyle(style);
        }

        String[] titleAndDesp = determineTitleAndDespByDIP(context, metaInfo);
        FocusNotificationSafety.ResolvedContent dipResolved =
                FocusNotificationSafety.resolveReadableContent(
                        titleAndDesp[0], titleAndDesp[1], focusParameter,
                        title, description);
        notificationBuilder.setContentTitle(dipResolved.title());
        notificationBuilder.setContentText(dipResolved.body());
        return notificationBuilder;
    }

    private static Bitmap getBigPic(Context context, PushMetaInfo metaInfo) {
        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        String bigPicUri = configuration.notificationBigPicUri(null);
        return Global.IconCache().getBitmap(context, bigPicUri,
                (context1, iconUri) -> getBitmapFromUri(
                        context1, iconUri, 1 * MiB));
    }

    @NonNull
    private static NotificationCompat.Builder messagingStyleNotificationBuilder(
            Context context, XmPushActionContainer container, int notificationId, NotificationCompat.MessagingStyle.Message message, Context pkgCtx) {
        String packageName = publishPackageName(container);
        NotificationCompat.Builder messagingBuilder = addToExistingMessageNotification(context, packageName, notificationId, message);
        if (messagingBuilder != null) {
            return messagingBuilder;
        }

        return createMessageStyleNotificationBuilder(context, container, message, pkgCtx, packageName);
    }

    @NonNull
    private static NotificationCompat.Builder createMessageStyleNotificationBuilder(Context context, XmPushActionContainer container, NotificationCompat.MessagingStyle.Message message, Context pkgCtx, String packageName) {
        PushMetaInfo metaInfo = container.getMetaInfo();
        Person group = getGroupFor(context, metaInfo, packageName).build();

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
        attachMessagingStyle(message, group, metaInfo, notificationBuilder);
        // The official large-icon URI is applied later by NotificationController.
        // When it is absent (for example QQ QZone events), keep the same local
        // conversation icon visible in the ordinary notification surface.
        setLargeIconFromPerson(notificationBuilder, group);
        addShortcutToEnableMessagingStyle(context, container, pkgCtx, packageName, group, notificationBuilder);
        return notificationBuilder;
    }

    private static void attachMessagingStyle(NotificationCompat.MessagingStyle.Message message, Person group, PushMetaInfo metaInfo, NotificationCompat.Builder notificationBuilder) {
        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(group);
        style.setConversationTitle(group.getName());
        style.setGroupConversation(isGroupConversation(metaInfo));
        style.addMessage(message);
        notificationBuilder.setStyle(style);
    }

    private static void addShortcutToEnableMessagingStyle(Context context, XmPushActionContainer container, Context pkgCtx, String packageName, Person group, NotificationCompat.Builder notificationBuilder) {
        // if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        try {
            String key = group.getKey() != null ? group.getKey() : group.getName().toString();
            Intent intent = getIntentForMessagingStyle(context, container, packageName);
            ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(pkgCtx, key)
                    .setIntent(intent)
                    .setLongLived(true)
                    .setShortLabel(group.getName())
                    .setIcon(group.getIcon())
                    .build();

            ShortcutManagerCompat.pushDynamicShortcut(pkgCtx, shortcut);
            notificationBuilder.setShortcutInfo(shortcut);
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    private static Intent getIntentForMessagingStyle(Context context, XmPushActionContainer container, String packageName) {
        Intent intent = getSdkIntent(context, container);
        if (intent == null) {
            PackageManager packageManager = context.getPackageManager();
            intent = packageManager.getLaunchIntentForPackage(packageName);
        }
        return intent;
    }

    @Nullable
    private static NotificationCompat.MessagingStyle.Message createMessage(
            Context context, XmPushActionContainer container, Context pkgCtx,
            String packageName) {
        PushMetaInfo metaInfo = container.metaInfo;
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        String senderMessage = custom.conversationMessage(null);
        if (senderMessage == null) {
            return null;
        }
        return createMessage(context, pkgCtx, metaInfo, senderMessage, packageName);
    }

    @NonNull
    private static NotificationCompat.MessagingStyle.Message createMessage(
            Context context, Context pkgCtx, PushMetaInfo metaInfo, String senderMessage,
            String packageName) {
        boolean atLeastP = pkgCtx != null &&
                pkgCtx.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P;

        Person person = null;
        if (isGroupConversation(metaInfo) || atLeastP) {
            person = getPerson(context, metaInfo, packageName).build();
        }
        return new NotificationCompat.MessagingStyle.Message(
                senderMessage, metaInfo.getMessageTs(), person);
    }

    private static boolean isGroupConversation(PushMetaInfo metaInfo) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        return custom.conversationTitle(null) != null;
    }

    @NonNull
    private static Person.Builder getGroupFor(
            Context context, PushMetaInfo metaInfo, String packageName) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        String conversation = custom.conversationTitle(null);
        String conversationId = custom.conversationId(null);
        String conversationIcon = custom.conversationIcon(null);
        if (TextUtils.isEmpty(conversationIcon)) {
            conversationIcon = custom.notificationLargeIconUri(null);
        }

        Person.Builder personBuilder = isGroupConversation(metaInfo) ?
                new Person.Builder() :
                getPerson(context, metaInfo, packageName);
        if (conversation != null) {
            personBuilder.setName(conversation);
        } else if (personBuilder.build().getName() == null) {
            personBuilder.setName(metaInfo.getTitle());
        }
        if (conversationId != null) {
            personBuilder.setKey(conversationId);
        }
        Bitmap largeIcon = getLargeIcon(context, metaInfo, conversationIcon);
        if (largeIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(largeIcon));
        }
        return personBuilder;
    }

    @NonNull
    private static Person.Builder getPerson(
            Context context, PushMetaInfo metaInfo, String packageName) {
        CustomConfiguration custom = XMPushUtils.getConfiguration(metaInfo);
        String sender = custom.conversationSender(null);
        String senderId = custom.conversationSenderId(null);
        String senderIcon = custom.conversationSenderIcon(null);
        if (TextUtils.isEmpty(senderIcon)) {
            senderIcon = custom.notificationLargeIconUri(null);
        }
        String textIcon = custom.textIcon(null);

        Person.Builder personBuilder = new Person.Builder().setName(sender);
        personBuilder.setImportant(custom.conversationImportant(false));
        if (senderId != null) {
            personBuilder.setKey(senderId);
        }
        Bitmap largeIcon = getLargeIcon(context, metaInfo, senderIcon);
        if (largeIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(largeIcon));
        } else if (textIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(
                    roundLargeIconIfConfigured(metaInfo,
                            ImageUtils.INSTANCE.textToBitmap(textIcon, 72, 0xFF003E6F, Color.WHITE)
                    )));
        } else if (!isGroupConversation(metaInfo)
                && shouldUseApplicationIconFallback(senderIcon)) {
            Bitmap applicationIcon = getApplicationIconForConversation(
                    context, packageName, metaInfo);
            if (applicationIcon != null) {
                personBuilder.setIcon(IconCompat.createWithBitmap(applicationIcon));
            }

        }
        return personBuilder;
    }

    /**
     * A sender URI is authoritative.  Only a missing URI may use the local
     * application icon; an empty URI is treated as missing because config
     * replacement can intentionally clear an older value.
     */
    static boolean shouldUseApplicationIconFallback(@Nullable String senderIcon) {
        return senderIcon == null || senderIcon.trim().isEmpty();
    }

    @Nullable
    private static Bitmap getApplicationIconForConversation(
            Context context, String packageName, PushMetaInfo metaInfo) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            Bitmap icon = Global.IconCache().getRawIconBitmap(context, packageName);
            if (icon != null && !icon.isRecycled()) {
                return prepareLargeIconForNotification(context, metaInfo, icon);
            }
        } catch (Throwable error) {
            logger.w("Unable to resolve local conversation application icon", error);
        }
        return null;
    }

    private static void setLargeIconFromPerson(
            NotificationCompat.Builder notificationBuilder, Person person) {
        if (notificationBuilder == null || person == null || person.getIcon() == null) {
            return;
        }
        try {
            Bitmap icon = person.getIcon().getBitmap();
            if (icon != null && !icon.isRecycled()) {
                notificationBuilder.setLargeIcon(icon);
            }
        } catch (Throwable ignored) {
            // Resource/URI-backed Person icons are handled by SystemUI. Only
            // bitmap-backed icons can be copied to Notification.largeIcon.
        }
    }

    private static void carryPendingIntentForTemporarilyWhitelisted(Context xmPushService, XmPushActionContainer buildContainer, NotificationCompat.Builder localBuilder) {
        PushMetaInfo metaInfo = buildContainer.getMetaInfo();
        // Also carry along the target PendingIntent, whose target will get temporarily whitelisted for background-activity-start upon sent.
        final Intent targetIntent = buildTargetIntentWithoutExtras(
                publishPackageName(buildContainer), metaInfo);
        final PendingIntent pi = PendingIntent.getService(xmPushService, 0, targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        localBuilder.getExtras().putParcelable("mipush.target", pi);
    }

    public static int getNotificationId(XmPushActionContainer container) {
        final PushMetaInfo metaInfo = container.getMetaInfo();
        String id = metaInfo.isSetNotifyId() ? String.valueOf(metaInfo.getNotifyId()) : metaInfo.getId();
        String idWithPackage = publishPackageName(container) + "_" + id;
        return idWithPackage.hashCode();
    }

    public static String getNotificationTag(String packageName) {
        return "mipush_" + packageName;
    }

    public static String getNotificationTag(XmPushActionContainer container) {
        return getNotificationTag(publishPackageName(container));
    }

    private static String getGroupName(Context xmPushService, XmPushActionContainer buildContainer) {
        PushMetaInfo metaInfo = buildContainer.getMetaInfo();
        String packageName = publishPackageName(buildContainer);
        RegisteredApplication application = RegisteredApplicationDb.getRegisteredApplication(packageName);

        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        String configuredGroup = configuration.notificationGroup(null);
        if (configuredGroup != null && !configuredGroup.trim().isEmpty()) {
            String group = configuredGroup;
            group = packageName + "_" + GROUP_TYPE_MIPUSH_GROUP + "_" + group;
            return group;
        } else if (metaInfo.passThrough == 1) {
            return packageName + "_" + GROUP_TYPE_PASS_THROUGH;
        } else {
            CustomConfiguration.FocusNotificationPayload focusPayload =
                    configuration.focusNotificationPayload();
            boolean hasDeliverableFocusPayload =
                    (FocusNotificationSafety.isWellFormedParameter(focusPayload.parameter())
                            || FocusNotificationSafety.isWellFormedParameter(
                                    focusPayload.customParameter())
                            || !focusPayload.pictureUrls().isEmpty());
            if (FocusNotificationSafety.shouldIsolateFocusGroup(
                    configuredGroup, hasDeliverableFocusPayload)) {
                return FocusNotificationSafety.stableFocusGroup(packageName);
            }
        }
        // This is the SDK's historical default for ordinary notifications. A
        // focus payload takes the isolated branch above unless the sender gave
        // an explicit official group, preventing SystemUI from folding it into
        // unrelated package notifications.
        return packageName;
    }

    private static void addDebugAction(Context xmPushService, XmPushActionContainer buildContainer, byte[] var1, PushMetaInfo metaInfo, String packageName, NotificationCompat.Builder localBuilder) {
        if (Global.ConfigCenter().isDebugMode()) {
            int i = R.drawable.ic_notifications_black_24dp;

            PendingIntent pendingIntentOpenActivity = openActivityPendingIntent(xmPushService, buildContainer, metaInfo, var1);
            if (pendingIntentOpenActivity != null) {
                localBuilder.addAction(new NotificationCompat.Action(i, "Open App", pendingIntentOpenActivity));
            }

            PendingIntent pendingIntentJump = startServicePendingIntent(xmPushService, buildContainer, metaInfo, var1);
            if (pendingIntentJump != null) {
                localBuilder.addAction(new NotificationCompat.Action(i, "Jump", pendingIntentJump));
            }

            Intent sdkIntentJump = getSdkIntent(xmPushService, buildContainer);
            if (sdkIntentJump != null) {
                PendingIntent pendingIntent = PendingIntent.getActivity(xmPushService, 0,
                        sdkIntentJump,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                localBuilder.addAction(new NotificationCompat.Action(i, "SDK Intent", pendingIntent));
            }
        }
    }

    public static Intent buildTargetIntentWithoutExtras(final String pkg, final PushMetaInfo metaInfo) {
        return new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).addCategory(String.valueOf(metaInfo.getNotifyId()))
                .setClassName(pkg, CLASS_NAME_PUSH_MESSAGE_HANDLER);
    }

    private static PendingIntent openActivityPendingIntent(Context paramContext, XmPushActionContainer paramXmPushActionContainer, PushMetaInfo paramPushMetaInfo, byte[] paramArrayOfByte) {
        String packageName = publishPackageName(paramXmPushActionContainer);
        PackageManager packageManager = paramContext.getPackageManager();
        Intent localIntent1 = packageManager.getLaunchIntentForPackage(packageName);
        if (localIntent1 != null) {
            localIntent1.addCategory(String.valueOf(paramPushMetaInfo.getNotifyId()));
            return PendingIntent.getActivity(paramContext, 0, localIntent1,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        return null;
    }

    private static ClickPendingIntent getClickedPendingIntent(
            Context context, XmPushActionContainer container, byte[] decryptedContent,
            int notificationId, Bundle extra, boolean messagingStyle) {
        PushMetaInfo metaInfo = container.getMetaInfo();
        if (metaInfo == null) {
            return null;
        }
        String targetPackage = publishPackageName(container);

        // Resolve a safe sender-declared route before the legacy web shortcut:
        // a configuration may have replaced an SDK intent with a URL, and the
        // shortcut would otherwise make that replacement impossible to audit.
        ClickRouteResolution restoredSenderRoute = resolveRestoredSenderClickRoute(
                context, container, decryptedContent);

        //Jump web
        String urlJump = null;
        if (!TextUtils.isEmpty(metaInfo.url)) {
            urlJump = metaInfo.url;
        } else if (metaInfo.getExtra() != null) {
            urlJump = metaInfo.getExtra().get(PushConstants.EXTRA_PARAM_WEB_URI);
        }

        if (restoredSenderRoute == null && !TextUtils.isEmpty(urlJump)) {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse(urlJump));
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            return ClickPendingIntent.activity(PendingIntent.getActivity(
                    context, notificationId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.xiaomi.xmsf",
                isBusinessMessage(container) ?
                        "com.xiaomi.mipush.sdk.PushMessageHandler" :
                        "com.xiaomi.push.sdk.MyPushMessageHandler"));
        intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, decryptedContent);
        intent.putExtra(FROM_NOTIFICATION, true);
        intent.putExtras(extra);
        intent.addCategory(String.valueOf(metaInfo.getNotifyId()));

        CustomConfiguration configuration = XMPushUtils.getConfiguration(metaInfo);
        // Prefer a validated client Activity for every ordinary notification. The
        // historical service PendingIntent requires a background service to call
        // startActivity(), which Android 16/HyperOS may reject even though the
        // notification click itself is user initiated. A package launcher is a
        // safe fallback when the sender did not provide notify_effect metadata.
        ClickRouteResolution clickRoute = restoredSenderRoute != null
                ? restoredSenderRoute : resolveSdkClickRoute(context, container);
        Intent activityIntent = clickRoute == null ? null : clickRoute.intent;
        if (activityIntent == null) {
            activityIntent = getLaunchIntent(context, targetPackage);
        }
        boolean replaySenderRoute = shouldUseReplayClickTrampoline(
                NotificationReplayMarker.isMarked(container),
                clickRoute != null,
                clickRoute != null && clickRoute.discoveredRoute);
        // Keep the setting tri-state: an absent key selects the direct Activity
        // path, while an explicitly supplied false can still request the
        // historical service PendingIntent for live-notification compatibility.
        // Historical replays with an official sender route always use the
        // SDK-first Activity hand-off because stale vendor bridge tokens must
        // never re-enter the legacy Service click path.
        Boolean explicitSetting = configuration.keys().contains("use_clicked_activity")
                ? configuration.useClickedActivity(false)
                : null;
        boolean useActivity = shouldUseActivityClick(
                explicitSetting, messagingStyle, activityIntent, replaySenderRoute);
        if (!useActivity) {
            return ClickPendingIntent.service(PendingIntent.getService(
                    context, notificationId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        // A sender may publish a private proxy Activity (the common pattern for
        // MiPush bridge implementations). XMSF cannot start a non-exported
        // Activity because Android enforces the target UID at PendingIntent
        // send time. Route those clicks through an isolated user-click hand-off;
        // it delivers the payload to the target SDK first and opens the target
        // launcher only as a bounded fallback. Exported routes use the direct
        // Activity PendingIntent so HyperOS can provide its normal conversation
        // and floating-window affordances.
        boolean targetActivityExported = isActivityExported(context, activityIntent);
        if (shouldUseClickTrampoline(replaySenderRoute, targetActivityExported)) {
            Intent clickTrampoline = new Intent(context,
                    com.xiaomi.xmsf.NotificationClickActivity.class);
            clickTrampoline.putExtras(extra);
            clickTrampoline.putExtra(
                    com.xiaomi.xmsf.NotificationClickActivity.EXTRA_TARGET_INTENT,
                    activityIntent);
            clickTrampoline.putExtra(
                    com.xiaomi.xmsf.NotificationClickActivity.EXTRA_SERVICE_INTENT,
                    intent);
            clickTrampoline.putExtra(
                    com.xiaomi.xmsf.NotificationClickActivity.EXTRA_TARGET_ACTIVITY_PRIVATE,
                    !targetActivityExported);
            clickTrampoline.putExtra(
                    com.xiaomi.xmsf.NotificationClickActivity.EXTRA_MANUAL_REPLAY,
                    replaySenderRoute);
            clickTrampoline.putExtra(
                    com.xiaomi.xmsf.NotificationClickActivity.EXTRA_TARGET_PACKAGE,
                    targetPackage);
            clickTrampoline.setData(new Uri.Builder()
                    .scheme("xmsf-notification")
                    .authority("click")
                    .appendPath(targetPackage)
                    .appendPath(Integer.toString(notificationId))
                    .build());
            clickTrampoline.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            return ClickPendingIntent.activity(PendingIntent.getActivity(
                    context, notificationId, clickTrampoline,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        // Keep the official MiPush click contract for sender-declared routes
        // and launcher fallback: launch the validated client Activity directly
        // and attach the original service Intent under the standard bridge key.
        // Focus/payload-discovered deep links are already complete routes and
        // must remain free of unrelated MiPush bridge extras.
        if (shouldAttachMiPushBridgeExtras(
                clickRoute != null && clickRoute.discoveredRoute)) {
            activityIntent.putExtra("mipush_serviceIntent", intent);
            activityIntent.putExtras(intent);
        }
        return ClickPendingIntent.activity(PendingIntent.getActivity(
                context, notificationId, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    /** API-independent PendingIntent type contract; PendingIntent.isActivity() requires API 31. */
    private static final class ClickPendingIntent {
        final PendingIntent pendingIntent;
        final boolean activity;

        private ClickPendingIntent(PendingIntent pendingIntent, boolean activity) {
            this.pendingIntent = pendingIntent;
            this.activity = activity;
        }

        static ClickPendingIntent activity(PendingIntent pendingIntent) {
            return new ClickPendingIntent(pendingIntent, true);
        }

        static ClickPendingIntent service(PendingIntent pendingIntent) {
            return new ClickPendingIntent(pendingIntent, false);
        }
    }

    /** Only a service contentIntent needs HyperOS's auxiliary service whitelist token. */
    static boolean shouldCarryTemporaryWhitelist(boolean activityPendingIntent) {
        return !activityPendingIntent;
    }

    /** SDK-owned/private routes use the isolated hand-off; exported live routes stay direct. */
    static boolean shouldUseClickTrampoline(
            boolean replaySenderRoute, boolean targetActivityExported) {
        return replaySenderRoute || !targetActivityExported;
    }

    /**
     * Sender-declared routes and launcher fallback consume the original MiPush
     * bridge payload. Routes inferred from focus parameters or encrypted
     * application payloads are already complete deep links and must stay clean,
     * matching the target application's own notification PendingIntent.
     */
    static boolean shouldAttachMiPushBridgeExtras(boolean discoveredRoute) {
        return !discoveredRoute;
    }

    /** Only manual replays of an actual sender route use the SDK-first hand-off. */
    static boolean shouldUseReplayClickTrampoline(
            boolean replay, boolean routePresent, boolean discoveredRoute) {
        return replay && routePresent && !discoveredRoute;
    }

    /**
     * Preserve the sender's navigation contract when a presentation
     * configuration rewrites click metadata. External deep links can open the
     * correct detail page but make it the root of a new task; the sender's
     * official bridge retains application-specific routing and back-stack
     * behavior. A configuration can explicitly opt into its replacement route
     * through {@link #ALLOW_CLICK_ROUTE_REWRITE}.
     */
    @Nullable
    private static ClickRouteResolution resolveRestoredSenderClickRoute(
            Context context, XmPushActionContainer configuredContainer,
            @Nullable byte[] originalPayload) {
        XmPushActionContainer senderContainer = null;
        try {
            senderContainer = XMPushUtils.packToContainer(originalPayload);
        } catch (Throwable error) {
            logger.d("Unable to restore sender notification click metadata");
        }

        if (senderContainer != null
                && configuredContainer != null
                && Objects.equals(publishPackageName(senderContainer),
                publishPackageName(configuredContainer))
                && shouldPreferSenderClickContract(
                senderContainer.getMetaInfo(), configuredContainer.getMetaInfo())) {
            ClickRouteResolution senderRoute =
                    resolveSdkClickRoute(context, senderContainer);
            if (senderRoute != null
                    && !senderRoute.discoveredRoute
                    && isActivityExported(context, senderRoute.intent)) {
                logger.d("Restoring sender-declared notification click route for "
                        + publishPackageName(configuredContainer));
                return senderRoute;
            }
        }
        return null;
    }

    /**
     * Returns whether configuration changed fields that define the notification
     * click contract and did not explicitly opt into that rewrite. Styling,
     * grouping and focus-rendering metadata are deliberately ignored.
     */
    static boolean shouldPreferSenderClickContract(
            @Nullable PushMetaInfo senderMeta,
            @Nullable PushMetaInfo configuredMeta) {
        if (senderMeta == null || configuredMeta == null) {
            return false;
        }
        Map<String, String> configuredExtra = configuredMeta.getExtra();
        String rewriteOptIn = configuredExtra == null
                ? null : configuredExtra.get(ALLOW_CLICK_ROUTE_REWRITE);
        if (rewriteOptIn != null
                && "true".equalsIgnoreCase(rewriteOptIn.trim())) {
            return false;
        }
        if (!Objects.equals(senderMeta.url, configuredMeta.url)) {
            return true;
        }
        Map<String, String> senderExtra = senderMeta.getExtra();
        for (String key : CLICK_ROUTE_EXTRA_KEYS) {
            String senderValue = senderExtra == null ? null : senderExtra.get(key);
            String configuredValue =
                    configuredExtra == null ? null : configuredExtra.get(key);
            if (!Objects.equals(senderValue, configuredValue)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isActivityExported(Context context, @Nullable Intent activityIntent) {
        if (context == null || activityIntent == null) {
            return false;
        }
        try {
            ResolveInfo resolved = context.getPackageManager()
                    .resolveActivity(activityIntent, PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null && resolved.activityInfo != null
                    && resolved.activityInfo.exported;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * HyperOS exposes the conversation mini-window affordance only when the
     * notification click is an Activity PendingIntent. Messaging notifications
     * already carry a validated target Activity through their SDK intent. All
     * notification types use that path by default; the service PendingIntent is
     * retained only when configuration explicitly opts out or no Activity can
     * be resolved.
     */
    static boolean shouldUseActivityClick(
            @Nullable Boolean explicitSetting, boolean messagingStyle,
            @Nullable Intent activityIntent, boolean replaySenderRoute) {
        // A missing/invalid target can never be upgraded to an Activity
        // PendingIntent. The caller supplies only intents validated against the
        // target package, while this guard keeps the fallback safe for all paths.
        if (activityIntent == null) {
            return false;
        }
        // A manual replay has no valid live vendor-click token to fall back to.
        // Keep it in the user-initiated SDK hand-off even when an old per-app
        // compatibility setting requested the legacy Service PendingIntent.
        if (replaySenderRoute) {
            return true;
        }
        // Explicit configuration always wins over the MessagingStyle default,
        // including an explicit false. An absent setting now uses the direct
        // Activity path for both ordinary and MessagingStyle notifications;
        // this is required for reliable Android 16 background-click handling.
        return explicitSetting == null || explicitSetting;
    }

    /**
     * Returns the target package's exported launcher Activity after checking the
     * resolved component. This prevents an implicit launcher intent from being
     * redirected to another package on unusual ROMs.
     */
    @Nullable
    private static Intent getLaunchIntent(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            Intent launchIntent = context.getPackageManager()
                    .getLaunchIntentForPackage(packageName);
            if (launchIntent == null) {
                return null;
            }
            ResolveInfo resolved = context.getPackageManager()
                    .resolveActivity(launchIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (!isResolvedActivityInTargetPackage(packageName, resolved)) {
                return null;
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return launchIntent;
        } catch (Throwable error) {
            logger.w("Unable to resolve launcher Activity for " + packageName, error);
            return null;
        }
    }

    /**
     * @see PushMessageProcessor#getNotificationMessageIntent
     */
    public static Intent getSdkIntent(Context context, XmPushActionContainer container) {
        ClickRouteResolution route = resolveSdkClickRoute(context, container);
        return route == null ? null : route.intent;
    }

    @Nullable
    private static ClickRouteResolution resolveSdkClickRoute(
            Context context, XmPushActionContainer container) {
        if (context == null || container == null
                || TextUtils.isEmpty(publishPackageName(container))) {
            return null;
        }
        String pkgName = publishPackageName(container);
        PushMetaInfo paramPushMetaInfo = container.getMetaInfo();
        if (paramPushMetaInfo == null) {
            return null;
        }
        Map<String, String> extra = paramPushMetaInfo.getExtra();
        String typeId = extra == null ? null : extra.get(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT);
        Intent intent = null;
        if (PushConstants.NOTIFICATION_CLICK_DEFAULT.equals(typeId)) {
            try {
                intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);
            } catch (Exception e2) {
                logger.e("Cause: " + e2.getMessage());
            }
        } else if (PushConstants.NOTIFICATION_CLICK_INTENT.equals(typeId)) {

            if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_URI)) {
                String intentStr = extra.get(PushConstants.EXTRA_PARAM_INTENT_URI);
                if (intentStr != null) {
                    try {
                        intent = Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME);
                        intent.setPackage(pkgName);
                    } catch (URISyntaxException e3) {
                        logger.e("Cause: " + e3.getMessage());
                    }
                }
            } else {
                if (extra.containsKey(PushConstants.EXTRA_PARAM_CLASS_NAME)) {
                    String className = (String) extra.get(PushConstants.EXTRA_PARAM_CLASS_NAME);
                    intent = new Intent();
                    intent.setComponent(new ComponentName(pkgName, className));
                    try {
                        if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_FLAG)) {
                            intent.setFlags(Integer.parseInt(extra.get(PushConstants.EXTRA_PARAM_INTENT_FLAG)));
                        }
                    } catch (NumberFormatException e4) {
                        logger.e("Cause by intent_flag: " + e4.getMessage());
                    }

                }
            }
        } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE.equals(typeId)) {
            String uri = extra.get(PushConstants.EXTRA_PARAM_WEB_URI);

            MalformedURLException e;

            if (uri != null) {
                String tmp = uri.trim();
                if (!(tmp.startsWith("http://") || tmp.startsWith("https://"))) {
                    tmp = "http://" + tmp;
                }
                try {
                    String protocol = new URL(tmp).getProtocol();
                    if (!"http".equals(protocol)) {
                        if (!"https".equals(protocol)) {
                            //why ?
                        }
                    }
                    Intent intent2 = new Intent("android.intent.action.VIEW");
                    intent2.setData(Uri.parse(tmp));
                    intent = intent2;
                } catch (MalformedURLException e6) {
                    e = e6;
                    logger.e("Cause: " + e.getMessage());
                    return null;
                }
            }
        }

        // Focus notifications may carry the actual detail page in the public
        // miui.focus.param JSON instead of the ordinary MiPush click fields.
        // Resolve that route before falling back to the encrypted message
        // payload; this is generic and keeps delivery pages usable for every
        // sender that follows the focus protocol.
        Intent focusIntent = getFocusRouteIntent(context, container);

        // Some SDKs keep the actual deep link in the encrypted SendMessage
        // payload rather than in metaInfo.extra. Decode it with the stored
        // registration secret and inspect only documented route-like fields;
        // this remains app-agnostic and lets clients without click metadata
        // work without package-specific adapters.
        //
        // An explicit notify_effect/intent_uri is the sender's official bridge
        // contract. Keep it authoritative: sender proxy Activities normally
        // consume the complete MiPush click extras.
        // A few clients publish a private proxy, however. XMSF cannot launch
        // that Activity under its own UID, so an exported route discovered in
        // the encrypted payload is safer and more useful than retaining an
        // unusable private component. Keep the official route for exported
        // Activities; only private routes may be replaced by an exported
        // payload deep link.
        Intent payloadIntent = getPayloadRouteIntent(context, container);
        // A default-launcher effect is only a generic fallback. If the client
        // also supplied a concrete deep link in its encrypted payload, prefer
        // that link. Explicit routes remain authoritative while they resolve
        // to an exported Activity; private routes are replaced above.
        Intent authoritativeSdkRoute =
                PushConstants.NOTIFICATION_CLICK_DEFAULT.equals(typeId) ? null : intent;
        if (authoritativeSdkRoute != null
                && focusIntent != null
                && !isActivityExported(context, authoritativeSdkRoute)
                && isActivityExported(context, focusIntent)) {
            authoritativeSdkRoute = focusIntent;
        }
        if (authoritativeSdkRoute != null
                && payloadIntent != null
                && !isActivityExported(context, authoritativeSdkRoute)
                && isActivityExported(context, payloadIntent)) {
            authoritativeSdkRoute = payloadIntent;
        }
        intent = chooseClickRoute(authoritativeSdkRoute, focusIntent, payloadIntent);


        if (intent != null) {
            boolean discoveredRoute = isDiscoveredClickRoute(
                    intent, focusIntent, payloadIntent);
            // Activity PendingIntents use the standard target-task contract.
            // addFlags preserves all flags explicitly supplied by the sender.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo resolvedActivity = context.getPackageManager()
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (isResolvedActivityInTargetPackage(pkgName, resolvedActivity)) {
                // A package-only Intent is still implicit at PendingIntent send
                // time. HyperOS/Android 16 may resolve it again under a
                // different foreground policy (or reject it while the shade is
                // closing), which makes a notification appear to do nothing.
                // Freeze the component selected by PackageManager after the
                // package ownership check so the user click has a deterministic
                // destination. Keep the sender-defined action, data, flags and
                // extras because URI routes may rely on all of them.
                intent = makeResolvedActivityExplicit(pkgName, intent, resolvedActivity);
                if (intent == null) {
                    return null;
                }

                return new ClickRouteResolution(intent, discoveredRoute);
            }
        }

        return null;
    }

    private static final class ClickRouteResolution {
        final Intent intent;
        final boolean discoveredRoute;

        ClickRouteResolution(Intent intent, boolean discoveredRoute) {
            this.intent = intent;
            this.discoveredRoute = discoveredRoute;
        }
    }

    private static final int PAYLOAD_ROUTE_MAX_DEPTH = 8;
    private static final int PAYLOAD_ROUTE_MAX_NODES = 256;
    /** Maximum size of a single URI/intent route extracted from a payload. */
    private static final int PAYLOAD_ROUTE_MAX_LENGTH = 16 * 1024;
    /**
     * Do not truncate JSON before parsing it. A truncated document is invalid
     * and silently forces a launcher fallback. Reject truly unreasonable
     * documents instead.
     */
    private static final int PAYLOAD_DOCUMENT_MAX_LENGTH = 64 * 1024;

    /**
     * Selects the sender-provided click bridge before a route discovered in the
     * encrypted application payload. The latter is only a fallback for clients
     * that did not publish a notify_effect route at all.
     */
    @Nullable
    static Intent chooseClickRoute(
            @Nullable Intent explicitSdkRoute, @Nullable Intent payloadRoute) {
        return chooseClickRoute(explicitSdkRoute, null, payloadRoute);
    }

    /**
     * Select the sender's explicit route, then a public focus-protocol route,
     * then a route discovered in the encrypted application payload.
     */
    @Nullable
    static Intent chooseClickRoute(
            @Nullable Intent explicitSdkRoute,
            @Nullable Intent focusRoute,
            @Nullable Intent payloadRoute) {
        if (explicitSdkRoute != null) {
            return explicitSdkRoute;
        }
        return focusRoute != null ? focusRoute : payloadRoute;
    }

    /**
     * Route selection intentionally preserves object identity so the caller can
     * distinguish an SDK-declared bridge from a deep link inferred by XMSF.
     */
    static boolean isDiscoveredClickRoute(
            @Nullable Intent selectedRoute,
            @Nullable Intent focusRoute,
            @Nullable Intent payloadRoute) {
        return selectedRoute != null
                && (selectedRoute == focusRoute || selectedRoute == payloadRoute);
    }

    @Nullable
    private static Intent getFocusRouteIntent(
            Context context, XmPushActionContainer container) {
        if (context == null || container == null
                || TextUtils.isEmpty(publishPackageName(container))) {
            return null;
        }
        try {
            CustomConfiguration configuration =
                    XMPushUtils.getConfiguration(container.getMetaInfo());
            String[] parameters = {
                    configuration.focusParam(null),
                    configuration.focusParamCustom(null)
            };
            for (String parameter : parameters) {
                if (!FocusNotificationSafety.isWellFormedParameter(parameter)) {
                    continue;
                }
                Intent route = findPayloadRoute(
                        context, publishPackageName(container), new JSONObject(parameter), 0,
                        new int[]{0});
                if (route != null) {
                    return route;
                }
            }
        } catch (Throwable error) {
            // Focus routing is optional. A malformed or unsupported focus
            // payload must retain the normal MiPush click fallback.
            logger.d("Unable to decode a focus notification click route");
        }
        return null;
    }

    @Nullable
    private static Intent getPayloadRouteIntent(Context context, XmPushActionContainer container) {
        try {
            String regSec = RegSecUtils.getRegSec(container);
            if (TextUtils.isEmpty(regSec)) {
                return null;
            }
            TBase messageBody = ConvertUtils.getResponseMessageBodyFromContainer(container, regSec);
            if (!(messageBody instanceof XmPushActionSendMessage)) {
                return null;
            }
            PushMessage message = ((XmPushActionSendMessage) messageBody).getMessage();
            if (message == null || TextUtils.isEmpty(message.getPayload())) {
                return null;
            }
            String payload = message.getPayload().trim();
            if (payload.length() > PAYLOAD_DOCUMENT_MAX_LENGTH) {
                return null;
            }
            int[] nodeCount = new int[]{0};
            if (payload.startsWith("{")) {
                return findPayloadRoute(context, publishPackageName(container),
                        new JSONObject(payload), 0, nodeCount);
            }
            if (payload.startsWith("[")) {
                return findPayloadRoute(context, publishPackageName(container),
                        new JSONArray(payload), 0, nodeCount);
            }
            return resolvePayloadRoute(context, publishPackageName(container), payload);
        } catch (Throwable error) {
            // Missing registration secrets, malformed app payloads, and old
            // protocol variants must fall back to notify_effect/Launcher.
            logger.d("Unable to decode a notification payload route for "
                    + publishPackageName(container));
            return null;
        }
    }

    @Nullable
    private static Intent findPayloadRoute(
            Context context, String packageName, Object value, int depth, int[] nodeCount) {
        if (value == null || depth > PAYLOAD_ROUTE_MAX_DEPTH
                || nodeCount[0]++ >= PAYLOAD_ROUTE_MAX_NODES) {
            return null;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                if (child instanceof String && isPayloadRouteKey(key)) {
                    Intent candidate = resolvePayloadRoute(
                            context, packageName, (String) child);
                    if (candidate != null) {
                        return candidate;
                    }
                }
                Intent nested = findPayloadRoute(
                        context, packageName, child, depth + 1, nodeCount);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Intent nested = findPayloadRoute(
                        context, packageName, array.opt(i), depth + 1, nodeCount);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (value instanceof String) {
            String candidate = ((String) value).trim();
            if (candidate.startsWith("{") || candidate.startsWith("[")) {
                try {
                    Object nested = candidate.startsWith("{")
                            ? new JSONObject(candidate) : new JSONArray(candidate);
                    return findPayloadRoute(
                            context, packageName, nested, depth + 1, nodeCount);
                } catch (Throwable ignored) {
                    // Not nested JSON; continue with the safe no-route result.
                }
            }
        }
        return null;
    }

    private static boolean isPayloadRouteKey(String key) {
        if (TextUtils.isEmpty(key)) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("url")
                || normalized.equals("uri")
                || normalized.equals("scheme")
                || normalized.equals("jump_scheme")
                || normalized.equals("intent_uri")
                || normalized.equals("deep_link")
                || normalized.equals("deeplink")
                || normalized.equals("link")
                || normalized.endsWith("_url")
                || normalized.endsWith("_uri");
    }

    @Nullable
    private static Intent resolvePayloadRoute(Context context, String packageName, String value) {
        if (context == null || TextUtils.isEmpty(packageName) || TextUtils.isEmpty(value)) {
            return null;
        }
        String route = value.trim();
        if (route.length() == 0 || route.length() > PAYLOAD_ROUTE_MAX_LENGTH
                || route.indexOf('\n') >= 0 || route.indexOf('\r') >= 0) {
            return null;
        }
        try {
            Intent intent;
            if (route.startsWith("intent:")) {
                intent = Intent.parseUri(route, Intent.URI_INTENT_SCHEME);
            } else {
                Uri uri = Uri.parse(route);
                if (TextUtils.isEmpty(uri.getScheme())) {
                    return null;
                }
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            intent.setPackage(packageName);
            ResolveInfo resolved = context.getPackageManager()
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (!isResolvedActivityInTargetPackage(packageName, resolved)) {
                return null;
            }
            return makeResolvedActivityExplicit(packageName, intent, resolved);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Converts a package-only click intent into the exact Activity selected by
     * PackageManager. Keeping this small and side-effect free makes the
     * security boundary easy to exercise in unit tests.
     */
    @Nullable
    static Intent makeResolvedActivityExplicit(
            String targetPackage, @Nullable Intent intent, @Nullable ResolveInfo resolvedActivity) {
        if (intent == null
                || !isResolvedActivityInTargetPackage(targetPackage, resolvedActivity)
                || resolvedActivity.activityInfo.name == null
                || resolvedActivity.activityInfo.name.length() == 0) {
            return null;
        }
        intent.setComponent(new ComponentName(
                resolvedActivity.activityInfo.packageName,
                resolvedActivity.activityInfo.name));
        return intent;
    }

    /**
     * Ensures a click Activity resolved from push metadata cannot escape the
     * package that owns the notification. A null/empty target or incomplete
     * resolution is rejected using the safe service-pending-intent fallback.
     */
    static boolean isResolvedActivityInTargetPackage(String targetPackage, ResolveInfo resolveInfo) {
        return targetPackage != null
                && !targetPackage.isEmpty()
                && resolveInfo != null
                && resolveInfo.activityInfo != null
                && targetPackage.equals(resolveInfo.activityInfo.packageName);
    }

    private static PendingIntent startServicePendingIntent(Context paramContext, XmPushActionContainer paramXmPushActionContainer, PushMetaInfo paramPushMetaInfo, byte[] paramArrayOfByte) {
        if (paramPushMetaInfo == null) {
            return null;
        }

        Intent localIntent;
        if (isBusinessMessage(paramXmPushActionContainer)) {
            localIntent = new Intent();
            localIntent.setComponent(new ComponentName("com.xiaomi.xmsf", "com.xiaomi.mipush.sdk.PushMessageHandler"));
        } else {
            String targetPackage = publishPackageName(paramXmPushActionContainer);
            localIntent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
            localIntent.setComponent(new ComponentName(targetPackage, "com.xiaomi.mipush.sdk.PushMessageHandler"));
        }
        localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, paramArrayOfByte);
        localIntent.putExtra(FROM_NOTIFICATION, true);
        localIntent.addCategory(String.valueOf(paramPushMetaInfo.getNotifyId()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(paramContext, 0, localIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            return PendingIntent.getService(paramContext, 0, localIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
    }

    /**
     * @see MIPushNotificationHelper#determineTitleAndDespByDIP
     */
    private static String[] determineTitleAndDespByDIP(Context paramContext, PushMetaInfo paramPushMetaInfo) {

        try {
            return JavaCalls.callStaticMethodOrThrow(MIPushNotificationHelper.class, "determineTitleAndDespByDIP", paramContext, paramPushMetaInfo);
        } catch (Exception e) {
            logger.e(e.getMessage(), e);
            return new String[]{paramPushMetaInfo.getTitle(), paramPushMetaInfo.getDescription()};
        }
    }

    // from sdk 3.7.2
    @TargetApi(16)
    private static NotificationCompat.Builder setNotificationStyleAction(NotificationCompat.Builder builder, Context context, String pkgName, Map<String, String> metaExtra) {
        PendingIntent stylePendingIntent = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_LEFT, metaExtra);
        if (stylePendingIntent != null && !TextUtils.isEmpty(metaExtra.get(NOTIFICATION_STYLE_BUTTON_LEFT_NAME))) {
            builder.addAction(0, metaExtra.get(NOTIFICATION_STYLE_BUTTON_LEFT_NAME), stylePendingIntent);
        }
        PendingIntent stylePendingIntent2 = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_MID, metaExtra);
        if (stylePendingIntent2 != null && !TextUtils.isEmpty(metaExtra.get(NOTIFICATION_STYLE_BUTTON_MID_NAME))) {
            builder.addAction(0, metaExtra.get(NOTIFICATION_STYLE_BUTTON_MID_NAME), stylePendingIntent2);
        }
        PendingIntent stylePendingIntent3 = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT, metaExtra);
        if (stylePendingIntent3 != null && !TextUtils.isEmpty(metaExtra.get(NOTIFICATION_STYLE_BUTTON_RIGHT_NAME))) {
            builder.addAction(0, metaExtra.get(NOTIFICATION_STYLE_BUTTON_RIGHT_NAME), stylePendingIntent3);
        }
        if ("3".equals(metaExtra.get(NOTIFICATION_STYLE_TYPE))) {
            PendingIntent colorfulPendingIntent = getStylePendingIntent(
                    context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_COLORFUL, metaExtra);
            String colorfulButtonText = metaExtra.get(NOTIFICATION_COLORFUL_BUTTON_TEXT);
            if (colorfulPendingIntent != null && !TextUtils.isEmpty(colorfulButtonText)) {
                // Preserve the official Colorful button as a standard action
                // when MIUI's private RemoteViews implementation is unavailable.
                builder.addAction(0, colorfulButtonText, colorfulPendingIntent);
            }
        }
        return builder;
    }

    private static PendingIntent getStylePendingIntent(Context context, String pkgName, int place, Map<String, String> metaExtra) {
        Intent intent;
        if (metaExtra == null || (intent = getPendingIntentFromExtra(context, pkgName, place, metaExtra)) == null) {
            return null;
        }
        return PendingIntent.getActivity(context, place, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent getPendingIntentFromExtra(Context context, String pkgName, int place, Map<String, String> extra) {
        StyleActionKeys keys = styleActionKeys(place);
        String typeId = extra.get(keys.notifyEffect);
        if (TextUtils.isEmpty(typeId)) {
            return null;
        }
        Intent intent = null;
        if (PushConstants.NOTIFICATION_CLICK_DEFAULT.equals(typeId)) {
            try {
                intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);
            } catch (Exception e) {
                logger.e("Cause: " + e.getMessage());
            }
        } else if (PushConstants.NOTIFICATION_CLICK_INTENT.equals(typeId)) {
            if (extra.containsKey(keys.intentUri)) {
                String intentStr = extra.get(keys.intentUri);
                if (intentStr != null) {
                    try {
                        intent = Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME);
                        intent.setPackage(pkgName);
                    } catch (URISyntaxException e2) {
                        logger.e("Cause: " + e2.getMessage());
                    }
                }
            } else if (extra.containsKey(keys.intentClass)) {
                String className = extra.get(keys.intentClass);
                intent = new Intent();
                intent.setComponent(new ComponentName(pkgName, className));
            }
        } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE.equals(typeId)) {
            String uri = extra.get(keys.webUri);
            if (!TextUtils.isEmpty(uri)) {
                String tmp = uri.trim();
                if (!tmp.startsWith("http://") && !tmp.startsWith("https://")) {
                    tmp = "http://" + tmp;
                }
                try {
                    URL url = new URL(tmp);
                    String protocol = url.getProtocol();
                    if ("http".equals(protocol) || "https".equals(protocol)) {
                        intent = new Intent("android.intent.action.VIEW");
                        intent.setData(Uri.parse(tmp));
                    }
                } catch (MalformedURLException e3) {
                    logger.e("Cause: " + e3.getMessage());
                }
            }
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ResolveInfo rinfo = context.getPackageManager().resolveActivity(intent, Intent.FLAG_ACTIVITY_NO_ANIMATION);
                if (rinfo != null) {
                    return intent;
                }
            } catch (Exception e4) {
                logger.e("Cause: " + e4.getMessage());
            }
        }
        return null;
    }

    static StyleActionKeys styleActionKeys(int place) {
        if (place == NOTIFICATION_ACTION_BUTTON_PLACE_COLORFUL) {
            return COLORFUL_ACTION_KEYS;
        }
        if (place < NOTIFICATION_ACTION_BUTTON_PLACE_MID) {
            return LEFT_ACTION_KEYS;
        }
        if (place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT) {
            return MID_ACTION_KEYS;
        }
        return RIGHT_ACTION_KEYS;
    }

    static final class StyleActionKeys {
        final String notifyEffect;
        final String intentUri;
        final String intentClass;
        final String webUri;

        StyleActionKeys(String notifyEffect, String intentUri,
                        String intentClass, String webUri) {
            this.notifyEffect = notifyEffect;
            this.intentUri = intentUri;
            this.intentClass = intentClass;
            this.webUri = webUri;
        }
    }


}
