package com.docreader;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

import com.docreader.utils.PreferencesManager;

/**
 * Application class for Document Reader.
 */
public class DocumentReaderApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Apply saved theme preference
        PreferencesManager prefs = new PreferencesManager(this);
        if (prefs.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}
