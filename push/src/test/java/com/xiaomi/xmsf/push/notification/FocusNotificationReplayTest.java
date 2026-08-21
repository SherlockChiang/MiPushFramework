package com.xiaomi.xmsf.push.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class FocusNotificationReplayTest {
    @Test
    public void refreshesTopLevelAndNestedSequenceWithoutMutatingInput() {
        String original = "{\"title\":\"待支付\",\"sequence\":\"123\","
                + "\"param_v2\":{\"sequence\":123,\"business\":\"food_delivery\","
                + "\"filterWhenNoPermission\":true}}";
        Map<String, String> input = new HashMap<>();
        input.put("miui.focus.param", original);
        input.put("t_fe_s", "123");

        Map<String, String> refreshed = FocusNotificationReplay.refreshExtras(input, 456L);

        assertNotSame(input, refreshed);
        assertEquals(original, input.get("miui.focus.param"));
        assertTrue(refreshed.get("miui.focus.param").contains("\"sequence\":\"456\""));
        assertTrue(refreshed.get("miui.focus.param").contains("\"business\":\"food_delivery\""));
        assertTrue(refreshed.get("miui.focus.param").contains("\"filterWhenNoPermission\":false"));
        assertEquals("456", refreshed.get("t_fe_s"));
    }

    @Test
    public void disablesRootPermissionFilterAndKeepsNumericNestedSequence() {
        Map<String, String> input = new HashMap<>();
        input.put("miui.focus.param", "{\"sequence\":123,"
                + "\"filterWhenNoPermission\":true,\"param_v2\":{\"sequence\":456}} ");

        String refreshed = FocusNotificationReplay.refreshExtras(input, 789L)
                .get("miui.focus.param");

        assertTrue(refreshed.contains("\"sequence\":789"));
        assertTrue(refreshed.contains("\"filterWhenNoPermission\":false"));
        assertTrue(refreshed.contains("\"param_v2\":{\"sequence\":789}"));
    }

    @Test
    public void keepsMalformedAndOversizedParametersSafe() {
        Map<String, String> malformed = new HashMap<>();
        malformed.put("miui.focus.param", "not-json");
        assertEquals("not-json",
                FocusNotificationReplay.refreshExtras(malformed, 456L)
                        .get("miui.focus.param"));

        Map<String, String> oversized = new HashMap<>();
        oversized.put("miui.focus.param", "{\"payload\":\""
                + "a".repeat(FocusNotificationSafety.MAX_PARAMETER_BYTES) + "\"}");
        assertEquals(oversized.get("miui.focus.param"),
                FocusNotificationReplay.refreshExtras(oversized, 456L)
                        .get("miui.focus.param"));
    }

    @Test
    public void returnsEmptyOrNullMapAsIs() {
        assertEquals(null, FocusNotificationReplay.refreshExtras(null, 456L));
        Map<String, String> empty = new HashMap<>();
        assertEquals(empty, FocusNotificationReplay.refreshExtras(empty, 456L));
    }
}
