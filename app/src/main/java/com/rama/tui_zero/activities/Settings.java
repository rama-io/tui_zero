package com.rama.tui_zero.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rama.tui_zero.R;
import com.rama.tui_zero.helpers.SystemBars;
import com.rama.tui_zero.managers.FontManager;
import com.rama.tui_zero.managers.MusicManager;
import com.rama.tui_zero.managers.PrefsManager;
import com.rama.tui_zero.managers.ThemeManager;
import com.rama.tui_zero.managers.ZoomManager;
import com.rama.tui_zero.objects.PrefTheme;
import com.rama.tui_zero.objects.Themes;
import com.rama.tui_zero.widgets.WdCheckbox;
import com.rama.tui_zero.widgets.WdRadio;
import com.rama.tui_zero.widgets.WdRadioGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Settings extends BaseActivity {
    private static final int REQ_PERMS = 10;
    private static final int REQ_STORAGE = 11;

    private PrefsManager prefs;
    private LinearLayout foldersContainer;
    private boolean changed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = PrefsManager.getInstance(this);
        foldersContainer = findViewById(R.id.folders_container);
        View root = findViewById(R.id.root);
        SystemBars.applyInsets(root);
        FontManager.apply(root, FontManager.getJersey25(this));
        setupSystemSection();
        setupZoomSection();
        renderFolders();
        setupThemeSection();
        setupListSection();
        ThemeManager.applyTheme(this, root);
        findViewById(R.id.go_about).setOnClickListener(v -> startActivity(new Intent(Settings.this, About.class)));
        findViewById(R.id.go_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionButtonStates();
    }

    @Override
    protected void onPause() {
        saveZoomFromField();
        super.onPause();
    }

    @Override
    public void finish() {
        if (changed) setResult(RESULT_OK);
        super.finish();
    }

    // region System

    private void setupSystemSection() {
        findViewById(R.id.media_permission_button).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQ_PERMS);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMS);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById(R.id.storage_permission_button).setOnClickListener(v -> {
                try {
                    startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName())), REQ_STORAGE);
                } catch (Exception e) {
                    openSettingsFallback();
                }
            });
        } else {
            findViewById(R.id.storage_permission_button).setVisibility(View.GONE);
            findViewById(R.id.storage_separator).setVisibility(View.GONE);
        }

        findViewById(R.id.notification_permission_button).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMS);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
                } catch (Exception e) {
                    openSettingsFallback();
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            findViewById(R.id.background_playback_button).setOnClickListener(v -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception e) {
                    openSettingsFallback();
                }
            });
        } else {
            findViewById(R.id.background_playback_button).setVisibility(View.GONE);
            findViewById(R.id.background_separator).setVisibility(View.GONE);
        }

        final WdCheckbox systemBar = findViewById(R.id.show_system_bar);
        systemBar.setChecked(prefs.isSystemBarVisible());
        systemBar.setOnCheckedChangeListener(checked -> {
            prefs.setSystemBarVisible(checked);
            SystemBars.setVisible(Settings.this, checked);
        });

        final WdCheckbox keepAwake = findViewById(R.id.keep_screen_awake);
        keepAwake.setChecked(prefs.isKeepScreenAwake());
        keepAwake.setOnCheckedChangeListener(checked -> {
            prefs.setKeepScreenAwake(checked);
            if (checked) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    private void openSettingsFallback() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_unable_open_settings, Toast.LENGTH_SHORT).show();
        }
    }

    private void setButtonState(int id, boolean done) {
        Button button = findViewById(id);
        button.setEnabled(!done);
        button.setAlpha(done ? 0.4f : 1f);
    }

    private void refreshPermissionButtonStates() {
        boolean media;
        if (Build.VERSION.SDK_INT >= 33) {
            media = checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            media = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            media = true;
        }
        setButtonState(R.id.media_permission_button, media);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setButtonState(R.id.storage_permission_button, android.os.Environment.isExternalStorageManager());
        }
        boolean notifications = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        setButtonState(R.id.notification_permission_button, notifications);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            setButtonState(R.id.background_playback_button, pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()));
        }
        // Rendering the folder list needs a finished scan, which needs permission.
        if (foldersContainer != null && prefs.getAllFolders().isEmpty() && MusicManager.hasPermission(this)) {
            rescan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshPermissionButtonStates();
        if (MusicManager.hasPermission(this)) rescan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_STORAGE && MusicManager.hasPermission(this)) rescan();
    }

    // endregion

    // region Zoom

    private void setupZoomSection() {
        final EditText zoom = findViewById(R.id.zoom);
        zoom.setText(String.valueOf(ZoomManager.getPercent(this)));
        zoom.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) applyZoom();
            return false;
        });
        zoom.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyZoom();
        });
    }

    private void applyZoom() {
        saveZoomFromField();
        ZoomManager.apply(this, findViewById(R.id.root));
    }

    private void saveZoomFromField() {
        EditText zoom = findViewById(R.id.zoom);
        int percent;
        try {
            percent = ZoomManager.clamp(Integer.parseInt(zoom.getText().toString().trim()));
        } catch (NumberFormatException e) {
            percent = ZoomManager.getPercent(this);
        }
        String normalized = String.valueOf(percent);
        if (!normalized.equals(zoom.getText().toString())) zoom.setText(normalized);
        prefs.setZoomPercent(percent);
    }

    // endregion

    // region Folders

    private void rescan() {
        MusicManager.loadTracks(this, success -> {
            if (!isFinishing()) renderFolders();
        });
    }

    private static String shortLabel(String path) {
        String[] parts = path.split("/");
        int n = parts.length;
        if (n >= 2) return parts[n - 2] + "/" + parts[n - 1];
        return n == 1 ? parts[0] : path;
    }

    private void renderFolders() {
        foldersContainer.removeAllViews();
        List<String> folders = new ArrayList<>(prefs.getAllFolders());
        java.util.Collections.sort(folders);
        View empty = findViewById(R.id.folders_empty);
        empty.setVisibility(folders.isEmpty() ? View.VISIBLE : View.GONE);
        final Set<String> disabled = new HashSet<>(prefs.getDisabledFolders());
        for (final String folder : folders) {
            WdCheckbox box = new WdCheckbox(this);
            box.setText(shortLabel(folder));
            box.setChecked(!disabled.contains(folder));
            box.setOnCheckedChangeListener(checked -> {
                Set<String> now = new HashSet<>(prefs.getDisabledFolders());
                if (checked) now.remove(folder);
                else now.add(folder);
                prefs.setDisabledFolders(now);
                changed = true;
                setResult(RESULT_OK);
            });
            foldersContainer.addView(box);
        }
        FontManager.apply(foldersContainer, FontManager.getJersey25(this));
        ThemeManager.applyTheme(this, foldersContainer);
        ZoomManager.apply(this, foldersContainer);
    }

    // endregion

    // region Themes

    private void setupThemeSection() {
        WdRadioGroup themeGroup = findViewById(R.id.theme_group);
        boolean randomMode = PrefTheme.CATPPUCCIN_MOCHA_RANDOM.equals(prefs.getTheme());
        String currentTheme = ThemeManager.currentPalette(this).id;

        WdRadio randomRadio = new WdRadio(this);
        randomRadio.setId(999);
        randomRadio.setText(getString(R.string.label_theme_random));
        randomRadio.setTextColor(getResources().getColor(R.color.text));
        randomRadio.setChecked(randomMode);
        themeGroup.addView(randomRadio);
        FontManager.apply(randomRadio, FontManager.getJersey25(this));
        randomRadio.setOnClickListener(v -> {
            prefs.setTheme(PrefTheme.CATPPUCCIN_MOCHA_RANDOM);
            ThemeManager.roll(Settings.this);
            ThemeManager.applyTheme(Settings.this, findViewById(R.id.root));
        });

        List<Themes.Palette> palettes = Themes.all();
        for (int i = 0; i < palettes.size(); i++) {
            final Themes.Palette palette = palettes.get(i);
            WdRadio radio = new WdRadio(this);
            radio.setId(1000 + i);
            radio.setText(palette.label);
            radio.setTextColor(getResources().getColor(R.color.text));
            radio.setChecked(!randomMode && palette.id.equals(currentTheme));
            themeGroup.addView(radio);
            FontManager.apply(radio, FontManager.getJersey25(this));
            radio.setOnClickListener(v -> {
                prefs.setTheme(palette.id);
                ThemeManager.applyTheme(Settings.this, findViewById(R.id.root));
            });
        }
    }

    // endregion

    // region List

    private void setupListSection() {
        WdRadioGroup sortGroup = findViewById(R.id.sort_group);
        boolean az = !PrefsManager.SORT_ZA.equals(prefs.getSortStyle());
        WdRadio a = sortGroup.addOption(getString(R.string.label_sort_az));
        WdRadio z = sortGroup.addOption(getString(R.string.label_sort_za));
        a.setChecked(az);
        z.setChecked(!az);
        a.setOnClickListener(v -> setSort(PrefsManager.SORT_AZ));
        z.setOnClickListener(v -> setSort(PrefsManager.SORT_ZA));

        final WdCheckbox together = findViewById(R.id.keep_folder_files_together);
        together.setChecked(prefs.isKeepTogether());
        together.setOnCheckedChangeListener(checked -> {
            prefs.setKeepTogether(checked);
            MusicManager.reSort(Settings.this);
            changed = true;
        });

        final WdCheckbox nomedia = findViewById(R.id.respect_nomedia);
        nomedia.setChecked(prefs.isRespectNomedia());
        nomedia.setOnCheckedChangeListener(checked -> {
            prefs.setRespectNomedia(checked);
            changed = true;
            rescan();
        });

        LinearLayout formats = findViewById(R.id.audio_formats_container);
        for (final String format : prefs.allSupportedAudioFormats()) {
            WdCheckbox box = new WdCheckbox(this);
            box.setText(format.toUpperCase(java.util.Locale.ROOT));
            box.setChecked(prefs.isAudioFormatEnabled(format));
            box.setOnCheckedChangeListener(checked -> {
                prefs.setAudioFormatEnabled(format, checked);
                changed = true;
                rescan();
            });
            formats.addView(box);
        }
        FontManager.apply(formats, FontManager.getJersey25(this));
    }

    private void setSort(String style) {
        prefs.setSortStyle(style);
        MusicManager.reSort(this);
        changed = true;
    }

    // endregion
}
