package com.comp3606.lostlink.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.ArrayList;

// Sends an SMS the same way as the lecturer's SMS lab, plus multipart support for long replies.
public final class SmsSender {
    private static final String TAG = "SmsSender";

    private SmsSender() {}

    public static boolean send(Context context, String number, String message) {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS not granted");
            return false;
        }
        try {
            SmsManager manager;
            if (Build.VERSION.SDK_INT >= 31) {
                manager = context.getSystemService(SmsManager.class);
            } else {
                manager = SmsManager.getDefault();
            }
            if (manager == null) return false;

            // One SMS holds 160 characters; longer replies are split and re-joined by the receiver.
            ArrayList<String> parts = manager.divideMessage(message);
            if (parts.size() == 1) {
                manager.sendTextMessage(number, null, message, null, null);
            } else {
                manager.sendMultipartTextMessage(number, null, parts, null, null);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "SMS failed", e);
            return false;
        }
    }
}
