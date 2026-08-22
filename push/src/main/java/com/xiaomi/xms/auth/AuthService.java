package com.xiaomi.xms.auth;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Small compatibility implementation of the Xiaomi XMS auth endpoint.
 *
 * <p>HyperOS SystemUI binds this endpoint before it renders a focus
 * notification.  The auth split shipped by recent Xiaomi builds is not part
 * of our single-APK distribution, so the bind otherwise succeeds with no
 * usable result and the renderer receives an empty {@code authResult}.</p>
 *
 * <p>The wire format intentionally mirrors the generated AIDL interface in
 * the stock framework.  Keeping it here avoids a dependency on proprietary
 * auth classes while remaining harmless on AOSP devices (the service is only
 * selected by the Xiaomi SystemUI plugin).</p>
 */
public final class AuthService extends Service {
    private static final String TAG = "MiPushAuthService";
    private static final String SERVICE_ACTION = "com.xiaomi.xms.auth.BIND_AUTH_SERVICE";
    private static final String DESCRIPTOR = "com.xiaomi.xms.auth.IAuthService";
    private static final String CALLBACK_DESCRIPTOR =
            "com.xiaomi.xms.auth.IAuthServiceCallback";
    private static final int TRANSACTION_AUTH = 1;
    private static final int TRANSACTION_SYNC_AUTH = 2;
    private static final int INTERFACE_TRANSACTION = 0x5f4e5446;

    private final IBinder binder = new AuthBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Xiaomi auth compatibility service created");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (intent == null || !SERVICE_ACTION.equals(intent.getAction())) {
            // The framework resolves the service by this action.  Returning
            // the binder for an implicit/empty bind is still safe and keeps
            // compatibility with older plugin revisions which omit action.
            Log.w(TAG, "bind without the Xiaomi auth action: " + intent);
        }
        return binder;
    }

    private final class AuthBinder extends Binder {
        AuthBinder() {
            attachInterface(null, DESCRIPTOR);
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }

            if (data == null) {
                return false;
            }
            data.enforceInterface(DESCRIPTOR);
            switch (code) {
                case TRANSACTION_AUTH:
                    Bundle asyncRequest = readBundle(data);
                    IBinder callback = data.readStrongBinder();
                    Bundle asyncResponse = handleRequest(asyncRequest);
                    if (callback != null) {
                        notifyCallback(callback, asyncResponse);
                    }
                    return true;
                case TRANSACTION_SYNC_AUTH:
                    Bundle syncRequest = readBundle(data);
                    Bundle syncResponse = handleRequest(syncRequest);
                    reply.writeNoException();
                    writeBundle(reply, syncResponse);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    @Nullable
    private static Bundle readBundle(Parcel data) {
        if (data.readInt() == 0) {
            return null;
        }
        Bundle bundle = Bundle.CREATOR.createFromParcel(data);
        if (bundle != null) {
            bundle.setClassLoader(AuthService.class.getClassLoader());
        }
        return bundle;
    }

    private static void writeBundle(Parcel reply, @Nullable Bundle bundle) {
        if (bundle == null) {
            reply.writeInt(0);
            return;
        }
        reply.writeInt(1);
        bundle.writeToParcel(reply, 0);
    }

    private static Bundle handleRequest(@Nullable Bundle request) {
        Bundle response = new Bundle();
        response.putInt("result_code", 0);
        response.putString("result_msg", "Auth is successful");
        // Stock AuthSession returns the original auth parameters.  SystemUI
        // uses this nested bundle when deciding whether the focus template is
        // allowed to render, so returning an empty result is not equivalent.
        response.putBundle("result_auth_params", request == null ? new Bundle() : request);
        response.putBundle("result_extra_bundle", new Bundle());
        return response;
    }

    private static void notifyCallback(IBinder callback, Bundle response) {
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(CALLBACK_DESCRIPTOR);
            writeBundle(data, response);
            // The stock callback is declared one-way.  Do not make the
            // SystemUI binder thread wait for our service process.
            callback.transact(1, data, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable error) {
            // A dead SystemUI callback must never take down the push process.
            Log.w(TAG, "Unable to deliver Xiaomi auth callback", error);
        } finally {
            data.recycle();
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Xiaomi auth compatibility service destroyed");
        super.onDestroy();
    }
}
