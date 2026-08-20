package com.xiaomi.xmsf.push.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, payload-free status for the most recently published configuration load.
 *
 * <p>The snapshot deliberately contains only structured configuration-reference metadata. It
 * never retains source JSON, exceptions, or notification payloads.</p>
 */
public final class ConfigurationDiagnosticsSnapshot {
    public enum Status {
        NOT_CONFIGURED,
        READY,
        FAILED
    }

    private static final ConfigurationDiagnosticsSnapshot NOT_CONFIGURED =
            new ConfigurationDiagnosticsSnapshot(Status.NOT_CONFIGURED, Collections.emptyList());
    private static final ConfigurationDiagnosticsSnapshot FAILED =
            new ConfigurationDiagnosticsSnapshot(Status.FAILED, Collections.emptyList());

    private final Status status;
    private final List<ConfigurationReferenceDiagnostics.UnresolvedReference>
            unresolvedReferences;

    private ConfigurationDiagnosticsSnapshot(
            Status status,
            List<ConfigurationReferenceDiagnostics.UnresolvedReference> unresolvedReferences) {
        this.status = Objects.requireNonNull(status, "status");
        this.unresolvedReferences = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(
                        unresolvedReferences, "unresolvedReferences")));
    }

    public static ConfigurationDiagnosticsSnapshot notConfigured() {
        return NOT_CONFIGURED;
    }

    public static ConfigurationDiagnosticsSnapshot ready(
            List<ConfigurationReferenceDiagnostics.UnresolvedReference> unresolvedReferences) {
        return new ConfigurationDiagnosticsSnapshot(Status.READY, unresolvedReferences);
    }

    public static ConfigurationDiagnosticsSnapshot failed() {
        return FAILED;
    }

    public Status getStatus() {
        return status;
    }

    public List<ConfigurationReferenceDiagnostics.UnresolvedReference> getUnresolvedReferences() {
        return unresolvedReferences;
    }
}
