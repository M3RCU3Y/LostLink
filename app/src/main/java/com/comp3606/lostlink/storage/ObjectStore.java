package com.comp3606.lostlink.storage;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

// Saves and loads whole Java objects to files in the app's private storage.
public final class ObjectStore {
    private static final String TAG = "ObjectStore";

    private ObjectStore() {}

    // Write the object to "<file>.tmp" first, then rename it over the real file.
    // A crash half-way through can therefore never leave a corrupt save behind.
    public static synchronized boolean save(Context context, String fileName, Serializable object) {
        File dir = context.getFilesDir();
        File temp = new File(dir, fileName + ".tmp");
        File target = new File(dir, fileName);
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(temp))) {
            out.writeObject(object);
        } catch (Exception e) {
            Log.e(TAG, "Could not save " + fileName, e);
            return false;
        }
        return temp.renameTo(target);
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T> T load(Context context, String fileName, T fallback) {
        File file = new File(context.getFilesDir(), fileName);
        if (!file.exists()) return fallback;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (T) in.readObject();
        } catch (Exception e) {
            Log.e(TAG, "Could not load " + fileName, e);
            return fallback;
        }
    }

    public static synchronized void delete(Context context, String fileName) {
        new File(context.getFilesDir(), fileName).delete();
    }
}
