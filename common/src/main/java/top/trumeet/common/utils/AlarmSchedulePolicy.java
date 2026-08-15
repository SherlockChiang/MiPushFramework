package top.trumeet.common.utils;

/**
 * Pure policy to determine alarm scheduling strategy across Android API levels and permissions.
 */
public class AlarmSchedulePolicy {

    public enum AlarmScheduleType {
        EXACT,
        INEXACT_ALLOW_WHILE_IDLE
    }

    public static AlarmScheduleType determineScheduleType(int sdkInt, boolean canScheduleExactAlarms) {
        if (sdkInt >= 31) {
            return canScheduleExactAlarms ? AlarmScheduleType.EXACT : AlarmScheduleType.INEXACT_ALLOW_WHILE_IDLE;
        }
        return AlarmScheduleType.EXACT;
    }
}
