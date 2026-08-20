package test.top.trumeet.common.utils.utils;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import top.trumeet.common.utils.CustomConfiguration;
import top.trumeet.common.utils.NotificationMetadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NotificationMetadataTest {
    @Test
    public void parsesHyperOsFieldsAndAliases() {
        Map<String, String> values = new HashMap<>();
        values.put("timeout", "30");
        values.put("enable_keyguard", "false");
        values.put("enable_float", "1");
        values.put("notification_fold", "true");
        values.put("miui.fold.timeout", "12");
        values.put("notification_top_repeat", "true");
        values.put("notification_top_period", "3600");
        values.put("notification_top_frequency", "4");
        values.put("notification_ongoing", "false");
        values.put("notification_colorized", "true");
        values.put("background_color", "#112233");
        values.put("visibility", "secret");
        values.put("category", "message");

        NotificationMetadata metadata = NotificationMetadata.from(new CustomConfiguration(values));
        assertEquals(Integer.valueOf(30), metadata.timeoutSeconds);
        assertEquals(Boolean.FALSE, metadata.enableKeyguard);
        assertEquals(Boolean.TRUE, metadata.enableFloat);
        assertEquals(Boolean.TRUE, metadata.topRepeat);
        assertEquals("true", metadata.fold);
        assertEquals(Integer.valueOf(12), metadata.foldTimeoutSeconds);
        assertEquals(Integer.valueOf(3600), metadata.topPeriodSeconds);
        assertEquals(Integer.valueOf(4), metadata.topFrequency);
        assertEquals(Boolean.FALSE, metadata.ongoing);
        assertEquals(Boolean.TRUE, metadata.colorized);
        assertEquals(Integer.valueOf(-1), metadata.visibility);
        assertEquals("message", metadata.category);
        assertEquals(Integer.valueOf(0xff112233), metadata.backgroundColor);
    }

    @Test
    public void acceptsDocumentedModernAndroidCategories() {
        Map<String, String> values = new HashMap<>();
        values.put("notification_category", "missed_call");

        NotificationMetadata metadata = NotificationMetadata.from(new CustomConfiguration(values));

        assertEquals("missed_call", metadata.category);
    }

    @Test
    public void rejectsOutOfRangeAndUnsafeHints() {
        Map<String, String> values = new HashMap<>();
        values.put("notification_timeout", "999999999");
        values.put("notification_top_repeat", "-1");
        values.put("notification_visibility", "admin");
        values.put("notification_category", "vendor-private");
        values.put("background_color", "red");

        NotificationMetadata metadata = NotificationMetadata.from(new CustomConfiguration(values));
        assertNull(metadata.timeoutSeconds);
        assertNull(metadata.topRepeat);
        assertNull(metadata.visibility);
        assertNull(metadata.category);
        assertNull(metadata.backgroundColor);
        assertTrue(NotificationMetadata.MAX_TIMEOUT_SECONDS > 0);
    }
}
