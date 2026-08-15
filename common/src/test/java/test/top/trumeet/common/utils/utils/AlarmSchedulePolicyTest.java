package test.top.trumeet.common.utils.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import top.trumeet.common.utils.AlarmSchedulePolicy;
import top.trumeet.common.utils.AlarmSchedulePolicy.AlarmScheduleType;

public class AlarmSchedulePolicyTest {

    @Test
    public void api34ExactAlarmAllowedReturnsExact() {
        AlarmScheduleType type = AlarmSchedulePolicy.determineScheduleType(34, true);
        assertEquals(AlarmScheduleType.EXACT, type);
    }

    @Test
    public void api34ExactAlarmNotAllowedReturnsInexactAllowWhileIdle() {
        AlarmScheduleType type = AlarmSchedulePolicy.determineScheduleType(34, false);
        assertEquals(AlarmScheduleType.INEXACT_ALLOW_WHILE_IDLE, type);
    }

    @Test
    public void api31ExactAlarmAllowedReturnsExact() {
        AlarmScheduleType type = AlarmSchedulePolicy.determineScheduleType(31, true);
        assertEquals(AlarmScheduleType.EXACT, type);
    }

    @Test
    public void api31ExactAlarmNotAllowedReturnsInexactAllowWhileIdle() {
        AlarmScheduleType type = AlarmSchedulePolicy.determineScheduleType(31, false);
        assertEquals(AlarmScheduleType.INEXACT_ALLOW_WHILE_IDLE, type);
    }

    @Test
    public void legacyApiReturnsExact() {
        AlarmScheduleType type = AlarmSchedulePolicy.determineScheduleType(30, false);
        assertEquals(AlarmScheduleType.EXACT, type);

        AlarmScheduleType type2 = AlarmSchedulePolicy.determineScheduleType(26, false);
        assertEquals(AlarmScheduleType.EXACT, type2);
    }
}
