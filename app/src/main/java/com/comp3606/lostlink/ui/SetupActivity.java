package com.comp3606.lostlink.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.guard.Security;
import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.storage.Repo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

// First-run wizard (welcome -> owner details -> PIN). Also reused to edit those details later.
public class SetupActivity extends BaseActivity {
    public static final String EXTRA_EDIT = "edit";
    private static final int LAST_PAGE = 2;

    private ViewFlipper flipper;
    private MaterialButton next, back;
    private View[] dots;
    private TextInputEditText inputName, inputBackup, inputPin, inputConfirm;
    private TextInputLayout layoutPin, layoutConfirm;
    private boolean editing;
    private int firstPage;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setScreen(R.layout.activity_setup);

        flipper = findViewById(R.id.flipper);
        next = findViewById(R.id.buttonNext);
        back = findViewById(R.id.buttonBack);
        dots = new View[]{findViewById(R.id.dot0), findViewById(R.id.dot1), findViewById(R.id.dot2)};
        inputName = findViewById(R.id.inputName);
        inputBackup = findViewById(R.id.inputBackup);
        inputPin = findViewById(R.id.inputPin);
        inputConfirm = findViewById(R.id.inputConfirm);
        layoutPin = findViewById(R.id.layoutPin);
        layoutConfirm = findViewById(R.id.layoutConfirm);

        feature(R.id.feature1, R.drawable.ic_location);
        feature(R.id.feature2, R.drawable.ic_ring);
        feature(R.id.feature3, R.drawable.ic_lock);

        Settings s = Repo.settings(this);
        editing = getIntent().getBooleanExtra(EXTRA_EDIT, false);
        if (editing) {
            inputName.setText(s.ownerName);
            inputBackup.setText(s.backupNumber);
            if (s.hasPin()) {
                layoutPin.setHint("New PIN (leave blank to keep)");
                layoutConfirm.setHint("Confirm new PIN");
            }
            firstPage = 1;
            flipper.setDisplayedChild(1);
        }

        next.setOnClickListener(v -> onNext());
        back.setOnClickListener(v -> onBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });
        updateControls();
    }

    private void feature(int id, int icon) {
        TextView t = findViewById(id);
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        t.setCompoundDrawableTintList(ColorStateList.valueOf(color(R.color.mint)));
    }

    private void onNext() {
        int page = flipper.getDisplayedChild();
        if (page == 1 && text(inputName).isEmpty()) {
            inputName.setError("Enter your name");
            inputName.requestFocus();
            return;
        }
        if (page < LAST_PAGE) {
            flipper.setInAnimation(this, R.anim.slide_in_right);
            flipper.setOutAnimation(this, R.anim.slide_out_left);
            flipper.showNext();
            updateControls();
            return;
        }
        finishSetup();
    }

    private void onBack() {
        if (flipper.getDisplayedChild() > firstPage) {
            flipper.setInAnimation(this, R.anim.slide_in_left);
            flipper.setOutAnimation(this, R.anim.slide_out_right);
            flipper.showPrevious();
            updateControls();
        } else {
            finish();
        }
    }

    private void finishSetup() {
        Settings s = Repo.settings(this);
        String pin = text(inputPin);
        String confirm = text(inputConfirm);
        layoutPin.setError(null);
        layoutConfirm.setError(null);

        boolean keepPin = editing && s.hasPin() && pin.isEmpty();
        if (!keepPin) {
            if (!pin.matches("\\d{4,6}")) {
                layoutPin.setError("Use 4 to 6 digits");
                return;
            }
            if (!pin.equals(confirm)) {
                layoutConfirm.setError("The PINs don't match");
                return;
            }
            Security.setPin(s, pin);
        }

        s.ownerName = text(inputName);
        s.backupNumber = text(inputBackup);
        boolean firstTime = !s.setupDone;
        s.setupDone = true;
        Repo.save(this, s);

        if (firstTime) {
            Intent i = new Intent(this, PermissionsActivity.class).putExtra(PermissionsActivity.EXTRA_FROM_SETUP, true);
            startActivity(i);
        } else {
            toast(keepPin ? "Details saved" : "Details and new PIN saved");
        }
        finish();
    }

    private void updateControls() {
        int page = flipper.getDisplayedChild();
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < dots.length; i++) {
            boolean on = i == page;
            dots[i].setBackgroundResource(on ? R.drawable.dot_on : R.drawable.dot_off);
            ViewGroup.LayoutParams lp = dots[i].getLayoutParams();
            lp.width = (int) ((on ? 24 : 8) * density);
            dots[i].setLayoutParams(lp);
        }
        next.setText(page == 0 ? "Get started" : page == LAST_PAGE ? (editing ? "Save" : "Finish") : "Next");
        back.setVisibility(page > firstPage || editing ? View.VISIBLE : View.INVISIBLE);
    }

    private static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}
