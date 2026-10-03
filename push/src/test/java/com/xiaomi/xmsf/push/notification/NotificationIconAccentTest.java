package com.xiaomi.xmsf.push.notification;

import static org.mockito.Mockito.*;

import android.app.Notification;
import androidx.core.app.NotificationCompat;
import com.elvishew.xlog.XLog;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

public class NotificationIconAccentTest {
    @BeforeClass
    public static void initializeLogging() {
        XLog.init();
    }

    @Test
    public void messagingIconPackDoesNotOverrideThemeOrExistingColor() {
        NotificationCompat.Builder builder = mock(NotificationCompat.Builder.class);
        Notification snapshot = mock(Notification.class);
        when(builder.build()).thenReturn(snapshot);
        try (MockedStatic<NotificationCompat.MessagingStyle> styles =
                     mockStatic(NotificationCompat.MessagingStyle.class)) {
            styles.when(() -> NotificationCompat.MessagingStyle
                    .extractMessagingStyleFromNotification(snapshot))
                    .thenReturn(mock(NotificationCompat.MessagingStyle.class));
            NotificationController.applyIconAccent(builder, 0xff6bb8f1);
            verify(builder, never()).setColor(anyInt());
        }
    }

    @Test
    public void ordinaryNotificationsRetainIconAccent() {
        NotificationCompat.Builder builder = mock(NotificationCompat.Builder.class);
        Notification snapshot = mock(Notification.class);
        when(builder.build()).thenReturn(snapshot);
        try (MockedStatic<NotificationCompat.MessagingStyle> styles =
                     mockStatic(NotificationCompat.MessagingStyle.class)) {
            NotificationController.applyIconAccent(builder, 0xff6bb8f1);
        }
        verify(builder).setColor(0xff6bb8f1);
    }

    @Test
    public void incompleteBuilderKeepsItsColor() {
        NotificationCompat.Builder builder = mock(NotificationCompat.Builder.class);
        when(builder.build()).thenThrow(new IllegalStateException("incomplete builder"));
        NotificationController.applyIconAccent(builder, 0xff6bb8f1);
        verify(builder, never()).setColor(anyInt());
    }
}
