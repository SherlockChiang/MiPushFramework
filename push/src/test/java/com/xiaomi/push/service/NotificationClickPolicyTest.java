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
}
