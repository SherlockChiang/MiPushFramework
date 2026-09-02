package com.xiaomi.push.sdk;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.nihility.XMPushUtils;
import com.nihility.utils.NotificationReplayMarker;
import com.xiaomi.push.service.MIPushNotificationHelper;
import com.xiaomi.push.service.MyMIPushNotificationHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/**
 * Capability-based SDK click delivery for routes owned by the target MiPush SDK.
 *
 * <p>A notification click may enter XMSF first when the sender-declared Activity
 * is private. Starting the target service directly while the target UID is
 * still backgrounded is rejected by modern Android. This dispatcher uses an
 * immutable, one-shot PendingIntent directly from the user-click trampoline,
 * allowing supported platform/OEM implementations to retain the
 * user-initiated hand-off metadata. The target launcher is only a bounded
 * fallback when SDK delivery produces no visible UI. Neither mechanism
 * requires package-specific routing.</p>
 */
public final class TargetSdkClickDispatcher {
    private static final String SDK_SERVICE_CLASS =
            MyMIPushNotificationHelper.CLASS_NAME_PUSH_MESSAGE_HANDLER;
    private static final String RECEIVER_PERMISSION_SUFFIX =
            ".permission.MIPUSH_RECEIVE";

    public enum DispatchResult {
        SERVICE_DELIVERY_ACCEPTED,
        BROADCAST_DELIVERY_ACCEPTED,
        UNAVAILABLE,
        FAILED;

        /** Delivery acceptance is deliberately not a claim that navigation completed. */
        public boolean isAccepted() {
            return this == SERVICE_DELIVERY_ACCEPTED
                    || this == BROADCAST_DELIVERY_ACCEPTED;
        }
    }

    enum Kind {
        SERVICE,
        RECEIVER
    }

    static final class Candidate {
        final String packageName;
        final String className;
        final boolean enabled;
        final boolean exported;

        Candidate(String packageName, String className, boolean enabled, boolean exported) {
            this.packageName = packageName;
            this.className = className;
            this.enabled = enabled;
            this.exported = exported;
        }
    }

    static final class Capability {
        final Kind kind;
        final Candidate candidate;

        Capability(Kind kind, Candidate candidate) {
            this.kind = kind;
            this.candidate = candidate;
        }
    }

    interface CapabilitySource {
        @Nullable Candidate service(String targetPackage, String serviceClass);

        List<Candidate> receivers(String targetPackage, String action);
    }

    private TargetSdkClickDispatcher() {
    }

    static String receiverPermission(String targetPackage) {
        return targetPackage == null ? null : targetPackage + RECEIVER_PERMISSION_SUFFIX;
    }

    public static DispatchResult dispatchReplay(
            Context context, @Nullable XmPushActionContainer replayContainer) {
        if (context == null || replayContainer == null
                || MyMIPushNotificationHelper.getNotificationTargetPackage(replayContainer)
                .isEmpty()) {
            return DispatchResult.UNAVAILABLE;
        }
        XmPushActionContainer targetContainer =
                NotificationReplayMarker.copyWithoutMarker(replayContainer);
        if (targetContainer == null) {
            return DispatchResult.FAILED;
        }
        byte[] targetPayload;
        try {
            targetPayload = XMPushUtils.packToBytes(targetContainer);
        } catch (Throwable ignored) {
            return DispatchResult.FAILED;
        }

        return dispatchPayload(context, targetContainer, targetPayload);
    }

    /** Deliver an unmodified live-notification payload through the target SDK. */
    public static DispatchResult dispatchPayload(
            Context context,
            @Nullable XmPushActionContainer container,
            @Nullable byte[] targetPayload) {
        if (context == null || container == null || targetPayload == null
                || targetPayload.length == 0
                || MyMIPushNotificationHelper.getNotificationTargetPackage(container).isEmpty()) {
            return DispatchResult.UNAVAILABLE;
        }
        String targetPackage = MyMIPushNotificationHelper
                .getNotificationTargetPackage(container);
        final Capability capability;
        try {
            capability = selectCapability(
                    targetPackage, new AndroidCapabilitySource(context.getPackageManager()));
        } catch (Throwable ignored) {
            return DispatchResult.FAILED;
        }
        if (capability == null) {
            return DispatchResult.UNAVAILABLE;
        }

        Intent targetIntent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE)
                .setPackage(targetPackage)
                .putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, targetPayload)
                .putExtra(PushConstants.MESSAGE_RECEIVE_TIME,
                        Long.toString(System.currentTimeMillis()))
                .putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true);
        PushMetaInfo metaInfo = container.getMetaInfo();

        try {
            if (capability.kind == Kind.SERVICE) {
                targetIntent.setComponent(new ComponentName(
                        capability.candidate.packageName, capability.candidate.className));
                if (metaInfo != null) {
                    targetIntent.addCategory(String.valueOf(metaInfo.getNotifyId()));
                }
                sendAsUserInitiatedPendingIntent(
                        context, targetIntent, capability.kind, targetPackage, metaInfo);
                return DispatchResult.SERVICE_DELIVERY_ACCEPTED;
            }
            // Match the Xiaomi SDK's normal broadcast contract. Keep the
            // package scope so every receiver registered by the target SDK can
            // participate (Agoo/vendor bridges often fan out internally), while
            // the package-scoped permission lets signature/privileged receivers
            // accept the replay.
            sendAsUserInitiatedPendingIntent(
                    context, targetIntent, capability.kind, targetPackage, metaInfo);
            return DispatchResult.BROADCAST_DELIVERY_ACCEPTED;
        } catch (Throwable ignored) {
            return DispatchResult.FAILED;
        }
    }

    private static void sendAsUserInitiatedPendingIntent(
            Context context,
            Intent targetIntent,
            Kind kind,
            String targetPackage,
            @Nullable PushMetaInfo metaInfo) throws PendingIntent.CanceledException {
        int notifyId = metaInfo == null ? 0 : metaInfo.getNotifyId();
        int requestCode = deliveryRequestCode(targetPackage, notifyId, kind);
        int flags = PendingIntent.FLAG_CANCEL_CURRENT
                | PendingIntent.FLAG_ONE_SHOT
                | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = kind == Kind.SERVICE
                ? PendingIntent.getService(context, requestCode, targetIntent, flags)
                : PendingIntent.getBroadcast(context, requestCode, targetIntent, flags);

        Bundle options = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions activityOptions = ActivityOptions.makeBasic();
            activityOptions.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            options = activityOptions.toBundle();
        }
        String requiredPermission = kind == Kind.RECEIVER
                ? receiverPermission(targetPackage) : null;
        pendingIntent.send(context, 0, null, null, null, requiredPermission, options);
    }

    static int deliveryRequestCode(String targetPackage, int notifyId, Kind kind) {
        String identity = String.valueOf(targetPackage) + ':' + notifyId + ':' + kind;
        return identity.hashCode();
    }

    @Nullable
    static Capability selectCapability(String targetPackage, CapabilitySource source) {
        if (targetPackage == null || source == null) {
            return null;
        }
        Candidate service = source.service(targetPackage, SDK_SERVICE_CLASS);
        if (isUsable(targetPackage, service)) {
            return new Capability(Kind.SERVICE, service);
        }

        List<Candidate> receivers = source.receivers(
                targetPackage, PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
        if (receivers == null || receivers.isEmpty()) {
            return null;
        }
        List<Candidate> ordered = new ArrayList<>(receivers.size());
        for (Candidate receiver : receivers) {
            if (isUsable(targetPackage, receiver)) {
                ordered.add(receiver);
            }
        }
        Collections.sort(ordered, Comparator.comparing(candidate -> candidate.className));
        for (Candidate receiver : ordered) {
            return new Capability(Kind.RECEIVER, receiver);
        }
        return null;
    }

    private static boolean isUsable(String targetPackage, @Nullable Candidate candidate) {
        return candidate != null && candidate.enabled && candidate.exported
                && targetPackage.equals(candidate.packageName)
                && candidate.className != null && !candidate.className.isEmpty();
    }

    private static final class AndroidCapabilitySource implements CapabilitySource {
        private final PackageManager packageManager;

        AndroidCapabilitySource(PackageManager packageManager) {
            this.packageManager = packageManager;
        }

        @Override
        @SuppressWarnings("deprecation")
        public Candidate service(String targetPackage, String serviceClass) {
            ComponentName component = new ComponentName(targetPackage, serviceClass);
            try {
                ServiceInfo info = packageManager.getServiceInfo(component, 0);
                return candidate(info, component);
            } catch (PackageManager.NameNotFoundException ignored) {
                return null;
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public List<Candidate> receivers(String targetPackage, String action) {
            List<ResolveInfo> resolved = packageManager.queryBroadcastReceivers(
                    new Intent(action).setPackage(targetPackage), 0);
            if (resolved == null || resolved.isEmpty()) {
                return Collections.emptyList();
            }
            List<Candidate> candidates = new ArrayList<>(resolved.size());
            for (ResolveInfo resolveInfo : resolved) {
                ActivityInfo info = resolveInfo == null ? null : resolveInfo.activityInfo;
                if (info != null) {
                    candidates.add(candidate(info,
                            new ComponentName(info.packageName, info.name)));
                }
            }
            return candidates;
        }

        private Candidate candidate(ComponentInfo info, ComponentName component) {
            boolean applicationEnabled = info.applicationInfo == null
                    || info.applicationInfo.enabled;
            return new Candidate(info.packageName, info.name,
                    applicationEnabled && isComponentEnabled(info, component), info.exported);
        }

        private boolean isComponentEnabled(ComponentInfo info, ComponentName component) {
            int setting = packageManager.getComponentEnabledSetting(component);
            if (setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return true;
            }
            if (setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                return false;
            }
            return info.enabled;
        }
    }
}
