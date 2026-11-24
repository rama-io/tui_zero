package com.rama.tui_zero.objects;

import com.rama.tui_zero.helpers.Formula;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What the visualizer draws: counters (a name that counts from one number to another) and formulas that give the
 * X and Y of a point in terms of those counters, e.g. counter t from 1 to 100, x = cos($t), y = sin($t).
 *
 * All counters share one clock (the delay): on every tick each of them takes one step, and every formula gives one
 * new point. The line is the trail of those points. When a pass is over and some counter is infinite, the line is
 * erased and the next pass starts, so nothing is ever drawn on top of an older drawing.
 *
 * The fields keep the text as typed, so editing never reformats what the person is in the middle of writing.
 * Plain Java, no Android classes.
 */
public final class Visualizer {
    public static final int MAX_POINTS = 50000;
    /** How long a finished drawing stays before it is erased and the next pass begins. */
    public static final long HOLD_MS = 600;

    public static final class Counter {
        public String alias;
        public String from;
        public String to;
        /** Keeps going after reaching "to" instead of stopping there. */
        public boolean infinite;
        /** When infinite: true goes back down (1 to 100, then 100 to 1), false starts over (1 to 100, then 1 again). */
        public boolean alternate;
        /** A turned off counter is ignored, as if it was not there. */
        public boolean enabled = true;
        /** Only whether its block is folded into its title in Settings. */
        public boolean collapsed;

        public Counter(String alias, String from, String to) {
            this.alias = alias;
            this.from = from;
            this.to = to;
        }
    }

    public static final class FormulaDef {
        public String x;
        public String y;
        /** The accent id its line is drawn with. Random and different per formula; see assignMissingColors. */
        public String color = "";
        public boolean enabled = true;
        public boolean collapsed;

        public FormulaDef(String x, String y) {
            this.x = x;
            this.y = y;
        }

        public FormulaDef(String x, String y, String color) {
            this(x, y);
            this.color = color;
        }
    }

    /** The points of one formula, one per step of a pass. */
    public static final class Series {
        public final double[] xs;
        public final double[] ys;
        public final String color;
        /** Position of the formula in the list, so a line and its title agree on the color even if a formula before it is turned off. */
        public final int formulaIndex;

        Series(double[] xs, double[] ys, String color, int formulaIndex) {
            this.xs = xs;
            this.ys = ys;
            this.color = color;
            this.formulaIndex = formulaIndex;
        }
    }

    /**
     * The timeline of a pass, worked out from the elapsed time alone so there is no state to keep in sync.
     * A pass lasts as long as its longest counter: finite and linear counters take n steps (n values), an alternating
     * one takes 2 * (n - 1) (1 up to 100 and back down to 2, then the pass ends and 1 comes again). A finite counter
     * that finishes early waits at its last value.
     */
    public static final class Animation {
        final int[] counts;
        final boolean[] infinite;
        final boolean[] alternate;
        final long delayMs;
        public final int passLength;
        /** True when some counter is infinite, so passes follow each other forever. */
        public final boolean looping;

        Animation(int[] counts, boolean[] infinite, boolean[] alternate, long delayMs) {
            this.counts = counts;
            this.infinite = infinite;
            this.alternate = alternate;
            this.delayMs = delayMs;
            int length = 1;
            boolean loops = false;
            for (int i = 0; i < counts.length; i++) {
                length = Math.max(length, passLengthOf(i));
                if (infinite[i] && counts[i] > 1) loops = true;
            }
            this.passLength = length;
            this.looping = loops;
        }

        private int passLengthOf(int i) {
            int n = counts[i];
            if (infinite[i] && alternate[i] && n > 1) return 2 * (n - 1);
            return n;
        }

        /** Index of the value counter i shows at step m of a pass. */
        int indexOf(int i, long m) {
            int n = counts[i];
            if (n <= 1) return 0;
            if (!infinite[i]) return (int) Math.min(m, n - 1);
            if (!alternate[i]) return (int) (m % n);
            long period = 2L * (n - 1);
            long r = m % period;
            return (int) (r < n ? r : period - r);
        }

        private long holdSteps() {
            return (HOLD_MS + delayMs - 1) / delayMs;
        }

        /** How many points of the pass, counted from the first one, are drawn after elapsedMs. */
        public int visiblePoints(long elapsedMs) {
            if (delayMs <= 0) return passLength; // no delay: no animation, the whole drawing
            long steps = elapsedMs / delayMs;
            if (!looping) return (int) Math.min(steps, passLength - 1) + 1;
            long inCycle = steps % (passLength + holdSteps());
            return (int) (inCycle < passLength ? inCycle + 1 : passLength);
        }

        /** False once nothing will change any more, so the screen can stop redrawing. */
        public boolean isRunning(long elapsedMs) {
            if (delayMs <= 0 || passLength <= 1) return false;
            return looping || elapsedMs / delayMs < passLength - 1;
        }
    }

    public static final class Result {
        public final List<Series> series = new ArrayList<Series>();
        /** Null when everything was valid. */
        public String error;
        public int points;
        public Animation animation;
    }

    public final List<Counter> counters = new ArrayList<Counter>();
    public final List<FormulaDef> formulas = new ArrayList<FormulaDef>();
    /** Milliseconds between two steps, for all counters. Empty or 0: no animation. */
    public String delay = "";

    /** What a fresh install starts with, so the canvas is not empty. */
    public static Visualizer example() {
        Visualizer v = new Visualizer();
        v.delay = "10";
        v.counters.add(new Counter("t", "1", "100"));
        v.formulas.add(new FormulaDef("cos($t)", "sin($t)"));
        return v;
    }

    /** A random accent id no other formula uses yet (any of them once all are taken). */
    public String pickColor(Random random) {
        List<String> free = Themes.accentIds();
        for (FormulaDef f : formulas) free.remove(f.color);
        List<String> pool = free.isEmpty() ? Themes.accentIds() : free;
        return pool.get(random.nextInt(pool.size()));
    }

    /** Gives a color to every formula that has none. Returns true if it changed anything. */
    public boolean assignMissingColors(Random random) {
        boolean changed = false;
        for (FormulaDef f : formulas) {
            if (f.color == null || f.color.isEmpty() || !Themes.isAccent(f.color)) {
                f.color = "";
                f.color = pickColor(random);
                changed = true;
            }
        }
        return changed;
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
     * Works out one pass: the point every turned-on formula gives at every step. A counter steps by 1 from "from"
     * towards "to", both included, so 1 to 100 has 100 values. Turned off counters and formulas are skipped; a
     * formula that uses a turned off counter says so. Stops at the first problem and describes it in Result.error.
     */
    public Result build() {
        Result result = new Result();
        List<Counter> active = new ArrayList<Counter>();
        List<String> off = new ArrayList<String>();
        for (Counter c : counters) {
            if (c.enabled) active.add(c);
            else if (Formula.isValidName(c.alias.trim())) off.add(c.alias.trim());
        }

        int n = active.size();
        String[] names = new String[n];
        double[][] values = new double[n][];
        int[] counts = new int[n];
        boolean[] infinite = new boolean[n];
        boolean[] alternate = new boolean[n];
        for (int i = 0; i < n; i++) {
            Counter c = active.get(i);
            String label = "COUNTER_" + (counters.indexOf(c) + 1);
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
            if (count > MAX_POINTS) {
                result.error = "too many points (max " + MAX_POINTS + ")";
                return result;
            }
            double step = to >= from ? 1 : -1;
            values[i] = new double[(int) count];
            for (int k = 0; k < count; k++) values[i][k] = from + step * k;
            counts[i] = (int) count;
            infinite[i] = c.infinite;
            alternate[i] = c.alternate;
        }
        Animation animation = new Animation(counts, infinite, alternate, parseDelay(delay));
        if (animation.passLength > MAX_POINTS) {
            result.error = "too many points (max " + MAX_POINTS + ")";
            return result;
        }
        result.animation = animation;

        int length = animation.passLength;
        for (int f = 0; f < formulas.size(); f++) {
            FormulaDef def = formulas.get(f);
            if (!def.enabled) continue;
            String label = "FORMULA_" + (f + 1);
            if (def.x.trim().isEmpty() && def.y.trim().isEmpty()) continue;
            Formula fx, fy;
            try {
                fx = Formula.parse(def.x, names);
            } catch (Formula.FormulaException e) {
                result.error = label + " X: " + explain(e.getMessage(), off);
                return result;
            }
            try {
                fy = Formula.parse(def.y, names);
            } catch (Formula.FormulaException e) {
                result.error = label + " Y: " + explain(e.getMessage(), off);
                return result;
            }
            double[] xs = new double[length];
            double[] ys = new double[length];
            double[] vars = new double[n];
            for (int m = 0; m < length; m++) {
                for (int i = 0; i < n; i++) vars[i] = values[i][animation.indexOf(i, m)];
                xs[m] = fx.eval(vars);
                ys[m] = fy.eval(vars);
            }
            result.series.add(new Series(xs, ys, def.color == null ? "" : def.color, f));
            result.points += length;
        }
        return result;
    }

    /** "no counter named $t" is confusing when the counter exists but is turned off. */
    private static String explain(String message, List<String> off) {
        String prefix = "no counter named $";
        if (message.startsWith(prefix) && off.contains(message.substring(prefix.length()))) {
            return "counter $" + message.substring(prefix.length()) + " is turned off";
        }
        return message;
    }

    /** Empty or not a number means 0: no animation. */
    private static long parseDelay(String text) {
        try {
            return Math.max(0, Long.parseLong(text.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseNumber(String text) {
        double v = Double.parseDouble(text.trim());
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException(text);
        return v;
    }
}
