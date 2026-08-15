package top.trumeet.mipushframework;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.nihility.Global;
import com.nihility.InternalMessenger;
import com.nihility.service.XMPushServiceListener;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.push.service.XMPushServiceMessenger;
import com.xiaomi.smack.ConnectionConfiguration;

public class MainPageUtils implements AutoCloseable {
    private static final String TAG = MainPageUtils.class.getSimpleName();
    private InternalMessenger messenger;

    public interface ConnectionStatusChanged {
        void onChange(XMPushServiceListener.ConnectionStatus status);
    }

    public MainPageUtils() {
    }

    public synchronized void initOnCreate(Context context, ConnectionStatusChanged connectionStatusChanged) {
        context = context.getApplicationContext();
        if (messenger != null) {
            messenger.close();
        }
        messenger = new InternalMessenger(context) {{
            register(new IntentFilter(XMPushServiceMessenger.IntentSetConnectionStatus));
            addListener(intent -> {
                String status = intent.getStringExtra("status");
                if (status != null && connectionStatusChanged != null) {
                    try {
                        connectionStatusChanged.onChange(XMPushServiceListener.ConnectionStatus.valueOf(status));
                    } catch (Throwable ignored) {
                    }
                }
            });
        }};

        printHookResultForCheck();

        Global.ConfigCenter().loadConfigurations(context);

        messenger.send(new Intent(XMPushServiceMessenger.IntentGetConnectionStatus));
    }

    @Override
    public synchronized void close() {
        if (messenger != null) {
            messenger.close();
            messenger = null;
        }
    }

    void printHookResultForCheck() {
        Log.i(TAG, String.format("[hook_res] MIUIUtils.getIsMIUI() -> [%s]", MIUIUtils.getIsMIUI()));
        Log.i(TAG, String.format("[hook_res] DeviceInfo.quicklyGetIMEI() -> [%s]", DeviceInfo.quicklyGetIMEI(null)));
        Log.i(TAG, String.format("[hook_res] DeviceInfo.getMacAddress() -> [%s]", DeviceInfo.getMacAddress(null)));
        Log.i(TAG, String.format("[hook_res] ConnectionConfiguration.getXmppServerHost() -> [%s]", ConnectionConfiguration.getXmppServerHost()));
    }
}