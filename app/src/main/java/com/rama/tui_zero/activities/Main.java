package com.rama.tui_zero.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.rama.tui_zero.MediaPlaybackService;
import com.rama.tui_zero.R;
import com.rama.tui_zero.helpers.Haptics;
import com.rama.tui_zero.helpers.SystemBars;
import com.rama.tui_zero.managers.FontManager;
import com.rama.tui_zero.managers.MusicManager;
import com.rama.tui_zero.managers.PrefsManager;
import com.rama.tui_zero.managers.ThemeManager;
import com.rama.tui_zero.managers.ZoomManager;
import com.rama.tui_zero.objects.Themes;
import com.rama.tui_zero.objects.Track;
import com.rama.tui_zero.pages.Page;
import com.rama.tui_zero.pages.PlaylistPage;
import com.rama.tui_zero.pages.SettingsPage;
import com.rama.tui_zero.pages.VisualizerPage;
import com.rama.tui_zero.widgets.WdNavbar;

public class Main extends BaseActivity {
    private static final int REQ_MANAGE = 1;
    private static final int REQ_PERMS = 3;
    private static final long TICK_MS = 500;

    private static final String KEY_PAGE = "current_page";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private View root;
    private WdNavbar navbar;
    private final View[] pageViews = new View[3];
    private final Page[] pages = new Page[3];
    private PlaylistPage playlist;
    private SettingsPage settings;
    private int currentPage = WdNavbar.PLAYLIST;
    private View progressBar;
    private View nowPlaying;
    private TextView nowPlayingText;
    private ImageView playPauseIcon;
    private ImageView repeatIcon;
    private boolean askedForStorage;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MusicManager.init(this);
        MusicManager.setOnPlaybackError(track -> runOnUiThread(() ->
                Toast.makeText(Main.this, getString(R.string.toast_playback_unsupported, track.title), Toast.LENGTH_SHORT).show()));
        MediaPlaybackService.start(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMS);
        }

        root = findViewById(R.id.root);
        SystemBars.applyInsets(root);
        progressBar = findViewById(R.id.progress_bg);
        nowPlaying = findViewById(R.id.currently_playing_display);
        nowPlayingText = findViewById(R.id.currently_playing_text);
        playPauseIcon = findViewById(R.id.play_pause_icon);
        repeatIcon = findViewById(R.id.repeat_icon);

        FontManager.apply(root, FontManager.getJersey25(this));

        pageViews[WdNavbar.PLAYLIST] = findViewById(R.id.page_playlist);
        pageViews[WdNavbar.VISUALIZER] = findViewById(R.id.page_visualizer);
        playlist = new PlaylistPage(this, pageViews[WdNavbar.PLAYLIST]);
        pages[WdNavbar.PLAYLIST] = playlist;
        pages[WdNavbar.VISUALIZER] = new VisualizerPage(this, pageViews[WdNavbar.VISUALIZER]);
        pageViews[WdNavbar.SETTINGS] = findViewById(R.id.page_settings);
        settings = new SettingsPage(this, pageViews[WdNavbar.SETTINGS], new SettingsPage.Host() {
            @Override
            public void refreshTheme() {
                Main.this.refreshTheme();
            }

            @Override
            public void onLibrarySettingsChanged() {
                playlist.clearFilter();
                loadOrRequestTracks();
            }
        });
        pages[WdNavbar.SETTINGS] = settings;

        navbar = findViewById(R.id.navbar);
        navbar.setOnNavigateListener(page -> {
            if (page != currentPage) {
                navigateTo(page);
            }
        });

        findViewById(R.id.prev_button).setOnClickListener(Haptics.onClick(v -> MusicManager.prev()));
        findViewById(R.id.next_button).setOnClickListener(Haptics.onClick(v -> MusicManager.next()));
        findViewById(R.id.play_pause_button).setOnClickListener(Haptics.onClick(v -> MusicManager.togglePlayPause()));
        findViewById(R.id.repeat_button).setOnClickListener(Haptics.onClick(v -> {
            MusicManager.setRepeat(!MusicManager.isRepeat());
            refreshUi();
        }));
        findViewById(R.id.shuffle_button).setOnClickListener(Haptics.onClick(v -> {
            playlist.shuffle();
            refreshUi();
        }));
        nowPlaying.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) && v.getWidth() > 0) {
                MusicManager.seekTo(event.getX() / v.getWidth());
                updateProgress();
                return true;
            }
            return action == MotionEvent.ACTION_UP;
        });

        MusicManager.setOnStateChanged(this::refreshUi);
        ThemeManager.rollIfRandom(this);
        currentPage = savedInstanceState != null ? savedInstanceState.getInt(KEY_PAGE, WdNavbar.PLAYLIST) : WdNavbar.PLAYLIST;
        navigateTo(currentPage);
        loadOrRequestTracks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.rollIfRandom(this);
        refreshTheme();
        pages[currentPage].onShow(); // e.g. the visualizer reloads what Settings changed
        refreshUi();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        if (MusicManager.getAllTracks().isEmpty() && MusicManager.hasPermission(this)) {
            loadOrRequestTracks();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        settings.flush();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_PAGE, currentPage);
    }

    @Override
    protected void onDestroy() {
        for (Page page : pages) {
            if (page != null) page.destroy();
        }
        MusicManager.setOnStateChanged(null);
        MusicManager.setOnPlaybackError(null);
        // The service is intentionally left running: playback must survive the activity.
        super.onDestroy();
    }

    private void loadOrRequestTracks() {
        if (MusicManager.hasPermission(this)) {
            playlist.setLoading(true);
            MusicManager.loadTracks(this, success -> {
                playlist.setLoading(false);
                showLibrary();
            });
            return;
        }
        if (askedForStorage) return;
        askedForStorage = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())), REQ_MANAGE);
            } catch (Exception e) {
                try {
                    startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_MANAGE);
                } catch (Exception ignored) {
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMS);
        }
    }

    private void showLibrary() {
        playlist.showLibrary();
        refreshUi();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MANAGE) {
            if (MusicManager.hasPermission(this)) loadOrRequestTracks();
        } else if (requestCode == SettingsPage.REQ_STORAGE) {
            settings.onStorageResult();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SettingsPage.REQ_PERMS) settings.onPermissionsResult();
        if (MusicManager.hasPermission(this)) loadOrRequestTracks();
    }

    private void refreshUi() {
        Track current = MusicManager.getCurrentTrack();
        playlist.refresh(current);
        playPauseIcon.setImageResource(MusicManager.isPlaying() ? R.drawable.px_pause_circle : R.drawable.px_play_circle);
        repeatIcon.setAlpha(MusicManager.isRepeat() ? 1f : 0.4f);
        if (current == null) {
            nowPlayingText.setText(R.string.placeholder_now_playing);
        } else if (current.artists.isEmpty()) {
            nowPlayingText.setText(current.title);
        } else {
            nowPlayingText.setText(current.displayArtists() + " - " + current.title);
        }
        updateProgress();
    }

    private void navigateTo(int page) {
        Page leaving = pages[currentPage];
        if (page != currentPage && leaving != null) {
            leaving.onHide();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
        currentPage = page;
        for (int i = 0; i < pageViews.length; i++) {
            pageViews[i].setVisibility(i == page ? View.VISIBLE : View.GONE);
        }
        pages[page].onShow();
        refreshTheme();
    }

    /** Re-applies zoom, theme and the navbar highlight, then lets the pages that draw by themselves follow. */
    private void refreshTheme() {
        ZoomManager.apply(this, root);
        ThemeManager.applyTheme(this, root);
        Themes.Palette palette = ThemeManager.currentPalette(this);
        navbar.setActive(currentPage, palette);
        for (Page page : pages) {
            page.refreshTheme(palette);
        }
    }

    private void updateProgress() {
        float progress = MusicManager.getProgress();
        ViewGroup.LayoutParams lp = progressBar.getLayoutParams();
        int width = progress < 0 ? 0 : (int) (nowPlaying.getWidth() * progress);
        if (lp.width != width) {
            lp.width = width;
            progressBar.setLayoutParams(lp);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    MusicManager.togglePlayPause();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    MusicManager.next();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    MusicManager.prev();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
