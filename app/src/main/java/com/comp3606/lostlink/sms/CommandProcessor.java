package com.comp3606.lostlink.sms;

import android.content.Context;
import android.location.Location;

import com.comp3606.lostlink.guard.AlarmController;
import com.comp3606.lostlink.guard.DeviceInfo;
import com.comp3606.lostlink.guard.LocationHelper;
import com.comp3606.lostlink.guard.Screens;
import com.comp3606.lostlink.guard.Security;
import com.comp3606.lostlink.guard.Tracker;
import com.comp3606.lostlink.model.LogEntry;
import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.storage.Repo;
import com.comp3606.lostlink.util.LiveEvents;

// Runs on the PROTECTED phone: checks the PIN, carries out the command, texts back the result.
public final class CommandProcessor {
    public static final int MAX_ATTEMPTS = 3;
    public static final int LOCKOUT_MINUTES = 15;

    private CommandProcessor() {}

    // done is called when all work (including slow GPS lookups) has finished; may be null.
    public static void handle(Context context, String sender, String body, Runnable done) {
        Context c = context.getApplicationContext();
        Protocol.Command cmd = Protocol.parseCommand(body);
        if (cmd == null) {
            finish(done);
            return;
        }

        Settings s = Repo.settings(c);
        long now = System.currentTimeMillis();
        s.commandsReceived++;
        s.lastCommandAt = now;

        // 1. Still locked out after too many wrong PINs?
        if (s.lockoutUntil > now) {
            Repo.save(c, s);
            long mins = (s.lockoutUntil - now + 59_999) / 60_000;
            respond(c, sender, cmd, LogEntry.DENIED, "Blocked - lockout active",
                    Protocol.reply("LOCKOUT", "mins=" + mins, "Too many wrong PINs. Try again later."));
            finish(done);
            return;
        }

        // 2. HELP is the only command that works without a PIN.
        if (cmd.name.equals("HELP")) {
            Repo.save(c, s);
            respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND, "Sent the command list",
                    Protocol.reply("HELP", "", "Send LL PIN CMD. CMD: LOCATE, RING secs, STOP, LOCK msg, UNLOCK, STATUS, TRACK mins times, CALLME"));
            finish(done);
            return;
        }

        // 3. Check the PIN. Three strikes = lockout + text every trusted contact.
        if (cmd.pin == null || !Security.checkPin(s, cmd.pin)) {
            s.failedAttempts++;
            int left = MAX_ATTEMPTS - s.failedAttempts;
            if (left <= 0) {
                s.failedAttempts = 0;
                s.lockoutUntil = now + LOCKOUT_MINUTES * 60_000L;
                Repo.save(c, s);
                respond(c, sender, cmd, LogEntry.DENIED, "Wrong PIN - lockout started",
                        Protocol.reply("LOCKOUT", "mins=" + LOCKOUT_MINUTES, "Too many wrong PINs."));
                Alerts.toTrusted(c, Protocol.reply("ALERT", "type=pin",
                        "Someone at " + sender + " sent " + MAX_ATTEMPTS + " wrong PINs to "
                                + s.displayName() + "'s phone. Remote commands are blocked for "
                                + LOCKOUT_MINUTES + " min."));
            } else {
                Repo.save(c, s);
                respond(c, sender, cmd, LogEntry.DENIED, "Wrong PIN (" + left + " left)",
                        Protocol.reply("DENIED", "left=" + left, "Wrong PIN."));
            }
            finish(done);
            return;
        }

        s.failedAttempts = 0;
        Repo.save(c, s);
        execute(c, s, sender, cmd, done);
    }

    private static void execute(Context c, Settings s, String sender, Protocol.Command cmd, Runnable done) {
        switch (cmd.name) {
            case "LOCATE":
                // GPS is asynchronous: reply only once a fix (or the timeout) comes back.
                LocationHelper.getFix(c, done == null ? 30_000 : 8_000, loc -> {
                    respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND,
                            loc == null ? "No GPS fix - sent battery only" : "Sent GPS location",
                            locationReply(c, loc, null));
                    finish(done);
                });
                return;

            case "RING": {
                int seconds = cmd.intArg(0, 60, 5, 300);
                AlarmController.start(c, seconds);
                Screens.showRing(c);
                respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND, "Siren sounding for " + seconds + "s",
                        Protocol.reply("OK", "cmd=RING secs=" + seconds, "Siren sounding for " + seconds + "s."));
                break;
            }

            case "STOP":
                AlarmController.stop();
                Tracker.stop();
                respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND, "Siren and tracking stopped",
                        Protocol.reply("OK", "cmd=STOP", "Siren and tracking stopped."));
                break;

            case "LOCK":
                s.locked = true;
                s.lockMessage = cmd.args.isEmpty() ? "This phone is lost. Please help return it." : cmd.args;
                Repo.save(c, s);
                LiveEvents.post(LiveEvents.LOCK_CHANGED, true);
                Screens.showLock(c);
                Screens.lockScreenNow(c);
                respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND, "Phone locked",
                        Protocol.reply("OK", "cmd=LOCK", "Phone locked with your message."));
                break;

            case "UNLOCK":
                s.locked = false;
                Repo.save(c, s);
                LiveEvents.post(LiveEvents.LOCK_CHANGED, false);
                respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND, "Phone unlocked",
                        Protocol.reply("OK", "cmd=UNLOCK", "Lock screen removed."));
                break;

            case "STATUS":
                respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND, "Sent status report",
                        DeviceInfo.statusReply(c, s));
                break;

            case "TRACK": {
                int minutes = cmd.intArg(0, 2, 1, 60);
                int times = cmd.intArg(1, 5, 1, 20);
                Tracker.start(c, sender, minutes, times);
                respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND,
                        "Tracking every " + minutes + " min, " + times + " times",
                        Protocol.reply("OK", "cmd=TRACK every=" + minutes + " times=" + times,
                                "Sending location every " + minutes + " min, " + times + " times."));
                break;
            }

            case "CALLME":
                if (Screens.callBack(c, sender)) {
                    respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND, "Calling the sender",
                            Protocol.reply("OK", "cmd=CALLME", "Calling you now."));
                } else {
                    respond(c, sender, cmd, LogEntry.RECEIVED_COMMAND, "Call permission missing",
                            Protocol.reply("ERR", "cmd=CALLME", "Calling is not allowed on this phone."));
                }
                break;
        }
        finish(done);
    }

    // Shared by LOCATE, TRACK and the low-battery alert.
    public static String locationReply(Context c, Location loc, String seq) {
        String values = "bat=" + DeviceInfo.batteryPercent(c) + (seq == null ? "" : " seq=" + seq);
        if (loc == null) {
            return Protocol.reply("NOLOC", values, "No GPS fix yet.");
        }
        long ageSeconds = Math.max(0, (System.currentTimeMillis() - loc.getTime()) / 1000);
        return Protocol.reply("LOC", Protocol.locationValues(loc) + " " + values + " age=" + ageSeconds,
                "\n" + Protocol.mapsLink(loc.getLatitude(), loc.getLongitude()));
    }

    private static void respond(Context c, String sender, Protocol.Command cmd, int kind, String detail, String reply) {
        SmsSender.send(c, sender, reply);
        Repo.addLog(c, new LogEntry(kind, sender, cmd.name + " from " + sender, detail));
    }

    private static void finish(Runnable done) {
        if (done != null) done.run();
    }
}
