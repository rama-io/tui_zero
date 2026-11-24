package com.rama.tui_zero.objects;

import com.rama.tui_zero.helpers.Formula;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What the visualizer draws: counters (a name that counts from one number to another) and formulas that give the
 * X and Y of a point in terms of those counters, e.g. counter t from 1 to 100, x = cos($t), y = sin($t).
 *
 * Every counter has its own delay, so it runs on its own clock. What is on screen is every combination of counter
 * values where each counter is: without a delay, at all of its values at once; with a delay and "keep trail", at the
 * values it has gone through so far (so a line grows, and with alternate shrinks on the way back); with a delay and no
 * trail, only at its current value (so a shape moves instead of piling up). Example: a circle t = 1..360 without delay and
 * a radius r = 0..10 with a delay and no trail is a circle that grows.
 *
 * The fields keep the text as typed, so editing never reformats what the person is in the middle of writing.
 * Plain Java, no Android classes.
 */
public final class Visualizer {
    public static final int MAX_POINTS = 50000;

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
        /**
         * For a counter with a delay: true keeps what it already went through on screen (the line grows, and with
         * alternate shrinks on the way back); false shows only its current value, so the shape moves instead of piling up.
         */
        public boolean keepTrail = true;
        /** Milliseconds between two steps of this counter. Empty or 0: no animation, it shows its last value. */
        public String delay = "";

        public Counter(String alias, String from, String to) {
            this.alias = alias;
            this.from = from;
            this.to = to;
        }

        public Counter(String alias, String from, String to, String delay) {
            this(alias, from, to);
            this.delay = delay;
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

    /** What is on screen at a given time, worked out from the elapsed time alone so there is no state to keep in sync. */
    public static final class Animation {
        final int[] counts;
        final long[] delays;
        final boolean[] infinite;
        final boolean[] alternate;
        final boolean[] trail;
        final long[] strides;
        public final int totalPoints;

        Animation(int[] counts, long[] delays, boolean[] infinite, boolean[] alternate, boolean[] trail) {
            this.counts = counts;
            this.delays = delays;
            this.infinite = infinite;
            this.alternate = alternate;
            this.trail = trail;
            strides = new long[counts.length];
            long stride = 1;
            for (int i = counts.length - 1; i >= 0; i--) {
                strides[i] = stride;
                stride *= counts[i];
            }
            totalPoints = (int) stride;
        }

        private boolean animated(int i) {
            return counts[i] > 1 && delays[i] > 0;
        }

        /** Index of the value counter i is showing after elapsedMs (its last one when it has no delay). */
        int indexOf(int i, long elapsedMs) {
            int n = counts[i];
            if (n <= 1 || delays[i] <= 0) return n - 1 < 0 ? 0 : n - 1;
            long steps = elapsedMs / delays[i];
            if (!infinite[i]) return (int) Math.min(steps, n - 1);
            if (!alternate[i]) return (int) (steps % n);
            long period = 2L * (n - 1);
            long r = steps % period;
            return (int) (r < n ? r : period - r);
        }

        /** First and last value index counter i has on screen. */
        private int low(int i, long elapsedMs) {
            if (!animated(i) || trail[i]) return 0;
            return indexOf(i, elapsedMs);
        }

        private int high(int i, long elapsedMs) {
            if (!animated(i)) return counts[i] - 1;
            return indexOf(i, elapsedMs);
        }

        /**
         * The points on screen after elapsedMs, as pairs {from, to} (to excluded) of positions in a series. Each pair is
         * one stretch of line; the gaps between pairs are not joined.
         */
        public int[] ranges(long elapsedMs) {
            int n = counts.length;
            int[] lo = new int[n];
            int[] hi = new int[n];
            int k = -1; // the last counter that is not showing all of its values
            for (int i = 0; i < n; i++) {
                lo[i] = low(i, elapsedMs);
                hi[i] = high(i, elapsedMs);
                if (lo[i] != 0 || hi[i] != counts[i] - 1) k = i;
            }
            if (k < 0) return new int[]{0, totalPoints};
            // The counters before k pick a stretch each; counter k's values and everything after it are one stretch.
            int[] at = new int[k];
            long blocks = 1;
            for (int i = 0; i < k; i++) {
                at[i] = lo[i];
                blocks *= hi[i] - lo[i] + 1;
            }
            int[] out = new int[(int) blocks * 2];
            for (int b = 0; b < blocks; b++) {
                long base = 0;
                for (int i = 0; i < k; i++) base += at[i] * strides[i];
                out[2 * b] = (int) (base + lo[k] * strides[k]);
                out[2 * b + 1] = (int) (base + (hi[k] + 1) * strides[k]);
                for (int i = k - 1; i >= 0; i--) { // odometer over the counters before k
                    if (++at[i] <= hi[i]) break;
                    at[i] = lo[i];
                }
            }
            return out;
        }

        /** How many points are on screen after elapsedMs. */
        public int visiblePoints(long elapsedMs) {
            int[] r = ranges(elapsedMs);
            int total = 0;
            for (int i = 0; i < r.length; i += 2) total += r[i + 1] - r[i];
            return total;
        }

        /** False once nothing will change any more, so the screen can stop redrawing. */
        public boolean isRunning(long elapsedMs) {
            for (int i = 0; i < counts.length; i++) {
                if (!animated(i)) continue;
                if (infinite[i] || elapsedMs / delays[i] < counts[i] - 1) return true;
            }
            return false;
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

    /** What a fresh install starts with, so the canvas is not empty. */
    public static Visualizer example() {
        Visualizer v = new Visualizer();
        // 360 values: 0 would repeat the point of 360
        v.counters.add(new Counter("t", "1", "360", "10"));
        v.formulas.add(new FormulaDef("sin($t*pi/180)", "cos($t*pi/180)"));
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
     * Evaluates every turned-on formula for every combination of values of the turned-on counters (the first counter is
     * the outer loop). A counter steps by 1 from "from" towards "to", both included, so 1 to 100 gives 100 values.
     * A formula that uses a turned off counter says so. Stops at the first problem and describes it in Result.error.
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
        long[] delays = new long[n];
        boolean[] infinite = new boolean[n];
        boolean[] alternate = new boolean[n];
        boolean[] trail = new boolean[n];
        long combos = 1;
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
            combos *= count;
            if (combos > MAX_POINTS) {
                result.error = "too many points (max " + MAX_POINTS + ")";
                return result;
            }
            double step = to >= from ? 1 : -1;
            values[i] = new double[(int) count];
            for (int k = 0; k < count; k++) values[i][k] = from + step * k;
            counts[i] = (int) count;
            delays[i] = parseDelay(c.delay);
            infinite[i] = c.infinite;
            alternate[i] = c.alternate;
            trail[i] = c.keepTrail;
        }
        // Counters that show only their current value go outermost, so what is left of the combination (the counters
        // that draw) stays together as one line whatever order the counters are listed in.
        int[] perm = new int[n];
        int at = 0;
        for (int i = 0; i < n; i++) if (counts[i] > 1 && delays[i] > 0 && !trail[i]) perm[at++] = i;
        for (int i = 0; i < n; i++) if (!(counts[i] > 1 && delays[i] > 0 && !trail[i])) perm[at++] = i;
        int[] pc = new int[n];
        long[] pd = new long[n];
        boolean[] pi = new boolean[n];
        boolean[] pa = new boolean[n];
        boolean[] pt = new boolean[n];
        for (int j = 0; j < n; j++) {
            pc[j] = counts[perm[j]];
            pd[j] = delays[perm[j]];
            pi[j] = infinite[perm[j]];
            pa[j] = alternate[perm[j]];
            pt[j] = trail[perm[j]];
        }
        result.animation = new Animation(pc, pd, pi, pa, pt);

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
            double[] xs = new double[(int) combos];
            double[] ys = new double[(int) combos];
            int[] index = new int[n];
            double[] vars = new double[n];
            for (int p = 0; p < combos; p++) {
                for (int j = 0; j < n; j++) vars[perm[j]] = values[perm[j]][index[j]];
                xs[p] = fx.eval(vars);
                ys[p] = fy.eval(vars);
                for (int j = n - 1; j >= 0; j--) { // odometer: the last counter moves fastest
                    if (++index[j] < counts[perm[j]]) break;
                    index[j] = 0;
                }
            }
            result.series.add(new Series(xs, ys, def.color == null ? "" : def.color, f));
            result.points += xs.length;
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
