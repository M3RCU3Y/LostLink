package com.comp3606.lostlink.model;

import java.io.Serializable;

// Someone who is texted automatically when something suspicious happens.
public class TrustedContact implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public final String number;

    public TrustedContact(String name, String number) {
        this.name = name;
        this.number = number;
    }
}
