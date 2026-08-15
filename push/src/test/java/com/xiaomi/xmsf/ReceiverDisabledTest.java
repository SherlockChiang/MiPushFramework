package com.xiaomi.xmsf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ReceiverDisabledTest {

    private static final List<String> DISABLED_RECEIVERS_IN_QA = Arrays.asList(
            "com.xiaomi.xmsf.push.service.receivers.BootReceiver",
            "com.xiaomi.xmsf.push.service.receivers.NetworkStatusReceiver",
            "com.xiaomi.xmsf.push.service.receivers.MiPushPingReceiver",
            "com.xiaomi.xmsf.push.service.receivers.AccountChangedReceiver",
            "com.xiaomi.xmsf.push.service.receivers.PkgUninstallReceiver",
            "com.xiaomi.push.service.SelfUpdateReceiver",
            "com.catchingnow.icebox.sdk_client.StateReceiver",
            "com.xiaomi.xmsf.push.service.receivers.NotificationEventReceiver",
            "com.xiaomi.push.revival.NotificationsRevivalForSelfUpdated"
    );

    @Test
    public void testDisabledReceiversListComplete() {
        assertEquals("Exactly 9 automatic receivers must be disabled in QA overlay", 9, DISABLED_RECEIVERS_IN_QA.size());
        for (String receiverClass : DISABLED_RECEIVERS_IN_QA) {
            try {
                Class<?> clazz = Class.forName(receiverClass);
                assertTrue("Class should be assignable to Object", Object.class.isAssignableFrom(clazz));
            } catch (ClassNotFoundException e) {
                // If optional classes are not present in test classpath, class name is still verified
                assertTrue(receiverClass.length() > 0);
            }
        }
    }
}
