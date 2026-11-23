package com.rama.tui_zero.managers;

import android.content.Context;

import com.rama.tui_zero.objects.Visualizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Saves the visualizer setup as one JSON string in the preferences. */
public final class VisualizerStore {
    private VisualizerStore() {
    }

    public static Visualizer load(Context context) {
        String raw = PrefsManager.getInstance(context).getVisualizerJson();
        if (raw == null) return Visualizer.example();
        Visualizer v = new Visualizer();
        try {
            JSONObject json = new JSONObject(raw);
            JSONArray counters = json.optJSONArray("counters");
            for (int i = 0; counters != null && i < counters.length(); i++) {
                JSONObject c = counters.getJSONObject(i);
                v.counters.add(new Visualizer.Counter(c.optString("alias"), c.optString("from"), c.optString("to"), c.optString("delay")));
            }
            JSONArray formulas = json.optJSONArray("formulas");
            for (int i = 0; formulas != null && i < formulas.length(); i++) {
                JSONObject f = formulas.getJSONObject(i);
                v.formulas.add(new Visualizer.FormulaDef(f.optString("x"), f.optString("y")));
            }
        } catch (JSONException e) {
            return Visualizer.example();
        }
        return v;
    }

    public static void save(Context context, Visualizer v) {
        try {
            JSONArray counters = new JSONArray();
            for (Visualizer.Counter c : v.counters) {
                counters.put(new JSONObject().put("alias", c.alias).put("from", c.from).put("to", c.to).put("delay", c.delay));
            }
            JSONArray formulas = new JSONArray();
            for (Visualizer.FormulaDef f : v.formulas) {
                formulas.put(new JSONObject().put("x", f.x).put("y", f.y));
            }
            PrefsManager.getInstance(context).setVisualizerJson(
                    new JSONObject().put("counters", counters).put("formulas", formulas).toString());
        } catch (JSONException ignored) {
            // keys are constants and values are strings, so this cannot happen
        }
    }
}
