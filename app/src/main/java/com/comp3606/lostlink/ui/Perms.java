package com.comp3606.lostlink.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import com.comp3606.lostlink.guard.LocationHelper;

final class Perms {
    private Perms() {}

    static boolean granted(Context c, String permission) {
        return c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean sms(Context c) {
        return granted(c, Manifest.permission.SEND_SMS) && granted(c, Manifest.permission.RECEIVE_SMS);
    }

    static boolean location(Context c) {
        return LocationHelper.hasPermission(c);
    }

    // The minimum needed before protection can be switched on.
    static boolean core(Context c) {
        return sms(c) && location(c);
    }
}
