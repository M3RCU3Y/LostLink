package com.comp3606.lostlink.model;

import java.io.Serializable;

// One line of the activity history (activity_log.dat).
public class LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int RECEIVED_COMMAND = 0;
    public static final int SENT_REPLY = 1;
    public static final int SENT_COMMAND = 2;
    public static final int RECEIVED_REPLY = 3;
    public static final int ALERT = 4;
    public static final int DENIED = 5;

    public final long time;
    public final int kind;
    public final String number;
    public final String title;
    public final String detail;

    public LogEntry(int kind, String number, String title, String detail) {
        this.time = System.currentTimeMillis();
        this.kind = kind;
        this.number = number;
        this.title = title;
        this.detail = detail;
    }
}
