package com.comp3606.lostlink.guard;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.sms.Alerts;
import com.comp3606.lostlink.sms.CommandProcessor;
import com.comp3606.lostlink.sms.Protocol;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.Notifier;

// "Protection is on": a foreground service that keeps LostLink alive in the background,
// lets it read GPS while the screen is off, and watches the battery for a last-gasp alert.
public class GuardService extends Service {
    private static final String TAG = "GuardService";
    private static final String ACTION_COMMAND = "com.comp3606.lostlink.COMMAND";
    private static final String EXTRA_SENDER = "sender";
    private static final String EXTRA_BODY = "body";
    private static final int LOW_BATTERY = 10;

    private static boolean running;
    private BroadcastReceiver batteryWatcher;

    public static boolean isRunning() { return running; }

    public static void start(Context c) {
        ContextCompat.startForegroundService(c, new Intent(c, GuardService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, GuardService.class));
    }

    // Called by SmsReceiver. Throws if Android refuses to start the service from the background.
    public static void deliver(Context c, String sender, String body) {
        Intent i = new Intent(c, GuardService.class)
                .setAction(ACTION_COMMAND)
                .putExtra(EXTRA_SENDER, sender)
                .putExtra(EXTRA_BODY, body);
        ContextCompat.startForegroundService(c, i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        batteryWatcher = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onBattery(intent);
            }
        };
        ContextCompat.registerReceiver(this, batteryWatcher,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!goForeground()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_COMMAND.equals(intent.getAction())) {
            CommandProcessor.handle(this, intent.getStringExtra(EXTRA_SENDER), intent.getStringExtra(EXTRA_BODY), null);
        }
        return START_STICKY;
    }

    private boolean goForeground() {
        try {
            Notification n = Notifier.guard(this);
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(Notifier.GUARD_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(Notifier.GUARD_ID, n);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Could not enter the foreground", e);
            return false;
        }
    }

    // Battery about to die? Text the last known location to trusted contacts (once per discharge).
    private void onBattery(Intent b) {
        int level = Math.round(b.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) * 100f
                / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;

        Settings s = Repo.settings(this);
        if (!s.protectionOn) return;
        if (level <= LOW_BATTERY && !charging && !s.lowBatteryAlertSent && !s.trusted.isEmpty()) {
            s.lowBatteryAlertSent = true;
            Repo.save(this, s);
            String owner = s.displayName();
            LocationHelper.getFix(this, 30_000, loc -> {
                String values = "type=battery bat=" + level + (loc != null ? " " + Protocol.locationValues(loc) : "");
                String text = owner + "'s phone is at " + level + "% and may switch off soon."
                        + (loc != null ? " Last location:\n" + Protocol.mapsLink(loc.getLatitude(), loc.getLongitude()) : "");
                Alerts.toTrusted(this, Protocol.reply("ALERT", values, text));
            });
        } else if (level >= 20 && s.lowBatteryAlertSent) {
            s.lowBatteryAlertSent = false;
            Repo.save(this, s);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (batteryWatcher != null) unregisterReceiver(batteryWatcher);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
