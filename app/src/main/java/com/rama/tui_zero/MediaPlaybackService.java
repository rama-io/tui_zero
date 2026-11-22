package com.rama.tui_zero;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;

import com.rama.tui_zero.activities.Main;
import com.rama.tui_zero.managers.MusicManager;
import com.rama.tui_zero.managers.ThemeManager;
import com.rama.tui_zero.objects.Track;

/**
 * Always-on foreground service. As long as it is running the process has foreground priority, so
 * the system does not freeze or kill it when the screen locks. It starts with the app and only
 * ends through the notification's Stop action.
 */
public class MediaPlaybackService extends Service {
    public static final String ACTION_PLAY_PAUSE = "com.rama.tui_zero.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.rama.tui_zero.NEXT";
    public static final String ACTION_PREV = "com.rama.tui_zero.PREV";
    public static final String ACTION_STOP = "com.rama.tui_zero.STOP";

    private static final String CHANNEL_ID = "tui_playback";
    private static final int NOTIFICATION_ID = 1;

    private boolean foregroundStarted;

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleAction(intent.getAction());
        }
    };

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()) && MusicManager.isPlaying()) {
                MusicManager.togglePlayPause();
            }
        }
    };

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, MediaPlaybackService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent);
            else app.startService(intent);
        } catch (RuntimeException ignored) {
            // Background start restrictions: the next time the app is opened it starts for real.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        MusicManager.init(this);
        MusicManager.setOnNotificationChanged(this::refreshNotification);

        IntentFilter controls = new IntentFilter();
        controls.addAction(ACTION_PLAY_PAUSE);
        controls.addAction(ACTION_NEXT);
        controls.addAction(ACTION_PREV);
        controls.addAction(ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(controlReceiver, controls, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, controls);
        }
        // System broadcast, must stay a plain registration.
        registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        enterForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // startForegroundService() requires a startForeground() for every start call.
        enterForeground();
        if (intent != null && intent.getAction() != null) handleAction(intent.getAction());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(controlReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(noisyReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        MusicManager.setOnNotificationChanged(null);
        MusicManager.shutdown();
        super.onDestroy();
    }

    private void handleAction(String action) {
        if (ACTION_PLAY_PAUSE.equals(action)) {
            MusicManager.togglePlayPause();
        } else if (ACTION_NEXT.equals(action)) {
            MusicManager.next();
        } else if (ACTION_PREV.equals(action)) {
            MusicManager.prev();
        } else if (ACTION_STOP.equals(action)) {
            MusicManager.release();
            stopForeground(true);
            foregroundStarted = false;
            stopSelf();
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_desc));
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private void enterForeground() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foregroundStarted = true;
        } catch (RuntimeException ignored) {
            // E.g. not allowed to start from the background: stay a plain service.
        }
    }

    private void refreshNotification() {
        if (!foregroundStarted) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private PendingIntent action(String action, int requestCode) {
        Intent intent = new Intent(action).setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(this, requestCode, intent, flags);
    }

    private Notification buildNotification() {
        Track track = MusicManager.getCurrentTrack();
        boolean playing = MusicManager.isPlaying();

        Intent open = new Intent(this, Main.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(track != null ? track.title : getString(R.string.notification_not_playing))
                .setContentText(track != null ? track.displayArtists() : "")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(ThemeManager.currentPalette(this).accent);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        builder.addAction(actionOf(R.drawable.px_prev, R.string.notification_previous, ACTION_PREV, 1));
        builder.addAction(playing
                ? actionOf(R.drawable.px_pause, R.string.notification_pause, ACTION_PLAY_PAUSE, 2)
                : actionOf(R.drawable.px_play, R.string.notification_play, ACTION_PLAY_PAUSE, 2));
        builder.addAction(actionOf(R.drawable.px_next, R.string.notification_next, ACTION_NEXT, 3));
        builder.addAction(actionOf(R.drawable.px_times, R.string.notification_stop, ACTION_STOP, 4));

        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
        if (MusicManager.getSessionToken() != null) style.setMediaSession(MusicManager.getSessionToken());
        builder.setStyle(style);

        return builder.build();
    }

    @SuppressWarnings("deprecation")
    private Notification.Action actionOf(int icon, int title, String action, int requestCode) {
        PendingIntent pi = action(action, requestCode);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), getString(title), pi).build();
        }
        return new Notification.Action.Builder(icon, getString(title), pi).build();
    }
}
