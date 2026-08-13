package test.com.nihility.service.service;

import static com.nihility.service.XMPushServiceListener.ConnectionStatus.connecting;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import android.content.Intent;

import com.nihility.service.XMPushServiceListener;
import com.nihility.service.XMPushServiceListenerNotifier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class XMPushServiceListenerNotifierTest {
    @Mock
    XMPushServiceListener listener;
    XMPushServiceListenerNotifier notifier = new XMPushServiceListenerNotifier();

    @Before
    public void setUp() {
        notifier.addListener(listener);
    }

    @Test
    public void invokeListenersForCreated() {
        notifier.created();
        verify(listener).created();
    }

    @Test
    public void invokeListenersForDestroy() {
        notifier.destroy();
        verify(listener).destroy();
    }

    @Test
    public void releaseListenersAfterDestroy() {
        notifier.destroy();
        notifier.destroy();

        verify(listener, times(1)).destroy();
    }

    @Test
    public void listenerMayBeAddedDuringNotification() {
        XMPushServiceListener addedListener = org.mockito.Mockito.mock(
                XMPushServiceListener.class);
        doAnswer(invocation -> {
            notifier.addListener(addedListener);
            return null;
        }).when(listener).created();

        notifier.created();

        verify(listener).created();
        verify(addedListener, times(0)).created();
    }

    @Test(expected = IllegalStateException.class)
    public void destroyContinuesCleanupAfterListenerFailure() {
        XMPushServiceListener secondListener = org.mockito.Mockito.mock(
                XMPushServiceListener.class);
        notifier.addListener(secondListener);
        doThrow(new IllegalStateException("failure")).when(listener).destroy();

        try {
            notifier.destroy();
        } finally {
            verify(secondListener).destroy();
        }
    }

    @Test
    public void invokeListenersForStart() {
        Intent intent = new Intent();
        notifier.start(intent);
        verify(listener).start(intent);
    }

    @Test
    public void invokeListenersForConnectionStatusChanged() {
        notifier.connectionStatusChanged(connecting);
        verify(listener).connectionStatusChanged(connecting);
    }
}
