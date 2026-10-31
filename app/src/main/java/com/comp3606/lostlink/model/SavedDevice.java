package com.comp3606.lostlink.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;

// A phone this app can control remotely (devices.dat).
public class SavedDevice implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_TRAIL = 30;
    private static final int MAX_FEED = 60;

    public final String id = UUID.randomUUID().toString();
    public String nickname;
    public String number;
    public String pin;

    public long lastSeen;
    public int battery = -1;
    public String lastSummary = "";
    public ArrayList<Ping> trail = new ArrayList<>();
    public ArrayList<FeedEntry> feed = new ArrayList<>();

    public SavedDevice(String nickname, String number, String pin) {
        this.nickname = nickname;
        this.number = number;
        this.pin = pin;
    }

    public Ping lastPing() {
        return trail.isEmpty() ? null : trail.get(trail.size() - 1);
    }

    public void addPing(Ping p) {
        trail.add(p);
        while (trail.size() > MAX_TRAIL) trail.remove(0);
    }

    public void addFeed(FeedEntry e) {
        feed.add(e);
        while (feed.size() > MAX_FEED) feed.remove(0);
    }
}
