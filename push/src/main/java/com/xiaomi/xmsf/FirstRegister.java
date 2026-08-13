package com.xiaomi.xmsf;

import static com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered;

import static top.trumeet.common.Constants.APP_ID;
import static top.trumeet.common.Constants.APP_KEY;

import android.content.Context;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.xmsf.push.control.PushControllerUtils;

public class FirstRegister extends ScheduledJobManager.Job {
    public static final String JOB_ID = "xmsf-first-register";

    final Context context;

    public FirstRegister(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
    }

    @Override
    public String getJobId() {
        return JOB_ID;
    }

    @Override
    public void run() {
        if (!PushControllerUtils.isPrefsEnable(this.context)) {
            MyLog.i("push disabled, skip initial registration");
            return;
        }
        boolean registrationStarted = PushControllerUtils.runInitialRegistrationIfEnabled(
                () -> MiPushClient.registerPush(this.context, APP_ID, APP_KEY));
        if (!registrationStarted) {
            MyLog.i("push disabled while initial registration was starting");
            return;
        }
        if (pushRegistered(this.context)) {
            PushControllerUtils.cancelRegistrationRetry();
            MyLog.i("register successed");
        } else {
            PushControllerUtils.registerPush(this.context, 0);
        }
    }
}
