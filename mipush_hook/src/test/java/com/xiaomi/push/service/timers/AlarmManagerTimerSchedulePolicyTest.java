package com.xiaomi.push.service.timers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import top.trumeet.common.utils.AlarmSchedulePolicy.AlarmScheduleType;

public class AlarmManagerTimerSchedulePolicyTest {

    private static final long WALL_CLOCK_DEADLINE_MS = 1_800_000_123_456L;

    @Test
    public void exactSchedulePreservesRtcWakeupDeadlineAndNextPingTimestamp() {
        AlarmManagerTimerSchedulePolicy.Schedule schedule =
                AlarmManagerTimerSchedulePolicy.forWallClockDeadline(
                        34, true, WALL_CLOCK_DEADLINE_MS);

        assertEquals(AlarmScheduleType.EXACT, schedule.getScheduleType());
        assertEquals(
                AlarmManagerTimerSchedulePolicy.ClockType.RTC_WAKEUP,
                schedule.getClockType());
        assertEquals(WALL_CLOCK_DEADLINE_MS, schedule.getTriggerAtMillis());
        assertEquals(WALL_CLOCK_DEADLINE_MS, schedule.getNextPingTimestampMillis());
    }

    @Test
    public void inexactScheduleChangesOnlyPrecisionAndPreservesDeadlineContract() {
        AlarmManagerTimerSchedulePolicy.Schedule schedule =
                AlarmManagerTimerSchedulePolicy.forWallClockDeadline(
                        34, false, WALL_CLOCK_DEADLINE_MS);

        assertEquals(AlarmScheduleType.INEXACT_ALLOW_WHILE_IDLE, schedule.getScheduleType());
        assertEquals(
                AlarmManagerTimerSchedulePolicy.ClockType.RTC_WAKEUP,
                schedule.getClockType());
        assertEquals(WALL_CLOCK_DEADLINE_MS, schedule.getTriggerAtMillis());
        assertEquals(WALL_CLOCK_DEADLINE_MS, schedule.getNextPingTimestampMillis());
    }

    @Test
    public void powerSaveModeUsesInexactScheduleAndPreservesDeadline() {
        AlarmManagerTimerSchedulePolicy.Schedule schedule =
                AlarmManagerTimerSchedulePolicy.forWallClockDeadline(
                        34, true, true, WALL_CLOCK_DEADLINE_MS);

        assertEquals(AlarmScheduleType.INEXACT_ALLOW_WHILE_IDLE, schedule.getScheduleType());
        assertEquals(WALL_CLOCK_DEADLINE_MS, schedule.getTriggerAtMillis());
        assertEquals(WALL_CLOCK_DEADLINE_MS, schedule.getNextPingTimestampMillis());
    }

    @Test
    public void legacyPowerSaveModeUsesInexactSchedule() {
        AlarmManagerTimerSchedulePolicy.Schedule schedule =
                AlarmManagerTimerSchedulePolicy.forWallClockDeadline(
                        30, true, true, WALL_CLOCK_DEADLINE_MS);

        assertEquals(AlarmScheduleType.INEXACT_ALLOW_WHILE_IDLE, schedule.getScheduleType());
    }

    @Test
    public void expiredOrZeroDeadlineIsPassedThroughWithoutDelayConversionOrClamping() {
        AlarmManagerTimerSchedulePolicy.Schedule expired =
                AlarmManagerTimerSchedulePolicy.forWallClockDeadline(30, false, 0L);

        assertEquals(AlarmScheduleType.EXACT, expired.getScheduleType());
        assertEquals(0L, expired.getTriggerAtMillis());
        assertEquals(0L, expired.getNextPingTimestampMillis());
    }
}
