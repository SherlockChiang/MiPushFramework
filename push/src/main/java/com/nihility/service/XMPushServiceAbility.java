package com.nihility.service;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;
import static top.trumeet.common.Constants.TAG_CONDOM;

import android.content.Context;
import android.os.Build;

import com.nihility.Global;
import com.oasisfeng.condom.CondomContext;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.revival.NotificationsRevivalForSelfUpdated;
import com.xiaomi.push.service.BackgroundActivityStartEnabler;
import com.xiaomi.push.service.PullAllApplicationDataFromServerJob;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.push.service.XMPushServiceMessenger;
import com.xiaomi.xmsf.push.control.XMOutbound;
import com.xiaomi.xmsf.push.control.PushControllerUtils;

public class XMPushServiceAbility extends XMPushServiceListenerNotifier {
    /**
     * Compatibility handle used by the event replay tool. It is populated only while the
     * service is alive and cleared deterministically from destroy(), so it cannot retain a
     * stopped Service for the rest of the process lifetime.
     */
    public static volatile XMPushService xmPushService;
    private XMPushService pushService;

    public XMPushServiceAbility(XMPushService pushService) {
        this.pushService = pushService;
        Global.RegistrationRecorder().initContext(pushService);
        condomContext(pushService);
        initListeners(pushService);
    }

    @Override
    public void created() {
        XMPushService service = pushService;
        try {
            super.created();
            xmPushService = service;
            PushControllerUtils.onPushServiceCreated();
        } catch (RuntimeException | Error e) {
            try {
                super.destroy();
            } catch (RuntimeException | Error cleanupError) {
                e.addSuppressed(cleanupError);
            }
            Global.RegistrationRecorder().clearContext();
            pushService = null;
            throw e;
        }
    }

    @Override
    public void destroy() {
        XMPushService service = pushService;
        try {
            super.destroy();
        } finally {
            if (xmPushService == service) {
                xmPushService = null;
            }
            Global.RegistrationRecorder().clearContext();
            pushService = null;
            PushControllerUtils.onPushServiceDestroyed();
        }
    }


    private void initListeners(XMPushService pushService) {
        addListener(new RegisterRecordAbility(new RegisterRecorder(pushService)));
        addListener(new ForegroundAbility(new ForegroundHelper(pushService)));
        addListener(new MessengerAbility(new XMPushServiceMessenger(pushService)));
        if (SDK_INT > P) {
            addListener(new XMPushServiceListener() {
                @Override
                public void created() {
                    BackgroundActivityStartEnabler.initialize(pushService);
                }
            });
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addListener(new NotificationsRevivalAbility(new NotificationsRevivalForSelfUpdated(pushService, sbn -> sbn.getTag() == null)));
        }
        addListener(new XMPushServiceListener() {
            @Override
            public void connectionStatusChanged(ConnectionStatus connectionStatus) {
                if (connectionStatus == ConnectionStatus.connected) {
                    pushService.executeJob(new PullAllApplicationDataFromServerJob(pushService));
                }
            }
        });
    }

    // todo: 搞清楚这里 hook 了什么，起了什么作用，要不要移到 mipush_hook 中
    private static void condomContext(XMPushService pushService) {
        Context mBase = pushService.getBaseContext();
        JavaCalls.setField(pushService, "mBase",
                CondomContext.wrap(mBase, TAG_CONDOM, XMOutbound.create(mBase, XMPushServiceAbility.class.getSimpleName())));
    }
}
