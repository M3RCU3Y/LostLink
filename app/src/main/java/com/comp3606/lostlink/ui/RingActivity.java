package com.comp3606.lostlink.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.guard.AlarmController;
import com.comp3606.lostlink.guard.Security;
import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.LiveEvents;
import com.comp3606.lostlink.util.Notifier;
import com.comp3606.lostlink.util.Phone;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

// Full-screen flashing alarm shown while the siren plays, with a live countdown.
public class RingActivity extends BaseActivity {
    private ValueAnimator flash;
    private TextView countdown;
    private LinearProgressIndicator progress;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        showOverLockScreen();
        setScreen(R.layout.activity_ring);
        Notifier.cancelFullScreen(this);

        Settings s = Repo.settings(this);
        countdown = findViewById(R.id.ringCountdown);
        progress = findViewById(R.id.ringProgress);
        ((RadarView) findViewById(R.id.ringRadar)).setColor(Color.WHITE);
        ((TextView) findViewById(R.id.ringOwner)).setText("It belongs to " + s.displayName()
                + ". The owner is trying to find it.");

        View call = findViewById(R.id.buttonCallOwner);
        call.setVisibility(s.backupNumber.isEmpty() ? View.GONE : View.VISIBLE);
        call.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + s.backupNumber))));

        findViewById(R.id.buttonSilence).setOnClickListener(v -> trySilence());

        // The whole screen pulses between two reds.
        View root = findViewById(R.id.main);
        flash = ValueAnimator.ofArgb(color(R.color.danger), color(R.color.danger_deep));
        flash.setDuration(450);
        flash.setRepeatMode(ValueAnimator.REVERSE);
        flash.setRepeatCount(ValueAnimator.INFINITE);
        flash.addUpdateListener(a -> root.setBackgroundColor((int) a.getAnimatedValue()));
        flash.start();

        // Back cannot dismiss the siren - only the PIN (or the timer) can.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                toast("Enter the owner's PIN to silence the siren");
            }
        });

        render(AlarmController.secondsLeft());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AlarmController.isRinging()) finish();
    }

    @Override
    public void onEvent(String type, Object data) {
        if (type.equals(LiveEvents.RING_TICK)) render((Integer) data);
        if (type.equals(LiveEvents.RING_STOPPED)) finish();
    }

    private void render(int secondsLeft) {
        countdown.setText(Phone.mmss(secondsLeft * 1000L));
        int total = Math.max(1, AlarmController.totalSeconds());
        progress.setProgressCompat(secondsLeft * 1000 / total, true);
    }

    private void trySilence() {
        TextInputEditText pin = findViewById(R.id.ringPin);
        TextInputLayout layout = findViewById(R.id.ringPinLayout);
        String entered = pin.getText() == null ? "" : pin.getText().toString();
        if (Security.checkPin(Repo.settings(this), entered)) {
            AlarmController.stop();
            toast("Siren silenced");
            finish();
        } else {
            layout.setError("Wrong PIN");
            layout.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
            pin.setText("");
        }
    }

    @Override
    protected void onDestroy() {
        if (flash != null) flash.cancel();
        super.onDestroy();
    }
}
