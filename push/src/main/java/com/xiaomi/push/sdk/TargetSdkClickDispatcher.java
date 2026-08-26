package com.xiaomi.push.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

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

/** Capability-based SDK click delivery used only for historical notification replay. */
public final class TargetSdkClickDispatcher {
    private static final String SDK_SERVICE_CLASS =
            MyMIPushNotificationHelper.CLASS_NAME_PUSH_MESSAGE_HANDLER;
    private static final String RECEIVER_PERMISSION_SUFFIX =
            ".permission.MIPUSH_RECEIVE";

    public enum DispatchResult {
        SERVICE_STARTED,
        BROADCAST_SENT,
        UNAVAILABLE,
        FAILED;

        public boolean isSuccess() {
            return this == SERVICE_STARTED || this == BROADCAST_SENT;
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

    /** A successful SDK hand-off owns navigation; only failure may open the launcher. */
    public static boolean shouldLaunchReplayFallback(DispatchResult result) {
        return result == null || !result.isSuccess();
    }

    static String receiverPermission(String targetPackage) {
        return targetPackage == null ? null : targetPackage + RECEIVER_PERMISSION_SUFFIX;
    }

    public static DispatchResult dispatchReplay(
            Context context, @Nullable XmPushActionContainer replayContainer) {
        if (context == null || replayContainer == null
                || replayContainer.getPackageName() == null) {
            return DispatchResult.UNAVAILABLE;
        }
        String targetPackage = replayContainer.getPackageName();
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

        Intent targetIntent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE)
                .setPackage(targetPackage)
                .putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, targetPayload)
                .putExtra(PushConstants.MESSAGE_RECEIVE_TIME,
                        Long.toString(System.currentTimeMillis()))
                .putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true);
        PushMetaInfo metaInfo = targetContainer.getMetaInfo();

        try {
            if (capability.kind == Kind.SERVICE) {
                targetIntent.setComponent(new ComponentName(
                        capability.candidate.packageName, capability.candidate.className));
                if (metaInfo != null) {
                    targetIntent.addCategory(String.valueOf(metaInfo.getNotifyId()));
                }
                return context.startService(targetIntent) == null
                        ? DispatchResult.FAILED : DispatchResult.SERVICE_STARTED;
            }
            // Match the Xiaomi SDK's normal broadcast contract. Keep the
            // package scope so every receiver registered by the target SDK can
            // participate (Agoo/vendor bridges often fan out internally), while
            // the package-scoped permission lets signature/privileged receivers
            // accept the replay.
            context.sendBroadcast(targetIntent, receiverPermission(targetPackage));
            return DispatchResult.BROADCAST_SENT;
        } catch (Throwable ignored) {
            return DispatchResult.FAILED;
        }
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
