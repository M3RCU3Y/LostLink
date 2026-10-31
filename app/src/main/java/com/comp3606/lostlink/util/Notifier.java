package com.comp3606.lostlink.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.ui.MainActivity;

public final class Notifier {
    public static final int GUARD_ID = 1;
    private static final int FULL_SCREEN_ID = 2;
    private static final String CH_GUARD = "guard";
    private static final String CH_ALERTS = "alerts";
    private static int nextId = 100;

    private Notifier() {}

    public static void createChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel guard = new NotificationChannel(CH_GUARD, "Protection status", NotificationManager.IMPORTANCE_LOW);
        guard.setDescription("Shown while LostLink is listening for commands");
        NotificationChannel alerts = new NotificationChannel(CH_ALERTS, "Replies and alerts", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Replies from your phones, sirens and lock screens");
        nm.createNotificationChannel(guard);
        nm.createNotificationChannel(alerts);
    }

    public static Notification guard(Context c) {
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(c, CH_GUARD)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setColor(ContextCompat.getColor(c, R.color.mint))
                .setContentTitle("LostLink is protecting this phone")
                .setContentText("Listening for remote SMS commands")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    public static void alert(Context c, String title, String text, Intent tap) {
        tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int id = nextId++;
        PendingIntent pi = PendingIntent.getActivity(c, id, tap,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        notify(c, id, new NotificationCompat.Builder(c, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setColor(ContextCompat.getColor(c, R.color.mint))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build());
    }

    public static void fullScreen(Context c, Intent screen, String title, String text) {
        PendingIntent pi = PendingIntent.getActivity(c, FULL_SCREEN_ID, screen,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        notify(c, FULL_SCREEN_ID, new NotificationCompat.Builder(c, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setColor(ContextCompat.getColor(c, R.color.danger))
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }

    public static void cancelFullScreen(Context c) {
        c.getSystemService(NotificationManager.class).cancel(FULL_SCREEN_ID);
    }

    private static void notify(Context c, int id, Notification n) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        try {
            if (nm.areNotificationsEnabled()) nm.notify(id, n);
        } catch (SecurityException ignored) {
        }
    }
}
