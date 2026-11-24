package com.rama.tui_zero.pages;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rama.tui_zero.R;
import com.rama.tui_zero.activities.About;
import com.rama.tui_zero.helpers.Haptics;
import com.rama.tui_zero.helpers.SystemBars;
import com.rama.tui_zero.managers.FontManager;
import com.rama.tui_zero.managers.MusicManager;
import com.rama.tui_zero.managers.PrefsManager;
import com.rama.tui_zero.managers.ThemeManager;
import com.rama.tui_zero.managers.VisualizerStore;
import com.rama.tui_zero.managers.ZoomManager;
import com.rama.tui_zero.objects.PrefTheme;
import com.rama.tui_zero.objects.Themes;
import com.rama.tui_zero.objects.Visualizer;
import com.rama.tui_zero.widgets.WdCheckbox;
import com.rama.tui_zero.widgets.WdPlot;
import com.rama.tui_zero.widgets.WdRadio;
import com.rama.tui_zero.widgets.WdRadioGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Settings, as a page of the main activity so the navbar and the player stay where they are. It used to be an
 * activity of its own. The main activity forwards what only an activity receives (permission and activity results,
 * pause) and gives the page a Host for what only it can do.
 */
public class SettingsPage implements Page {
    /** What the page needs from the main activity. */
    public interface Host {
        /** Applies the current theme and zoom to the whole screen, and tells every page. */
        void refreshTheme();

        /** The track list is out of date: folders, formats, sorting or .nomedia changed. Called when the page is left. */
        void onLibrarySettingsChanged();
    }

    public static final int REQ_PERMS = 10;
    public static final int REQ_STORAGE = 11;

    private interface TextSink {
        void set(String text);
    }

    private interface ItemState {
        void setEnabled(boolean enabled);

        void setCollapsed(boolean collapsed);
    }

    private final Activity activity;
    private final View root;
    private final Host host;
    private final PrefsManager prefs;
    private final LinearLayout foldersContainer;

    private boolean changed;

    private Visualizer visualizer;
    private WdPlot canvas;
    private TextView visualizerStatus;
    private LinearLayout countersContainer;
    private LinearLayout formulasContainer;
    private final List<TextView> formulaTitles = new ArrayList<TextView>();
    private String lastError;
    private int lastPoints;

    public SettingsPage(Activity activity, View root, Host host) {
        this.activity = activity;
        this.root = root;
        this.host = host;
        prefs = PrefsManager.getInstance(activity);
        foldersContainer = root.findViewById(R.id.folders_container);
        setupSystemSection();
        setupZoomSection();
        renderFolders();
        setupThemeSection();
        setupAccentSection();
        updateAccentVisibility();
        setupListSection();
        setupVisualizerSection();
        root.findViewById(R.id.go_about).setOnClickListener(v -> activity.startActivity(new Intent(activity, About.class)));
    }

    // region Page

    @Override
    public void onShow() {
        refreshPermissionButtonStates(); // also covers coming back from the system's permission screens
    }

    @Override
    public void onHide() {
        saveZoomFromField();
        if (changed) {
            changed = false;
            host.onLibrarySettingsChanged();
        }
    }

    /** The app is going to the background while this page may be showing. */
    public void flush() {
        saveZoomFromField();
    }

    @Override
    public void refreshTheme(Themes.Palette palette) {
        canvas.setPalette(palette);
        colorFormulaTitles(); // after the theme, which turns every accent colored text into the current accent
        refreshVisualizerStatus();
    }

    @Override
    public void destroy() {
    }

    /** Forwarded by the main activity. */
    public void onPermissionsResult() {
        refreshPermissionButtonStates();
        if (MusicManager.hasPermission(activity)) rescan();
    }

    /** Forwarded by the main activity. */
    public void onStorageResult() {
        if (MusicManager.hasPermission(activity)) rescan();
    }

    // endregion

    // region System

    private void setupSystemSection() {
        root.findViewById(R.id.media_permission_button).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQ_PERMS);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMS);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.findViewById(R.id.storage_permission_button).setOnClickListener(v -> {
                try {
                    activity.startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + activity.getPackageName())), REQ_STORAGE);
                } catch (Exception e) {
                    openSettingsFallback();
                }
            });
        } else {
            root.findViewById(R.id.storage_permission_button).setVisibility(View.GONE);
            root.findViewById(R.id.storage_separator).setVisibility(View.GONE);
        }

        root.findViewById(R.id.notification_permission_button).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMS);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    activity.startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
                } catch (Exception e) {
                    openSettingsFallback();
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            root.findViewById(R.id.background_playback_button).setOnClickListener(v -> {
                try {
                    activity.startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception e) {
                    openSettingsFallback();
                }
            });
        } else {
            root.findViewById(R.id.background_playback_button).setVisibility(View.GONE);
            root.findViewById(R.id.background_separator).setVisibility(View.GONE);
        }

        final WdCheckbox systemBar = root.findViewById(R.id.show_system_bar);
        systemBar.setChecked(prefs.isSystemBarVisible());
        systemBar.setOnCheckedChangeListener(checked -> {
            prefs.setSystemBarVisible(checked);
            SystemBars.setVisible(activity, checked);
        });

        final WdCheckbox keepAwake = root.findViewById(R.id.keep_screen_awake);
        keepAwake.setChecked(prefs.isKeepScreenAwake());
        keepAwake.setOnCheckedChangeListener(checked -> {
            prefs.setKeepScreenAwake(checked);
            if (checked) activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    private void openSettingsFallback() {
        try {
            activity.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (Exception e) {
            Toast.makeText(activity, R.string.toast_unable_open_settings, Toast.LENGTH_SHORT).show();
        }
    }

    private void setButtonState(int id, boolean done) {
        Button button = root.findViewById(id);
        button.setEnabled(!done);
        button.setAlpha(done ? 0.4f : 1f);
    }

    private void refreshPermissionButtonStates() {
        boolean media;
        if (Build.VERSION.SDK_INT >= 33) {
            media = activity.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            media = activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            media = true;
        }
        setButtonState(R.id.media_permission_button, media);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setButtonState(R.id.storage_permission_button, android.os.Environment.isExternalStorageManager());
        }
        boolean notifications = Build.VERSION.SDK_INT < 33
                || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        setButtonState(R.id.notification_permission_button, notifications);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            setButtonState(R.id.background_playback_button, pm != null && pm.isIgnoringBatteryOptimizations(activity.getPackageName()));
        }
        // Rendering the folder list needs a finished scan, which needs permission.
        if (prefs.getAllFolders().isEmpty() && MusicManager.hasPermission(activity)) {
            rescan();
        }
    }

    // endregion

    // region Zoom

    private void setupZoomSection() {
        final EditText zoom = root.findViewById(R.id.zoom);
        zoom.setText(String.valueOf(ZoomManager.getPercent(activity)));
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
        host.refreshTheme(); // zooms the whole screen, and the plot labels with it
    }

    private void saveZoomFromField() {
        EditText zoom = root.findViewById(R.id.zoom);
        int percent;
        try {
            percent = ZoomManager.clamp(Integer.parseInt(zoom.getText().toString().trim()));
        } catch (NumberFormatException e) {
            percent = ZoomManager.getPercent(activity);
        }
        String normalized = String.valueOf(percent);
        if (!normalized.equals(zoom.getText().toString())) zoom.setText(normalized);
        prefs.setZoomPercent(percent);
    }

    // endregion

    // region Folders

    private void rescan() {
        MusicManager.loadTracks(activity, success -> {
            if (!activity.isFinishing()) renderFolders();
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
        View empty = root.findViewById(R.id.folders_empty);
        empty.setVisibility(folders.isEmpty() ? View.VISIBLE : View.GONE);
        final Set<String> disabled = new HashSet<>(prefs.getDisabledFolders());
        for (final String folder : folders) {
            WdCheckbox box = new WdCheckbox(activity);
            box.setText(shortLabel(folder));
            box.setChecked(!disabled.contains(folder));
            box.setOnCheckedChangeListener(checked -> {
                Set<String> now = new HashSet<>(prefs.getDisabledFolders());
                if (checked) now.remove(folder);
                else now.add(folder);
                prefs.setDisabledFolders(now);
                changed = true;
            });
            foldersContainer.addView(box);
        }
        FontManager.apply(foldersContainer, FontManager.getJersey25(activity));
        ThemeManager.applyTheme(activity, foldersContainer);
        ZoomManager.apply(activity, foldersContainer);
    }

    // endregion

    // region Themes

    private void setupThemeSection() {
        WdRadioGroup group = root.findViewById(R.id.theme_group);
        String current = prefs.getFlavor();
        for (final Themes.Flavor flavor : Themes.flavors()) {
            WdRadio radio = group.addOption(flavor.label);
            radio.setChecked(flavor.id.equals(current));
            radio.setOnClickListener(v -> {
                prefs.setFlavor(flavor.id);
                updateAccentVisibility();
                host.refreshTheme();
            });
        }
        FontManager.apply(group, FontManager.getJersey25(activity));
    }

    /** Mono themes have no accent: the section is hidden while one is selected, and the choice is kept for later. */
    private void updateAccentVisibility() {
        int visibility = Themes.hasAccents(prefs.getFlavor()) ? View.VISIBLE : View.GONE;
        root.findViewById(R.id.accent_section).setVisibility(visibility);
        root.findViewById(R.id.accent_separator).setVisibility(visibility);
    }

    private void setupAccentSection() {
        WdRadioGroup group = root.findViewById(R.id.accent_group);
        String current = prefs.getAccent();
        boolean random = PrefTheme.ACCENT_RANDOM.equals(current);

        WdRadio randomRadio = group.addOption(activity.getString(R.string.label_accent_random));
        randomRadio.setChecked(random);
        randomRadio.setOnClickListener(v -> {
            prefs.setAccent(PrefTheme.ACCENT_RANDOM);
            ThemeManager.roll(activity);
            host.refreshTheme();
        });
        for (final Themes.Accent accent : Themes.accents()) {
            WdRadio radio = group.addOption(accent.label);
            radio.setChecked(!random && accent.id.equals(current));
            radio.setOnClickListener(v -> {
                prefs.setAccent(accent.id);
                host.refreshTheme();
            });
        }
        FontManager.apply(group, FontManager.getJersey25(activity));
    }

    // endregion

    // region Visualizer

    private void setupVisualizerSection() {
        visualizer = VisualizerStore.load(activity);
        canvas = root.findViewById(R.id.canvas_graph);
        visualizerStatus = root.findViewById(R.id.visualizer_status);
        countersContainer = root.findViewById(R.id.counters_container);
        formulasContainer = root.findViewById(R.id.formulas_container);

        // One delay for every counter, outside of them: they all step on the same clock.
        EditText delay = root.findViewById(R.id.visualizer_delay);
        delay.setText(visualizer.delay);
        delay.addTextChangedListener(watcher(text -> visualizer.delay = text));

        root.findViewById(R.id.add_counter).setOnClickListener(v -> {
            visualizer.counters.add(new Visualizer.Counter(visualizer.nextAlias(), "1", "10"));
            renderVisualizerFields();
            onVisualizerChanged();
        });
        root.findViewById(R.id.add_formula).setOnClickListener(v -> {
            visualizer.formulas.add(new Visualizer.FormulaDef("", "", visualizer.pickColor(new Random())));
            renderVisualizerFields();
            onVisualizerChanged();
        });
        renderVisualizerFields();
        onVisualizerChanged();
    }

    /** One block per counter and per formula. Rebuilt when one is added or removed. */
    private void renderVisualizerFields() {
        LayoutInflater inflater = LayoutInflater.from(activity);
        countersContainer.removeAllViews();
        for (int i = 0; i < visualizer.counters.size(); i++) {
            final Visualizer.Counter counter = visualizer.counters.get(i);
            View block = inflater.inflate(R.layout.list_item_counter, countersContainer, false);
            ((TextView) block.findViewById(R.id.counter_name)).setText(activity.getString(R.string.label_counter_title, i + 1));
            bind(block, R.id.counter_alias, counter.alias, text -> counter.alias = text);
            bind(block, R.id.counter_from, counter.from, text -> counter.from = text);
            bind(block, R.id.counter_to, counter.to, text -> counter.to = text);
            bindCounterOptions(block, counter);
            bindChrome(block, R.id.counter_enabled, R.id.counter_header, R.id.counter_indicator, R.id.counter_body,
                    R.id.counter_delete, counter.enabled, counter.collapsed, new ItemState() {
                        public void setEnabled(boolean enabled) {
                            counter.enabled = enabled;
                        }

                        public void setCollapsed(boolean collapsed) {
                            counter.collapsed = collapsed;
                        }
                    }, () -> {
                        visualizer.counters.remove(counter);
                        renderVisualizerFields();
                        onVisualizerChanged();
                    });
            countersContainer.addView(block);
        }
        formulasContainer.removeAllViews();
        formulaTitles.clear();
        for (int i = 0; i < visualizer.formulas.size(); i++) {
            final Visualizer.FormulaDef formula = visualizer.formulas.get(i);
            View block = inflater.inflate(R.layout.list_item_formula, formulasContainer, false);
            TextView title = block.findViewById(R.id.formula_name);
            title.setText(activity.getString(R.string.label_formula_title, i + 1));
            formulaTitles.add(title);
            bind(block, R.id.formula_x, formula.x, text -> formula.x = text);
            bind(block, R.id.formula_y, formula.y, text -> formula.y = text);
            bindChrome(block, R.id.formula_enabled, R.id.formula_header, R.id.formula_indicator, R.id.formula_body,
                    R.id.formula_delete, formula.enabled, formula.collapsed, new ItemState() {
                        public void setEnabled(boolean enabled) {
                            formula.enabled = enabled;
                        }

                        public void setCollapsed(boolean collapsed) {
                            formula.collapsed = collapsed;
                        }
                    }, () -> {
                        visualizer.formulas.remove(formula);
                        renderVisualizerFields();
                        onVisualizerChanged();
                    });
            formulasContainer.addView(block);
        }
        FontManager.apply(countersContainer, FontManager.getJersey25(activity));
        FontManager.apply(formulasContainer, FontManager.getJersey25(activity));
        ThemeManager.applyTheme(activity, countersContainer);
        ThemeManager.applyTheme(activity, formulasContainer);
        ZoomManager.apply(activity, countersContainer);
        ZoomManager.apply(activity, formulasContainer);
        colorFormulaTitles(); // after the theme, which would turn every accent colored text into the current accent
    }

    /**
     * What every counter and formula block shares: a checkbox that turns it on and off, a title that folds the block
     * into itself, and a delete button.
     */
    private void bindChrome(final View block, int enabledId, int headerId, int indicatorId, int bodyId, int deleteId,
                            boolean enabled, boolean collapsed, final ItemState state, final Runnable onDelete) {
        WdCheckbox box = block.findViewById(enabledId);
        box.setChecked(enabled);
        block.setAlpha(enabled ? 1f : 0.5f);
        box.setOnCheckedChangeListener(checked -> {
            state.setEnabled(checked);
            block.setAlpha(checked ? 1f : 0.5f);
            onVisualizerChanged();
        });

        final View body = block.findViewById(bodyId);
        final TextView indicator = block.findViewById(indicatorId);
        showCollapsed(body, indicator, collapsed);
        block.findViewById(headerId).setOnClickListener(Haptics.onClick(v -> {
            boolean next = body.getVisibility() == View.VISIBLE;
            showCollapsed(body, indicator, next);
            state.setCollapsed(next);
            VisualizerStore.save(activity, visualizer); // only the fold changed: nothing to redraw
        }));

        block.findViewById(deleteId).setOnClickListener(v -> onDelete.run());
    }

    private void showCollapsed(View body, TextView indicator, boolean collapsed) {
        body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        indicator.setText(collapsed ? R.string.label_expand_indicator : R.string.label_collapse_indicator);
    }

    /** Each formula title wears the color of its line. */
    private void colorFormulaTitles() {
        Themes.Palette palette = ThemeManager.currentPalette(activity);
        for (int i = 0; i < formulaTitles.size() && i < visualizer.formulas.size(); i++) {
            formulaTitles.get(i).setTextColor(Themes.lineColor(palette, visualizer.formulas.get(i).color, i));
        }
    }

    /** Infinite loops the counter; the two radios (how it loops) only matter, and only show, when it is on. */
    private void bindCounterOptions(View block, final Visualizer.Counter counter) {
        WdCheckbox infinite = block.findViewById(R.id.counter_infinite);
        final WdRadioGroup mode = block.findViewById(R.id.counter_mode);

        WdRadio alternate = mode.addOption(activity.getString(R.string.label_counter_alternate));
        WdRadio linear = mode.addOption(activity.getString(R.string.label_counter_linear));
        alternate.setChecked(counter.alternate);
        linear.setChecked(!counter.alternate);
        alternate.setOnClickListener(v -> {
            counter.alternate = true;
            onVisualizerChanged();
        });
        linear.setOnClickListener(v -> {
            counter.alternate = false;
            onVisualizerChanged();
        });

        infinite.setChecked(counter.infinite);
        mode.setVisibility(counter.infinite ? View.VISIBLE : View.GONE);
        infinite.setOnCheckedChangeListener(checked -> {
            counter.infinite = checked;
            mode.setVisibility(checked ? View.VISIBLE : View.GONE);
            onVisualizerChanged();
        });
    }

    private void bind(View block, int id, String value, TextSink sink) {
        EditText field = block.findViewById(id);
        field.setText(value);
        field.addTextChangedListener(watcher(sink));
    }

    private TextWatcher watcher(final TextSink sink) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                sink.set(s.toString());
                onVisualizerChanged();
            }
        };
    }

    /**
     * Saves and redraws on every keystroke. Half-typed input is normal, so on a problem the last good drawing stays
     * and the status line says what is wrong.
     */
    private void onVisualizerChanged() {
        VisualizerStore.save(activity, visualizer);
        Visualizer.Result result = visualizer.build();
        lastError = result.error;
        lastPoints = result.points;
        if (result.error == null) canvas.setScene(result); // restarts the animation
        refreshVisualizerStatus();
    }

    private void refreshVisualizerStatus() {
        Themes.Palette palette = ThemeManager.currentPalette(activity);
        if (lastError != null) {
            visualizerStatus.setText(lastError);
            visualizerStatus.setTextColor(palette.error);
        } else {
            visualizerStatus.setText(lastPoints == 0
                    ? activity.getString(R.string.status_visualizer_empty)
                    : activity.getString(R.string.status_visualizer_points, lastPoints));
            visualizerStatus.setTextColor(palette.text);
        }
    }

    // endregion

    // region List

    private void setupListSection() {
        WdRadioGroup sortGroup = root.findViewById(R.id.sort_group);
        boolean az = !PrefsManager.SORT_ZA.equals(prefs.getSortStyle());
        WdRadio a = sortGroup.addOption(activity.getString(R.string.label_sort_az));
        WdRadio z = sortGroup.addOption(activity.getString(R.string.label_sort_za));
        a.setChecked(az);
        z.setChecked(!az);
        a.setOnClickListener(v -> setSort(PrefsManager.SORT_AZ));
        z.setOnClickListener(v -> setSort(PrefsManager.SORT_ZA));

        final WdCheckbox together = root.findViewById(R.id.keep_folder_files_together);
        together.setChecked(prefs.isKeepTogether());
        together.setOnCheckedChangeListener(checked -> {
            prefs.setKeepTogether(checked);
            MusicManager.reSort(activity);
            changed = true;
        });

        final WdCheckbox nomedia = root.findViewById(R.id.respect_nomedia);
        nomedia.setChecked(prefs.isRespectNomedia());
        nomedia.setOnCheckedChangeListener(checked -> {
            prefs.setRespectNomedia(checked);
            changed = true;
            rescan();
        });

        LinearLayout formats = root.findViewById(R.id.audio_formats_container);
        for (final String format : prefs.allSupportedAudioFormats()) {
            WdCheckbox box = new WdCheckbox(activity);
            box.setText(format.toUpperCase(java.util.Locale.ROOT));
            box.setChecked(prefs.isAudioFormatEnabled(format));
            box.setOnCheckedChangeListener(checked -> {
                prefs.setAudioFormatEnabled(format, checked);
                changed = true;
                rescan();
            });
            formats.addView(box);
        }
        FontManager.apply(formats, FontManager.getJersey25(activity));
    }

    private void setSort(String style) {
        prefs.setSortStyle(style);
        MusicManager.reSort(activity);
        changed = true;
    }

    // endregion
}
