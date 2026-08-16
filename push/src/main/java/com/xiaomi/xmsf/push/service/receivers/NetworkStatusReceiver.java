package com.xiaomi.xmsf.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.mipush.sdk.PushServiceClient;
import com.xiaomi.smack.util.TrafficUtils;
import com.xiaomi.xmsf.push.control.PushControllerUtils;
import com.xiaomi.xmsf.push.control.PushServiceDispatcher;

public class NetworkStatusReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (context == null || !PushControllerUtils.isPrefsEnable(context)) {
            return;
        }
        PushServiceDispatcher.dispatchStart(context, false);
        try {
            TrafficUtils.notifyNetworkChanage(context);
        } catch (Throwable ignored) {
        }
        try {
            if (Network.hasNetwork(context) && PushServiceClient.getInstance(context).isProvisioned()) {
                PushServiceClient.getInstance(context).processRegisterTask();
            }
        } catch (Throwable ignored) {
        }
    }
}
