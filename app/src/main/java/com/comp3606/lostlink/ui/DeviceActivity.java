package com.comp3606.lostlink.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.comp3606.lostlink.R;
import com.comp3606.lostlink.model.FeedEntry;
import com.comp3606.lostlink.model.LogEntry;
import com.comp3606.lostlink.model.Ping;
import com.comp3606.lostlink.model.SavedDevice;
import com.comp3606.lostlink.sms.Protocol;
import com.comp3606.lostlink.sms.SmsSender;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.LiveEvents;
import com.comp3606.lostlink.util.Phone;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Remote control for one phone: command buttons, a live map and the SMS conversation.
public class DeviceActivity extends BaseActivity {
    private static final String EXTRA_ID = "deviceId";
    private static final long REPLY_TIMEOUT_MS = 45_000;

    public static Intent intent(Context c, String id) {
        return new Intent(c, DeviceActivity.class).putExtra(EXTRA_ID, id);
    }

    private String deviceId;
    private SavedDevice device;
    private MapView map;
    private View mapEmpty, openMaps, waitCard, feedEmpty;
    private TextView waitText, chipBattery, chipSeen, chipAccuracy;
    private LinearProgressIndicator waitProgress;
    private MaterialToolbar toolbar;
    private ListAdapter<FeedEntry> feedAdapter;
    private CountDownTimer waitTimer;
    private boolean firstMapFix = true;
    private ActivityResultLauncher<String> askSms;
    private Runnable afterPermission;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setScreen(R.layout.activity_device);
        deviceId = getIntent().getStringExtra(EXTRA_ID);
        if (!load()) {
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.device);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menuEdit) {
                DeviceDialog.show(this, deviceId, () -> {
                    load();
                    render();
                });
            } else {
                confirmRemove();
            }
            return true;
        });

        mapEmpty = findViewById(R.id.mapEmpty);
        openMaps = findViewById(R.id.buttonOpenMaps);
        waitCard = findViewById(R.id.waitCard);
        waitText = findViewById(R.id.waitText);
        waitProgress = findViewById(R.id.waitProgress);
        chipBattery = findViewById(R.id.chipBattery);
        chipSeen = findViewById(R.id.chipSeen);
        chipAccuracy = findViewById(R.id.chipAccuracy);
        feedEmpty = findViewById(R.id.feedEmpty);
        setupMap();

        askSms = registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
            if (ok && afterPermission != null) afterPermission.run();
            else if (!ok) toast("SMS permission is needed to send commands");
            afterPermission = null;
        });

        command(R.id.cmdLocate, R.drawable.ic_location, R.color.mint, "Locate", "GPS position on the map",
                () -> send("LOCATE", ""));
        command(R.id.cmdRing, R.drawable.ic_ring, R.color.danger, "Ring", "Loud siren, even on silent",
                this::chooseRing);
        command(R.id.cmdLock, R.drawable.ic_lock, R.color.amber, "Lock", "Lock it with a message",
                this::chooseLock);
        command(R.id.cmdUnlock, R.drawable.ic_lock_open, R.color.mint, "Unlock", "Remove the lock screen",
                () -> send("UNLOCK", ""));
        command(R.id.cmdTrack, R.drawable.ic_track, R.color.sky, "Track", "Repeated location updates",
                this::chooseTrack);
        command(R.id.cmdStatus, R.drawable.ic_info, R.color.sky, "Status", "Battery, network, sound",
                () -> send("STATUS", ""));
        command(R.id.cmdCall, R.drawable.ic_call, R.color.mint, "Call me", "It rings you back",
                () -> send("CALLME", ""));
        command(R.id.cmdStop, R.drawable.ic_stop, R.color.muted, "Stop", "Silence siren, stop tracking",
                () -> send("STOP", ""));

        feedAdapter = new ListAdapter<>(R.layout.item_feed, this::bindFeed);
        RecyclerView feed = findViewById(R.id.feed);
        feed.setLayoutManager(new LinearLayoutManager(this));
        feed.setAdapter(feedAdapter);

        openMaps.setOnClickListener(v -> {
            Ping p = device.lastPing();
            if (p == null) return;
            String geo = "geo:" + p.lat + "," + p.lon + "?q=" + p.lat + "," + p.lon + "(" + Uri.encode(device.nickname) + ")";
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(geo)));
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Protocol.mapsLink(p.lat, p.lon))));
            }
        });

        render();
    }

    private boolean load() {
        device = Repo.findDevice(Repo.devices(this), deviceId);
        return device != null;
    }

    private void command(int id, int icon, int tint, String label, String caption, Runnable action) {
        View card = findViewById(id);
        ImageView iv = card.findViewById(R.id.cmdIcon);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(color(tint)));
        card.findViewById(R.id.cmdIconBg).setBackgroundTintList(ColorStateList.valueOf((color(tint) & 0x00FFFFFF) | 0x26000000));
        ((TextView) card.findViewById(R.id.cmdLabel)).setText(label);
        ((TextView) card.findViewById(R.id.cmdCaption)).setText(caption);
        card.setOnClickListener(v -> action.run());
    }

    // ---------- sending ----------

    private void send(String name, String args) {
        if (!Perms.granted(this, Manifest.permission.SEND_SMS)) {
            afterPermission = () -> send(name, args);
            askSms.launch(Manifest.permission.SEND_SMS);
            return;
        }
        String text = Protocol.buildCommand(device.pin, name, args);
        if (!SmsSender.send(this, device.number, text)) {
            toast("Could not send the SMS");
            return;
        }
        // Save what was sent (PIN hidden) to the device's conversation and the activity log.
        String shown = Protocol.buildCommand("****", name, args);
        ArrayList<SavedDevice> devices = Repo.devices(this);
        SavedDevice d = Repo.findDevice(devices, deviceId);
        if (d != null) {
            d.addFeed(new FeedEntry(true, "You sent " + name + (args.isEmpty() ? "" : " " + args), shown));
            Repo.saveDevices(this, devices);
        }
        Repo.addLog(this, new LogEntry(LogEntry.SENT_COMMAND, device.number, name + " sent to " + device.nickname, shown));
        load();
        render();
        startWaiting(name);
    }

    private void chooseRing() {
        String[] labels = {"15 seconds", "30 seconds", "1 minute", "2 minutes"};
        int[] seconds = {15, 30, 60, 120};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Ring for how long?")
                .setItems(labels, (d, which) -> send("RING", String.valueOf(seconds[which])))
                .show();
    }

    private void chooseTrack() {
        String[] labels = {"Every minute, 5 updates", "Every 2 minutes, 5 updates", "Every 5 minutes, 6 updates"};
        String[] args = {"1 5", "2 5", "5 6"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Live tracking")
                .setItems(labels, (d, which) -> send("TRACK", args[which]))
                .show();
    }

    private void chooseLock() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextInputEditText input = v.findViewById(R.id.input);
        ((com.google.android.material.textfield.TextInputLayout) v.findViewById(R.id.layoutInput))
                .setHint("Message for the finder");
        input.setText("Lost phone! Please call me. Reward if found.");
        new MaterialAlertDialogBuilder(this)
                .setTitle("Lock " + device.nickname)
                .setView(v)
                .setPositiveButton("Lock it", (d, w) -> send("LOCK", DeviceDialog.text(input)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Timer: show a countdown while we wait for the lost phone to text back.
    private void startWaiting(String name) {
        if (waitTimer != null) waitTimer.cancel();
        waitCard.setVisibility(View.VISIBLE);
        waitProgress.setIndicatorColor(color(R.color.sky));
        waitTimer = new CountDownTimer(REPLY_TIMEOUT_MS, 200) {
            @Override
            public void onTick(long ms) {
                waitText.setText("Waiting for a reply to " + name + "...  " + Phone.mmss(ms));
                waitProgress.setProgressCompat((int) (ms * 1000 / REPLY_TIMEOUT_MS), false);
            }

            @Override
            public void onFinish() {
                waitText.setText("No reply yet. The phone may be off or out of signal - its reply will still appear here if it arrives.");
                waitProgress.setProgressCompat(0, true);
            }
        }.start();
    }

    private void stopWaiting() {
        if (waitTimer != null) waitTimer.cancel();
        waitTimer = null;
        if (waitCard.getVisibility() != View.VISIBLE) return;
        waitText.setText("Reply received");
        waitProgress.setIndicatorColor(color(R.color.mint));
        waitProgress.setProgressCompat(1000, true);
        waitCard.postDelayed(() -> waitCard.setVisibility(View.GONE), 1500);
    }

    @Override
    public void onEvent(String type, Object data) {
        if (type.equals(LiveEvents.DEVICE_UPDATED) && deviceId.equals(data)) {
            load();
            render();
            stopWaiting();
        }
    }

    // ---------- drawing ----------

    private void render() {
        toolbar.setTitle(device.nickname);
        toolbar.setSubtitle(device.number);
        chipBattery.setText(device.battery >= 0 ? device.battery + "%" : "--");
        chipSeen.setText(device.lastSeen == 0 ? "Never" : Phone.ago(device.lastSeen));
        Ping p = device.lastPing();
        chipAccuracy.setVisibility(p == null ? View.GONE : View.VISIBLE);
        if (p != null) chipAccuracy.setText("±" + p.accuracy + " m");

        List<FeedEntry> feed = new ArrayList<>(device.feed);
        Collections.reverse(feed);
        feedAdapter.submit(feed);
        feedEmpty.setVisibility(feed.isEmpty() ? View.VISIBLE : View.GONE);
        updateMap();
    }

    private void bindFeed(View row, FeedEntry e, int position) {
        LinearLayout bubble = row.findViewById(R.id.bubble);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bubble.getLayoutParams();
        lp.gravity = e.outgoing ? Gravity.END : Gravity.START;
        bubble.setLayoutParams(lp);
        bubble.setBackgroundResource(e.outgoing ? R.drawable.bubble_out : R.drawable.bubble_in);
        ((TextView) row.findViewById(R.id.feedSummary)).setText(e.summary);
        ((TextView) row.findViewById(R.id.feedRaw)).setText(e.raw);
        ((TextView) row.findViewById(R.id.feedTime)).setText((e.outgoing ? "Sent " : "Received ") + Phone.clock(e.time));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupMap() {
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        // Inverting the standard map tiles gives a dark map that matches the app.
        map.getMapOverlay().setColorFilter(darkMapFilter());
        map.getController().setZoom(15.0);
        map.getController().setCenter(new GeoPoint(10.6418, -61.3995)); // UWI St. Augustine
        // Let the map pan without the page scrolling.
        map.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
    }

    // Brightness is inverted (light land -> dark) and tinted towards the app's navy.
    private static ColorMatrixColorFilter darkMapFilter() {
        float[] tint = {0.62f, 0.72f, 0.85f};
        float[] base = {10, 16, 32};
        float[] m = new float[20];
        for (int c = 0; c < 3; c++) {
            m[c * 5] = -0.30f * tint[c];
            m[c * 5 + 1] = -0.59f * tint[c];
            m[c * 5 + 2] = -0.11f * tint[c];
            m[c * 5 + 4] = base[c] + 255 * tint[c];
        }
        m[18] = 1;
        return new ColorMatrixColorFilter(new ColorMatrix(m));
    }

    private void updateMap() {
        map.getOverlays().clear();
        Ping last = device.lastPing();
        mapEmpty.setVisibility(last == null ? View.VISIBLE : View.GONE);
        openMaps.setVisibility(last == null ? View.GONE : View.VISIBLE);
        if (last == null) {
            map.invalidate();
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int mint = color(R.color.mint);

        // Trail of earlier positions.
        List<GeoPoint> points = new ArrayList<>();
        for (Ping p : device.trail) points.add(new GeoPoint(p.lat, p.lon));
        if (points.size() > 1) {
            Polyline line = new Polyline(map);
            line.setPoints(points);
            line.getOutlinePaint().setColor(mint);
            line.getOutlinePaint().setStrokeWidth(4 * density);
            map.getOverlays().add(line);
            for (int i = 0; i < device.trail.size() - 1; i++) {
                Marker dot = new Marker(map);
                dot.setPosition(points.get(i));
                dot.setIcon(getDrawable(R.drawable.map_dot));
                dot.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                dot.setInfoWindow(null);
                map.getOverlays().add(dot);
            }
        }

        // Accuracy circle + pin for the latest fix.
        GeoPoint here = new GeoPoint(last.lat, last.lon);
        Polygon circle = new Polygon(map);
        circle.setPoints(Polygon.pointsAsCircle(here, Math.max(10, last.accuracy)));
        circle.getFillPaint().setColor((mint & 0x00FFFFFF) | 0x33000000);
        circle.getOutlinePaint().setColor((mint & 0x00FFFFFF) | 0x99000000);
        circle.getOutlinePaint().setStrokeWidth(1.5f * density);
        circle.setInfoWindow(null);
        map.getOverlays().add(circle);

        Marker pin = new Marker(map);
        pin.setPosition(here);
        pin.setIcon(getDrawable(R.drawable.map_pin));
        pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        pin.setTitle(device.nickname);
        pin.setSnippet("Within " + last.accuracy + " m, " + Phone.ago(last.time));
        map.getOverlays().add(pin);

        if (firstMapFix) {
            map.getController().setZoom(17.0);
            map.getController().setCenter(here);
            firstMapFix = false;
        } else {
            map.getController().animateTo(here);
        }
        map.invalidate();
    }

    private void confirmRemove() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove " + device.nickname + "?")
                .setMessage("Its saved locations and conversation will be deleted from this phone.")
                .setPositiveButton("Remove", (d, w) -> {
                    ArrayList<SavedDevice> devices = Repo.devices(this);
                    devices.remove(Repo.findDevice(devices, deviceId));
                    Repo.saveDevices(this, devices);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        if (device != null && load()) render();
    }

    @Override
    protected void onPause() {
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (waitTimer != null) waitTimer.cancel();
        super.onDestroy();
    }
}
