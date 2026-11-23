package com.rama.tui_zero.widgets;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.rama.tui_zero.R;
import com.rama.tui_zero.helpers.Haptics;
import com.rama.tui_zero.objects.Themes;

public class WdNavbar extends LinearLayout {
    public static final int PLAYLIST = 0;
    public static final int VISUALIZER = 1;
    /** Not a page: it opens the settings activity, so it is never highlighted. */
    public static final int SETTINGS = 2;

    public interface OnNavigateListener {
        void onNavigate(int page);
    }

    private final View[] buttons = new View[3];
    private final ImageView[] icons = new ImageView[3];
    private OnNavigateListener listener;

    public WdNavbar(Context context) {
        this(context, null);
    }

    public WdNavbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.wd_navbar, this, true);
        buttons[PLAYLIST] = findViewById(R.id.nav_playlist);
        buttons[VISUALIZER] = findViewById(R.id.nav_visualizer);
        buttons[SETTINGS] = findViewById(R.id.nav_settings);
        icons[PLAYLIST] = findViewById(R.id.nav_playlist_icon);
        icons[VISUALIZER] = findViewById(R.id.nav_visualizer_icon);
        icons[SETTINGS] = findViewById(R.id.nav_settings_icon);
        for (int i = 0; i < buttons.length; i++) {
            final int page = i;
            buttons[i].setOnClickListener(Haptics.onClick(v -> {
                if (listener != null) listener.onNavigate(page);
            }));
        }
    }

    public void setOnNavigateListener(OnNavigateListener listener) {
        this.listener = listener;
    }

    /** The active page gets the accent background and an inverted icon. Call after the theme was applied. */
    public void setActive(int page, Themes.Palette palette) {
        for (int i = 0; i < buttons.length; i++) {
            boolean active = i == page;
            buttons[i].setBackgroundColor(active ? palette.accent : palette.surface_0);
            buttons[i].setEnabled(!active);
            icons[i].setTag(active ? "base" : "text");
            icons[i].setColorFilter(active ? palette.base : palette.text, PorterDuff.Mode.SRC_IN);
        }
    }
}
