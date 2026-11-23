package com.rama.tui_zero.pages;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import com.rama.tui_zero.R;
import com.rama.tui_zero.adapters.TrackAdapter;
import com.rama.tui_zero.helpers.Haptics;
import com.rama.tui_zero.managers.MusicManager;
import com.rama.tui_zero.managers.PrefsManager;
import com.rama.tui_zero.objects.Themes;
import com.rama.tui_zero.objects.Track;

/** The library: a search field over the list of tracks. The player bar below it lives in the main activity. */
public class PlaylistPage implements Page {
    private final Context context;
    private final ListView list;
    private final EditText filter;
    private final View clearButton;
    private final View loadingText;
    private final TrackAdapter adapter;
    private Track lastScrolledTo;

    public PlaylistPage(Context context, View root) {
        this.context = context;
        list = root.findViewById(R.id.track_list);
        filter = root.findViewById(R.id.filter_input);
        clearButton = root.findViewById(R.id.clear_button);
        loadingText = root.findViewById(R.id.loading_text);

        adapter = new TrackAdapter(context);
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
    }

    public void setLoading(boolean loading) {
        loadingText.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    public void showLibrary() {
        adapter.setLibrary(MusicManager.getAllTracks());
        adapter.filter(filter.getText().toString());
    }

    public void clearFilter() {
        filter.setText("");
    }

    public void shuffle() {
        MusicManager.restoreTracks(adapter.getDisplayed());
        MusicManager.shuffleTracks(PrefsManager.getInstance(context).isKeepTogether());
        adapter.showTracks(MusicManager.getTracks());
        lastScrolledTo = null;
    }

    /** Redraws the rows and follows the playing track. */
    public void refresh(Track current) {
        adapter.notifyDataSetChanged();
        // Only follow the playing track when it changes, so scrolling the list by hand is not undone.
        if (current != null && !current.equals(lastScrolledTo)) {
            lastScrolledTo = current;
            int pos = adapter.positionOf(current);
            if (pos >= 0) list.smoothScrollToPosition(pos);
        }
    }

    @Override
    public void onShow() {
    }

    @Override
    public void onHide() {
        filter.clearFocus();
    }

    @Override
    public void refreshTheme(Themes.Palette palette) {
        adapter.notifyDataSetChanged();
    }

    @Override
    public void destroy() {
    }
}
