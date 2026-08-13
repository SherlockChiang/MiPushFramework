package com.xiaomi.xmsf.push.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import com.elvishew.xlog.XLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PushControllerUtilsTest {
    @Before
    public void setUp() {
        XLog.init();
    }

    @After
    public void tearDown() {
        PushControllerUtils.unregisterLiveReceiver();
        PushControllerUtils.onPushServiceDestroyed();
    }

    @Test
    public void screenWakeReceiverRegistrationIsIdempotent() {
        Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);

        PushControllerUtils.registerLiveReceiver(context);
        PushControllerUtils.registerLiveReceiver(context);

        verify(context, times(1)).registerReceiver(
                any(BroadcastReceiver.class), any(IntentFilter.class));
    }

    @Test
    public void screenWakeReceiverUnregistrationIsIdempotent() {
        Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        PushControllerUtils.registerLiveReceiver(context);

        PushControllerUtils.unregisterLiveReceiver();
        PushControllerUtils.unregisterLiveReceiver();

        verify(context, times(1)).unregisterReceiver(any(BroadcastReceiver.class));
    }

    @Test
    public void pushServiceLifecycleStateIsIdempotent() {
        assertFalse(PushControllerUtils.isPushServiceRunning());
        PushControllerUtils.onPushServiceCreated();
        PushControllerUtils.onPushServiceCreated();
        assertTrue(PushControllerUtils.isPushServiceRunning());
        PushControllerUtils.onPushServiceDestroyed();
        PushControllerUtils.onPushServiceDestroyed();
        assertFalse(PushControllerUtils.isPushServiceRunning());
    }

    @Test
    public void screenWakeReceiverUsesApplicationContext() {
        Context owner = mock(Context.class);
        Context applicationContext = mock(Context.class);
        when(owner.getApplicationContext()).thenReturn(applicationContext);

        PushControllerUtils.registerLiveReceiver(owner);
        PushControllerUtils.unregisterLiveReceiver();

        verify(applicationContext).registerReceiver(
                any(BroadcastReceiver.class), any(IntentFilter.class));
        verify(applicationContext).unregisterReceiver(any(BroadcastReceiver.class));
    }
}
