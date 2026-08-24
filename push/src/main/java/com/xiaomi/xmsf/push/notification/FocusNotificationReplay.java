package com.xiaomi.xmsf.push.notification;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Refreshes the public, string-based part of Xiaomi's focus payload for a
 * user-requested replay.
 *
 * <p>Focus notifications carry an expiry sequence. Replaying the bytes from
 * an old event verbatim makes HyperOS correctly discard the notification as
 * stale, even though the ordinary Android title/body are still available.
 * This helper changes only replay timestamps; business content, URLs and
 * private Parcelable fields are never synthesized.</p>
 */
public final class FocusNotificationReplay {
    private static final String FOCUS_PARAM = "miui.focus.param";
    private static final String FOCUS_PARAM_CUSTOM = "miui.focus.param.custom";
    private static final String[] TIMESTAMP_KEYS = {
            "t_fe_s", "t_fe", "t_mt_s", "t_mt", "t_q_s", "t_q", "fe_ts", "__m_ts"
    };

    private FocusNotificationReplay() {
    }

    /**
     * Return a copy of extras with known server timestamps refreshed. The
     * input map is never mutated, which keeps the stored event immutable.
     */
    public static Map<String, String> refreshExtras(
            @Nullable Map<String, String> extras, long timestampMillis) {
        if (extras == null || extras.isEmpty()) {
            return extras;
        }
        Map<String, String> refreshed = new HashMap<>(extras);
        refreshParameter(refreshed, FOCUS_PARAM, timestampMillis);
        refreshParameter(refreshed, FOCUS_PARAM_CUSTOM, timestampMillis);
        String timestamp = Long.toString(timestampMillis);
        for (String key : TIMESTAMP_KEYS) {
            if (refreshed.containsKey(key)) {
                refreshed.put(key, timestamp);
            }
        }
        return refreshed;
    }

    @Nullable
    private static String refreshParameter(
            Map<String, String> extras, String key, long timestampMillis) {
        String parameter = extras.get(key);
        if (!FocusNotificationSafety.isWellFormedParameter(parameter)) {
            return parameter;
        }
        try {
            JsonElement parsed = JsonParser.parseString(parameter);
            if (!parsed.isJsonObject()) {
                return parameter;
            }
            JsonObject root = parsed.getAsJsonObject();
            // SystemUI uses the top-level sequence to reject expired focus
            // records. Some HyperOS templates duplicate it inside param_v2.
            // Keep the JSON scalar type supplied by the sender; clients may use
            // a string at the top level and a number in the nested protocol.
            // A few SystemUI builds read these fields with a strict accessor.
            replaceSequence(root, timestampMillis);
            JsonElement paramV2 = root.get("param_v2");
            if (paramV2 != null && paramV2.isJsonObject()) {
                JsonObject paramV2Object = paramV2.getAsJsonObject();
                replaceSequence(paramV2Object, timestampMillis);
                // A replay is an explicit user action. The original sender's
                // permission gate describes its live delivery context and can
                // make HyperOS hide the entire focus view for a locally
                // replayed third-party event. Keep the focus payload visible;
                // the normal Android notification remains the fallback.
                disablePermissionFilter(paramV2Object);
            }
            // A few payload producers put the permission gate at the root;
            // handle that form as well without inventing a new protocol field.
            disablePermissionFilter(root);
            String refreshed = root.toString();
            // Never turn a valid payload into an oversized one. The normal
            // notification path will still deliver the original value.
            if (refreshed.getBytes(StandardCharsets.UTF_8).length
                    > FocusNotificationSafety.MAX_PARAMETER_BYTES) {
                return parameter;
            }
            extras.put(key, refreshed);
            return refreshed;
        } catch (Throwable ignored) {
            return parameter;
        }
    }

    private static void replaceSequence(JsonObject object, long timestampMillis) {
        JsonElement existing = object.get("sequence");
        if (existing == null || !existing.isJsonPrimitive()) {
            return;
        }
        try {
            if (existing.getAsJsonPrimitive().isString()) {
                object.addProperty("sequence", Long.toString(timestampMillis));
            } else {
                object.addProperty("sequence", timestampMillis);
            }
        } catch (Throwable ignored) {
            // Keep the original scalar if an unusual Gson primitive cannot be
            // inspected; replay must never invalidate the stored payload.
        }
    }

    private static void disablePermissionFilter(JsonObject object) {
        if (object.has("filterWhenNoPermission")) {
            object.addProperty("filterWhenNoPermission", false);
        }
    }
}
