package com.comp3606.lostlink.ui;

import android.Manifest;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.guard.AdminReceiver;
import com.comp3606.lostlink.guard.Screens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// A checklist of everything LostLink needs, each with an "Allow" button.
public class PermissionsActivity extends BaseActivity {
    public static final String EXTRA_FROM_SETUP = "fromSetup";

    private static class Item {
        final int icon;
        final String title, why;
        final boolean optional;
        final BooleanSupplier granted;
        final Runnable request;

        Item(int icon, String title, String why, boolean optional, BooleanSupplier granted, Runnable request) {
            this.icon = icon;
            this.title = title;
            this.why = why;
            this.optional = optional;
            this.granted = granted;
            this.request = request;
        }
    }

    private ActivityResultLauncher<String[]> askPermissions;
    private LinearLayout list;
    private TextView summary;
    private boolean fromSetup;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setScreen(R.layout.activity_permissions);
        list = findViewById(R.id.permList);
        summary = findViewById(R.id.permSummary);
        fromSetup = getIntent().getBooleanExtra(EXTRA_FROM_SETUP, false);

        askPermissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> render());

        View done = findViewById(R.id.buttonContinue);
        ((TextView) done).setText(fromSetup ? "Continue" : "Done");
        done.setOnClickListener(v -> {
            if (fromSetup) {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(); // the user may be returning from a system settings page
    }

    private List<Item> items() {
        List<Item> items = new ArrayList<>();
        items.add(new Item(R.drawable.ic_sms, "Text messages", "Receive commands and send replies", false,
                () -> Perms.sms(this),
                () -> askPermissions.launch(new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS})));
        items.add(new Item(R.drawable.ic_location, "Location", "Answer LOCATE and TRACK with GPS", false,
                () -> Perms.location(this),
                () -> askPermissions.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})));
        if (Build.VERSION.SDK_INT >= 33) {
            items.add(new Item(R.drawable.ic_ring, "Notifications", "Protection status and replies from your phones", false,
                    () -> Perms.granted(this, Manifest.permission.POST_NOTIFICATIONS),
                    () -> askPermissions.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS})));
        }
        items.add(new Item(R.drawable.ic_phone, "Display over other apps", "Pop up the siren and lock screen instantly", false,
                () -> Settings.canDrawOverlays(this),
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))));
        if (Build.VERSION.SDK_INT >= 34) {
            items.add(new Item(R.drawable.ic_bolt, "Full-screen alerts", "Wake the screen when the siren starts", true,
                    () -> getSystemService(NotificationManager.class).canUseFullScreenIntent(),
                    () -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())))));
        }
        items.add(new Item(R.drawable.ic_call, "Phone calls", "CALLME: the lost phone rings you back", true,
                () -> Perms.granted(this, Manifest.permission.CALL_PHONE),
                () -> askPermissions.launch(new String[]{Manifest.permission.CALL_PHONE})));
        items.add(new Item(R.drawable.ic_lock, "Screen lock", "LOCK also locks the real screen (device admin)", true,
                () -> Screens.isAdmin(this),
                () -> startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, AdminReceiver.class))
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets LostLink lock this screen when you text LOCK."))));
        return items;
    }

    private void render() {
        list.removeAllViews();
        int missing = 0;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Item item : items()) {
            View row = inflater.inflate(R.layout.item_permission, list, false);
            boolean ok = item.granted.getAsBoolean();
            if (!ok && !item.optional) missing++;

            int tint = ok ? R.color.mint : item.optional ? R.color.muted : R.color.amber;
            ImageView icon = row.findViewById(R.id.permIcon);
            icon.setImageResource(item.icon);
            icon.setImageTintList(ColorStateList.valueOf(color(tint)));
            row.findViewById(R.id.permIconBg).setBackgroundTintList(ColorStateList.valueOf((color(tint) & 0x00FFFFFF) | 0x26000000));
            ((TextView) row.findViewById(R.id.permTitle)).setText(item.title);
            ((TextView) row.findViewById(R.id.permWhy)).setText(item.why);

            TextView status = row.findViewById(R.id.permStatus);
            status.setText(ok ? "ALLOWED" : item.optional ? "OPTIONAL" : "NEEDED");
            status.setTextColor(color(tint));

            View button = row.findViewById(R.id.permButton);
            button.setVisibility(ok ? View.GONE : View.VISIBLE);
            button.setOnClickListener(v -> item.request.run());
            list.addView(row);
        }
        summary.setText(missing == 0
                ? "Everything essential is allowed. The optional extras make LostLink even harder to beat."
                : missing + " essential permission" + (missing == 1 ? " is" : "s are") + " still needed before this phone can be protected.");
    }
}
