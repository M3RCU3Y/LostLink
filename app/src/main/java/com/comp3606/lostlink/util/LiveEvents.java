package com.comp3606.lostlink.util;

import android.os.Handler;
import android.os.Looper;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

// Tiny in-app event bus so open screens refresh the moment an SMS is processed.
public final class LiveEvents {
    public static final String SETTINGS_CHANGED = "settings";
    public static final String LOG_CHANGED = "log";
    public static final String DEVICE_UPDATED = "device";
    public static final String RING_TICK = "ring_tick";
    public static final String RING_STOPPED = "ring_stopped";
    public static final String LOCK_CHANGED = "lock";
    public static final String TRACK_CHANGED = "track";

    public interface Listener {
        void onEvent(String type, Object data);
    }

    private static final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private LiveEvents() {}

    public static void subscribe(Listener l) { listeners.add(l); }

    public static void unsubscribe(Listener l) { listeners.remove(l); }

    public static void post(String type, Object data) {
        main.post(() -> {
            for (Listener l : listeners) l.onEvent(type, data);
        });
    }
}
