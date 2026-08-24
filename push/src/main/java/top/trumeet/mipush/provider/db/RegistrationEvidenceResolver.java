package top.trumeet.mipush.provider.db;

import androidx.annotation.Nullable;

import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult;

import java.util.HashSet;
import java.util.Set;

import top.trumeet.mipush.provider.entities.Event;
import top.trumeet.mipush.provider.entities.RegisteredApplication;

/** Resolves registration state from protocol evidence without package-name rules. */
public final class RegistrationEvidenceResolver {
    private RegistrationEvidenceResolver() {
    }

    public enum EventEvidence {
        POSITIVE,
        NEGATIVE,
        UNKNOWN
    }

    /**
     * A decoded successful registration repairs stale SDK state first. The persisted
     * unregistered flag then rejects late/in-flight messages before receive evidence or the
     * active registry are considered; older negative control evidence is the final fallback.
     */
    public static @RegisteredApplication.RegisteredType int resolve(
            boolean explicitlyUnregistered,
            boolean activeRegistryContainsPackage,
            boolean latestControlEventIsPositive,
            boolean newerReceiveEvidence,
            boolean latestControlEventIsNegative) {
        if (latestControlEventIsPositive) {
            return RegisteredApplication.RegisteredType.Registered;
        }
        if (explicitlyUnregistered) {
            return RegisteredApplication.RegisteredType.Unregistered;
        }
        if (newerReceiveEvidence) {
            return RegisteredApplication.RegisteredType.Registered;
        }
        if (activeRegistryContainsPackage) {
            return RegisteredApplication.RegisteredType.Registered;
        }
        if (latestControlEventIsNegative) {
            return RegisteredApplication.RegisteredType.Unregistered;
        }
        return RegisteredApplication.RegisteredType.NotRegistered;
    }

    /** A registration result only counts when decoded with an explicit error code. */
    public static EventEvidence classifyEvent(
            @Event.Type int eventType,
            @Nullable XmPushActionRegistrationResult registrationResult) {
        if (eventType == Event.Type.UnRegistration) {
            return EventEvidence.NEGATIVE;
        }
        if (eventType != Event.Type.RegistrationResult
                || registrationResult == null
                || !registrationResult.isSetErrorCode()) {
            return EventEvidence.UNKNOWN;
        }
        return registrationResult.getErrorCode() == 0
                ? EventEvidence.POSITIVE : EventEvidence.NEGATIVE;
    }

    /** A delivered message supersedes older control evidence, but not an equal-time event. */
    public static boolean isReceiveEvidenceNewer(
            long lastReceiveTime, @Nullable Long latestControlEvidenceTime) {
        return lastReceiveTime > 0
                && (latestControlEvidenceTime == null
                || lastReceiveTime > latestControlEvidenceTime);
    }

    /** Parse the SDK's persisted comma-separated package set without relying on its cache. */
    public static Set<String> parsePersistedPackageSet(@Nullable String persistedPackages) {
        Set<String> packages = new HashSet<>();
        if (persistedPackages == null || persistedPackages.trim().isEmpty()) {
            return packages;
        }
        for (String candidate : persistedPackages.split(",")) {
            String packageName = candidate.trim();
            if (!packageName.isEmpty()) {
                packages.add(packageName);
            }
        }
        return packages;
    }
}
