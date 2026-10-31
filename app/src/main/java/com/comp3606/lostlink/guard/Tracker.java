package com.comp3606.lostlink.guard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.comp3606.lostlink.model.LogEntry;
import com.comp3606.lostlink.sms.CommandProcessor;
import com.comp3606.lostlink.sms.SmsSender;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.LiveEvents;

// The TRACK command: a repeating timer that texts the location every N minutes, M times.
public final class Tracker {
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable job;
    private static String target = "";
    private static int sent;
    private static int total;
    private static int everyMinutes;

    private Tracker() {}

    public static boolean isActive() { return job != null; }

    public static String describe() {
        if (!isActive()) return "";
        return "Sending location to " + target + " every " + everyMinutes + " min (" + sent + "/" + total + " sent)";
    }

    public static void start(Context context, String number, int minutes, int times) {
        Context c = context.getApplicationContext();
        stop();
        target = number;
        everyMinutes = minutes;
        total = times;
        sent = 0;
        job = new Runnable() {
            @Override
            public void run() {
                Runnable self = this;
                LocationHelper.getFix(c, 20_000, loc -> {
                    if (job != self) return; // stopped while waiting for GPS
                    sent++;
                    String seq = sent + "/" + total;
                    SmsSender.send(c, target, CommandProcessor.locationReply(c, loc, seq));
                    Repo.addLog(c, new LogEntry(LogEntry.SENT_REPLY, target, "Tracking update " + seq,
                            loc == null ? "No GPS fix - sent battery only" : "Location sent"));
                    if (sent >= total) {
                        job = null;
                    } else {
                        handler.postDelayed(self, everyMinutes * 60_000L);
                    }
                    LiveEvents.post(LiveEvents.TRACK_CHANGED, null);
                });
            }
        };
        handler.post(job);
        LiveEvents.post(LiveEvents.TRACK_CHANGED, null);
    }

    public static void stop() {
        if (job != null) handler.removeCallbacks(job);
        job = null;
        LiveEvents.post(LiveEvents.TRACK_CHANGED, null);
    }
}
