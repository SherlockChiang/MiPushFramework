package com.xiaomi.push.service.timers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Field;

import top.trumeet.common.utils.AlarmSchedulePolicy.AlarmScheduleType;

@Aspect
public class AlarmManagerTimerAspect {

    @Around("execution(* com.xiaomi.push.service.timers.AlarmManagerTimer.register(..)) && this(timer) && args(intent, deadlineMs)")
    public void aroundRegister(ProceedingJoinPoint joinPoint, Object timer, Intent intent, long deadlineMs) throws Throwable {
        try {
            Context context = getContext(timer);
            if (context == null) {
                joinPoint.proceed();
                return;
            }

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                joinPoint.proceed();
                return;
            }

            boolean canScheduleExact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canScheduleExact = alarmManager.canScheduleExactAlarms();
            }

            AlarmManagerTimerSchedulePolicy.Schedule schedule =
                    AlarmManagerTimerSchedulePolicy.forWallClockDeadline(
                            Build.VERSION.SDK_INT, canScheduleExact, deadlineMs);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, flags);
            long triggerAtMillis = schedule.getTriggerAtMillis();
            int alarmType = toAndroidAlarmType(schedule.getClockType());

            if (schedule.getScheduleType() == AlarmScheduleType.EXACT) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(alarmType, triggerAtMillis, pi);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        alarmManager.setExact(alarmType, triggerAtMillis, pi);
                    } else {
                        alarmManager.set(alarmType, triggerAtMillis, pi);
                    }
                } catch (SecurityException se) {
                    // Fallback to inexact allow while idle on SecurityException
                    scheduleInexact(alarmManager, alarmType, triggerAtMillis, pi);
                }
            } else {
                scheduleInexact(alarmManager, alarmType, triggerAtMillis, pi);
            }

            setField(timer, "mPi", pi);
            setField(timer, "mNextPingTs", schedule.getNextPingTimestampMillis());
        } catch (Throwable e) {
            joinPoint.proceed();
        }
    }

    private static int toAndroidAlarmType(AlarmManagerTimerSchedulePolicy.ClockType clockType) {
        if (clockType != AlarmManagerTimerSchedulePolicy.ClockType.RTC_WAKEUP) {
            throw new IllegalArgumentException("Unsupported alarm clock type: " + clockType);
        }
        return AlarmManager.RTC_WAKEUP;
    }

    private static void scheduleInexact(
            AlarmManager alarmManager, int alarmType, long triggerAtMillis, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(alarmType, triggerAtMillis, pi);
        } else {
            alarmManager.set(alarmType, triggerAtMillis, pi);
        }
    }

    public static Context getContext(Object timer) {
        try {
            Field field = timer.getClass().getDeclaredField("mContext");
            field.setAccessible(true);
            return (Context) field.get(timer);
        } catch (Throwable ignored) {
            try {
                Field field = timer.getClass().getSuperclass().getDeclaredField("mContext");
                field.setAccessible(true);
                return (Context) field.get(timer);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    public static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Throwable ignored) {
            try {
                Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (Throwable ignored2) {
            }
        }
    }
}
