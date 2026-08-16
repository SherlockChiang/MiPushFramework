package com.xiaomi.push.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MyNotificationIconHelperTest {
    @Test
    public void sampleSizeKeepsNormalIconsSharp() {
        assertEquals(1, MyNotificationIconHelper.calculateSampleSize(48, 48, 48));
        assertEquals(2, MyNotificationIconHelper.calculateSampleSize(192, 96, 48));
    }

    @Test
    public void sampleSizeBoundsPanoramicDecodeMemory() {
        int sample = MyNotificationIconHelper.calculateSampleSize(20_000, 100, 48);

        assertTrue(sample >= 10);
        assertTrue((20_000L / sample) * (100L / sample) <= 1024L * 1024L);
    }

    @Test
    public void invalidBoundsUseSafeDefault() {
        assertEquals(1, MyNotificationIconHelper.calculateSampleSize(-1, 100, 48));
        assertEquals(1, MyNotificationIconHelper.calculateSampleSize(100, 100, 0));
    }
}
