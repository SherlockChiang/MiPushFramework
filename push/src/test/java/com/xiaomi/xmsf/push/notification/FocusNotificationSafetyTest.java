package com.xiaomi.xmsf.push.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class FocusNotificationSafetyTest {

    @Test
    public void keepsExistingReadableFieldsAndFillsOnlyMissingField() {
        FocusNotificationSafety.ResolvedContent result =
                FocusNotificationSafety.resolveReadableContent(
                        "normal title", "",
                        "{\"ticker\":\"Ticker\",\"title\":\"Focus title\","
                                + "\"description\":\"Focus description\"}",
                        "Fallback title", "Fallback body");

        assertEquals("normal title", result.title());
        assertEquals("Focus description", result.body());
    }

    @Test
    public void usesTickerWhenFocusTitleAndMetaTitleAreEmpty() {
        FocusNotificationSafety.ResolvedContent result =
                FocusNotificationSafety.resolveReadableContent(
                        null, null,
                        "{\"ticker\":\"Ticker\",\"title\":\"\","
                                + "\"description\":\"Description\"}",
                        "Fallback title", "Fallback body");

        assertEquals("Ticker", result.title());
        assertEquals("Description", result.body());
    }

    @Test
    public void malformedAndOversizedParametersStillProduceSafeText() {
        FocusNotificationSafety.ResolvedContent malformed =
                FocusNotificationSafety.resolveReadableContent(
                        " ", "\t", "not-json", "App", "Body");
        assertEquals("App", malformed.title());
        assertEquals("Body", malformed.body());

        String oversized = "{\"title\":\"" + "x".repeat(4_000) + "\"}";
        FocusNotificationSafety.ResolvedContent tooLarge =
                FocusNotificationSafety.resolveReadableContent(
                        null, null, oversized, "App", "Body");
        assertNotNull(tooLarge.title());
        assertNotNull(tooLarge.body());
        assertFalse(tooLarge.title().isEmpty());
        assertFalse(tooLarge.body().isEmpty());
    }

    @Test
    public void boundsFocusParameterByUtf8Bytes() {
        assertTrue(FocusNotificationSafety.isParameterWithinLimit("a".repeat(3_072)));
        assertFalse(FocusNotificationSafety.isParameterWithinLimit("a".repeat(3_073)));
        assertFalse(FocusNotificationSafety.isParameterWithinLimit("😀".repeat(1_000)));
    }

    @Test
    public void imageEnrichmentUsesSmallGlobalCallerBudget() {
        assertTrue(FocusNotificationSafety.IMAGE_ENRICHMENT_BUDGET_MILLIS > 0L);
        assertTrue(FocusNotificationSafety.IMAGE_ENRICHMENT_BUDGET_MILLIS <= 750L);
    }

    @Test
    public void malformedFocusParameterIsRejectedBeforePrivateDelivery() {
        assertTrue(FocusNotificationSafety.isWellFormedParameter("{}"));
        assertTrue(FocusNotificationSafety.isWellFormedParameter(
                "{\"ticker\":\"hello\"}"));
        assertFalse(FocusNotificationSafety.isWellFormedParameter("not-json"));
        assertFalse(FocusNotificationSafety.isWellFormedParameter("[]"));
        assertFalse(FocusNotificationSafety.isWellFormedParameter(
                "{\"x\":\"" + "x".repeat(3_100) + "\"}"));
    }

    @Test
    public void customFocusParameterUsesTheSameBoundedJsonObjectContract() {
        assertTrue(FocusNotificationSafety.isWellFormedParameter(
                "{\"business\":\"tsmclient\",\"param_island\":{}}"));
        assertFalse(FocusNotificationSafety.isWellFormedParameter("\"not-an-object\""));
        assertFalse(FocusNotificationSafety.isWellFormedParameter("[]"));
    }

    @Test
    public void deeplyNestedJsonCannotBreakTheStandardFallback() {
        String deeplyNested = "[".repeat(1_200) + "0" + "]".repeat(1_200);

        assertTrue(FocusNotificationSafety.isParameterWithinLimit(deeplyNested));
        assertFalse(FocusNotificationSafety.isWellFormedParameter(deeplyNested));

        FocusNotificationSafety.ResolvedContent result =
                FocusNotificationSafety.resolveReadableContent(
                        null, null, deeplyNested, "App", "Body");
        assertEquals("App", result.title());
        assertEquals("Body", result.body());
    }

    @Test
    public void focusEnrichmentFailureRetriesOnceWithSameIdentity() {
        List<String> identities = new ArrayList<>();
        List<Boolean> focusFlags = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

        String result = FocusNotificationSafety.deliverWithSingleFallback(
                "client.example", "mipush_client.example", 42, true,
                (packageName, tag, id, includeFocus, focusFailure) -> {
                    identities.add(packageName + "|" + tag + "|" + id);
                    focusFlags.add(includeFocus);
                    failures.add(focusFailure);
                    if (includeFocus) {
                        // Models image/Bundle/Binder failure from the optional path.
                        throw new IllegalStateException("focus enrichment failed");
                    }
                    return "standard";
                });

        assertEquals("standard", result);
        assertEquals(Arrays.asList(
                "client.example|mipush_client.example|42",
                "client.example|mipush_client.example|42"), identities);
        assertEquals(Arrays.asList(true, false), focusFlags);
        assertEquals(null, failures.get(0));
        assertNotNull(failures.get(1));
    }

    @Test
    public void disabledFocusDoesNotRetryOrTouchStandardPath() {
        int[] calls = {0};
        String result = FocusNotificationSafety.deliverWithSingleFallback(
                "client.example", "tag", 7, false,
                (packageName, tag, id, includeFocus, focusFailure) -> {
                    calls[0]++;
                    assertFalse(includeFocus);
                    assertEquals(null, focusFailure);
                    return "standard";
                });

        assertEquals("standard", result);
        assertEquals(1, calls[0]);
    }

    @Test
    public void fallbackFailureIsNotRetriedAgain() {
        int[] calls = {0};
        try {
            FocusNotificationSafety.deliverWithSingleFallback(
                    "client.example", "tag", 9, true,
                    (packageName, tag, id, includeFocus, focusFailure) -> {
                        calls[0]++;
                        throw new IllegalStateException(includeFocus
                                ? "focus failed" : "standard failed");
                    });
            fail("fallback failure must propagate");
        } catch (IllegalStateException expected) {
            assertEquals("standard failed", expected.getMessage());
        }
        assertEquals(2, calls[0]);
    }

    @Test
    public void focusExtraPrefixCoversParamPicturesAndNativeBundle() {
        Map<String, String> extras = new HashMap<>();
        extras.put("miui.focus.param", "{}");
        extras.put("miui.focus.pic_0", "https://example.test/p.png");
        extras.put("miui.focus.pics", "native-icons");
        extras.put("ordinary.key", "keep");

        for (String key : new ArrayList<>(extras.keySet())) {
            if (FocusNotificationSafety.isFocusExtraKey(key)) {
                extras.remove(key);
            }
        }

        assertEquals(1, extras.size());
        assertTrue(extras.containsKey("ordinary.key"));
    }

    @Test
    public void stableFocusGroupIsDeterministic() {
        assertEquals("client.example_#focus#",
                FocusNotificationSafety.stableFocusGroup("client.example"));
        assertEquals(FocusNotificationSafety.stableFocusGroup("client.example"),
                FocusNotificationSafety.stableFocusGroup("client.example"));
    }

    @Test
    public void onlyUngroupedFocusPayloadUsesIsolatedGroup() {
        assertTrue(FocusNotificationSafety.shouldIsolateFocusGroup(null, true));
        assertTrue(FocusNotificationSafety.shouldIsolateFocusGroup("", true));
        assertFalse(FocusNotificationSafety.shouldIsolateFocusGroup(
                "client.example_#group#_official", true));
        assertFalse(FocusNotificationSafety.shouldIsolateFocusGroup(null, false));
    }
}
