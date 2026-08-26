package com.nihility.utils;

import android.widget.Toast;
import android.os.SystemClock;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.nihility.XMPushUtils;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.service.MIPushEventProcessor;
import com.xiaomi.push.service.MiPushMessageDuplicateAspect;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmsf.push.notification.FocusNotificationReplay;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import top.trumeet.common.utils.Utils;

public class MockMIPushMessage {
    private static final String TAG = MockMIPushMessage.class.getSimpleName();
    private static final Logger logger = XLog.tag(TAG).build();
    private static final AtomicInteger REPLAY_NOTIFY_ID = new AtomicInteger(
            0x60000000 | ((int) SystemClock.uptimeMillis() & 0x0fffffff));

    /**
     * Dispatch an event as a fresh notification. Historical focus payloads
     * contain an expired sequence, so replay must refresh the public timestamp
     * fields before entering the normal push pipeline.
     *
     * @return true when the processor was invoked successfully
     */
    public static boolean mockProcessMIPushMessage(XMPushService pushService,
                                                    XmPushActionContainer container) {
        try {
            XmPushActionContainer replayContainer = prepareForReplay(container);
            MiPushMessageDuplicateAspect.markAsMock(replayContainer);
            invokeProcessMiPushMessage(pushService, replayContainer);
            return true;
        } catch (Exception e) {
            logger.e("mock notification failure: ", e);
            if (pushService != null) {
                Utils.makeText(pushService, "failure", Toast.LENGTH_SHORT);
            }
            return false;
        }
    }

    static XmPushActionContainer prepareForReplay(XmPushActionContainer container) {
        XmPushActionContainer replay = NotificationReplayMarker.markedCopy(container);
        PushMetaInfo metaInfo = replay.getMetaInfo();
        if (metaInfo == null) {
            return replay;
        }
        long now = System.currentTimeMillis();
        metaInfo.setMessageTs(now);
        // A replay should create a visible notification instead of silently
        // updating the historical row with the same ID.
        metaInfo.setNotifyId(nextReplayNotifyId());
        Map<String, String> extras = metaInfo.getExtra();
        if (extras != null && !extras.isEmpty()) {
            metaInfo.setExtra(FocusNotificationReplay.refreshExtras(extras, now));
        }
        return replay;
    }

    private static int nextReplayNotifyId() {
        int next = REPLAY_NOTIFY_ID.incrementAndGet();
        if (next < 0) {
            REPLAY_NOTIFY_ID.compareAndSet(next, 0x60000000);
            return 0x60000000;
        }
        return next;
    }

    public static void invokeProcessMiPushMessage(XMPushService pushService, XmPushActionContainer container) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
        byte[] mockDecryptedContent = XMPushUtils.packToBytes(container);
        invokeProcessMiPushMessage(pushService, mockDecryptedContent);
    }

    public static void invokeProcessMiPushMessage(XMPushService pushService, byte[] mockDecryptedContent) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
        JavaCalls.<Boolean>callStaticMethodOrThrow(MIPushEventProcessor.class.getName(), "processMIPushMessage",
                pushService, mockDecryptedContent, (long) mockDecryptedContent.length);
    }
}
