package com.comp3606.lostlink.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.guard.LocationHelper;
import com.comp3606.lostlink.guard.Security;
import com.comp3606.lostlink.sms.Alerts;
import com.comp3606.lostlink.sms.Protocol;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.LiveEvents;
import com.comp3606.lostlink.util.Notifier;
import com.comp3606.lostlink.util.Phone;
import com.google.android.material.button.MaterialButton;

// The LOCK command's screen: owner's message, a call button and a PIN keypad.
// Leaving it (Home / Recents) makes it pop straight back, until the right PIN is entered.
public class LockActivity extends BaseActivity {
    private static final int PIN_DOTS = 6;
    private static final int MAX_TRIES = 5;
    private static final long COOLDOWN_MS = 60_000;

    private final StringBuilder entered = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout dots;
    private TextView error;
    private GridLayout keypad;
    private CountDownTimer cooldown;
    private int wrongTries;
    private boolean unlocked;
    private boolean leavingOnPurpose;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        showOverLockScreen();
        setScreen(R.layout.activity_lock);
        Notifier.cancelFullScreen(this);

        com.comp3606.lostlink.model.Settings s = Repo.settings(this);
        ((RadarView) findViewById(R.id.lockRadar)).setColor(color(R.color.danger));
        ((TextView) findViewById(R.id.lockOwner)).setText("Owner: " + s.displayName());
        ((TextView) findViewById(R.id.lockMessage)).setText(s.lockMessage.isEmpty()
                ? "This phone is lost. Please help return it." : s.lockMessage);

        View call = findViewById(R.id.buttonCallOwner);
        call.setVisibility(s.backupNumber.isEmpty() ? View.GONE : View.VISIBLE);
        call.setOnClickListener(v -> {
            leavingOnPurpose = true;
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + s.backupNumber)));
        });

        dots = findViewById(R.id.pinDots);
        error = findViewById(R.id.lockError);
        keypad = findViewById(R.id.keypad);
        buildDots();
        buildKeypad();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Back does nothing while locked.
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        leavingOnPurpose = false;
        if (!Repo.settings(this).locked) finish();
    }

    @Override
    public void onEvent(String type, Object data) {
        if (type.equals(LiveEvents.LOCK_CHANGED) && !Repo.settings(this).locked) {
            unlocked = true; // unlocked remotely by an UNLOCK command
            finish();
        }
    }

    private void buildDots() {
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < PIN_DOTS; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (14 * d), (int) (14 * d));
            lp.setMargins((int) (7 * d), 0, (int) (7 * d), 0);
            dot.setLayoutParams(lp);
            dots.addView(dot);
        }
        renderDots();
    }

    private void renderDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            dots.getChildAt(i).setBackgroundResource(i < entered.length() ? R.drawable.dot_on : R.drawable.dot_off);
        }
    }

    private void buildKeypad() {
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "OK"};
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String key : keys) {
            MaterialButton button = (MaterialButton) inflater.inflate(R.layout.view_key, keypad, false);
            if (key.equals("DEL")) {
                button.setIconResource(R.drawable.ic_backspace);
                button.setContentDescription("Delete");
                button.setOnClickListener(v -> {
                    if (entered.length() > 0) entered.deleteCharAt(entered.length() - 1);
                    renderDots();
                });
            } else if (key.equals("OK")) {
                button.setIconResource(R.drawable.ic_check);
                button.setIconTintResource(R.color.ink);
                button.setBackgroundTintList(getColorStateList(R.color.mint));
                button.setContentDescription("Unlock");
                button.setOnClickListener(v -> submit());
            } else {
                button.setText(key);
                button.setOnClickListener(v -> {
                    if (entered.length() < PIN_DOTS) entered.append(key);
                    error.setText("");
                    renderDots();
                });
            }
            keypad.addView(button);
        }
    }

    private void submit() {
        if (entered.length() == 0) return;
        com.comp3606.lostlink.model.Settings s = Repo.settings(this);
        if (Security.checkPin(s, entered.toString())) {
            unlocked = true;
            s.locked = false;
            Repo.save(this, s);
            LiveEvents.post(LiveEvents.LOCK_CHANGED, false);
            toast("Unlocked - welcome back");
            finish();
            return;
        }
        wrongTries++;
        entered.setLength(0);
        renderDots();
        dots.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
        if (wrongTries >= MAX_TRIES) {
            startCooldown();
            alertOwner();
        } else {
            error.setText("Wrong PIN - " + (MAX_TRIES - wrongTries) + " tries left");
        }
    }

    // Timer: after 5 wrong PINs the keypad is frozen for a minute.
    private void startCooldown() {
        wrongTries = 0;
        setKeypadEnabled(false);
        cooldown = new CountDownTimer(COOLDOWN_MS, 1000) {
            @Override
            public void onTick(long ms) {
                error.setText("Too many attempts. Try again in " + Phone.mmss(ms));
            }

            @Override
            public void onFinish() {
                error.setText("");
                setKeypadEnabled(true);
            }
        }.start();
    }

    private void setKeypadEnabled(boolean enabled) {
        for (int i = 0; i < keypad.getChildCount(); i++) {
            keypad.getChildAt(i).setEnabled(enabled);
            keypad.getChildAt(i).setAlpha(enabled ? 1f : 0.35f);
        }
    }

    // Someone is guessing PINs on the lock screen: text trusted contacts with the phone's location.
    private void alertOwner() {
        String owner = Repo.settings(this).displayName();
        LocationHelper.getFix(this, 15_000, loc -> {
            String values = "type=lockscreen" + (loc != null ? " " + Protocol.locationValues(loc) : "");
            String text = "Someone entered " + MAX_TRIES + " wrong PINs on " + owner + "'s locked phone."
                    + (loc != null ? " It is here:\n" + Protocol.mapsLink(loc.getLatitude(), loc.getLongitude()) : "");
            Alerts.toTrusted(getApplicationContext(), Protocol.reply("ALERT", values, text));
        });
    }

    // Pressing Home or Recents does not escape: the lock screen comes straight back.
    @Override
    protected void onStop() {
        super.onStop();
        if (!unlocked && !leavingOnPurpose && !isFinishing()
                && Repo.settings(this).locked && Settings.canDrawOverlays(this)) {
            handler.postDelayed(() -> startActivity(new Intent(getApplicationContext(), LockActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)), 600);
        }
    }

    @Override
    protected void onDestroy() {
        if (cooldown != null) cooldown.cancel();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
