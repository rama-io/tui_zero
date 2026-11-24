package com.rama.tui_zero;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;

public class MediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
        KeyEvent event;
        if (Build.VERSION.SDK_INT >= 33) {
            event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class);
        } else {
            event = getLegacyKeyEvent(intent);
        }
        if (event == null || event.getAction() != KeyEvent.ACTION_UP) return;

        String action;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                action = MediaPlaybackService.ACTION_PLAY_PAUSE;
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                action = MediaPlaybackService.ACTION_NEXT;
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                action = MediaPlaybackService.ACTION_PREV;
                break;
            default:
                return;
        }
        Intent forward = new Intent(action).setPackage(context.getPackageName());
        context.sendBroadcast(forward);
    }

    @SuppressWarnings("deprecation")
    private static KeyEvent getLegacyKeyEvent(Intent intent) {
        return intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
    }
}
