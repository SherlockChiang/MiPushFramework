package com.nihility.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;

import org.junit.Test;

import java.util.HashMap;

public class NotificationReplayMarkerTest {
    @Test
    public void replayMarkerLivesOnlyInDeepCopyAndCanBeStripped() {
        XmPushActionContainer original = new XmPushActionContainer();
        PushMetaInfo metaInfo = new PushMetaInfo();
        metaInfo.extra = new HashMap<>();
        metaInfo.extra.put("business", "value");
        original.metaInfo = metaInfo;

        XmPushActionContainer replay = NotificationReplayMarker.markedCopy(original);

        assertFalse(NotificationReplayMarker.isMarked(original));
        assertTrue(NotificationReplayMarker.isMarked(replay));
        assertNotSame(original.getMetaInfo().getExtra(), replay.getMetaInfo().getExtra());

        XmPushActionContainer target =
                NotificationReplayMarker.copyWithoutMarker(replay);
        assertTrue(NotificationReplayMarker.isMarked(replay));
        assertFalse(NotificationReplayMarker.isMarked(target));
        assertEquals("value", target.getMetaInfo().getExtra().get("business"));
    }

    @Test
    public void markerCreatesMetadataMapWithoutMutatingOriginal() {
        XmPushActionContainer original = new XmPushActionContainer();
        original.metaInfo = new PushMetaInfo();

        XmPushActionContainer replay = NotificationReplayMarker.markedCopy(original);

        assertEquals(null, original.getMetaInfo().getExtra());
        assertTrue(NotificationReplayMarker.isMarked(replay));
    }
}
