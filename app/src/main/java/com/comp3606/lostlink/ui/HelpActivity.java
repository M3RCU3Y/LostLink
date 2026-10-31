package com.comp3606.lostlink.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.comp3606.lostlink.R;

// Reference card for every SMS command.
public class HelpActivity extends BaseActivity {
    private static final Object[][] COMMANDS = {
            {R.drawable.ic_location, "Locate", "LL 4821 LOCATE", "Texts back GPS coordinates, accuracy, battery and a Google Maps link."},
            {R.drawable.ic_ring, "Ring", "LL 4821 RING 60", "Loud siren for 5-300 seconds, even on silent. The torch strobes and the screen flashes."},
            {R.drawable.ic_stop, "Stop", "LL 4821 STOP", "Silences the siren and stops live tracking."},
            {R.drawable.ic_lock, "Lock", "LL 4821 LOCK Reward if found!", "Covers the screen with your message and a call button. Only your PIN removes it."},
            {R.drawable.ic_lock_open, "Unlock", "LL 4821 UNLOCK", "Removes the LostLink lock screen remotely."},
            {R.drawable.ic_info, "Status", "LL 4821 STATUS", "Battery, charging, network, sound mode and whether it's locked or tracking."},
            {R.drawable.ic_track, "Track", "LL 4821 TRACK 2 5", "Texts its location every 2 minutes, 5 times. Great for a moving phone."},
            {R.drawable.ic_call, "Call me", "LL 4821 CALLME", "The lost phone phones you back, so you can hear where it is or talk to the finder."},
            {R.drawable.ic_help, "Help", "LL HELP", "Replies with the list of commands. No PIN needed."},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setScreen(R.layout.activity_help);

        LinearLayout list = findViewById(R.id.commandList);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Object[] c : COMMANDS) {
            View card = inflater.inflate(R.layout.item_help, list, false);
            ((ImageView) card.findViewById(R.id.helpIcon)).setImageResource((Integer) c[0]);
            ((TextView) card.findViewById(R.id.helpName)).setText((String) c[1]);
            ((TextView) card.findViewById(R.id.helpCode)).setText((String) c[2]);
            ((TextView) card.findViewById(R.id.helpWhat)).setText((String) c[3]);
            list.addView(card);
        }
    }
}
