package com.comp3606.lostlink.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.model.LogEntry;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.LiveEvents;
import com.comp3606.lostlink.util.Phone;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

// History of every command and reply, loaded from activity_log.dat.
public class LogActivity extends BaseActivity {
    private ListAdapter<LogEntry> adapter;
    private ChipGroup filters;
    private View empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setScreen(R.layout.activity_log);
        empty = findViewById(R.id.empty);
        filters = findViewById(R.id.filters);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.log);
        toolbar.setOnMenuItemClickListener(item -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Clear the activity log?")
                    .setPositiveButton("Clear", (d, w) -> Repo.clearLog(this))
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        adapter = new ListAdapter<>(R.layout.item_log, (row, e, pos) -> {
            int icon, tint;
            switch (e.kind) {
                case LogEntry.RECEIVED_COMMAND: icon = R.drawable.ic_sms; tint = R.color.mint; break;
                case LogEntry.SENT_COMMAND: icon = R.drawable.ic_send; tint = R.color.mint; break;
                case LogEntry.SENT_REPLY: icon = R.drawable.ic_send; tint = R.color.sky; break;
                case LogEntry.RECEIVED_REPLY: icon = R.drawable.ic_sms; tint = R.color.sky; break;
                case LogEntry.DENIED: icon = R.drawable.ic_lock; tint = R.color.danger; break;
                default: icon = R.drawable.ic_warning; tint = R.color.amber; break;
            }
            ImageView iv = row.findViewById(R.id.logIcon);
            iv.setImageResource(icon);
            iv.setImageTintList(ColorStateList.valueOf(color(tint)));
            row.findViewById(R.id.logIconBg).setBackgroundTintList(ColorStateList.valueOf((color(tint) & 0x00FFFFFF) | 0x26000000));
            ((TextView) row.findViewById(R.id.logTitle)).setText(e.title);
            TextView detail = row.findViewById(R.id.logDetail);
            detail.setText(e.detail);
            detail.setVisibility(e.detail == null || e.detail.isEmpty() ? View.GONE : View.VISIBLE);
            ((TextView) row.findViewById(R.id.logTime)).setText(Phone.stamp(e.time));
        });
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        filters.setOnCheckedStateChangeListener((group, ids) -> refresh());
        refresh();
    }

    @Override
    public void onEvent(String type, Object data) {
        if (type.equals(LiveEvents.LOG_CHANGED)) refresh();
    }

    private void refresh() {
        int chip = filters.getCheckedChipId();
        List<LogEntry> shown = new ArrayList<>();
        for (LogEntry e : Repo.log(this)) {
            boolean commands = e.kind == LogEntry.RECEIVED_COMMAND || e.kind == LogEntry.SENT_COMMAND || e.kind == LogEntry.DENIED;
            boolean replies = e.kind == LogEntry.SENT_REPLY || e.kind == LogEntry.RECEIVED_REPLY;
            boolean security = e.kind == LogEntry.ALERT || e.kind == LogEntry.DENIED;
            if (chip == R.id.chipCommands && !commands) continue;
            if (chip == R.id.chipReplies && !replies) continue;
            if (chip == R.id.chipAlerts && !security) continue;
            shown.add(e);
        }
        adapter.submit(shown);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
