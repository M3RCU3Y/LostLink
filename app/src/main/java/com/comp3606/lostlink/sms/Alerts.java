package com.comp3606.lostlink.sms;

import android.content.Context;

import com.comp3606.lostlink.model.LogEntry;
import com.comp3606.lostlink.model.Settings;
import com.comp3606.lostlink.model.TrustedContact;
import com.comp3606.lostlink.storage.Repo;

// Texts every trusted contact when something suspicious happens.
public final class Alerts {
    private Alerts() {}

    public static int toTrusted(Context c, String message) {
        Settings s = Repo.settings(c);
        int sent = 0;
        for (TrustedContact t : s.trusted) {
            if (SmsSender.send(c, t.number, message)) sent++;
        }
        if (!s.trusted.isEmpty()) {
            Repo.addLog(c, new LogEntry(LogEntry.ALERT, "",
                    "Alert sent to " + sent + " trusted contact" + (sent == 1 ? "" : "s"),
                    Protocol.parseReply(message).text));
        }
        return sent;
    }
}
