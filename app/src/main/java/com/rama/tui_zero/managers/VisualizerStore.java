package com.rama.tui_zero.managers;

import android.content.Context;

import com.rama.tui_zero.objects.Visualizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

/** Saves the visualizer setup as one JSON string in the preferences. */
public final class VisualizerStore {
    private VisualizerStore() {
    }

    public static Visualizer load(Context context) {
        Visualizer v = read(PrefsManager.getInstance(context).getVisualizerJson());
        // Colors are picked once and then saved, so a line keeps its color between launches.
        if (v.assignMissingColors(new Random())) save(context, v);
        return v;
    }

    private static Visualizer read(String raw) {
        if (raw == null) return Visualizer.example();
        Visualizer v = new Visualizer();
        try {
            JSONObject json = new JSONObject(raw);
            // Before the delay was shared, every counter had its own: take the first one that was set.
            String oldDelay = "";
            JSONArray counters = json.optJSONArray("counters");
            for (int i = 0; counters != null && i < counters.length(); i++) {
                JSONObject c = counters.getJSONObject(i);
                Visualizer.Counter counter = new Visualizer.Counter(c.optString("alias"), c.optString("from"), c.optString("to"));
                counter.infinite = c.optBoolean("infinite", false);
                counter.alternate = c.optBoolean("alternate", false);
                counter.enabled = c.optBoolean("enabled", true);
                counter.collapsed = c.optBoolean("collapsed", false);
                v.counters.add(counter);
                if (oldDelay.isEmpty()) oldDelay = c.optString("delay");
            }
            v.delay = json.has("delay") ? json.optString("delay") : oldDelay;
            JSONArray formulas = json.optJSONArray("formulas");
            for (int i = 0; formulas != null && i < formulas.length(); i++) {
                JSONObject f = formulas.getJSONObject(i);
                Visualizer.FormulaDef formula = new Visualizer.FormulaDef(f.optString("x"), f.optString("y"), f.optString("color"));
                formula.enabled = f.optBoolean("enabled", true);
                formula.collapsed = f.optBoolean("collapsed", false);
                v.formulas.add(formula);
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
                counters.put(new JSONObject().put("alias", c.alias).put("from", c.from).put("to", c.to)
                        .put("infinite", c.infinite).put("alternate", c.alternate)
                        .put("enabled", c.enabled).put("collapsed", c.collapsed));
            }
            JSONArray formulas = new JSONArray();
            for (Visualizer.FormulaDef f : v.formulas) {
                formulas.put(new JSONObject().put("x", f.x).put("y", f.y).put("color", f.color)
                        .put("enabled", f.enabled).put("collapsed", f.collapsed));
            }
            PrefsManager.getInstance(context).setVisualizerJson(new JSONObject()
                    .put("delay", v.delay).put("counters", counters).put("formulas", formulas).toString());
        } catch (JSONException ignored) {
            // keys are constants and values are strings, so this cannot happen
        }
    }
}
