package com.xiaomi.xmsf.push.control;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.xiaomi.xmsf.utils.ConfigCenter;

import org.junit.Test;

public class PushServiceDispatcherConfigTest {

    @Test
    public void dispatcherUsesConfigCenterForegroundServiceContract() {
        Context context = mock(Context.class);
        ConfigCenter configCenter = mock(ConfigCenter.class);
        when(configCenter.isStartForegroundService(context)).thenReturn(true);

        assertTrue(PushServiceDispatcher.isPersistentForegroundEnabled(context, configCenter));

        verify(configCenter).isStartForegroundService(context);
    }
}
