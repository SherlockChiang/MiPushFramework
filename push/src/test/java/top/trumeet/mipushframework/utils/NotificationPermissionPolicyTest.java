package top.trumeet.mipushframework.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotificationPermissionPolicyTest {
    @Test
    public void preAndroid13DoesNotRequireRuntimePermission() {
        assertEquals(NotificationPermissionPolicy.Status.NOT_REQUIRED,
                NotificationPermissionPolicy.evaluate(32, false, false, false));
    }

    @Test
    public void grantedAlwaysWinsOnAndroid13AndLater() {
        assertEquals(NotificationPermissionPolicy.Status.GRANTED,
                NotificationPermissionPolicy.evaluate(34, true, true, false));
    }

    @Test
    public void firstVisitIsRequestableExactlyOnce() {
        NotificationPermissionPolicy.Status firstVisit =
                NotificationPermissionPolicy.evaluate(34, false, false, false);
        assertEquals(NotificationPermissionPolicy.Status.REQUESTABLE, firstVisit);
        assertTrue(NotificationPermissionPolicy.shouldAutoRequest(firstVisit));

        NotificationPermissionPolicy.Status denied =
                NotificationPermissionPolicy.evaluate(34, false, true, true);
        assertEquals(NotificationPermissionPolicy.Status.DENIED_CAN_ASK_AGAIN, denied);
        assertFalse(NotificationPermissionPolicy.shouldAutoRequest(denied));
    }

    @Test
    public void permanentlyDeniedRoutesToSettingsWithoutRepromptLoop() {
        NotificationPermissionPolicy.Status blocked =
                NotificationPermissionPolicy.evaluate(34, false, true, false);
        assertEquals(NotificationPermissionPolicy.Status.BLOCKED, blocked);
        assertFalse(NotificationPermissionPolicy.shouldAutoRequest(blocked));
    }

    @Test
    public void settingsRoutePrefersPerAppNotificationSettingsWhenResolvable() {
        assertEquals(NotificationPermissionPolicy.SettingsRoute.APP_NOTIFICATION_SETTINGS,
                NotificationPermissionPolicy.chooseSettingsRoute(true));
    }

    @Test
    public void settingsRouteFallsBackToApplicationDetailsWhenUnavailable() {
        assertEquals(NotificationPermissionPolicy.SettingsRoute.APPLICATION_DETAILS_SETTINGS,
                NotificationPermissionPolicy.chooseSettingsRoute(false));
    }
}
