package com.comp3606.lostlink.model;

import java.io.Serializable;
import java.util.ArrayList;

// Everything the protected phone remembers. Saved to settings.dat with ObjectOutputStream.
public class Settings implements Serializable {
    private static final long serialVersionUID = 1L;

    public boolean setupDone;
    public boolean protectionOn;
    public String ownerName = "";
    public String backupNumber = "";
    public String pinHash = "";
    public String pinSalt = "";

    public boolean locked;
    public String lockMessage = "";

    public int failedAttempts;
    public long lockoutUntil;
    public boolean lowBatteryAlertSent;

    public int commandsReceived;
    public long lastCommandAt;

    public ArrayList<TrustedContact> trusted = new ArrayList<>();

    public boolean hasPin() {
        return pinHash != null && !pinHash.isEmpty();
    }

    public String displayName() {
        return ownerName == null || ownerName.trim().isEmpty() ? "The owner" : ownerName.trim();
    }
}
