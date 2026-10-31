package com.comp3606.lostlink.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.util.LiveEvents;
import com.google.android.material.appbar.MaterialToolbar;

// Shared setup for every screen: edge-to-edge insets (as in the lecturer's labs),
// a back arrow on the toolbar and live refresh when an SMS changes something.
public abstract class BaseActivity extends AppCompatActivity implements LiveEvents.Listener {

    protected void setScreen(int layout) {
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(layout);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());
    }

    // Used by the siren and lock screens: appear on top of the keyguard and switch the screen on.
    protected void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LiveEvents.subscribe(this);
    }

    @Override
    protected void onStop() {
        LiveEvents.unsubscribe(this);
        super.onStop();
    }

    @Override
    public void onEvent(String type, Object data) {
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected int color(int res) {
        return ContextCompat.getColor(this, res);
    }

    // Fills in one of the reusable menu rows (layout/view_row.xml).
    protected void bindRow(int rowId, int icon, int tint, String title, String subtitle, View.OnClickListener click) {
        View row = findViewById(rowId);
        ImageView iconView = row.findViewById(R.id.rowIcon);
        iconView.setImageResource(icon);
        iconView.setImageTintList(ColorStateList.valueOf(color(tint)));
        row.findViewById(R.id.rowIconBg).setBackgroundTintList(ColorStateList.valueOf(
                (color(tint) & 0x00FFFFFF) | 0x26000000));
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        setRowSubtitle(rowId, subtitle);
        row.setOnClickListener(click);
    }

    protected void setRowSubtitle(int rowId, String subtitle) {
        ((TextView) findViewById(rowId).findViewById(R.id.rowSubtitle)).setText(subtitle);
    }
}
