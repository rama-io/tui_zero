package com.rama.tui_zero.managers;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.provider.MediaStore;

import com.rama.tui_zero.MediaButtonReceiver;
import com.rama.tui_zero.helpers.TrackCache;
import com.rama.tui_zero.objects.Track;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Library scan + playback queue + the MediaPlayer. Everything except the scan itself runs on the
 * main thread. It lives as long as the process, which MediaPlaybackService keeps alive.
 */
public final class MusicManager {
    public interface Callback {
        void onComplete(boolean success);
    }

    public interface ErrorListener {
        void onPlaybackError(Track track);
    }

    // The wake lock is only a backstop: it is refreshed on every track start/resume and released
    // on pause/stop, so it can never outlive the current track by more than this.
    private static final long WAKE_LOCK_MARGIN_MS = 5 * 60 * 1000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService SCANNER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tui-scan");
        t.setDaemon(true);
        return t;
    });

    private static Context appContext;
    private static MediaPlayer player;
    private static MediaSession mediaSession;
    private static PowerManager.WakeLock wakeLock;
    private static AudioFocusRequest focusRequest;

    private static List<Track> tracks = Collections.emptyList();
    private static List<Track> allTracks = Collections.emptyList();
    private static int currentIndex = -1;
    private static boolean playing;
    private static boolean repeat;
    private static boolean pausedByFocusLoss;

    private static Runnable onStateChanged;
    private static Runnable onNotificationChanged;
    private static ErrorListener onPlaybackError;

    // loadTracks() bookkeeping (main thread only)
    private static final List<Callback> waiting = new ArrayList<>();
    private static boolean scanning;
    private static boolean rescan;

    private MusicManager() {
    }

    // region Listeners

    public static void setOnStateChanged(Runnable listener) {
        onStateChanged = listener;
    }

    public static void setOnNotificationChanged(Runnable listener) {
        onNotificationChanged = listener;
    }

    public static void setOnPlaybackError(ErrorListener listener) {
        onPlaybackError = listener;
    }

    private static void notifyChanged() {
        updatePlaybackState();
        if (onStateChanged != null) onStateChanged.run();
        if (onNotificationChanged != null) onNotificationChanged.run();
    }

    // endregion

    // region State

    public static List<Track> getTracks() {
        return tracks;
    }

    public static List<Track> getAllTracks() {
        return allTracks;
    }

    public static Track getCurrentTrack() {
        return currentIndex >= 0 && currentIndex < tracks.size() ? tracks.get(currentIndex) : null;
    }

    public static boolean isPlaying() {
        return playing;
    }

    public static boolean isRepeat() {
        return repeat;
    }

    public static void setRepeat(boolean value) {
        repeat = value;
    }

    // endregion

    // region Media session

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        if (mediaSession == null) initMediaSession();
    }

    private static void initMediaSession() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent buttons = PendingIntent.getBroadcast(appContext, 0, new Intent(appContext, MediaButtonReceiver.class), flags);

        mediaSession = new MediaSession(appContext, "TuiMediaSession");
        mediaSession.setMediaButtonReceiver(buttons);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (!playing) togglePlayPause();
            }

            @Override
            public void onPause() {
                if (playing) togglePlayPause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                prev();
            }
        });
        mediaSession.setActive(true);
        updatePlaybackState();
    }

    public static MediaSession.Token getSessionToken() {
        return mediaSession == null ? null : mediaSession.getSessionToken();
    }

    private static void updateMetadata() {
        if (mediaSession == null) return;
        Track track = getCurrentTrack();
        long duration = 0L;
        try {
            if (player != null) duration = Math.max(0, player.getDuration());
        } catch (IllegalStateException ignored) {
        }
        mediaSession.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track == null ? "" : track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track == null ? "" : track.displayArtists())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track == null ? "" : new File(track.folder()).getName())
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, track == null ? "" : track.title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                .build());
    }

    private static void updatePlaybackState() {
        if (mediaSession == null) return;
        long position = 0L;
        try {
            if (player != null) position = player.getCurrentPosition();
        } catch (IllegalStateException ignored) {
        }
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, playing ? 1f : 0f)
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .build());
    }

    // endregion

    // region Keep-alive

    private static void setPlaying(boolean value) {
        playing = value;
        if (value) acquireWakeLock();
        else releaseWakeLock();
    }

    private static void acquireWakeLock() {
        if (appContext == null) return;
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TuiZero:playback");
                wakeLock.setReferenceCounted(false);
            }
            long duration = 0L;
            try {
                if (player != null) duration = Math.max(0, player.getDuration());
            } catch (IllegalStateException ignored) {
            }
            wakeLock.acquire(duration + WAKE_LOCK_MARGIN_MS);
        } catch (RuntimeException ignored) {
            // No WAKE_LOCK permission or no PowerManager: playback itself still works.
        }
    }

    private static void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (RuntimeException ignored) {
        }
    }

    // endregion

    // region Audio focus

    private static final AudioManager.OnAudioFocusChangeListener FOCUS_LISTENER = focus -> {
        if (focus == AudioManager.AUDIOFOCUS_LOSS) {
            pausedByFocusLoss = false;
            pause();
        } else if (focus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (playing) {
                pause();
                pausedByFocusLoss = true;
            }
        } else if (focus == AudioManager.AUDIOFOCUS_GAIN) {
            if (pausedByFocusLoss) {
                pausedByFocusLoss = false;
                if (!playing && player != null) resume();
            }
        }
    };

    private static AudioAttributes audioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
    }

    @SuppressWarnings("deprecation")
    private static void requestAudioFocus() {
        if (appContext == null) return;
        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(audioAttributes())
                        .setOnAudioFocusChangeListener(FOCUS_LISTENER)
                        .build();
            }
            am.requestAudioFocus(focusRequest);
        } else {
            am.requestAudioFocus(FOCUS_LISTENER, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    @SuppressWarnings("deprecation")
    private static void abandonAudioFocus() {
        if (appContext == null) return;
        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) am.abandonAudioFocusRequest(focusRequest);
        } else {
            am.abandonAudioFocus(FOCUS_LISTENER);
        }
    }

    // endregion

    // region Permissions

    public static boolean hasPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        // Android 11+: MANAGE_EXTERNAL_STORAGE is what lets us list arbitrary folders and the SD card.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    // endregion

    // region Library

    /**
     * Rescans storage. Calls made while a scan is running are folded into one follow-up scan, so
     * toggling several settings in a row does not walk the disk several times.
     */
    public static void loadTracks(Context context, Callback callback) {
        if (!hasPermission(context)) {
            if (callback != null) callback.onComplete(false);
            return;
        }
        appContext = context.getApplicationContext();
        if (callback != null) waiting.add(callback);
        if (scanning) {
            rescan = true;
            return;
        }
        startScan();
    }

    private static void startScan() {
        scanning = true;
        rescan = false;
        final List<Callback> batch = new ArrayList<>(waiting);
        waiting.clear();
        final Context ctx = appContext;
        SCANNER.execute(() -> {
            List<Track> result = null;
            try {
                result = scan(ctx);
            } catch (RuntimeException e) {
                // Leave the previous library in place.
            }
            final List<Track> scanned = result;
            MAIN.post(() -> {
                if (scanned != null) applyLibrary(scanned);
                scanning = false;
                for (Callback cb : batch) cb.onComplete(scanned != null);
                if (rescan || !waiting.isEmpty()) startScan();
            });
        });
    }

    private static void applyLibrary(List<Track> sorted) {
        Track playingTrack = getCurrentTrack();
        allTracks = sorted;
        tracks = sorted;
        int index = playingTrack == null ? -1 : tracks.indexOf(playingTrack);
        if (index >= 0) currentIndex = index;
        else if (currentIndex >= tracks.size()) currentIndex = tracks.isEmpty() ? -1 : 0;
        if (!tracks.isEmpty() && currentIndex < 0) currentIndex = 0;
    }

    private static List<Track> scan(Context ctx) {
        PrefsManager prefs = PrefsManager.getInstance(ctx);
        String sortStyle = prefs.getSortStyle();
        boolean keepTogether = prefs.isKeepTogether();
        Set<String> formats = prefs.getEnabledAudioFormats();

        // Android 10: scoped storage blocks File.listFiles() even with READ_EXTERNAL_STORAGE.
        List<Track> raw = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                ? scanViaMediaStore(ctx, formats)
                : syncTracks(ctx, getStorageRoots(ctx), formats, prefs.isRespectNomedia());

        // Remember every folder before filtering so Settings can still list the disabled ones.
        Set<String> folders = new TreeSet<>();
        for (Track t : raw) {
            if (t.file.getParent() != null) folders.add(t.file.getParent());
        }
        prefs.setAllFolders(folders);

        Set<String> disabled = prefs.getDisabledFolders();
        List<Track> visible = new ArrayList<>(raw.size());
        for (Track t : raw) {
            if (!disabled.contains(t.file.getParent())) visible.add(t);
        }
        return sortTracks(visible, sortStyle, keepTogether);
    }

    public static void reSort(Context context) {
        PrefsManager prefs = PrefsManager.getInstance(context);
        Track playingTrack = getCurrentTrack();
        allTracks = sortTracks(allTracks, prefs.getSortStyle(), prefs.isKeepTogether());
        tracks = allTracks;
        int index = playingTrack == null ? -1 : tracks.indexOf(playingTrack);
        currentIndex = Math.max(index, tracks.isEmpty() ? -1 : 0);
        if (onStateChanged != null) onStateChanged.run();
    }

    /** Sort by title (a-z / z-a), optionally keeping files of the same folder together. */
    public static List<Track> sortTracks(List<Track> source, String sortStyle, boolean keepTogether) {
        final boolean descending = PrefsManager.SORT_ZA.equals(sortStyle);
        final Comparator<String> text = (a, b) -> {
            int c = a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
            return descending ? -c : c;
        };
        Comparator<Track> byTitle = (a, b) -> text.compare(a.title, b.title);

        List<Track> result = new ArrayList<>(source.size());
        if (keepTogether) {
            Map<String, List<Track>> byFolder = new HashMap<>();
            for (Track t : source) {
                List<Track> group = byFolder.get(t.folder());
                if (group == null) {
                    group = new ArrayList<>();
                    byFolder.put(t.folder(), group);
                }
                group.add(t);
            }
            List<String> folders = new ArrayList<>(byFolder.keySet());
            Collections.sort(folders, text);
            for (String folder : folders) {
                List<Track> group = byFolder.get(folder);
                Collections.sort(group, byTitle);
                result.addAll(group);
            }
        } else {
            result.addAll(source);
            Collections.sort(result, byTitle);
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private static List<Track> scanViaMediaStore(Context context, Set<String> allowedFormats) {
        List<Track> results = new ArrayList<>();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
        };
        Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE + " ASC");
        if (cursor == null) return results;
        try {
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            while (cursor.moveToNext()) {
                String path = cursor.getString(dataCol);
                if (path == null) continue;
                File file = new File(path);
                if (!allowedFormats.contains(Track.extensionOf(file).toLowerCase(Locale.ROOT))) continue;
                long duration = cursor.getLong(durationCol);
                if (duration <= 0) continue;
                android.net.Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol));

                Track base = Track.fromFile(file);
                String msTitle = cursor.getString(titleCol);
                String msArtist = cursor.getString(artistCol);
                if (msTitle != null && msTitle.trim().length() == 0) msTitle = null;
                if (msArtist != null && (msArtist.trim().length() == 0 || msArtist.equals("<unknown>"))) msArtist = null;
                List<String> artists = msArtist != null ? Collections.singletonList(msArtist) : base.artists;
                results.add(base.withMediaStore(msTitle != null ? msTitle : base.title, artists, uri, duration));
            }
        } finally {
            cursor.close();
        }
        return results;
    }

    private static List<File> getStorageRoots(Context context) {
        List<File> roots = new ArrayList<>();

        File internal = Environment.getExternalStorageDirectory();
        if (internal.exists()) roots.add(internal);

        // SD cards and other volumes: walk up from the app-specific dir
        // (/storage/<id>/Android/data/<pkg>/files, 4 levels up = volume root).
        File[] appDirs = context.getExternalFilesDirs(null);
        if (appDirs != null) {
            for (File dir : appDirs) {
                if (dir == null) continue;
                File root = dir;
                for (int i = 0; i < 4 && root != null; i++) root = root.getParentFile();
                if (root != null && root.exists() && !roots.contains(root)) roots.add(root);
            }
        }

        List<File> usable = new ArrayList<>();
        for (File root : roots) {
            if (root.exists() && root.isDirectory()) usable.add(root);
        }
        return usable;
    }

    /** Returns the duration in ms, or -1 when the file has no audio this device can decode. */
    private static long probeDuration(File file) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            long durationUs = -1;
            boolean decodable = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) continue;
                try {
                    durationUs = format.getLong(MediaFormat.KEY_DURATION);
                } catch (Exception e) {
                    // Not every format carries a duration.
                }
                if (new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null) {
                    decodable = true;
                }
            }
            if (!decodable || durationUs <= 0) return -1;
            long ms = durationUs / 1000;
            return ms > 0 ? ms : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            extractor.release();
        }
    }

    private static List<Track> syncTracks(Context context, List<File> roots, Set<String> allowedFormats, boolean respectNomedia) {
        TrackCache cache = new TrackCache(context);
        Map<String, TrackCache.Entry> cached = cache.load();
        Map<String, TrackCache.Entry> current = new HashMap<>();
        List<Track> result = new ArrayList<>();
        boolean dirty = false;

        ArrayDeque<File> stack = new ArrayDeque<>(roots);
        Set<String> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            File dir = stack.removeLast();
            // Skip Android system directories (permission errors, nothing to play).
            if (dir.getName().equals("Android")) continue;
            if (respectNomedia && new File(dir, ".nomedia").exists()) continue;
            // Two storage roots can overlap (internal storage and its own mount).
            if (!visited.add(canonical(dir))) continue;

            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    stack.addLast(child);
                    continue;
                }
                if (!child.isFile() || !allowedFormats.contains(Track.extensionOf(child).toLowerCase(Locale.ROOT))) continue;

                String path = child.getAbsolutePath();
                TrackCache.Entry entry = cached.get(path);
                if (entry == null || !entry.matches(child)) {
                    // New or changed file: parse its name and probe its duration once.
                    entry = TrackCache.Entry.from(child, Track.fromFile(child), probeDuration(child));
                    dirty = true;
                }
                current.put(path, entry);
                if (entry.isPlayable()) result.add(entry.toTrack());
            }
        }

        // Files that disappeared since the last scan are dropped from the cache too.
        // (Files under folders that are merely unreadable right now are dropped as well; they come back on the next scan.)
        if (dirty || current.size() != cached.size()) cache.save(current);
        return result;
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    // endregion

    // region Playback

    public static void play(int index) {
        if (appContext == null || tracks.isEmpty() || index < 0 || index >= tracks.size()) return;
        currentIndex = index;
        final Track track = tracks.get(index);

        releasePlayer();
        requestAudioFocus();

        MediaPlayer p = new MediaPlayer();
        try {
            // Keeps the CPU awake while this player is playing (needs WAKE_LOCK).
            p.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK);
            p.setAudioAttributes(audioAttributes());
            if (track.contentUri != null) {
                // Android 10: raw file paths are blocked by scoped storage, go through the content:// URI.
                ParcelFileDescriptor pfd = appContext.getContentResolver().openFileDescriptor(track.contentUri, "r");
                if (pfd == null) throw new IOException("Could not open " + track.contentUri);
                try {
                    p.setDataSource(pfd.getFileDescriptor());
                } finally {
                    pfd.close();
                }
            } else {
                p.setDataSource(track.file.getAbsolutePath());
            }
            p.setOnCompletionListener(mp -> onTrackFinished());
            p.setOnErrorListener((mp, what, extra) -> {
                if (mp == player) handlePlaybackFailure(track);
                return true;
            });
            p.prepare();
            p.start();
        } catch (Exception e) {
            p.release();
            handlePlaybackFailure(track);
            return;
        }

        player = p;
        pausedByFocusLoss = false;
        setPlaying(true);
        updateMetadata();
        notifyChanged();
    }

    private static void handlePlaybackFailure(Track track) {
        releasePlayer();
        setPlaying(false);
        if (onPlaybackError != null) onPlaybackError.onPlaybackError(track);
        notifyChanged();
    }

    private static void releasePlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
            }
            player = null;
        }
    }

    public static void togglePlayPause() {
        if (player == null) {
            play(currentIndex);
        } else if (playing) {
            pausedByFocusLoss = false;
            pause();
        } else {
            resume();
        }
    }

    private static void pause() {
        if (player == null || !playing) return;
        try {
            player.pause();
        } catch (IllegalStateException ignored) {
        }
        setPlaying(false);
        notifyChanged();
    }

    private static void resume() {
        if (player == null) {
            play(currentIndex);
            return;
        }
        requestAudioFocus();
        try {
            player.start();
        } catch (IllegalStateException e) {
            play(currentIndex);
            return;
        }
        setPlaying(true);
        notifyChanged();
    }

    public static void next() {
        if (tracks.isEmpty()) return;
        int target;
        if (currentIndex < tracks.size() - 1) target = currentIndex + 1;
        else if (repeat) target = 0;
        else return;
        play(target);
    }

    public static void prev() {
        if (tracks.isEmpty()) return;
        play(currentIndex > 0 ? currentIndex - 1 : tracks.size() - 1);
    }

    public static void seekTo(float fraction) {
        MediaPlayer p = player;
        if (p == null) return;
        try {
            int duration = p.getDuration();
            if (duration <= 0) return;
            p.seekTo((int) (duration * Math.max(0f, Math.min(1f, fraction))));
        } catch (IllegalStateException ignored) {
            return;
        }
        updatePlaybackState();
    }

    /** 0..1, or -1 when nothing is loaded. */
    public static float getProgress() {
        MediaPlayer p = player;
        if (p == null) return -1f;
        try {
            int duration = p.getDuration();
            if (duration <= 0) return -1f;
            return Math.min(1f, p.getCurrentPosition() / (float) duration);
        } catch (IllegalStateException e) {
            return -1f;
        }
    }

    private static void onTrackFinished() {
        if (repeat) {
            play(currentIndex);
        } else if (currentIndex < tracks.size() - 1) {
            play(currentIndex + 1);
        } else {
            // End of the list: show it as stopped instead of "playing" forever.
            setPlaying(false);
            notifyChanged();
        }
    }

    /** Stops playback and lets go of audio focus. The queue stays, play starts the track over. */
    public static void release() {
        pausedByFocusLoss = false;
        releasePlayer();
        setPlaying(false);
        abandonAudioFocus();
        notifyChanged();
    }

    /** Called when the service goes away: also drops the media session. */
    public static void shutdown() {
        release();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    // endregion

    // region Queue

    public static void setTracks(List<Track> newTracks, int index) {
        tracks = newTracks;
        play(index);
    }

    public static void restoreTracks(List<Track> newTracks) {
        Track playingTrack = getCurrentTrack();
        tracks = newTracks;
        int index = playingTrack == null ? -1 : newTracks.indexOf(playingTrack);
        if (index >= 0) currentIndex = index;
        if (onStateChanged != null) onStateChanged.run();
    }

    public static void shuffleTracks(boolean keepTogether) {
        if (tracks.isEmpty()) return;
        Track current = getCurrentTrack();
        List<Track> shuffled = new ArrayList<>(tracks.size());

        if (keepTogether) {
            // Shuffle whole folders, keep the order inside each one; the playing folder goes first.
            Map<String, List<Track>> groups = new LinkedHashMap<>();
            for (Track t : tracks) {
                List<Track> g = groups.get(t.folder());
                if (g == null) {
                    g = new ArrayList<>();
                    groups.put(t.folder(), g);
                }
                g.add(t);
            }
            List<List<Track>> order = new ArrayList<>(groups.values());
            Collections.shuffle(order);
            String currentFolder = current == null ? null : current.folder();
            List<Track> rest = new ArrayList<>();
            for (List<Track> g : order) {
                if (currentFolder != null && g.get(0).folder().equals(currentFolder)) shuffled.addAll(g);
                else rest.addAll(g);
            }
            shuffled.addAll(rest);
        } else {
            List<Track> rest = new ArrayList<>(tracks);
            if (current != null) rest.remove(current);
            Collections.shuffle(rest);
            if (current != null) shuffled.add(current);
            shuffled.addAll(rest);
        }

        tracks = shuffled;
        currentIndex = current == null ? 0 : Math.max(0, tracks.indexOf(current));
        if (onStateChanged != null) onStateChanged.run();
    }

    // endregion
}
