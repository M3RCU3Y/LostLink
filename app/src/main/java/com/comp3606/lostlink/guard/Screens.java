package com.comp3606.lostlink.guard;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.comp3606.lostlink.ui.LockActivity;
import com.comp3606.lostlink.ui.RingActivity;
import com.comp3606.lostlink.util.Notifier;

// Opens LostLink screens from the background (when an SMS arrives the app is usually closed).
public final class Screens {
    private Screens() {}

    public static void showRing(Context c) {
        show(c, RingActivity.class, "Siren active", "The owner is trying to find this phone");
    }

    public static void showLock(Context c) {
        show(c, LockActivity.class, "This phone is locked", "Enter the owner's PIN to unlock");
    }

    private static void show(Context c, Class<?> screen, String title, String text) {
        Intent i = new Intent(c, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // With "Display over other apps" Android allows us to open a screen straight away.
        if (Settings.canDrawOverlays(c)) {
            try {
                c.startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        // Otherwise a full-screen notification wakes the screen and opens it.
        Notifier.fullScreen(c, i, title, text);
    }

    public static boolean callBack(Context c, String number) {
        if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false;
        Intent call = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(call);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAdmin(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm.isAdminActive(new ComponentName(c, AdminReceiver.class));
    }

    // If the owner enabled device admin, LOCK also locks the real screen
    // (our lock screen is shown on top of the keyguard when it wakes).
    public static void lockScreenNow(Context c) {
        if (!isAdmin(c)) return;
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                dpm.lockNow();
            } catch (SecurityException ignored) {
            }
        }, 1500);
    }
}
