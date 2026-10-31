package com.comp3606.lostlink.ui;

import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.model.SavedDevice;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.Phone;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

// Add or edit a phone you control (nickname, number, its PIN).
final class DeviceDialog {
    private DeviceDialog() {}

    static void show(BaseActivity activity, String deviceId, Runnable onSaved) {
        ArrayList<SavedDevice> devices = Repo.devices(activity);
        SavedDevice existing = deviceId == null ? null : Repo.findDevice(devices, deviceId);

        View v = LayoutInflater.from(activity).inflate(R.layout.dialog_contact, null);
        TextInputLayout nameLayout = v.findViewById(R.id.layoutName);
        TextInputLayout numberLayout = v.findViewById(R.id.layoutNumber);
        TextInputLayout pinLayout = v.findViewById(R.id.layoutPin);
        TextInputEditText name = v.findViewById(R.id.inputName);
        TextInputEditText number = v.findViewById(R.id.inputNumber);
        TextInputEditText pin = v.findViewById(R.id.inputPin);
        nameLayout.setHint("Nickname, e.g. Brandon's Pixel");
        numberLayout.setHint("Its phone number");
        pinLayout.setVisibility(View.VISIBLE);
        if (existing != null) {
            name.setText(existing.nickname);
            number.setText(existing.number);
            pin.setText(existing.pin);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(existing == null ? "Add a phone" : "Edit phone")
                .setView(v)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        // Validate before closing: the dialog stays open while something is wrong.
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            String n = text(name), num = text(number), p = text(pin);
            nameLayout.setError(n.isEmpty() ? "Give it a name" : null);
            numberLayout.setError(Phone.digits(num).length() < 3 ? "Enter a phone number" : null);
            pinLayout.setError(!p.matches("\\d{4,6}") ? "4 to 6 digits" : null);
            if (n.isEmpty() || Phone.digits(num).length() < 3 || !p.matches("\\d{4,6}")) return;

            if (existing == null) {
                devices.add(new SavedDevice(n, num, p));
            } else {
                existing.nickname = n;
                existing.number = num;
                existing.pin = p;
            }
            Repo.saveDevices(activity, devices);
            dialog.dismiss();
            onSaved.run();
        }));
        dialog.show();
    }

    static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}
