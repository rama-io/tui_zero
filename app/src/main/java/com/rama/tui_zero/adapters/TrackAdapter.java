package com.rama.tui_zero.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.rama.tui_zero.R;
import com.rama.tui_zero.managers.FontManager;
import com.rama.tui_zero.managers.MusicManager;
import com.rama.tui_zero.managers.ThemeManager;
import com.rama.tui_zero.managers.ZoomManager;
import com.rama.tui_zero.objects.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrackAdapter extends BaseAdapter {
    private final Context context;
    private List<Track> library = new ArrayList<>();
    private List<Track> displayed = new ArrayList<>();
    private String query = "";

    public TrackAdapter(Context context) {
        this.context = context;
    }

    public void setLibrary(List<Track> tracks) {
        library = new ArrayList<>(tracks);
        refilter();
    }

    public void filter(String text) {
        query = text == null ? "" : text;
        refilter();
    }

    public void resetFilter() {
        filter("");
    }

    public void showTracks(List<Track> tracks) {
        displayed = new ArrayList<>(tracks);
        notifyDataSetChanged();
    }

    public List<Track> getDisplayed() {
        return displayed;
    }

    public int positionOf(Track track) {
        return track == null ? -1 : displayed.indexOf(track);
    }

    private void refilter() {
        String needle = Track.normalize(query.trim());
        if (needle.length() == 0) {
            displayed = new ArrayList<>(library);
        } else {
            List<Track> out = new ArrayList<>();
            for (Track t : library) {
                if (t.normalizedName.contains(needle)) out.add(t);
            }
            displayed = out;
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return displayed.size();
    }

    @Override
    public Track getItem(int position) {
        return displayed.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView != null ? convertView : LayoutInflater.from(context).inflate(R.layout.list_item_track, parent, false);
        Track track = displayed.get(position);
        set(view, R.id.track_title, track.title);
        set(view, R.id.track_artist, track.displayArtists());
        set(view, R.id.track_country, track.displayCountries());
        set(view, R.id.track_lang, track.displayLanguages());
        set(view, R.id.track_ext, track.ext.toUpperCase(Locale.ROOT));
        set(view, R.id.track_duration, track.displayDuration());
        view.findViewById(R.id.disc).setAlpha(track.equals(MusicManager.getCurrentTrack()) ? 1f : 0.3f);

        FontManager.apply(view, FontManager.getJersey25(context));
        ThemeManager.applyTheme(context, view);
        ZoomManager.apply(context, view);
        return view;
    }

    private void set(View row, int id, String value) {
        ((TextView) row.findViewById(id)).setText(value == null || value.length() == 0 ? "---" : value);
    }
}
