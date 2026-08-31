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
        return determineScheduleType(sdkInt, canScheduleExactAlarms, false);
    }

    /**
     * Select alarm precision while respecting the user's/system battery saver
     * choice. In power-save mode an inexact idle-aware alarm still delivers the
     * event, while allowing the platform to coalesce wakeups and reduce drain.
     */
    public static AlarmScheduleType determineScheduleType(
            int sdkInt, boolean canScheduleExactAlarms, boolean powerSaveMode) {
        if (powerSaveMode && sdkInt >= 23) {
            return AlarmScheduleType.INEXACT_ALLOW_WHILE_IDLE;
        }
        if (sdkInt >= 31) {
            return canScheduleExactAlarms
                    ? AlarmScheduleType.EXACT
                    : AlarmScheduleType.INEXACT_ALLOW_WHILE_IDLE;
        }
        return AlarmScheduleType.EXACT;
    }
}
