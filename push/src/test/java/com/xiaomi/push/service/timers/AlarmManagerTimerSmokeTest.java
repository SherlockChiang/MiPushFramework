package com.xiaomi.push.service.timers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.PendingIntent;
import android.os.Build;

import org.junit.Test;

import top.trumeet.common.utils.AlarmSchedulePolicy;
import top.trumeet.common.utils.AlarmSchedulePolicy.AlarmScheduleType;

public class AlarmManagerTimerSmokeTest {

    private static class DummyTimer {
        private PendingIntent mPi;
        private volatile long mNextPingTs;
    }

    @Test
    public void testTimerReflectiveWriteBack() {
        DummyTimer timer = new DummyTimer();
        assertNull(timer.mPi);
        assertEquals(0L, timer.mNextPingTs);

        long testTs = 123456789L;
        AlarmManagerTimerAspect.setField(timer, "mNextPingTs", testTs);
        assertEquals("mNextPingTs should be updated via reflection", testTs, timer.mNextPingTs);
    }

    @Test
    public void testPolicyExactBranch() {
        AlarmScheduleType exactType =
                AlarmSchedulePolicy.determineScheduleType(Build.VERSION_CODES.S, true);
        assertEquals("Exact alarm allowed on API 31+ with permission",
                AlarmScheduleType.EXACT, exactType);

        AlarmScheduleType preSType =
                AlarmSchedulePolicy.determineScheduleType(Build.VERSION_CODES.R, false);
        assertEquals("Exact alarm allowed on API < 31",
                AlarmScheduleType.EXACT, preSType);
    }

    @Test
    public void testPolicyInexactBranch() {
        AlarmScheduleType inexactType =
                AlarmSchedulePolicy.determineScheduleType(Build.VERSION_CODES.S, false);
        assertEquals("Inexact alarm scheduled on API 31+ without permission",
                AlarmScheduleType.INEXACT_ALLOW_WHILE_IDLE, inexactType);
    }
}
