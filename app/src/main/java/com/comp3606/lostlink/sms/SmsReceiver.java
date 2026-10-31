package com.comp3606.lostlink.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.comp3606.lostlink.guard.GuardService;
import com.comp3606.lostlink.storage.Repo;

import java.util.LinkedHashMap;
import java.util.Map;

// Android wakes this receiver for EVERY incoming SMS, even when LostLink is closed.
// It decides whether the text is a command for this phone or a reply for the controller.
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        // A long SMS arrives as several parts; glue them back together per sender.
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        Map<String, StringBuilder> bySender = new LinkedHashMap<>();
        for (SmsMessage part : parts) {
            String sender = part.getOriginatingAddress();
            if (!bySender.containsKey(sender)) bySender.put(sender, new StringBuilder());
            bySender.get(sender).append(part.getMessageBody());
        }

        for (Map.Entry<String, StringBuilder> entry : bySender.entrySet()) {
            String sender = entry.getKey();
            String body = entry.getValue().toString();

            if (Protocol.parseCommand(body) != null) {
                handleCommand(context, sender, body);
            } else if (Protocol.isReply(body)) {
                ReplyProcessor.handle(context, sender, body);
            }
        }
    }

    private void handleCommand(Context context, String sender, String body) {
        if (!Repo.settings(context).protectionOn) {
            Log.i(TAG, "Command ignored: protection is off");
            return;
        }
        // Normal path: hand the command to the always-on GuardService (no time limit, has location access).
        try {
            GuardService.deliver(context, sender, body);
        } catch (Exception e) {
            // Android may refuse to start a service from the background; handle it right here instead.
            Log.w(TAG, "GuardService unavailable, processing inline", e);
            PendingResult pending = goAsync();
            CommandProcessor.handle(context, sender, body, pending::finish);
        }
    }
}
