package com.comp3606.lostlink.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.model.SavedDevice;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.LiveEvents;
import com.comp3606.lostlink.util.Phone;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

// The phones this app can control remotely.
public class DevicesActivity extends BaseActivity {
    private ListAdapter<SavedDevice> adapter;
    private View empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setScreen(R.layout.activity_devices);
        empty = findViewById(R.id.empty);

        adapter = new ListAdapter<>(R.layout.item_device, (row, d, pos) -> {
            ((TextView) row.findViewById(R.id.deviceName)).setText(d.nickname);
            ((TextView) row.findViewById(R.id.deviceNumber)).setText(d.number);
            ((TextView) row.findViewById(R.id.deviceSummary)).setText(d.lastSummary.isEmpty()
                    ? "No replies yet - tap to send a command" : d.lastSummary);
            ((TextView) row.findViewById(R.id.deviceBattery)).setText(d.battery >= 0 ? d.battery + "%" : "--");
            ((TextView) row.findViewById(R.id.deviceSeen)).setText(d.lastSeen == 0 ? "Never heard from" : "Seen " + Phone.ago(d.lastSeen));
            row.setOnClickListener(v -> startActivity(DeviceActivity.intent(this, d.id)));
            row.setOnLongClickListener(v -> {
                options(d);
                return true;
            });
        });
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.buttonAdd).setOnClickListener(v -> DeviceDialog.show(this, null, this::refresh));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onEvent(String type, Object data) {
        if (type.equals(LiveEvents.DEVICE_UPDATED)) refresh();
    }

    private void refresh() {
        ArrayList<SavedDevice> devices = Repo.devices(this);
        adapter.submit(devices);
        empty.setVisibility(devices.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void options(SavedDevice d) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(d.nickname)
                .setItems(new String[]{"Edit", "Remove"}, (dialog, which) -> {
                    if (which == 0) {
                        DeviceDialog.show(this, d.id, this::refresh);
                    } else {
                        ArrayList<SavedDevice> devices = Repo.devices(this);
                        devices.remove(Repo.findDevice(devices, d.id));
                        Repo.saveDevices(this, devices);
                        refresh();
                    }
                })
                .show();
    }
}
