package com.comp3606.lostlink.sms;

import android.location.Location;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The LostLink SMS language.
//
// Commands (typed by a person, from ANY phone):   LL <PIN> <COMMAND> [arguments]
//     e.g.  "LL 4821 LOCATE"   "LL 4821 RING 30"   "LL 4821 LOCK Reward if found!"
//
// Replies (sent back by the lost phone):          LostLink: <TYPE> key=value ... free text
//     e.g.  "LostLink: LOC lat=10.64123 lon=-61.39912 acc=12 bat=54 https://maps.google.com/?q=..."
// Replies stay readable on a basic phone, but the key=value pairs let the app parse them.
// Only characters from the basic GSM 7-bit alphabet are used: [ ] { } | ~ are "extension"
// characters that cost two slots and get garbled by some networks (and the emulator).
public final class Protocol {
    public static final String REPLY_TAG = "LostLink:";
    public static final List<String> COMMANDS = Arrays.asList(
            "LOCATE", "RING", "STOP", "LOCK", "UNLOCK", "STATUS", "TRACK", "CALLME", "HELP");

    // Group 1 = optional PIN, group 2 = command word, group 3 = everything after it.
    private static final Pattern COMMAND = Pattern.compile(
            "^\\s*LL\\s+(?:(\\d{4,6})\\s+)?([A-Za-z]+)(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern KEY_VALUE = Pattern.compile("^([a-z]+)=(\\S+)$");

    private Protocol() {}

    public static class Command {
        public final String pin;
        public final String name;
        public final String args;

        Command(String pin, String name, String args) {
            this.pin = pin;
            this.name = name;
            this.args = args;
        }

        // Reads the n-th number after the command, clamped to [min, max].
        public int intArg(int index, int fallback, int min, int max) {
            String[] parts = args.trim().split("\\s+");
            if (index >= parts.length) return fallback;
            try {
                return Math.max(min, Math.min(max, Integer.parseInt(parts[index])));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    // Returns null for any SMS that is not a LostLink command, so normal texts are ignored.
    public static Command parseCommand(String body) {
        if (body == null) return null;
        Matcher m = COMMAND.matcher(body.trim());
        if (!m.matches()) return null;
        String name = m.group(2).toUpperCase(Locale.US);
        if (!COMMANDS.contains(name)) return null;
        String args = m.group(3) == null ? "" : m.group(3).trim();
        return new Command(m.group(1), name, args);
    }

    public static String buildCommand(String pin, String name, String args) {
        String text = "LL " + pin + " " + name;
        return args == null || args.trim().isEmpty() ? text : text + " " + args.trim();
    }

    public static boolean isReply(String body) {
        return body != null && body.trim().startsWith(REPLY_TAG);
    }

    public static class Reply {
        public String type = "";
        public final Map<String, String> values = new HashMap<>();
        public String text = "";
        public String raw = "";

        public String get(String key) {
            return values.get(key);
        }

        public int getInt(String key, int fallback) {
            try {
                return Integer.parseInt(values.get(key));
            } catch (Exception e) {
                return fallback;
            }
        }

        public double getDouble(String key, double fallback) {
            try {
                return Double.parseDouble(values.get(key));
            } catch (Exception e) {
                return fallback;
            }
        }

        public boolean hasLocation() {
            return values.containsKey("lat") && values.containsKey("lon");
        }
    }

    public static Reply parseReply(String body) {
        Reply r = new Reply();
        r.raw = body;
        String rest = body.trim().substring(REPLY_TAG.length()).trim();
        if (rest.isEmpty()) return r;
        String[] tokens = rest.split("\\s+");
        r.type = tokens[0].toUpperCase(Locale.US);
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < tokens.length; i++) {
            Matcher m = KEY_VALUE.matcher(tokens[i]);
            if (m.matches()) {
                r.values.put(m.group(1), m.group(2));
            } else if (!tokens[i].startsWith("http")) {
                if (text.length() > 0) text.append(' ');
                text.append(tokens[i]);
            }
        }
        r.text = text.toString();
        return r;
    }

    public static String reply(String type, String values, String text) {
        StringBuilder sb = new StringBuilder(REPLY_TAG).append(' ').append(type);
        if (values != null && !values.isEmpty()) sb.append(' ').append(values);
        if (text != null && !text.isEmpty()) sb.append(' ').append(text);
        return sb.toString();
    }

    public static String locationValues(Location l) {
        return String.format(Locale.US, "lat=%.5f lon=%.5f acc=%d",
                l.getLatitude(), l.getLongitude(), Math.round(l.getAccuracy()));
    }

    public static String mapsLink(double lat, double lon) {
        return String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", lat, lon);
    }

    // Values cannot contain spaces, so "Digicel TT" travels as "Digicel_TT".
    public static String token(String s) {
        if (s == null || s.trim().isEmpty()) return "unknown";
        return s.trim().replaceAll("\\s+", "_");
    }

    public static String untoken(String s) {
        return s == null ? "" : s.replace('_', ' ');
    }

    // Turns a parsed reply into one friendly sentence for the controller's screen.
    public static String describe(Reply r) {
        int bat = r.getInt("bat", -1);
        String battery = bat >= 0 ? " · battery " + bat + "%" : "";
        switch (r.type) {
            case "LOC": {
                String seq = r.get("seq") != null ? "Tracking update " + r.get("seq") + ": l" : "L";
                return seq + "ocated within " + r.getInt("acc", 0) + " m" + battery;
            }
            case "NOLOC":
                return "Phone answered but has no GPS fix yet" + battery;
            case "STATUS":
                return "Battery " + bat + "%" + ("1".equals(r.get("chg")) ? " (charging)" : "")
                        + " · " + untoken(r.get("net")) + " · sound " + r.get("sound")
                        + ("1".equals(r.get("locked")) ? " · LOCKED" : "")
                        + ("1".equals(r.get("track")) ? " · tracking" : "");
            case "OK":
                return untoken(r.get("cmd")) + " confirmed" + (r.text.isEmpty() ? "" : " - " + r.text);
            case "DENIED":
                return "Wrong PIN - " + r.getInt("left", 0) + " tries left before lockout";
            case "LOCKOUT":
                return "Remote commands blocked for " + r.getInt("mins", 15) + " min (too many wrong PINs)";
            case "ALERT":
                return r.text.isEmpty() ? "Security alert" : r.text;
            case "ERR":
            case "HELP":
            default:
                return r.text.isEmpty() ? r.type : r.text;
        }
    }

    public static String title(Reply r) {
        switch (r.type) {
            case "LOC": return "Location received";
            case "NOLOC": return "No GPS fix";
            case "STATUS": return "Status report";
            case "OK": return untoken(r.get("cmd")) + " confirmed";
            case "DENIED": return "Wrong PIN";
            case "LOCKOUT": return "Locked out";
            case "ALERT": return "Security alert";
            default: return "Reply";
        }
    }
}
