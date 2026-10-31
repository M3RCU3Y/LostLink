package com.comp3606.lostlink.storage;

import android.content.Context;

import com.comp3606.lostlink.model.LogEntry;
import com.comp3606.lostlink.model.SavedDevice;
import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.util.LiveEvents;
import com.comp3606.lostlink.util.Phone;

import java.util.ArrayList;

// One place that knows which object lives in which file.
public final class Repo {
    private static final String SETTINGS_FILE = "settings.dat";
    private static final String LOG_FILE = "activity_log.dat";
    private static final String DEVICES_FILE = "devices.dat";
    private static final int MAX_LOG = 200;

    private Repo() {}

    public static Settings settings(Context c) {
        return ObjectStore.load(c, SETTINGS_FILE, new Settings());
    }

    public static void save(Context c, Settings s) {
        ObjectStore.save(c, SETTINGS_FILE, s);
        LiveEvents.post(LiveEvents.SETTINGS_CHANGED, null);
    }

    public static ArrayList<LogEntry> log(Context c) {
        return ObjectStore.load(c, LOG_FILE, new ArrayList<>());
    }

    public static void addLog(Context c, LogEntry entry) {
        ArrayList<LogEntry> list = log(c);
        list.add(0, entry);
        while (list.size() > MAX_LOG) list.remove(list.size() - 1);
        ObjectStore.save(c, LOG_FILE, list);
        LiveEvents.post(LiveEvents.LOG_CHANGED, null);
    }

    public static void clearLog(Context c) {
        ObjectStore.delete(c, LOG_FILE);
        LiveEvents.post(LiveEvents.LOG_CHANGED, null);
    }

    public static ArrayList<SavedDevice> devices(Context c) {
        return ObjectStore.load(c, DEVICES_FILE, new ArrayList<>());
    }

    public static void saveDevices(Context c, ArrayList<SavedDevice> devices) {
        ObjectStore.save(c, DEVICES_FILE, devices);
    }

    public static SavedDevice findDevice(ArrayList<SavedDevice> devices, String id) {
        for (SavedDevice d : devices) if (d.id.equals(id)) return d;
        return null;
    }

    public static SavedDevice findDeviceByNumber(ArrayList<SavedDevice> devices, String number) {
        for (SavedDevice d : devices) if (Phone.same(d.number, number)) return d;
        return null;
    }
}
