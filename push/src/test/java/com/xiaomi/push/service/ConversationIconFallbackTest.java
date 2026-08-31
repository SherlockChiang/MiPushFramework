package com.xiaomi.push.service;

import org.junit.Test;
import org.junit.BeforeClass;

import com.elvishew.xlog.XLog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression coverage for conversation-avatar fallback selection.  The
 * resolver must only be consulted when a configuration did not provide a
 * sender URI; it must never turn an explicit URI failure into a different
 * account lookup.
 */
public class ConversationIconFallbackTest {
    @BeforeClass
    public static void initializeLogging() {
        XLog.init();
    }

    @Test
    public void missingSenderUriUsesLocalApplicationFallback() {
        assertTrue(MyMIPushNotificationHelper.shouldUseApplicationIconFallback(null));
        assertTrue(MyMIPushNotificationHelper.shouldUseApplicationIconFallback(""));
        assertTrue(MyMIPushNotificationHelper.shouldUseApplicationIconFallback("  "));
    }

    @Test
    public void explicitSenderUriRemainsAuthoritative() {
        assertFalse(MyMIPushNotificationHelper
                .shouldUseApplicationIconFallback("https://q.qlogo.cn/g?b=qq&nk=123"));
        assertFalse(MyMIPushNotificationHelper
                .shouldUseApplicationIconFallback("content://com.example/avatar"));
    }
}
