package com.xiaomi.push.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.elvishew.xlog.XLog;

import org.junit.BeforeClass;
import org.junit.Test;

/** Pure click-routing contracts that do not depend on Android framework mocks. */
public class NotificationClickPolicyTest {
    @BeforeClass
    public static void initializeLogging() {
        XLog.init();
    }

    @Test
    public void onlyServiceContentIntentCarriesAuxiliaryWhitelistToken() {
        assertTrue(MyMIPushNotificationHelper.shouldCarryTemporaryWhitelist(false));
        assertFalse(MyMIPushNotificationHelper.shouldCarryTemporaryWhitelist(true));
    }

    @Test
    public void onlyPrivateOrReplayRoutesUseClickTrampoline() {
        assertTrue(MyMIPushNotificationHelper.shouldUseClickTrampoline(false, false));
        assertTrue(MyMIPushNotificationHelper.shouldUseClickTrampoline(true, true));
        assertFalse(MyMIPushNotificationHelper.shouldUseClickTrampoline(false, true));
    }

    @Test
    public void discoveredFocusRoutesRemainDirectAndBridgeExtraFree() {
        assertFalse(MyMIPushNotificationHelper.shouldUseClickTrampoline(false, true));
        assertFalse(MyMIPushNotificationHelper.shouldAttachMiPushBridgeExtras(true));
    }

    @Test
    public void liveEmptyQueryBridgeUsesTheReplayHandoff() {
        boolean liveSdkFirst = MyMIPushNotificationHelper.shouldDispatchSenderBridgeThroughSdk(
                false, "android.intent.action.VIEW", "agoo://example.test/thirdpush?");
        boolean replaySdkFirst = MyMIPushNotificationHelper.shouldUseReplayClickTrampoline(
                true, true, false);
        assertTrue(MyMIPushNotificationHelper.shouldUseClickTrampoline(liveSdkFirst, true));
        assertTrue(MyMIPushNotificationHelper.shouldUseClickTrampoline(replaySdkFirst, true));
        assertTrue(MyMIPushNotificationHelper.shouldDispatchSenderBridgeThroughSdk(
                false, "android.intent.action.VIEW", "agoo://example.test/thirdpush?&#fragment"));
    }

    @Test
    public void completeAndDiscoveredRoutesDoNotBecomeSdkBridges() {
        for (String uri : new String[] { null, "app://host/detail", "app://host/detail?id=42&",
                "app://host/detail#fragment?", "app://host/detail?q=%3F" }) {
            assertFalse(MyMIPushNotificationHelper.shouldDispatchSenderBridgeThroughSdk(
                    false, "android.intent.action.VIEW", uri));
        }
        assertFalse(MyMIPushNotificationHelper.shouldDispatchSenderBridgeThroughSdk(
                true, "android.intent.action.VIEW", "app://host/detail?"));
        assertFalse(MyMIPushNotificationHelper.shouldDispatchSenderBridgeThroughSdk(
                false, "example.third.push", "app://host/detail?"));
    }

    @Test
    public void incompletePayloadUriIsRejectedWithoutChangingExplicitRoutes() {
        assertFalse(MyMIPushNotificationHelper.hasUsablePayloadRouteSyntax(
                "agoo://example.test/detail?"));
        assertFalse(MyMIPushNotificationHelper.hasUsablePayloadRouteSyntax(
                "agoo://example.test/detail?&"));
        assertTrue(MyMIPushNotificationHelper.hasUsablePayloadRouteSyntax(
                "agoo://example.test/detail?orderId=42"));
        assertTrue(MyMIPushNotificationHelper.hasUsablePayloadRouteSyntax(
                "agoo://example.test/thirdpush"));
    }
}
