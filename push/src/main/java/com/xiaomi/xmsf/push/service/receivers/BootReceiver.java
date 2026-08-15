package com.xiaomi.xmsf.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.xiaomi.push.service.ClientEventDispatcher;
import com.xiaomi.xmsf.push.control.PushServiceDispatcher;

/**
 * Created by Trumeet on 2017/8/25.
 * @author Trumeet
 */

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && "android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            PushServiceDispatcher.dispatchStart(context, false);
            try {
                new ClientEventDispatcher().notifyServiceStarted(context);
            } catch (Throwable ignored) {
            }
        }
    }
}
