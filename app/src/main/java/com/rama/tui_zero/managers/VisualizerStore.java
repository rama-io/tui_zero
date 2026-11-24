package com.rama.tui_zero.managers;

import android.content.Context;

import com.rama.tui_zero.objects.Visualizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

public final class VisualizerStore {
    private VisualizerStore() {
    }

    public static Visualizer load(Context context) {
        Visualizer v = read(PrefsManager.getInstance(context).getVisualizerJson());
        if (v.assignMissingColors(new Random())) save(context, v);
        return v;
    }

    private static Visualizer read(String raw) {
        if (raw == null) return Visualizer.example();
        Visualizer v = new Visualizer();
        try {
            JSONObject json = new JSONObject(raw);
            String sharedDelay = json.optString("delay");
            JSONArray counters = json.optJSONArray("counters");
            for (int i = 0; counters != null && i < counters.length(); i++) {
                JSONObject c = counters.getJSONObject(i);
                Visualizer.Counter counter = new Visualizer.Counter(
                        c.optString("alias"),
                        c.optString("from"),
                        c.optString("to"),
                        c.optString("delay", sharedDelay),
                        c.optBoolean("infinite", false),
                        c.optBoolean("alternate", false)
                );
                counter.infinite = c.optBoolean("infinite", false);
                counter.alternate = c.optBoolean("alternate", false);
                counter.enabled = c.optBoolean("enabled", true);
                counter.collapsed = c.optBoolean("collapsed", false);
                counter.keepTrail = c.optBoolean("keepTrail", true);
                v.counters.add(counter);
                counter.delay = c.has("delay") ? c.optString("delay") : sharedDelay;
            }
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
                        .put("delay", c.delay).put("keepTrail", c.keepTrail).put("enabled", c.enabled).put("collapsed", c.collapsed));
            }
            JSONArray formulas = new JSONArray();
            for (Visualizer.FormulaDef f : v.formulas) {
                formulas.put(new JSONObject().put("x", f.x).put("y", f.y).put("color", f.color)
                        .put("enabled", f.enabled).put("collapsed", f.collapsed));
            }
            PrefsManager.getInstance(context).setVisualizerJson(new JSONObject()
                    .put("counters", counters).put("formulas", formulas).toString());
        } catch (JSONException ignored) {
        }
    }
}
