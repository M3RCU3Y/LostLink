package com.comp3606.lostlink.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.model.TrustedContact;
import com.comp3606.lostlink.sms.Alerts;
import com.comp3606.lostlink.sms.Protocol;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.Phone;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class TrustedContactsActivity extends BaseActivity {
    private ListAdapter<TrustedContact> adapter;
    private View empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setScreen(R.layout.activity_trusted);
        empty = findViewById(R.id.empty);

        adapter = new ListAdapter<>(R.layout.item_contact, (row, c, pos) -> {
            ((TextView) row.findViewById(R.id.contactInitial)).setText(c.name.substring(0, 1).toUpperCase(Locale.US));
            ((TextView) row.findViewById(R.id.contactName)).setText(c.name);
            ((TextView) row.findViewById(R.id.contactNumber)).setText(c.number);
            row.findViewById(R.id.contactDelete).setOnClickListener(v -> remove(c));
        });
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.buttonAdd).setOnClickListener(v -> add());
        findViewById(R.id.buttonTest).setOnClickListener(v -> sendTest());
        refresh();
    }

    private void refresh() {
        Settings s = Repo.settings(this);
        adapter.submit(s.trusted);
        empty.setVisibility(s.trusted.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void add() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null);
        TextInputLayout nameLayout = v.findViewById(R.id.layoutName);
        TextInputLayout numberLayout = v.findViewById(R.id.layoutNumber);
        TextInputEditText name = v.findViewById(R.id.inputName);
        TextInputEditText number = v.findViewById(R.id.inputNumber);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add trusted contact")
                .setView(v)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(x -> {
            String n = DeviceDialog.text(name), num = DeviceDialog.text(number);
            nameLayout.setError(n.isEmpty() ? "Enter a name" : null);
            numberLayout.setError(Phone.digits(num).length() < 3 ? "Enter a phone number" : null);
            if (n.isEmpty() || Phone.digits(num).length() < 3) return;
            Settings s = Repo.settings(this);
            s.trusted.add(new TrustedContact(n, num));
            Repo.save(this, s);
            dialog.dismiss();
            refresh();
        }));
        dialog.show();
    }

    private void remove(TrustedContact c) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove " + c.name + "?")
                .setPositiveButton("Remove", (d, w) -> {
                    Settings s = Repo.settings(this);
                    for (int i = 0; i < s.trusted.size(); i++) {
                        TrustedContact t = s.trusted.get(i);
                        if (t.name.equals(c.name) && t.number.equals(c.number)) {
                            s.trusted.remove(i);
                            break;
                        }
                    }
                    Repo.save(this, s);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendTest() {
        Settings s = Repo.settings(this);
        if (s.trusted.isEmpty()) {
            toast("Add a contact first");
            return;
        }
        if (!Perms.sms(this)) {
            toast("Allow text messages first");
            startActivity(new Intent(this, PermissionsActivity.class));
            return;
        }
        int sent = Alerts.toTrusted(this, Protocol.reply("ALERT", "type=test",
                s.displayName() + " added you as a LostLink trusted contact. You'll get a text if their phone is in trouble."));
        toast("Test alert sent to " + sent + " contact" + (sent == 1 ? "" : "s"));
    }
}
