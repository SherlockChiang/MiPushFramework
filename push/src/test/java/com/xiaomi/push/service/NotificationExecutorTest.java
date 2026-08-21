package com.xiaomi.push.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.elvishew.xlog.XLog;

import android.content.pm.ActivityInfo;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NotificationExecutorTest {

    @Before
    public void setUp() {
        XLog.init();
    }

    @Test
    public void testNotificationExecutorConfiguration() {
        ThreadPoolExecutor executor = MyMIPushNotificationHelper.getNotificationExecutor();
        assertNotNull("Notification executor must not be null", executor);

        assertEquals("Core pool size must be 3", 3, executor.getCorePoolSize());
        assertEquals("Maximum pool size must be 3", 3, executor.getMaximumPoolSize());
        assertEquals("Keep alive time must be 30 seconds", 30L, executor.getKeepAliveTime(TimeUnit.SECONDS));
        assertTrue("Core thread timeout must be enabled", executor.allowsCoreThreadTimeOut());

        assertTrue("Work queue must be ArrayBlockingQueue", executor.getQueue() instanceof ArrayBlockingQueue);
        assertEquals("Queue remaining + size initial capacity must match the bounded payload queue",
                MyMIPushNotificationHelper.NOTIFICATION_QUEUE_CAPACITY,
                executor.getQueue().remainingCapacity() + executor.getQueue().size());

        assertTrue("RejectedExecutionHandler must be CallerRunsPolicy",
                executor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy);
    }

    @Test
    public void testThreadFactoryNaming() {
        ThreadPoolExecutor executor = MyMIPushNotificationHelper.getNotificationExecutor();
        Thread thread = executor.getThreadFactory().newThread(() -> {});
        assertNotNull(thread);
        assertTrue("Thread name must start with mipush-notification-",
                thread.getName().startsWith("mipush-notification-"));
    }

    @Test
    public void styleActionsUseOfficialXiaomiKeys() {
        assertEquals("notification_style_button_left_notify_effect",
                MyMIPushNotificationHelper.styleActionKeys(1).notifyEffect);
        assertEquals("notification_style_button_mid_notify_effect",
                MyMIPushNotificationHelper.styleActionKeys(2).notifyEffect);
        assertEquals("notification_style_button_right_notify_effect",
                MyMIPushNotificationHelper.styleActionKeys(3).notifyEffect);

        MyMIPushNotificationHelper.StyleActionKeys keys =
                MyMIPushNotificationHelper.styleActionKeys(4);

        assertEquals("notification_colorful_button_notify_effect", keys.notifyEffect);
        assertEquals("notification_colorful_button_intent_uri", keys.intentUri);
        assertEquals("notification_colorful_button_intent_class", keys.intentClass);
        assertEquals("notification_colorful_button_web_uri", keys.webUri);
    }

    @Test
    public void resolvedActivityMustBelongToTargetPackage() {
        ResolveInfo resolved = new ResolveInfo();
        resolved.activityInfo = new ActivityInfo();
        resolved.activityInfo.packageName = "com.example.target";

        assertTrue(MyMIPushNotificationHelper.isResolvedActivityInTargetPackage(
                "com.example.target", resolved));
        assertTrue(!MyMIPushNotificationHelper.isResolvedActivityInTargetPackage(
                "com.example.other", resolved));
        assertTrue(!MyMIPushNotificationHelper.isResolvedActivityInTargetPackage(
                "com.example.target", null));

        ResolveInfo withoutActivity = new ResolveInfo();
        assertTrue(!MyMIPushNotificationHelper.isResolvedActivityInTargetPackage(
                "com.example.target", withoutActivity));
        assertTrue(!MyMIPushNotificationHelper.isResolvedActivityInTargetPackage(
                null, resolved));
        assertTrue(!MyMIPushNotificationHelper.isResolvedActivityInTargetPackage(
                "", resolved));
    }

    @Test
    public void clickedActivitySettingUsesThreeStateContract() {
        Intent activity = new Intent();

        // Explicit values take precedence over the style-derived default.
        assertTrue(MyMIPushNotificationHelper.shouldUseActivityClick(
                Boolean.TRUE, false, activity));
        assertTrue(!MyMIPushNotificationHelper.shouldUseActivityClick(
                Boolean.FALSE, true, activity));

        // An absent setting opts MessagingStyle into the Activity path, while
        // non-MessagingStyle notifications retain the service path.
        assertTrue(MyMIPushNotificationHelper.shouldUseActivityClick(
                null, true, activity));
        assertTrue(!MyMIPushNotificationHelper.shouldUseActivityClick(
                null, false, activity));

        // No resolved target Activity must always use the safe service path,
        // even when the setting or style asks for an Activity.
        assertTrue(!MyMIPushNotificationHelper.shouldUseActivityClick(
                Boolean.TRUE, true, null));
        assertTrue(!MyMIPushNotificationHelper.shouldUseActivityClick(
                null, true, null));
    }
}
