package com.rama.tui_zero.objects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Themes {
    public static final class Palette {
        public final String id;
        public final String label;
        public final int text;
        public final int base;
        public final int mantle;
        public final int crust;
        public final int surface_0;
        public final int surface_1;
        public final int disabled;
        public final int border;
        public final int accent;
        public final int accent_2;
        public final int success;
        public final int warning;
        public final int error;

        Palette(String id, String label, int text, int base, int mantle, int crust, int surface_0, int surface_1, int disabled, int border, int accent, int accent_2, int success, int warning, int error) {
            this.id = id;
            this.label = label;
            this.text = text;
            this.base = base;
            this.mantle = mantle;
            this.crust = crust;
            this.surface_0 = surface_0;
            this.surface_1 = surface_1;
            this.disabled = disabled;
            this.border = border;
            this.accent = accent;
            this.accent_2 = accent_2;
            this.success = success;
            this.warning = warning;
            this.error = error;
        }
    }

    private Themes() {
    }

    public static final class Flavor {
        public final String id;
        public final String label;
        public final boolean light;
        final int text, base, mantle, crust, surface0, surface1, disabled, border;
        final int[] accents;
        final Palette fixed;

        Flavor(String id, String label, boolean light, Palette fixed) {
            this.id = id;
            this.label = label;
            this.light = light;
            this.fixed = fixed;
            this.text = this.base = this.mantle = this.crust = this.surface0 = this.surface1 = this.disabled = this.border = 0;
            this.accents = null;
        }

        Flavor(String id, String label, boolean light, int text, int base, int mantle, int crust,
               int surface0, int surface1, int disabled, int border, int[] accents) {
            this.fixed = null;
            this.id = id;
            this.label = label;
            this.light = light;
            this.text = text;
            this.base = base;
            this.mantle = mantle;
            this.crust = crust;
            this.surface0 = surface0;
            this.surface1 = surface1;
            this.disabled = disabled;
            this.border = border;
            this.accents = accents;
        }
    }

    public static final class Accent {
        public final String id;
        public final String label;

        Accent(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static final String RANDOM = "random";
    public static final String DEFAULT_FLAVOR = "mocha";

    private static final String[] ACCENT_IDS = {"rosewater", "flamingo", "pink", "mauve", "red", "maroon", "peach", "yellow", "green", "teal", "sky", "sapphire", "blue", "lavender"};
    private static final String[] ACCENT_LABELS = {"Rosewater", "Flamingo", "Pink", "Mauve", "Red", "Maroon", "Peach", "Yellow", "Green", "Teal", "Sky", "Sapphire", "Blue", "Lavender"};
    private static final int RED = 4, GREEN = 8, YELLOW = 7;

    private static final Flavor[] FLAVORS = {
            new Flavor("mocha", "Catppuccin Mocha", false,
                    0xFFCDD6F4, 0xFF1E1E2E, 0xFF181825, 0xFF11111B, 0xFF45475A, 0xFF585B71, 0xFF6C7086, 0xFF6C7088,
                    new int[]{0xFFF5E0DC, 0xFFF2CDCD, 0xFFF5C2E7, 0xFFCBA6F7, 0xFFF38BA8, 0xFFEBA0AC, 0xFFFAB387, 0xFFF9E2AF, 0xFFA6E3A1, 0xFF94E2D5, 0xFF89DCEB, 0xFF74C7EC, 0xFF89B4FA, 0xFFB4BEFE}),
            new Flavor("macchiato", "Catppuccin Macchiato", false,
                    0xFFCAD3F5, 0xFF24273A, 0xFF1E2030, 0xFF181926, 0xFF494D64, 0xFF5B6079, 0xFF6E738D, 0xFF6E738F,
                    new int[]{0xFFF4DBD6, 0xFFF0C6C6, 0xFFF5BDE6, 0xFFC6A0F6, 0xFFED8796, 0xFFEE99A0, 0xFFF5A97F, 0xFFEED49F, 0xFFA6DA95, 0xFF8BD5CA, 0xFF91D7E3, 0xFF7DC4E4, 0xFF8AADF4, 0xFFB7BDF8}),
            new Flavor("frappe", "Catppuccin Frappe", false,
                    0xFFC6D0F5, 0xFF303446, 0xFF292C3C, 0xFF232634, 0xFF51576D, 0xFF626881, 0xFF737994, 0xFF737996,
                    new int[]{0xFFF2D5CF, 0xFFEEBEBE, 0xFFF4B8E4, 0xFFCA9EE6, 0xFFE78284, 0xFFEA999C, 0xFFEF9F76, 0xFFE5C890, 0xFFA6D189, 0xFF81C8BE, 0xFF99D1DB, 0xFF85C1DC, 0xFF8CAAEE, 0xFFBABBF1}),
            new Flavor("latte", "Catppuccin Latte", true,
                    0xFF4C4F69, 0xFFEFF1F5, 0xFFE6E9EF, 0xFFDCE0E8, 0xFFCCD0DA, 0xFFBCC0CC, 0xFF9CA0B1, 0xFF9CA0B2,
                    new int[]{0xFFDC8A78, 0xFFDD7878, 0xFFEA76CB, 0xFF8839EF, 0xFFD20F39, 0xFFE64553, 0xFFFE640B, 0xFFDF8E1D, 0xFF40A02B, 0xFF179299, 0xFF04A5E5, 0xFF209FB5, 0xFF1E66F5, 0xFF7287FD}),
            new Flavor("mono_dark", "Mono Dark", false, new Palette("mono_dark", "Mono Dark",
                    0xFFFFFFF9, 0xFF040100, 0xFF040102, 0xFF040201, 0xFF040101, 0xFF040200, 0xFF555555, 0xFF555556,
                    0xFFFFF9F9, 0xFF333334, 0xFFFBFFFF, 0xFFFBFEFF, 0xFFFBFFFB)),
            new Flavor("mono_light", "Mono Light", true, new Palette("mono_light", "Mono Light",
                    0xFF000040, 0xFFFFFFFF, 0xFFFEFFFF, 0xFFFFFFFE, 0xFFFFFEFF, 0xFFFEFFFE, 0xFFAAAAAA, 0xFFAAAAAC,
                    0xFF020000, 0xFFCCCCCE, 0xFF000001, 0xFF010000, 0xFF020002)),
    };

    private static final Map<String, Palette> PALETTES = new LinkedHashMap<String, Palette>();

    static {
        for (Flavor flavor : FLAVORS) {
            if (flavor.fixed != null) {
                PALETTES.put(flavor.id, flavor.fixed);
                continue;
            }
            for (int i = 0; i < ACCENT_IDS.length; i++) {
                int accent = flavor.accents[i];
                Palette p = new Palette(flavor.id + ":" + ACCENT_IDS[i], flavor.label + " " + ACCENT_LABELS[i],
                        flavor.text, flavor.base, flavor.mantle, flavor.crust, flavor.surface0, flavor.surface1,
                        flavor.disabled, flavor.border, accent, accentDark(accent, flavor.light),
                        shiftBlue(flavor.accents[GREEN], 1), shiftBlue(flavor.accents[YELLOW], -1), shiftBlue(flavor.accents[RED], 1));
                PALETTES.put(key(flavor.id, ACCENT_IDS[i]), p);
            }
        }
    }

    private static String key(String flavorId, String accentId) {
        return flavorId + ":" + accentId;
    }

    private static int shiftBlue(int color, int delta) {
        int b = Math.max(0, Math.min(255, (color & 0xFF) + delta));
        return (color & 0xFFFFFF00) | b;
    }

    static int accentDark(int accent, boolean light) {
        float r = ((accent >> 16) & 0xFF) / 255f, g = ((accent >> 8) & 0xFF) / 255f, b = (accent & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = (((g - b) / d) % 6 + 6) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        return hsv(h, light ? 0.30f : 0.93f, light ? 0.85f : 0.32f);
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs(h % 2 - 1)), m = v - c;
        float r, g, b;
        int sector = (int) h % 6;
        switch (sector) {
            case 0:
                r = c;
                g = x;
                b = 0;
                break;
            case 1:
                r = x;
                g = c;
                b = 0;
                break;
            case 2:
                r = 0;
                g = c;
                b = x;
                break;
            case 3:
                r = 0;
                g = x;
                b = c;
                break;
            case 4:
                r = x;
                g = 0;
                b = c;
                break;
            default:
                r = c;
                g = 0;
                b = x;
                break;
        }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    public static int getColor(Palette palette, String name) {
        switch (name) {
            case "text":
                return palette.text;
            case "base":
                return palette.base;
            case "mantle":
                return palette.mantle;
            case "crust":
                return palette.crust;
            case "surface_0":
                return palette.surface_0;
            case "surface_1":
                return palette.surface_1;
            case "disabled":
                return palette.disabled;
            case "border":
                return palette.border;
            case "accent":
                return palette.accent;
            case "accent_2":
                return palette.accent_2;
            case "success":
                return palette.success;
            case "warning":
                return palette.warning;
            case "error":
                return palette.error;
            default:
                return palette.text;
        }
    }

    public static List<Flavor> flavors() {
        List<Flavor> list = new ArrayList<Flavor>();
        for (Flavor f : FLAVORS) list.add(f);
        return list;
    }

    public static List<Accent> accents() {
        List<Accent> list = new ArrayList<Accent>();
        for (int i = 0; i < ACCENT_IDS.length; i++)
            list.add(new Accent(ACCENT_IDS[i], ACCENT_LABELS[i]));
        return list;
    }

    public static boolean isFlavor(String id) {
        for (Flavor f : FLAVORS) if (f.id.equals(id)) return true;
        return false;
    }

    public static boolean isAccent(String id) {
        for (String a : ACCENT_IDS) if (a.equals(id)) return true;
        return false;
    }

    public static List<String> accentIds() {
        List<String> list = new ArrayList<String>();
        for (String a : ACCENT_IDS) list.add(a);
        return list;
    }

    public static boolean hasAccents(String flavorId) {
        for (Flavor f : FLAVORS) if (f.id.equals(flavorId)) return f.fixed == null;
        return true;
    }

    public static Palette palette(String flavorId, String accentId) {
        Palette p = hasAccents(flavorId) ? PALETTES.get(key(flavorId, accentId)) : PALETTES.get(flavorId);
        return p != null ? p : PALETTES.get(key(DEFAULT_FLAVOR, "mauve"));
    }

    public static int lineColor(Palette palette, String accentId, int index) {
        int split = palette.id.indexOf(':');
        String flavorId = split < 0 ? palette.id : palette.id.substring(0, split);
        if (hasAccents(flavorId)) {
            for (Flavor f : FLAVORS) {
                if (!f.id.equals(flavorId)) continue;
                for (int i = 0; i < ACCENT_IDS.length; i++) {
                    if (ACCENT_IDS[i].equals(accentId)) return f.accents[i];
                }
                return f.accents[Math.abs(index) % ACCENT_IDS.length];
            }
        }
        float[] towardsBase = {0f, 0.35f, 0.6f, 0.18f, 0.75f};
        float t = towardsBase[Math.abs(index) % towardsBase.length];
        return mix(palette.text, palette.base, t);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static List<Palette> all() {
        return new ArrayList<Palette>(PALETTES.values());
    }
}
