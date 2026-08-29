package com.xiaomi.xmsf.push.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    public void parsesObservedDeliveryProgressWithoutApplicationSpecificRules() {
        int[] observedProgress = {0, 10, 35, 50, 75, 100};
        for (int progress : observedProgress) {
            FocusNotificationSafety.PortableFocusData result =
                    FocusNotificationSafety.parsePortableFocusData(
                            "{\"business\":\"food_delivery\",\"progress\":"
                                    + progress + ",\"updatable\":true}");

            assertTrue(result.hasProgress());
            assertEquals(progress, result.progress());
            assertTrue(result.updatable());
        }
    }

    @Test
    public void parsesNestedProgressAndReadableDeliveryFields() {
        FocusNotificationSafety.PortableFocusData result =
                FocusNotificationSafety.parsePortableFocusData(
                        "{\"title\":\"Arrives at 20:12\","
                                + "\"content\":\"Courier is delivering\","
                                + "\"url\":\"https://example.test/order/42\","
                                + "\"sequence\":1787400588773,"
                                + "\"progressCount\":2,"
                                + "\"param_v2\":{\"progressInfo\":{\"progress\":75}}}");

        assertEquals("Arrives at 20:12", result.title());
        assertEquals("Courier is delivering", result.body());
        assertEquals("https://example.test/order/42", result.url());
        assertEquals("1787400588773", result.sequence());
        assertEquals(2, result.progressCount());
        assertEquals(75, result.progress());
    }

    @Test
    public void nestedBaseInfoFillsMissingPortableText() {
        FocusNotificationSafety.PortableFocusData result =
                FocusNotificationSafety.parsePortableFocusData(
                        "{\"param_v2\":{\"baseInfo\":{"
                                + "\"title\":\"Order accepted\","
                                + "\"content\":\"Preparing food\"}}}");

        assertEquals("Order accepted", result.title());
        assertEquals("Preparing food", result.body());
    }

    @Test
    public void paramV2AodTitleIsLastReadableTitleFallback() {
        FocusNotificationSafety.PortableFocusData result =
                FocusNotificationSafety.parsePortableFocusData(
                        "{\"param_v2\":{\"aodTitle\":\"Delivered\"}}");

        assertEquals("Delivered", result.title());
    }

    @Test
    public void invalidProgressFallsBackOrClampsSafely() {
        FocusNotificationSafety.PortableFocusData nestedFallback =
                FocusNotificationSafety.parsePortableFocusData(
                        "{\"progress\":-1,\"param_v2\":{"
                                + "\"progressInfo\":{\"progress\":35}}}");
        assertEquals(35, nestedFallback.progress());

        FocusNotificationSafety.PortableFocusData clamped =
                FocusNotificationSafety.parsePortableFocusData(
                        "{\"progress\":1000}");
        assertEquals(100, clamped.progress());

        FocusNotificationSafety.PortableFocusData missing =
                FocusNotificationSafety.parsePortableFocusData(
                        "{\"progress\":-5}");
        assertFalse(missing.hasProgress());
    }

    @Test
    public void malformedAndOversizedPortableDataIsEmpty() {
        FocusNotificationSafety.PortableFocusData malformed =
                FocusNotificationSafety.parsePortableFocusData("not-json");
        assertFalse(malformed.hasProgress());
        assertNull(malformed.title());
        assertNull(malformed.body());
        assertNull(malformed.url());

        FocusNotificationSafety.PortableFocusData oversized =
                FocusNotificationSafety.parsePortableFocusData(
                        "{\"content\":\"" + "x".repeat(4_000) + "\"}");
        assertFalse(oversized.hasProgress());
        assertNull(oversized.body());
    }

    @Test
    public void contentAliasIsUsedForReadableFallbackBody() {
        FocusNotificationSafety.ResolvedContent result =
                FocusNotificationSafety.resolveReadableContent(
                        null, null,
                        "{\"title\":\"Delivery\",\"content\":\"On the way\"}",
                        "App", "New notification");

        assertEquals("Delivery", result.title());
        assertEquals("On the way", result.body());
    }

    @Test
    public void findsApplicationIconAliasInsideParamV2AndArrays() {
        String parameter = "{\"business\":\"food_delivery\","
                + "\"param_v2\":{\"image\":{\"pic\":\""
                + FocusNotificationSafety.FOCUS_APP_ICON_PICTURE + "\"}},"
                + "\"images\":[\"other\",\""
                + FocusNotificationSafety.FOCUS_APP_ICON_PICTURE + "\"]}";

        assertTrue(FocusNotificationSafety.referencesPictureAlias(
                parameter, FocusNotificationSafety.FOCUS_APP_ICON_PICTURE));
    }

    @Test
    public void applicationIconAliasDoesNotMatchMalformedOrSubstringValues() {
        assertFalse(FocusNotificationSafety.referencesPictureAlias(
                "{\"param_v2\":{\"pic\":\"miui.focus.pic_app_icon_extra\"}}",
                FocusNotificationSafety.FOCUS_APP_ICON_PICTURE));
        assertFalse(FocusNotificationSafety.referencesPictureAlias(
                "not-json", FocusNotificationSafety.FOCUS_APP_ICON_PICTURE));
        assertFalse(FocusNotificationSafety.referencesPictureAlias(
                "{\"param_v2\":{\"pic\":\""
                        + FocusNotificationSafety.FOCUS_APP_ICON_PICTURE + "\"}}",
                ""));
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
