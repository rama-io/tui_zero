package com.rama.tui_zero.pages;

import android.content.Context;
import android.view.View;

import com.rama.tui_zero.R;
import com.rama.tui_zero.managers.VisualizerStore;
import com.rama.tui_zero.objects.Themes;
import com.rama.tui_zero.objects.Visualizer;
import com.rama.tui_zero.widgets.WdPlot;

/**
 * Just the canvas. What it draws is set up in the Settings page (visualizer section); the player bar and its
 * controls below it belong to the main activity.
 */
public class VisualizerPage implements Page {
    private final Context context;
    private final WdPlot plot;

    public VisualizerPage(Context context, View root) {
        this.context = context;
        plot = root.findViewById(R.id.plot);
    }

    /** Reloads the setup, so changes made in Settings show up as soon as the page is visible again. */
    @Override
    public void onShow() {
        Visualizer.Result result = VisualizerStore.load(context).build();
        plot.setScene(result.error == null ? result : null); // starts the animation over
    }

    @Override
    public void onHide() {
    }

    @Override
    public void refreshTheme(Themes.Palette palette) {
        plot.setPalette(palette);
    }

    @Override
    public void destroy() {
    }
}
