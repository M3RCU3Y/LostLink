package com.comp3606.lostlink.guard;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.sms.Protocol;

// Facts about this phone that go into STATUS replies.
public final class DeviceInfo {
    private DeviceInfo() {}

    public static int batteryPercent(Context c) {
        Intent b = c.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return -1;
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return level < 0 ? -1 : Math.round(level * 100f / scale);
    }

    public static boolean isCharging(Context c) {
        Intent b = c.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return false;
        int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static String network(Context c) {
        TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
        String name = tm == null ? "" : tm.getNetworkOperatorName();
        return name == null || name.isEmpty() ? "No service" : name;
    }

    public static String soundMode(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        switch (am.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT: return "silent";
            case AudioManager.RINGER_MODE_VIBRATE: return "vibrate";
            default: return "normal";
        }
    }

    public static String model() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public static String statusReply(Context c, Settings s) {
        String values = "bat=" + batteryPercent(c)
                + " chg=" + (isCharging(c) ? 1 : 0)
                + " net=" + Protocol.token(network(c))
                + " sound=" + soundMode(c)
                + " locked=" + (s.locked ? 1 : 0)
                + " track=" + (Tracker.isActive() ? 1 : 0)
                + " model=" + Protocol.token(model());
        return Protocol.reply("STATUS", values, "Protection is on.");
    }
}
