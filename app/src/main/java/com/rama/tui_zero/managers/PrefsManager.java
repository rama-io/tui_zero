package com.rama.tui_zero.managers;

import android.content.Context;
import android.content.SharedPreferences;

import com.rama.tui_zero.R;
import com.rama.tui_zero.objects.PrefTheme;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class PrefsManager {
    public static final String SORT_AZ = "az";
    public static final String SORT_ZA = "za";

    private static final String PREFS_NAME = "tui_zero";

    private static final String KEY_THEME = "settings:theme";
    private static final String KEY_THEME_ROLLED_ID = "settings:theme_rolled_id";
    private static final String KEY_ZOOM_PERCENT = "settings:zoom_percent";
    private static final String KEY_SHOW_SYSTEM_BAR = "system:bar:visible";
    private static final String KEY_KEEP_SCREEN_AWAKE = "system:keep_screen_awake";
    private static final String KEY_SORT_STYLE = "list:sort:style";
    private static final String KEY_KEEP_TOGETHER = "list:sort:keep_together";
    private static final String KEY_RESPECT_NOMEDIA = "folders:respect_nomedia";
    private static final String KEY_DISABLED_FOLDERS = "folders:disabled";
    private static final String KEY_ALL_FOLDERS = "folders:all";
    private static final String FORMAT_KEY_PREFIX = "list:includes:";

    private static PrefsManager instance;
    private final SharedPreferences prefs;
    private final Context appContext;

    private PrefsManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized PrefsManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrefsManager(context);
        }
        return instance;
    }

    // Appearance

    public String getTheme() {
        return prefs.getString(KEY_THEME, PrefTheme.DEFAULT);
    }

    public void setTheme(String themeId) {
        prefs.edit().putString(KEY_THEME, themeId).apply();
    }

    public String getRolledThemeId() {
        return prefs.getString(KEY_THEME_ROLLED_ID, PrefTheme.CATPPUCCIN_MOCHA_MAUVE);
    }

    public void setRolledTheme(String themeId) {
        prefs.edit().putString(KEY_THEME_ROLLED_ID, themeId).apply();
    }

    public int getZoomPercent() {
        return prefs.getInt(KEY_ZOOM_PERCENT, 100);
    }

    public void setZoomPercent(int percent) {
        prefs.edit().putInt(KEY_ZOOM_PERCENT, percent).apply();
    }

    // System

    public boolean isSystemBarVisible() {
        return getBoolean(KEY_SHOW_SYSTEM_BAR, true);
    }

    public void setSystemBarVisible(boolean value) {
        setBoolean(KEY_SHOW_SYSTEM_BAR, value);
    }

    public boolean isKeepScreenAwake() {
        return getBoolean(KEY_KEEP_SCREEN_AWAKE, false);
    }

    public void setKeepScreenAwake(boolean value) {
        setBoolean(KEY_KEEP_SCREEN_AWAKE, value);
    }

    // List

    public String getSortStyle() {
        return prefs.getString(KEY_SORT_STYLE, SORT_AZ);
    }

    public void setSortStyle(String style) {
        prefs.edit().putString(KEY_SORT_STYLE, style).apply();
    }

    public boolean isKeepTogether() {
        return getBoolean(KEY_KEEP_TOGETHER, false);
    }

    public void setKeepTogether(boolean value) {
        setBoolean(KEY_KEEP_TOGETHER, value);
    }

    public boolean isRespectNomedia() {
        return getBoolean(KEY_RESPECT_NOMEDIA, true);
    }

    public void setRespectNomedia(boolean value) {
        setBoolean(KEY_RESPECT_NOMEDIA, value);
    }

    // Audio formats

    public String[] allSupportedAudioFormats() {
        return appContext.getResources().getStringArray(R.array.supported_audio_formats);
    }

    public boolean isAudioFormatEnabled(String format) {
        return getBoolean(formatKey(format), true);
    }

    public void setAudioFormatEnabled(String format, boolean enabled) {
        setBoolean(formatKey(format), enabled);
    }

    public Set<String> getEnabledAudioFormats() {
        Set<String> enabled = new HashSet<String>();
        for (String format : allSupportedAudioFormats()) {
            if (isAudioFormatEnabled(format)) enabled.add(format.toLowerCase(Locale.ROOT));
        }
        return enabled;
    }

    private static String formatKey(String format) {
        return FORMAT_KEY_PREFIX + format.toLowerCase(Locale.ROOT);
    }

    // Folders

    public Set<String> getDisabledFolders() {
        return splitLines(prefs.getString(KEY_DISABLED_FOLDERS, ""));
    }

    public void setDisabledFolders(Collection<String> folders) {
        prefs.edit().putString(KEY_DISABLED_FOLDERS, joinLines(folders)).apply();
    }

    /** Every folder the last scan found music in, including the ones switched off. */
    public Set<String> getAllFolders() {
        return splitLines(prefs.getString(KEY_ALL_FOLDERS, ""));
    }

    public void setAllFolders(Collection<String> folders) {
        prefs.edit().putString(KEY_ALL_FOLDERS, joinLines(folders)).apply();
    }

    private static Set<String> splitLines(String raw) {
        Set<String> result = new TreeSet<String>();
        if (raw == null || raw.trim().length() == 0) return result;
        for (String line : raw.split("\n")) {
            if (line.trim().length() > 0) result.add(line);
        }
        return result;
    }

    private static String joinLines(Collection<String> values) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String value : new TreeSet<String>(values)) {
            if (!first) sb.append('\n');
            sb.append(value);
            first = false;
        }
        return sb.toString();
    }

    // Generic

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }
}
