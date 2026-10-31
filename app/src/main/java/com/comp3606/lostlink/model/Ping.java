package com.comp3606.lostlink.model;

import java.io.Serializable;

// A single location fix reported by a remote phone.
public class Ping implements Serializable {
    private static final long serialVersionUID = 1L;

    public final double lat;
    public final double lon;
    public final int accuracy;
    public final long time;

    public Ping(double lat, double lon, int accuracy, long time) {
        this.lat = lat;
        this.lon = lon;
        this.accuracy = accuracy;
        this.time = time;
    }
}
