package com.xiaomi.xmsf.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.timers.Alarm;
import com.xiaomi.xmsf.push.control.PushServiceDispatcher;

public class MiPushPingReceiver extends BroadcastReceiver {

    public MiPushPingReceiver() {
    }

    public void onReceive(Context paramContext, Intent paramIntent) {
        if (paramIntent == null) {
            return;
        }
        MyLog.v(paramIntent.getPackage() + " is the package name");
        if (PushConstants.ACTION_PING_TIMER.equals(paramIntent.getAction())) {
            if (TextUtils.equals(paramContext.getPackageName(), paramIntent.getPackage())) {
                MyLog.v("Ping XMChannelService on timer");
                PushServiceDispatcher.dispatchStart(paramContext, false);
            } else {
                MyLog.w("cancel the old ping timer");
                Alarm.stop();
            }
        }
    }
}
