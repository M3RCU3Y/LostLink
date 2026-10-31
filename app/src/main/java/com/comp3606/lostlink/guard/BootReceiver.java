package com.comp3606.lostlink.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.comp3606.lostlink.storage.Repo;

// A thief restarting the phone does not switch protection off.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Repo.settings(context).protectionOn) return;
        try {
            GuardService.start(context);
        } catch (Exception e) {
            Log.w("BootReceiver", "Could not restart protection", e);
        }
    }
}
