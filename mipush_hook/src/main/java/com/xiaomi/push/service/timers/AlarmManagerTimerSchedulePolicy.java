package com.xiaomi.push.service.timers;

import top.trumeet.common.utils.AlarmSchedulePolicy;
import top.trumeet.common.utils.AlarmSchedulePolicy.AlarmScheduleType;

/**
 * Defines the clock and deadline contract used when replacing Xiaomi's alarm timer.
 *
 * <p>{@code AlarmManagerTimer.register(Intent, long)} receives an absolute
 * {@link System#currentTimeMillis()} deadline. It is not an elapsed-realtime delay. Exact-alarm
 * permission changes scheduling precision only; it must never change the clock domain or deadline.
 */
final class AlarmManagerTimerSchedulePolicy {

    enum ClockType {
        RTC_WAKEUP
    }

    static final class Schedule {
        private final AlarmScheduleType scheduleType;
        private final ClockType clockType;
        private final long triggerAtMillis;
        private final long nextPingTimestampMillis;

        private Schedule(
                AlarmScheduleType scheduleType,
                ClockType clockType,
                long triggerAtMillis,
                long nextPingTimestampMillis) {
            this.scheduleType = scheduleType;
            this.clockType = clockType;
            this.triggerAtMillis = triggerAtMillis;
            this.nextPingTimestampMillis = nextPingTimestampMillis;
        }

        AlarmScheduleType getScheduleType() {
            return scheduleType;
        }

        ClockType getClockType() {
            return clockType;
        }

        long getTriggerAtMillis() {
            return triggerAtMillis;
        }

        long getNextPingTimestampMillis() {
            return nextPingTimestampMillis;
        }
    }

    private AlarmManagerTimerSchedulePolicy() {
    }

    static Schedule forWallClockDeadline(
            int sdkInt, boolean canScheduleExactAlarms, long wallClockDeadlineMs) {
        return forWallClockDeadline(
                sdkInt, canScheduleExactAlarms, false, wallClockDeadlineMs);
    }

    static Schedule forWallClockDeadline(
            int sdkInt, boolean canScheduleExactAlarms, boolean powerSaveMode,
            long wallClockDeadlineMs) {
        AlarmScheduleType scheduleType =
                AlarmSchedulePolicy.determineScheduleType(
                        sdkInt, canScheduleExactAlarms, powerSaveMode);
        return new Schedule(
                scheduleType,
                ClockType.RTC_WAKEUP,
                wallClockDeadlineMs,
                wallClockDeadlineMs);
    }
}
