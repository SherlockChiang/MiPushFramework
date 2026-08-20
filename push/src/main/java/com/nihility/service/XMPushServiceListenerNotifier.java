package com.nihility.service;

import android.content.Intent;

import java.util.concurrent.CopyOnWriteArrayList;

public class XMPushServiceListenerNotifier implements XMPushServiceListener {
    private final CopyOnWriteArrayList<XMPushServiceListener> listeners =
            new CopyOnWriteArrayList<>();

    public final void addListener(XMPushServiceListener listener) {
        listeners.add(listener);
    }

    @Override
    public void created() {
        for (XMPushServiceListener listener : listeners) {
            listener.created();
        }
    }

    @Override
    public void destroy() {
        RuntimeException firstRuntimeFailure = null;
        Error firstError = null;
        try {
            for (XMPushServiceListener listener : listeners) {
                try {
                    listener.destroy();
                } catch (RuntimeException e) {
                    if (firstRuntimeFailure == null) {
                        firstRuntimeFailure = e;
                    }
                } catch (Error e) {
                    if (firstError == null) {
                        firstError = e;
                    }
                }
            }
        } finally {
            // Aspect instances can outlive a stopped Service. Drop listeners so they cannot
            // retain the Service and its receiver/notification helpers until process death.
            listeners.clear();
        }
        if (firstError != null) {
            throw firstError;
        }
        if (firstRuntimeFailure != null) {
            throw firstRuntimeFailure;
        }
    }

    @Override
    public void start(Intent intent) {
        for (XMPushServiceListener listener : listeners) {
            listener.start(intent);
        }
    }

    @Override
    public void connectionStatusChanged(ConnectionStatus connectionStatus) {
        for (XMPushServiceListener listener : listeners) {
            listener.connectionStatusChanged(connectionStatus);
        }
    }
}
