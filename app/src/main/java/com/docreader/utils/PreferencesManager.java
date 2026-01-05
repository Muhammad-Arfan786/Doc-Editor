package com.docreader.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.docreader.models.RecentFile;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages app preferences and recent files storage.
 */
public class PreferencesManager {

    private static final String PREFS_NAME = "document_reader_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_RECENT_FILES = "recent_files";
    private static final String KEY_DEFAULT_ZOOM = "default_zoom";
    private static final int MAX_RECENT_FILES = 20;

    private final SharedPreferences prefs;
    private final Gson gson;

    public PreferencesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    // Dark Mode
    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    public void setDarkMode(boolean darkMode) {
        prefs.edit().putBoolean(KEY_DARK_MODE, darkMode).apply();
    }

    // Default Zoom
    public int getDefaultZoom() {
        return prefs.getInt(KEY_DEFAULT_ZOOM, 100);
    }

    public void setDefaultZoom(int zoom) {
        prefs.edit().putInt(KEY_DEFAULT_ZOOM, zoom).apply();
    }

    // Recent Files
    public List<RecentFile> getRecentFiles() {
        String json = prefs.getString(KEY_RECENT_FILES, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<ArrayList<RecentFile>>() {}.getType();
        List<RecentFile> files = gson.fromJson(json, type);
        return files != null ? files : new ArrayList<>();
    }

    public void addRecentFile(RecentFile file) {
        List<RecentFile> files = getRecentFiles();

        // Remove if already exists
        files.removeIf(f -> f.getPath().equals(file.getPath()));

        // Add to beginning
        files.add(0, file);

        // Limit size
        while (files.size() > MAX_RECENT_FILES) {
            files.remove(files.size() - 1);
        }

        saveRecentFiles(files);
    }

    public void removeRecentFile(String path) {
        List<RecentFile> files = getRecentFiles();
        files.removeIf(f -> f.getPath().equals(path));
        saveRecentFiles(files);
    }

    public void clearRecentFiles() {
        prefs.edit().remove(KEY_RECENT_FILES).apply();
    }

    private void saveRecentFiles(List<RecentFile> files) {
        String json = gson.toJson(files);
        prefs.edit().putString(KEY_RECENT_FILES, json).apply();
    }
}
