package com.xiaomi.xmsf.utils;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import com.xiaomi.xmsf.BuildConfig;

import org.junit.Test;

public class ConfigCenterStartForegroundServiceTest {

    @Test
    public void readsCanonicalForegroundServicePreference() {
        Context context = mock(Context.class);
        SharedPreferences preferences = mock(SharedPreferences.class);
        when(context.getSharedPreferences(
                eq(BuildConfig.APPLICATION_ID + "_preferences"), eq(Context.MODE_MULTI_PROCESS)))
                .thenReturn(preferences);
        when(preferences.getBoolean(ConfigCenter.KEY_START_FOREGROUND_SERVICE, false))
                .thenReturn(true);

        assertTrue(new ConfigCenter().isStartForegroundService(context));

        verify(preferences).getBoolean("StartForegroundService", false);
        verify(preferences, never()).getBoolean("key_start_as_foreground", false);
    }
}
