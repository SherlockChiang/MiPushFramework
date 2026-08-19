package com.xiaomi.xmsf.push.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.elvishew.xlog.XLog;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConfigurationDiagnosticsSnapshotTest {
    @Before
    public void setUp() {
        XLog.init();
    }

    @Test
    public void loaderStartsNotConfiguredWithSharedImmutableEmptyList() {
        ConfigurationsLoader loader = new ConfigurationsLoader();

        ConfigurationDiagnosticsSnapshot snapshot = loader.getDiagnosticsSnapshot();

        assertEquals(
                ConfigurationDiagnosticsSnapshot.Status.NOT_CONFIGURED,
                snapshot.getStatus());
        assertTrue(snapshot.getUnresolvedReferences().isEmpty());
        assertSame(snapshot.getUnresolvedReferences(), loader.getUnresolvedReferences());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.getUnresolvedReferences().add(diagnostic("missing")));
    }

    @Test
    public void readySnapshotCanContainNoUnresolvedReferences() {
        ConfigurationDiagnosticsSnapshot snapshot =
                ConfigurationDiagnosticsSnapshot.ready(Collections.emptyList());

        assertEquals(ConfigurationDiagnosticsSnapshot.Status.READY, snapshot.getStatus());
        assertTrue(snapshot.getUnresolvedReferences().isEmpty());
    }

    @Test
    public void readySnapshotDefensivelyCopiesUnresolvedReferences() {
        List<ConfigurationReferenceDiagnostics.UnresolvedReference> source = new ArrayList<>();
        source.add(diagnostic("missing"));

        ConfigurationDiagnosticsSnapshot snapshot =
                ConfigurationDiagnosticsSnapshot.ready(source);
        source.clear();

        assertEquals(ConfigurationDiagnosticsSnapshot.Status.READY, snapshot.getStatus());
        assertEquals(1, snapshot.getUnresolvedReferences().size());
        assertEquals("missing", snapshot.getUnresolvedReferences().get(0).getReference());
    }

    @Test
    public void failedSnapshotCannotExposeStaleOrPartialDiagnostics() {
        ConfigurationDiagnosticsSnapshot previous = ConfigurationDiagnosticsSnapshot.ready(
                Collections.singletonList(diagnostic("stale-reference")));

        ConfigurationDiagnosticsSnapshot failed = ConfigurationDiagnosticsSnapshot.failed();

        assertEquals(1, previous.getUnresolvedReferences().size());
        assertEquals(ConfigurationDiagnosticsSnapshot.Status.FAILED, failed.getStatus());
        assertTrue(failed.getUnresolvedReferences().isEmpty());
    }

    @Test
    public void successfulMemoryLoadPublishesReadyStateAndMissingReference()
            throws JSONException {
        ConfigurationsLoader loader = new ConfigurationsLoader();

        loader.load(
                "memory-source.json",
                "{\"version\":\"1\",\"configs\":{\"consumer\":[\"missing-rule\"]}}");

        ConfigurationDiagnosticsSnapshot snapshot = loader.getDiagnosticsSnapshot();
        assertEquals(ConfigurationDiagnosticsSnapshot.Status.READY, snapshot.getStatus());
        assertEquals(1, snapshot.getUnresolvedReferences().size());
        assertEquals(
                "memory-source.json",
                snapshot.getUnresolvedReferences().get(0).getSourceName());
        assertTrue(loader.getConfigs().containsKey("consumer"));
        assertSame(snapshot.getUnresolvedReferences(), loader.getUnresolvedReferences());
        assertThrows(
                UnsupportedOperationException.class,
                () -> loader.getConfigs().get("consumer").add("another-reference"));
    }

    @Test
    public void successfulEmptyMemoryLoadPublishesReadyWithNoDiagnostics()
            throws JSONException {
        ConfigurationsLoader loader = new ConfigurationsLoader();

        loader.load("{\"version\":\"1\",\"configs\":{}}");

        assertEquals(
                ConfigurationDiagnosticsSnapshot.Status.READY,
                loader.getDiagnosticsSnapshot().getStatus());
        assertTrue(loader.getDiagnosticsSnapshot().getUnresolvedReferences().isEmpty());
        assertTrue(loader.getConfigs().isEmpty());
    }

    @Test
    public void nullDirectoryRequestResetsPublishedStateToNotConfigured()
            throws JSONException {
        ConfigurationsLoader loader = new ConfigurationsLoader();
        loader.load(
                "good.json",
                "{\"version\":\"1\",\"configs\":{\"consumer\":[\"missing-rule\"]}}");

        assertTrue(!loader.init(null, null));

        assertEquals(
                ConfigurationDiagnosticsSnapshot.Status.NOT_CONFIGURED,
                loader.getDiagnosticsSnapshot().getStatus());
        assertTrue(loader.getDiagnosticsSnapshot().getUnresolvedReferences().isEmpty());
        assertTrue(loader.getConfigs().isEmpty());
    }

    @Test
    public void failedMemoryLoadPreservesPreviouslyPublishedSnapshotAndConfigs()
            throws JSONException {
        ConfigurationsLoader loader = new ConfigurationsLoader();
        loader.load(
                "good.json",
                "{\"version\":\"1\",\"configs\":{\"consumer\":[\"missing-rule\"]}}");
        ConfigurationDiagnosticsSnapshot previousSnapshot = loader.getDiagnosticsSnapshot();
        Map<String, List<Object>> previousConfigs = loader.getConfigs();

        assertThrows(
                JSONException.class,
                () -> loader.load(
                        "bad.json",
                        "{\"version\":\"2\",\"configs\":{\"partial\":[],\"broken\":{}}}"));

        assertSame(previousSnapshot, loader.getDiagnosticsSnapshot());
        assertSame(previousConfigs, loader.getConfigs());
        assertEquals(1, loader.getUnresolvedReferences().size());
        assertTrue(loader.getConfigs().containsKey("consumer"));
        assertTrue(!loader.getConfigs().containsKey("partial"));
    }

    private static ConfigurationReferenceDiagnostics.UnresolvedReference diagnostic(
            String reference) {
        return ConfigurationReferenceDiagnostics.referenceSite(
                "source.json", "owner", reference);
    }
}
