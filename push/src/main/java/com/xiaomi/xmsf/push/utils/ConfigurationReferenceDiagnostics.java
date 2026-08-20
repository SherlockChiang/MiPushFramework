package com.xiaomi.xmsf.push.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Pure-Java resolution and metadata for configuration-to-configuration references. */
public final class ConfigurationReferenceDiagnostics {
    private ConfigurationReferenceDiagnostics() {
    }

    public static UnresolvedReference referenceSite(
            String sourceName, String ownerKey, String reference) {
        return new UnresolvedReference(sourceName, ownerKey, reference);
    }

    public static List<UnresolvedReference> resolve(
            Collection<String> loadedConfigKeys,
            Collection<UnresolvedReference> referenceSites) {
        HashSet<UnresolvedReference> unresolved = new HashSet<>();
        for (UnresolvedReference site : referenceSites) {
            if (!loadedConfigKeys.contains(site.getReference())) {
                unresolved.add(site);
            }
        }
        List<UnresolvedReference> sorted = new ArrayList<>(unresolved);
        sorted.sort(Comparator
                .comparing(UnresolvedReference::getSourceName)
                .thenComparing(UnresolvedReference::getOwnerKey)
                .thenComparing(UnresolvedReference::getReference));
        return Collections.unmodifiableList(sorted);
    }

    /** Structured config metadata only; notification payloads are never fields of this type. */
    public static final class UnresolvedReference {
        private final String sourceName;
        private final String ownerKey;
        private final String reference;

        private UnresolvedReference(String sourceName, String ownerKey, String reference) {
            this.sourceName = sourceName == null ? "<unknown>" : sourceName;
            this.ownerKey = Objects.requireNonNull(ownerKey, "ownerKey");
            this.reference = Objects.requireNonNull(reference, "reference");
        }

        public String getSourceName() {
            return sourceName;
        }

        public String getOwnerKey() {
            return ownerKey;
        }

        public String getReference() {
            return reference;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof UnresolvedReference)) {
                return false;
            }
            UnresolvedReference that = (UnresolvedReference) other;
            return sourceName.equals(that.sourceName)
                    && ownerKey.equals(that.ownerKey)
                    && reference.equals(that.reference);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceName, ownerKey, reference);
        }

        @Override
        public String toString() {
            return "UnresolvedReference{"
                    + "sourceName='" + sourceName + '\''
                    + ", ownerKey='" + ownerKey + '\''
                    + ", reference='" + reference + '\''
                    + '}';
        }
    }
}
