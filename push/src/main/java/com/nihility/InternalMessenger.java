package com.nihility;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PatternMatcher;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class InternalMessenger extends BroadcastReceiver implements AutoCloseable {
    private final LocalBroadcastManager localBroadcast;
    private final CopyOnWriteArrayList<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> registeredFilters = new HashSet<>();
    private boolean registered;
    private volatile boolean closed;

    public InternalMessenger(Context context) {
        Context applicationContext = context.getApplicationContext();
        localBroadcast = LocalBroadcastManager.getInstance(
                applicationContext == null ? context : applicationContext);
    }

    public void send(Intent intent) {
        if (!closed) {
            localBroadcast.sendBroadcast(intent);
        }
    }

    public synchronized void register(IntentFilter intentFilter) {
        if (closed) {
            return;
        }
        String signature = filterSignature(intentFilter);
        if (!registeredFilters.add(signature)) {
            return;
        }
        try {
            localBroadcast.registerReceiver(this, intentFilter);
            registered = true;
        } catch (RuntimeException | Error e) {
            registeredFilters.remove(signature);
            throw e;
        }
    }

    private static String filterSignature(IntentFilter filter) {
        ArrayList<String> fields = new ArrayList<>();
        for (int i = 0; i < filter.countActions(); i++) {
            fields.add(field("action", filter.getAction(i)));
        }
        for (int i = 0; i < filter.countCategories(); i++) {
            fields.add(field("category", filter.getCategory(i)));
        }
        for (int i = 0; i < filter.countDataTypes(); i++) {
            fields.add(field("type", filter.getDataType(i)));
        }
        for (int i = 0; i < filter.countDataSchemes(); i++) {
            fields.add(field("scheme", filter.getDataScheme(i)));
        }
        for (int i = 0; i < filter.countDataSchemeSpecificParts(); i++) {
            fields.add(field("schemePart", pattern(filter.getDataSchemeSpecificPart(i))));
        }
        for (int i = 0; i < filter.countDataAuthorities(); i++) {
            IntentFilter.AuthorityEntry authority = filter.getDataAuthority(i);
            fields.add(field("authority", authority.getHost() + "\u0000" + authority.getPort()));
        }
        for (int i = 0; i < filter.countDataPaths(); i++) {
            fields.add(field("path", pattern(filter.getDataPath(i))));
        }
        Collections.sort(fields);
        return fields.toString();
    }

    private static String pattern(PatternMatcher matcher) {
        return matcher.getType() + ":" + matcher.getPath();
    }

    private static String field(String name, Object value) {
        String text = String.valueOf(value);
        return name.length() + ":" + name + text.length() + ":" + text;
    }

    public synchronized void addListener(MessageListener listener) {
        if (!closed) {
            listeners.add(listener);
        }
    }

    /**
     * Releases this receiver from LocalBroadcastManager. Long-lived owners such as services
     * must call this from their lifecycle teardown; LocalBroadcastManager otherwise retains
     * the receiver (and its service/context graph) for the lifetime of the process.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (registered) {
                localBroadcast.unregisterReceiver(this);
            }
        } finally {
            registered = false;
            registeredFilters.clear();
            listeners.clear();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (closed) {
            return;
        }
        for (MessageListener listener : listeners) {
            if (closed) {
                return;
            }
            listener.onReceive(intent);
        }
    }
}
