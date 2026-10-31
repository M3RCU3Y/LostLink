package com.comp3606.lostlink;

import android.app.Application;

import com.comp3606.lostlink.util.Notifier;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

public class LostLinkApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Notifier.createChannels(this);

        // OpenStreetMap needs a user agent and somewhere to cache map tiles.
        IConfigurationProvider map = Configuration.getInstance();
        map.setUserAgentValue(getPackageName());
        File base = new File(getCacheDir(), "osmdroid");
        map.setOsmdroidBasePath(base);
        map.setOsmdroidTileCache(new File(base, "tiles"));
    }
}
