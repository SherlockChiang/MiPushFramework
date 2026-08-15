package com.xiaomi.push.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.elvishew.xlog.XLog;

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
        assertEquals("Queue remaining + size initial capacity must be 32", 32, executor.getQueue().remainingCapacity() + executor.getQueue().size());

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
}
