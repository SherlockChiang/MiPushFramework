package test.top.trumeet.common.utils.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import top.trumeet.common.utils.CustomConfiguration;
import top.trumeet.common.utils.NotificationAlertUtils;

public class CustomConfigurationTest {

    @Test
    public void textIcon() {
        CustomConfiguration custom = new CustomConfiguration(new HashMap<>() {{
            put("__mi_push_text_icon", "qwe");
        }});

        assertEquals("qwe", custom.textIcon(null));
    }

    @Test
    public void focusPayloadKeepsAtMostTenDistinctHttpsPictures() {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("miui.focus.param", "{\"ticker\":\"parcel\"}");
        extras.put("miui.focus.pic_http", "http://example.com/not-allowed.png");
        extras.put("miui.focus.pic_malformed", "https://bad host/image.png");
        for (int i : new int[]{11, 2, 7, 10, 1, 9, 0, 5, 3, 8, 6, 4}) {
            extras.put("miui.focus.pic_" + i, "https://example.com/" + i + ".png");
        }

        CustomConfiguration.FocusNotificationPayload payload =
                new CustomConfiguration(extras).focusNotificationPayload();

        assertTrue(payload.isUsable());
        assertEquals("{\"ticker\":\"parcel\"}", payload.parameter());
        assertEquals(CustomConfiguration.FOCUS_PICTURE_MAX_COUNT,
                payload.pictureUrls().size());
        assertEquals("https://example.com/0.png",
                payload.pictureUrls().get("miui.focus.pic_0"));
        assertEquals(Arrays.asList(
                        "miui.focus.pic_0", "miui.focus.pic_1", "miui.focus.pic_2",
                        "miui.focus.pic_3", "miui.focus.pic_4", "miui.focus.pic_5",
                        "miui.focus.pic_6", "miui.focus.pic_7", "miui.focus.pic_8",
                        "miui.focus.pic_9"),
                new ArrayList<>(payload.pictureUrls().keySet()));
        assertFalse(payload.pictureUrls().containsKey("miui.focus.pic_http"));
        assertFalse(payload.pictureUrls().containsKey("miui.focus.pic_malformed"));
    }

    @Test
    public void focusPayloadAcceptsParameterWithoutPictures() {
        Map<String, String> extras = new HashMap<>();
        extras.put("miui.focus.param", "{\"ticker\":\"text-only\"}");

        CustomConfiguration.FocusNotificationPayload payload =
                new CustomConfiguration(extras).focusNotificationPayload();

        assertTrue(payload.isUsable());
        assertTrue(payload.pictureUrls().isEmpty());
    }

    @Test
    public void focusPayloadRejectsParameterOverUtf8ByteLimit() {
        StringBuilder oversized = new StringBuilder();
        while (oversized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= CustomConfiguration.FOCUS_PARAM_MAX_BYTES) {
            oversized.append('\u754c');
        }
        Map<String, String> extras = new HashMap<>();
        extras.put("miui.focus.param", oversized.toString());

        CustomConfiguration.FocusNotificationPayload payload =
                new CustomConfiguration(extras).focusNotificationPayload();

        assertNull(payload.parameter());
        assertFalse(payload.isUsable());
    }

    @Test
    public void focusLimitsIncludeTheirPublishedBoundary() {
        String exactParameter = repeat('a', CustomConfiguration.FOCUS_PARAM_MAX_BYTES);
        String oversizedParameter = exactParameter + "a";

        assertTrue(CustomConfiguration.FocusNotificationPayload
                .isParameterWithinLimit(exactParameter));
        assertFalse(CustomConfiguration.FocusNotificationPayload
                .isParameterWithinLimit(oversizedParameter));
        assertTrue(CustomConfiguration.FocusNotificationPayload
                .isPictureSizeAllowed(CustomConfiguration.FOCUS_PICTURE_MAX_BYTES));
        assertFalse(CustomConfiguration.FocusNotificationPayload
                .isPictureSizeAllowed(CustomConfiguration.FOCUS_PICTURE_MAX_BYTES + 1L));
        assertFalse(CustomConfiguration.FocusNotificationPayload
                .isPictureSizeAllowed(-1));
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }

    @Test
    public void focusProtocolAcceptsEveryPositiveVersionLikeOfficialClient() {
        assertFalse(CustomConfiguration.FocusNotificationPayload
                .isSupportedProtocolVersion(0));
        assertTrue(CustomConfiguration.FocusNotificationPayload
                .isSupportedProtocolVersion(1));
        assertTrue(CustomConfiguration.FocusNotificationPayload
                .isSupportedProtocolVersion(3));
        assertTrue(CustomConfiguration.FocusNotificationPayload
                .isSupportedProtocolVersion(4));
        assertTrue(CustomConfiguration.FocusNotificationPayload
                .isSupportedProtocolVersion(99));
        assertFalse(CustomConfiguration.FocusNotificationPayload
                .isSupportedProtocolVersion(-1));
    }

    @Test
    public void channelFieldsUseOfficialNamesWithLegacySoundFallback() {
        Map<String, String> extras = new HashMap<>();
        extras.put("channel_importance", "4");
        extras.put("sound_url", "content://legacy");
        CustomConfiguration custom = new CustomConfiguration(extras);

        assertEquals(4, custom.channelImportance(3));
        assertEquals("content://legacy", custom.soundUri(null));

        extras.put("sound_uri", "content://official");
        assertEquals("content://official", custom.soundUri(null));
        extras.put("channel_importance", "99");
        assertEquals(3, custom.channelImportance(3));
    }

    @Test
    public void notificationShowWhenParsesValueInsteadOfPresence() {
        Map<String, String> extras = new HashMap<>();
        extras.put("notification_show_when", "false");
        CustomConfiguration custom = new CustomConfiguration(extras);

        assertFalse(custom.notificationShowWhen(true));
        extras.put("notification_show_when", "true");
        assertTrue(custom.notificationShowWhen(false));
        extras.remove("notification_show_when");
        assertTrue(custom.notificationShowWhen(true));
    }

    @Test
    public void resourceSoundRequiresSoundBitAndMatchingPackage() {
        String packageName = "com.example.app";
        String soundUri = "android.resource://com.example.app/raw/ping";

        assertTrue(NotificationAlertUtils.usesPackageResourceSound(
                NotificationAlertUtils.NOTIFY_TYPE_SOUND, soundUri, packageName));
        assertTrue(NotificationAlertUtils.usesPackageResourceSound(
                NotificationAlertUtils.NOTIFY_TYPE_SOUND
                        | NotificationAlertUtils.NOTIFY_TYPE_VIBRATE
                        | NotificationAlertUtils.NOTIFY_TYPE_LIGHTS,
                soundUri, packageName));
        assertFalse(NotificationAlertUtils.usesPackageResourceSound(
                NotificationAlertUtils.NOTIFY_TYPE_VIBRATE, soundUri, packageName));
        assertFalse(NotificationAlertUtils.usesPackageResourceSound(
                NotificationAlertUtils.NOTIFY_TYPE_SOUND,
                "android.resource://com.other.app/raw/ping", packageName));
        assertFalse(NotificationAlertUtils.usesPackageResourceSound(
                NotificationAlertUtils.NOTIFY_TYPE_SOUND,
                "content://com.example.app/ping", packageName));
        assertFalse(NotificationAlertUtils.usesPackageResourceSound(
                NotificationAlertUtils.NOTIFY_TYPE_SOUND, null, packageName));
    }
}
