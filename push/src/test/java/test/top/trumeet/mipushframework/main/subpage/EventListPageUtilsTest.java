package test.top.trumeet.mipushframework.main.subpage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xiaomi.xmpush.thrift.XmPushActionContainer;

import org.junit.Test;

import java.util.Set;

import top.trumeet.mipush.provider.entities.Event;
import top.trumeet.mipushframework.main.subpage.EventListPageUtils;

public class EventListPageUtilsTest {

    @Test
    public void getStatusShouldReturnMutableSet() {
        EventListPageUtils utils = new EventListPageUtils(null) {
            @Override
            protected boolean isNotificationDisabled(XmPushActionContainer container) {
                return true;
            }
        };

        {
            Set<String> set = utils.getStatus(null);
            set.add("test");
        }
        {
            XmPushActionContainer container = new XmPushActionContainer();
            Set<String> set = utils.getStatus(container);
            set.add("test");
        }
    }

    @Test
    public void replayStatusRejectsNonNotificationRecords() {
        Event registration = new Event();
        registration.setType(Event.Type.Registration);
        registration.setPayload(new byte[]{1});

        assertFalse(EventListPageUtils.isReplayableEvent(registration));
        assertEquals(
                EventListPageUtils.ReplayStatus.UNSUPPORTED_EVENT,
                EventListPageUtils.getReplayStatus(registration, true));
    }

    @Test
    public void replayStatusRequiresPayloadAndRunningService() {
        Event notification = new Event();
        notification.setType(Event.Type.SendMessage);

        assertTrue(EventListPageUtils.isReplayableEvent(notification));
        assertEquals(
                EventListPageUtils.ReplayStatus.INVALID_PAYLOAD,
                EventListPageUtils.getReplayStatus(notification, true));

        notification.setPayload(new byte[]{1});
        assertEquals(
                EventListPageUtils.ReplayStatus.SERVICE_UNAVAILABLE,
                EventListPageUtils.getReplayStatus(notification, false));
        assertEquals(
                EventListPageUtils.ReplayStatus.READY,
                EventListPageUtils.getReplayStatus(notification, true));
    }
}
