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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.rama.tui_zero.MediaPlaybackService;
import com.rama.tui_zero.R;
import com.rama.tui_zero.adapters.TrackAdapter;
import com.rama.tui_zero.helpers.Haptics;
import com.rama.tui_zero.helpers.SystemBars;
import com.rama.tui_zero.managers.FontManager;
import com.rama.tui_zero.managers.MusicManager;
import com.rama.tui_zero.managers.PrefsManager;
import com.rama.tui_zero.managers.ThemeManager;
import com.rama.tui_zero.managers.ZoomManager;
import com.rama.tui_zero.objects.Track;

public class Main extends BaseActivity {
    private static final int REQ_MANAGE = 1;
    private static final int REQ_SETTINGS = 2;
    private static final int REQ_PERMS = 3;
    private static final long TICK_MS = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TrackAdapter adapter;
    private ListView list;
    private EditText filter;
    private View clearButton;
    private View progressBar;
    private View nowPlaying;
    private TextView nowPlayingText;
    private TextView loadingText;
    private ImageView playPauseIcon;
    private ImageView repeatIcon;
    private Track lastScrolledTo;
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

        View root = findViewById(R.id.root);
        SystemBars.applyInsets(root);
        list = findViewById(R.id.track_list);
        filter = findViewById(R.id.filter_input);
        clearButton = findViewById(R.id.clear_button);
        progressBar = findViewById(R.id.progress_bg);
        nowPlaying = findViewById(R.id.currently_playing_display);
        nowPlayingText = findViewById(R.id.currently_playing_text);
        loadingText = findViewById(R.id.loading_text);
        playPauseIcon = findViewById(R.id.play_pause_icon);
        repeatIcon = findViewById(R.id.repeat_icon);

        FontManager.apply(root, FontManager.getJersey25(this));
        adapter = new TrackAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Haptics.tap(view);
            MusicManager.setTracks(adapter.getDisplayed(), position);
        });

        filter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                adapter.filter(s.toString());
                if (s.length() == 0) MusicManager.restoreTracks(MusicManager.getAllTracks());
            }

            public void afterTextChanged(Editable s) {
            }
        });
        clearButton.setOnClickListener(Haptics.onClick(v -> filter.setText("")));

        findViewById(R.id.open_settings).setOnClickListener(Haptics.onClick(v ->
                startActivityForResult(new Intent(Main.this, Settings.class), REQ_SETTINGS)));
        findViewById(R.id.prev_button).setOnClickListener(Haptics.onClick(v -> MusicManager.prev()));
        findViewById(R.id.next_button).setOnClickListener(Haptics.onClick(v -> MusicManager.next()));
        findViewById(R.id.play_pause_button).setOnClickListener(Haptics.onClick(v -> MusicManager.togglePlayPause()));
        findViewById(R.id.repeat_button).setOnClickListener(Haptics.onClick(v -> {
            MusicManager.setRepeat(!MusicManager.isRepeat());
            refreshUi();
        }));
        findViewById(R.id.shuffle_button).setOnClickListener(Haptics.onClick(v -> {
            MusicManager.restoreTracks(adapter.getDisplayed());
            MusicManager.shuffleTracks(PrefsManager.getInstance(Main.this).isKeepTogether());
            adapter.showTracks(MusicManager.getTracks());
            lastScrolledTo = null;
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
        ThemeManager.applyTheme(this, root);
        loadOrRequestTracks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        View root = findViewById(R.id.root);
        ThemeManager.rollIfRandom(this);
        ThemeManager.applyTheme(this, root);
        adapter.notifyDataSetChanged();
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
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        MusicManager.setOnStateChanged(null);
        MusicManager.setOnPlaybackError(null);
        // The service is intentionally left running: playback must survive the activity.
        super.onDestroy();
    }

    private void loadOrRequestTracks() {
        if (MusicManager.hasPermission(this)) {
            loadingText.setVisibility(View.VISIBLE);
            MusicManager.loadTracks(this, success -> {
                loadingText.setVisibility(View.GONE);
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
        adapter.setLibrary(MusicManager.getAllTracks());
        adapter.filter(filter.getText().toString());
        refreshUi();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MANAGE) {
            if (MusicManager.hasPermission(this)) loadOrRequestTracks();
        } else if (requestCode == REQ_SETTINGS) {
            if (resultCode == RESULT_OK) {
                filter.setText("");
                loadOrRequestTracks();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (MusicManager.hasPermission(this)) loadOrRequestTracks();
    }

    private void refreshUi() {
        Track current = MusicManager.getCurrentTrack();
        adapter.notifyDataSetChanged();
        playPauseIcon.setImageResource(MusicManager.isPlaying() ? R.drawable.px_pause_circle : R.drawable.px_play_circle);
        repeatIcon.setAlpha(MusicManager.isRepeat() ? 1f : 0.4f);
        if (current == null) {
            nowPlayingText.setText(R.string.placeholder_now_playing);
        } else if (current.artists.isEmpty()) {
            nowPlayingText.setText(current.title);
        } else {
            nowPlayingText.setText(current.displayArtists() + " - " + current.title);
        }
        // Only follow the playing track when it changes, so scrolling the list by hand is not undone.
        if (current != null && !current.equals(lastScrolledTo)) {
            lastScrolledTo = current;
            int pos = adapter.positionOf(current);
            if (pos >= 0) list.smoothScrollToPosition(pos);
        }
        updateProgress();
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
