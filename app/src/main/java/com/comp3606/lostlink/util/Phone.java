package com.comp3606.lostlink.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Phone-number matching and friendly time formatting.
public final class Phone {
    private Phone() {}

    public static String digits(String number) {
        return number == null ? "" : number.replaceAll("[^0-9]", "");
    }

    // "+1 868 555 1234", "18685551234" and "5551234" are the same phone.
    // Emulators show up as e.g. "5554" or "15555215554", so compare the trailing digits.
    public static boolean same(String a, String b) {
        String x = digits(a), y = digits(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        int n = Math.min(7, Math.min(x.length(), y.length()));
        return x.substring(x.length() - n).equals(y.substring(y.length() - n));
    }

    public static String ago(long time) {
        if (time <= 0) return "never";
        long s = (System.currentTimeMillis() - time) / 1000;
        if (s < 45) return "just now";
        if (s < 3600) return (s / 60 == 0 ? 1 : s / 60) + " min ago";
        if (s < 86400) return s / 3600 + " h ago";
        return new SimpleDateFormat("d MMM, h:mm a", Locale.US).format(new Date(time));
    }

    public static String clock(long time) {
        return new SimpleDateFormat("h:mm a", Locale.US).format(new Date(time));
    }

    public static String stamp(long time) {
        return new SimpleDateFormat("d MMM  h:mm:ss a", Locale.US).format(new Date(time));
    }

    public static String mmss(long millis) {
        long total = Math.max(0, (millis + 999) / 1000);
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }
}
