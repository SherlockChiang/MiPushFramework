package com.nihility.utils;

import androidx.annotation.Nullable;

import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;

import java.util.HashMap;
import java.util.Map;

/** XMSF-private marker and payload sanitiser for manually replayed notifications. */
public final class NotificationReplayMarker {
    public static final String META_EXTRA_KEY = "__xmsf_internal_manual_replay";
    private static final String MARKED_VALUE = "1";

    private NotificationReplayMarker() {
    }

    /** Creates the replay-owned copy before adding any internal metadata. */
    public static XmPushActionContainer markedCopy(XmPushActionContainer container) {
        XmPushActionContainer replay = container.deepCopy();
        mark(replay);
        return replay;
    }

    public static void mark(XmPushActionContainer container) {
        PushMetaInfo metaInfo = container == null ? null : container.getMetaInfo();
        if (metaInfo == null) {
            return;
        }
        Map<String, String> extras = metaInfo.getExtra() == null
                ? new HashMap<>() : new HashMap<>(metaInfo.getExtra());
        extras.put(META_EXTRA_KEY, MARKED_VALUE);
        metaInfo.setExtra(extras);
    }

    public static boolean isMarked(@Nullable XmPushActionContainer container) {
        PushMetaInfo metaInfo = container == null ? null : container.getMetaInfo();
        Map<String, String> extras = metaInfo == null ? null : metaInfo.getExtra();
        return extras != null && MARKED_VALUE.equals(extras.get(META_EXTRA_KEY));
    }

    /** Returns a deep copy whose application-visible metadata contains no XMSF marker. */
    @Nullable
    public static XmPushActionContainer copyWithoutMarker(
            @Nullable XmPushActionContainer container) {
        if (container == null) {
            return null;
        }
        XmPushActionContainer copy = container.deepCopy();
        PushMetaInfo metaInfo = copy.getMetaInfo();
        if (metaInfo == null || metaInfo.getExtra() == null
                || !metaInfo.getExtra().containsKey(META_EXTRA_KEY)) {
            return copy;
        }
        Map<String, String> extras = new HashMap<>(metaInfo.getExtra());
        extras.remove(META_EXTRA_KEY);
        metaInfo.setExtra(extras);
        return copy;
    }
}
