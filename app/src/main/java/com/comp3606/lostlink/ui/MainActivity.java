package com.comp3606.lostlink.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.guard.AlarmController;
import com.comp3606.lostlink.guard.GuardService;
import com.comp3606.lostlink.guard.Tracker;
import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.LiveEvents;
import com.comp3606.lostlink.util.Phone;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

// Home dashboard: protection on/off, live stats, links to every other screen and the Exit button.
public class MainActivity extends BaseActivity {
    private RadarView radar;
    private ImageView heroIcon;
    private TextView statusTitle, statusText, lockoutText;
    private View lockoutBanner;
    private MaterialSwitch protectionSwitch;
    private CountDownTimer lockoutTimer;
    private boolean binding;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setScreen(R.layout.activity_main);

        radar = findViewById(R.id.radar);
        heroIcon = findViewById(R.id.heroIcon);
        statusTitle = findViewById(R.id.statusTitle);
        statusText = findViewById(R.id.statusText);
        lockoutBanner = findViewById(R.id.lockoutBanner);
        lockoutText = findViewById(R.id.lockoutText);
        protectionSwitch = findViewById(R.id.switchProtection);

        findViewById(R.id.buttonSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SetupActivity.class).putExtra(SetupActivity.EXTRA_EDIT, true)));

        bindRow(R.id.rowFind, R.drawable.ic_location, R.color.mint, "Find a phone",
                "Locate, ring or lock another phone by SMS", v -> open(DevicesActivity.class));
        bindRow(R.id.rowGuide, R.drawable.ic_help, R.color.sky, "Command guide",
                "Every SMS command, with examples", v -> open(HelpActivity.class));
        bindRow(R.id.rowContacts, R.drawable.ic_people, R.color.mint, "Trusted contacts",
                "", v -> open(TrustedContactsActivity.class));
        bindRow(R.id.rowLog, R.drawable.ic_history, R.color.sky, "Activity log",
                "Every command and reply, saved on this phone", v -> open(LogActivity.class));
        bindRow(R.id.rowPermissions, R.drawable.ic_shield, R.color.amber, "Permissions",
                "", v -> open(PermissionsActivity.class));
        bindRow(R.id.rowTestSiren, R.drawable.ic_ring, R.color.danger, "Test the siren",
                "Sounds for 10 seconds. Your PIN stops it early", v -> testSiren());
        bindRow(R.id.rowTestLock, R.drawable.ic_lock, R.color.danger, "Preview the lock screen",
                "See exactly what a finder sees", v -> testLock());

        protectionSwitch.setOnCheckedChangeListener((button, on) -> {
            if (!binding) setProtection(on);
        });

        findViewById(R.id.buttonExit).setOnClickListener(v -> confirmExit());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Settings s = Repo.settings(this);
        if (s.locked) {
            startActivity(new Intent(this, LockActivity.class));
            return;
        }
        // Protection should be running; restart it if Android stopped it.
        if (s.protectionOn && !GuardService.isRunning() && Perms.core(this)) {
            try {
                GuardService.start(this);
            } catch (Exception ignored) {
            }
        }
        refresh();
    }

    @Override
    public void onEvent(String type, Object data) {
        if (!type.equals(LiveEvents.RING_TICK)) refresh();
    }

    private void refresh() {
        Settings s = Repo.settings(this);
        boolean on = s.protectionOn;

        binding = true;
        protectionSwitch.setChecked(on);
        binding = false;

        radar.setActive(on);
        heroIcon.setImageResource(on ? R.drawable.ic_shield_check : R.drawable.ic_shield);
        heroIcon.setImageTintList(ColorStateList.valueOf(color(on ? R.color.mint : R.color.grey)));
        statusTitle.setText(on ? "Protected" : "Protection is off");
        if (!on) {
            statusText.setText("Turn it on so this phone answers SMS commands, even when the app is closed.");
        } else if (Tracker.isActive()) {
            statusText.setText(Tracker.describe());
        } else {
            statusText.setText("Lost it? From any phone, text\n\"LL <your PIN> LOCATE\" to this number.");
        }

        stat(R.id.statCommands, String.valueOf(s.commandsReceived), "Commands");
        stat(R.id.statLast, s.lastCommandAt == 0 ? "-" : Phone.ago(s.lastCommandAt), "Last command");
        stat(R.id.statTrusted, String.valueOf(s.trusted.size()), "Trusted");

        setRowSubtitle(R.id.rowContacts, s.trusted.isEmpty()
                ? "Nobody yet - add someone to receive alerts"
                : s.trusted.size() + " will be alerted if something looks wrong");
        setRowSubtitle(R.id.rowPermissions, Perms.core(this)
                ? "Essentials allowed - tap to review extras"
                : "Action needed: allow SMS and location");

        showLockout(s.lockoutUntil);
    }

    private void stat(int id, String value, String label) {
        View card = findViewById(id);
        ((TextView) card.findViewById(R.id.statValue)).setText(value);
        ((TextView) card.findViewById(R.id.statLabel)).setText(label);
    }

    // Timer: a live countdown while remote commands are locked out after wrong PINs.
    private void showLockout(long until) {
        if (lockoutTimer != null) lockoutTimer.cancel();
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            lockoutBanner.setVisibility(View.GONE);
            return;
        }
        lockoutBanner.setVisibility(View.VISIBLE);
        lockoutTimer = new CountDownTimer(left, 1000) {
            @Override
            public void onTick(long ms) {
                lockoutText.setText("Someone sent 3 wrong PINs. Remote commands are blocked for "
                        + Phone.mmss(ms) + ".");
            }

            @Override
            public void onFinish() {
                lockoutBanner.setVisibility(View.GONE);
            }
        }.start();
    }

    private void setProtection(boolean on) {
        Settings s = Repo.settings(this);
        if (on && !Perms.core(this)) {
            toast("Allow text messages and location first");
            refresh();
            open(PermissionsActivity.class);
            return;
        }
        s.protectionOn = on;
        Repo.save(this, s);
        if (on) {
            GuardService.start(this);
            toast("This phone is now protected");
        } else {
            GuardService.stop(this);
            Tracker.stop();
        }
        refresh();
    }

    private void testSiren() {
        AlarmController.start(this, 10);
        open(RingActivity.class);
    }

    private void testLock() {
        Settings s = Repo.settings(this);
        s.locked = true;
        s.lockMessage = "This is a preview. Enter your PIN to unlock.";
        Repo.save(this, s);
        open(LockActivity.class);
    }

    private void confirmExit() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Exit LostLink?")
                .setMessage(Repo.settings(this).protectionOn
                        ? "Protection keeps running in the background, so this phone can still be found."
                        : "Protection is off, so this phone will not answer commands.")
                .setPositiveButton("Exit", (d, w) -> finishAffinity())
                .setNeutralButton("Exit and turn off", (d, w) -> {
                    setProtection(false);
                    finishAffinity();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
    }

    @Override
    protected void onDestroy() {
        if (lockoutTimer != null) lockoutTimer.cancel();
        super.onDestroy();
    }
}
