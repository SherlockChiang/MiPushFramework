package com.xiaomi.xmsf.push.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class ConfigurationsLoaderReferenceDiagnosticTest {
    @Test
    public void reportsOnlyMissingReferencesWithoutPayload() {
        ConfigurationReferenceDiagnostics.UnresolvedReference missing = diagnostic(
                "2_post_config.json", "com.example.app", "$-direct-open-intent");
        ConfigurationReferenceDiagnostics.UnresolvedReference valid = diagnostic(
                "consumer.json", "com.example.app", "shared-rule");

        List<ConfigurationReferenceDiagnostics.UnresolvedReference> diagnostics =
                ConfigurationReferenceDiagnostics.resolve(
                        new HashSet<>(Arrays.asList("shared-rule", "private notification message")),
                        Arrays.asList(missing, valid));

        assertEquals(1, diagnostics.size());
        ConfigurationReferenceDiagnostics.UnresolvedReference diagnostic = diagnostics.get(0);
        assertEquals("2_post_config.json", diagnostic.getSourceName());
        assertEquals("com.example.app", diagnostic.getOwnerKey());
        assertEquals("$-direct-open-intent", diagnostic.getReference());
        assertTrue(!diagnostic.toString().contains("private notification message"));
    }

    @Test
    public void validReferenceProducesNoDiagnostic() {
        ConfigurationReferenceDiagnostics.UnresolvedReference valid = diagnostic(
                "consumer.json", "com.example.app", "shared-rule");

        assertTrue(ConfigurationReferenceDiagnostics.resolve(
                new HashSet<>(Arrays.asList("shared-rule")),
                Arrays.asList(valid)).isEmpty());
    }

    @Test
    public void diagnosticsAreSortedAndDeduplicated() {
        ConfigurationReferenceDiagnostics.UnresolvedReference zRule = diagnostic(
                "consumer.json", "owner", "z-rule");
        ConfigurationReferenceDiagnostics.UnresolvedReference aRule = diagnostic(
                "consumer.json", "owner", "a-rule");

        List<ConfigurationReferenceDiagnostics.UnresolvedReference> diagnostics =
                ConfigurationReferenceDiagnostics.resolve(
                        new HashSet<>(), Arrays.asList(zRule, aRule, zRule));
        assertEquals(2, diagnostics.size());
        assertEquals("a-rule", diagnostics.get(0).getReference());
        assertEquals("z-rule", diagnostics.get(1).getReference());
    }

    private static ConfigurationReferenceDiagnostics.UnresolvedReference diagnostic(
            String sourceName, String ownerKey, String reference) {
        return ConfigurationReferenceDiagnostics.referenceSite(sourceName, ownerKey, reference);
    }
}
