package com.rama.tui_zero.objects;

import com.rama.tui_zero.helpers.Formula;

import java.util.ArrayList;
import java.util.List;

/**
 * What the visualizer draws: counters (a name that counts from one number to another) and formulas that give the
 * X and Y of a point in terms of those counters, e.g. counter t from 1 to 100, x = cos($t), y = sin($t).
 * The fields keep the text as typed, so editing never reformats what the person is in the middle of writing.
 * Plain Java, no Android classes.
 */
public final class Visualizer {
    public static final int MAX_POINTS = 50000;

    public static final class Counter {
        public String alias;
        public String from;
        public String to;
        /** Milliseconds between two steps. Not used while the drawing is static. */
        public String delay;

        public Counter(String alias, String from, String to, String delay) {
            this.alias = alias;
            this.from = from;
            this.to = to;
            this.delay = delay;
        }
    }

    public static final class FormulaDef {
        public String x;
        public String y;

        public FormulaDef(String x, String y) {
            this.x = x;
            this.y = y;
        }
    }

    /** The points of one formula, in counter order. */
    public static final class Series {
        public final double[] xs;
        public final double[] ys;

        Series(double[] xs, double[] ys) {
            this.xs = xs;
            this.ys = ys;
        }
    }

    public static final class Result {
        public final List<Series> series = new ArrayList<Series>();
        /** Null when everything was valid. */
        public String error;
        public int points;
    }

    public final List<Counter> counters = new ArrayList<Counter>();
    public final List<FormulaDef> formulas = new ArrayList<FormulaDef>();

    /** What a fresh install starts with, so the canvas is not empty. */
    public static Visualizer example() {
        Visualizer v = new Visualizer();
        v.counters.add(new Counter("t", "1", "100", "10"));
        v.formulas.add(new FormulaDef("cos($t)", "sin($t)"));
        return v;
    }

    /** Next free counter name: t, u, v, w, then c5, c6, ... */
    public String nextAlias() {
        String preferred = "tuvwsr";
        for (int i = 0; i < preferred.length(); i++) {
            String name = String.valueOf(preferred.charAt(i));
            if (!hasAlias(name)) return name;
        }
        for (int n = counters.size() + 1; ; n++) {
            String name = "c" + n;
            if (!hasAlias(name)) return name;
        }
    }

    private boolean hasAlias(String name) {
        for (Counter c : counters) if (name.equals(c.alias.trim())) return true;
        return false;
    }

    /**
     * Evaluates every formula for every combination of counter values (the first counter is the outer loop).
     * A counter steps by 1 from "from" towards "to", both included, so 1 to 100 gives 100 values.
     * Stops at the first problem and describes it in Result.error.
     */
    public Result build() {
        Result result = new Result();
        int n = counters.size();
        String[] names = new String[n];
        double[][] values = new double[n][];
        long combos = 1;
        for (int i = 0; i < n; i++) {
            Counter c = counters.get(i);
            String label = "COUNTER_" + (i + 1);
            String alias = c.alias.trim();
            if (!Formula.isValidName(alias)) {
                result.error = label + ": alias must be letters, digits or _";
                return result;
            }
            for (int j = 0; j < i; j++) {
                if (names[j].equals(alias)) {
                    result.error = label + ": alias " + alias + " is used twice";
                    return result;
                }
            }
            names[i] = alias;
            double from, to;
            try {
                from = parseNumber(c.from);
                to = parseNumber(c.to);
            } catch (NumberFormatException e) {
                result.error = label + ": from and to must be numbers";
                return result;
            }
            long count = (long) Math.floor(Math.abs(to - from) + 1e-9) + 1;
            combos *= count;
            if (combos > MAX_POINTS) {
                result.error = "too many points (max " + MAX_POINTS + ")";
                return result;
            }
            double step = to >= from ? 1 : -1;
            values[i] = new double[(int) count];
            for (int k = 0; k < count; k++) values[i][k] = from + step * k;
        }

        for (int f = 0; f < formulas.size(); f++) {
            FormulaDef def = formulas.get(f);
            String label = "FORMULA_" + (f + 1);
            if (def.x.trim().isEmpty() && def.y.trim().isEmpty()) continue;
            Formula fx, fy;
            try {
                fx = Formula.parse(def.x, names);
            } catch (Formula.FormulaException e) {
                result.error = label + " X: " + e.getMessage();
                return result;
            }
            try {
                fy = Formula.parse(def.y, names);
            } catch (Formula.FormulaException e) {
                result.error = label + " Y: " + e.getMessage();
                return result;
            }
            double[] xs = new double[(int) combos];
            double[] ys = new double[(int) combos];
            int[] index = new int[n];
            double[] vars = new double[n];
            for (int p = 0; p < combos; p++) {
                for (int i = 0; i < n; i++) vars[i] = values[i][index[i]];
                xs[p] = fx.eval(vars);
                ys[p] = fy.eval(vars);
                for (int i = n - 1; i >= 0; i--) { // odometer: the last counter moves fastest
                    if (++index[i] < values[i].length) break;
                    index[i] = 0;
                }
            }
            result.series.add(new Series(xs, ys));
            result.points += xs.length;
        }
        return result;
    }

    private static double parseNumber(String text) {
        double v = Double.parseDouble(text.trim());
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException(text);
        return v;
    }
}
