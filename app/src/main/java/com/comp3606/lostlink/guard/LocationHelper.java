package com.comp3606.lostlink.guard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Gets one fresh location fix, falling back to the last known position when the timer runs out.
public final class LocationHelper {
    public interface Callback {
        void onResult(Location location);
    }

    private LocationHelper() {}

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public static void getFix(Context context, long timeoutMs, Callback callback) {
        Context c = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        if (!hasPermission(c)) {
            main.post(() -> callback.onResult(null));
            return;
        }
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = new ArrayList<>();
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            if (lm.isProviderEnabled(p)) providers.add(p);
        }

        AtomicBoolean done = new AtomicBoolean(false);
        List<LocationListener> listeners = new ArrayList<>();
        Callback finish = loc -> {
            if (!done.compareAndSet(false, true)) return;
            for (LocationListener l : listeners) lm.removeUpdates(l);
            callback.onResult(loc != null ? loc : lastKnown(lm));
        };

        if (providers.isEmpty()) {
            main.post(() -> finish.onResult(null));
            return;
        }

        for (String provider : providers) {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, c.getMainExecutor(), loc -> {
                    if (loc != null) finish.onResult(loc);
                });
            } else {
                LocationListener listener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location loc) {
                        finish.onResult(loc);
                    }

                    @Override
                    public void onStatusChanged(String p, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(String p) {}

                    @Override
                    public void onProviderDisabled(String p) {}
                };
                listeners.add(listener);
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            }
        }
        // Timer: never keep the person waiting longer than timeoutMs.
        main.postDelayed(() -> finish.onResult(null), timeoutMs);
    }

    @SuppressLint("MissingPermission")
    private static Location lastKnown(LocationManager lm) {
        Location best = null;
        for (String p : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(p);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        return best;
    }
}
