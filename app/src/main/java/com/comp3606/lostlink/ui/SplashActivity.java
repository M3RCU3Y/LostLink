package com.comp3606.lostlink.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.storage.Repo;

public class SplashActivity extends BaseActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setScreen(R.layout.activity_splash);

        View text = findViewById(R.id.splashText);
        text.setAlpha(0f);
        text.setTranslationY(40f);
        text.animate().alpha(1f).translationY(0f).setStartDelay(250).setDuration(700).start();

        View icon = findViewById(R.id.splashIcon);
        icon.setScaleX(0.4f);
        icon.setScaleY(0.4f);
        icon.animate().scaleX(1f).scaleY(1f).setDuration(600).start();

        // Timer: show the logo for 1.8 seconds, then open the right first screen.
        handler.postDelayed(this::route, 1800);
    }

    private void route() {
        Settings s = Repo.settings(this);
        Class<?> next = !s.setupDone ? SetupActivity.class : MainActivity.class;
        startActivity(new Intent(this, next));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
