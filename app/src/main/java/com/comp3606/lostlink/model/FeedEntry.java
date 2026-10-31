package com.comp3606.lostlink.model;

import java.io.Serializable;

// One SMS in the conversation with a remote phone (shown as a chat bubble).
public class FeedEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    public final long time;
    public final boolean outgoing;
    public final String summary;
    public final String raw;

    public FeedEntry(boolean outgoing, String summary, String raw) {
        this.time = System.currentTimeMillis();
        this.outgoing = outgoing;
        this.summary = summary;
        this.raw = raw;
    }
}
