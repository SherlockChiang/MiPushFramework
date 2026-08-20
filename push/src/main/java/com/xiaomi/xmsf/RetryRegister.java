package com.xiaomi.xmsf;

import static com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered;

import static top.trumeet.common.Constants.APP_ID;
import static top.trumeet.common.Constants.APP_KEY;

import android.content.Context;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.xmsf.push.control.PushControllerUtils;

public class RetryRegister implements Runnable {

    final int tryRegisterCount;

    final Context context;
    final long generation;

    public RetryRegister(Context context, int i, long generation) {
        Context applicationContext = context.getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
        this.tryRegisterCount = i;
        this.generation = generation;
    }

    @Override
    public void run() {
        if (!PushControllerUtils.beginRegistrationRetry(this, generation)) {
            return;
        }
        if (pushRegistered(this.context)) {
            PushControllerUtils.cancelRegistrationRetry();
            MyLog.i("register successed, stop retry");
            return;
        }
        boolean registrationStarted = PushControllerUtils.runRegistrationRetryIfActive(
                generation, () -> MiPushClient.registerPush(this.context, APP_ID, APP_KEY));
        if (!registrationStarted) {
            return;
        }
        int tryRegisterCount = this.tryRegisterCount + 1;
        if (tryRegisterCount <= 10) {
            MyLog.i("register not successed, register again, retryIndex: " + tryRegisterCount);
            PushControllerUtils.registerPush(this.context, tryRegisterCount, generation);
            return;
        }
        MyLog.i("register not successed, but retry to many times, stop retry");
    }
}
