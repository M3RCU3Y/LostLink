package com.comp3606.lostlink.sms;

import android.content.Context;
import android.content.Intent;

import com.comp3606.lostlink.model.FeedEntry;
import com.comp3606.lostlink.model.LogEntry;
import com.comp3606.lostlink.model.Ping;
import com.comp3606.lostlink.model.SavedDevice;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.ui.DeviceActivity;
import com.comp3606.lostlink.ui.LogActivity;
import com.comp3606.lostlink.util.LiveEvents;
import com.comp3606.lostlink.util.Notifier;

import java.util.ArrayList;

// Runs on the CONTROLLER phone: turns a "LostLink: ..." reply into map pins, battery, feed entries.
public final class ReplyProcessor {
    private ReplyProcessor() {}

    public static void handle(Context context, String sender, String body) {
        Context c = context.getApplicationContext();
        Protocol.Reply r = Protocol.parseReply(body);
        String summary = Protocol.describe(r);

        ArrayList<SavedDevice> devices = Repo.devices(c);
        SavedDevice device = Repo.findDeviceByNumber(devices, sender);
        if (device != null) {
            long now = System.currentTimeMillis();
            device.lastSeen = now;
            int battery = r.getInt("bat", -1);
            if (battery >= 0) device.battery = battery;
            if (r.hasLocation()) {
                device.addPing(new Ping(r.getDouble("lat", 0), r.getDouble("lon", 0), r.getInt("acc", 0), now));
            }
            device.lastSummary = summary;
            device.addFeed(new FeedEntry(false, summary, body));
            Repo.saveDevices(c, devices);
            LiveEvents.post(LiveEvents.DEVICE_UPDATED, device.id);
        }

        String who = device != null ? device.nickname : sender;
        boolean alert = r.type.equals("ALERT");
        Repo.addLog(c, new LogEntry(alert ? LogEntry.ALERT : LogEntry.RECEIVED_REPLY, sender,
                (alert ? "Alert from " : "Reply from ") + who, summary));

        Intent tap = device != null ? DeviceActivity.intent(c, device.id) : new Intent(c, LogActivity.class);
        Notifier.alert(c, who + " - " + Protocol.title(r), summary, tap);
    }
}
