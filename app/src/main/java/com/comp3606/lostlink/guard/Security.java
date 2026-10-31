package com.comp3606.lostlink.guard;

import com.comp3606.lostlink.model.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

// The PIN is never stored: only a salted SHA-256 hash of it is saved to the settings file.
public final class Security {
    private Security() {}

    public static void setPin(Settings s, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        s.pinSalt = hex(salt);
        s.pinHash = hash(pin, s.pinSalt);
    }

    public static boolean checkPin(Settings s, String pin) {
        if (!s.hasPin() || pin == null) return false;
        byte[] expected = s.pinHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(pin, s.pinSalt).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String hash(String pin, String salt) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return hex(sha.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
