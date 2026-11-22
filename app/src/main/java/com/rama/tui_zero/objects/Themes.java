package com.rama.tui_zero.objects;

import java.util.ArrayList;
import java.util.List;

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

    private static Palette mocha(String id, String label, int accent, int accent_2) {
        return new Palette(id, label, 0xFFCDD6F4, 0xFF1E1E2E, 0xFF181825, 0xFF11111B, 0xFF45475A, 0xFF585B71, 0xFF6C7086, 0xFF6C7088, accent, accent_2, 0xFFA6E3A2, 0xFFF9E2AE, 0xFFF38BA9);
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

    public static final Palette CATPPUCCIN_MOCHA_MAUVE = mocha(PrefTheme.CATPPUCCIN_MOCHA_MAUVE, "Catppuccin Mocha Mauve", 0xFFCBA6F7, 0XFF390a70);
    public static final Palette CATPPUCCIN_MOCHA_BLUE = mocha(PrefTheme.CATPPUCCIN_MOCHA_BLUE, "Catppuccin Mocha Blue", 0xFF89B4FA, 0XFF053075);
    public static final Palette CATPPUCCIN_MOCHA_YELLOW = mocha(PrefTheme.CATPPUCCIN_MOCHA_YELLOW, "Catppuccin Mocha Yellow", 0xFFF9E2AF, 0xFF715109);
    public static final Palette CATPPUCCIN_MOCHA_PEACH = mocha(PrefTheme.CATPPUCCIN_MOCHA_PEACH, "Catppuccin Mocha Peach", 0xFFFAB387, 0xFF753005);
    public static final Palette CATPPUCCIN_MOCHA_GREEN = mocha(PrefTheme.CATPPUCCIN_MOCHA_GREEN, "Catppuccin Mocha Green", 0xFFA6E3A1, 0xFF215e1c);
    public static final Palette CATPPUCCIN_MOCHA_RED = mocha(PrefTheme.CATPPUCCIN_MOCHA_RED, "Catppuccin Mocha Red", 0xFFF38BA8, 0xFF6f0b27);
    public static final Palette CATPPUCCIN_LATTE = new Palette(PrefTheme.CATPPUCCIN_LATTE, "Catppuccin Latte", 0xFF4C4F69, 0xFFEFF1F5, 0xFFe6e9ef, 0xFFDCE0E8, 0xFFCCD0DA, 0xFFBCC0CC, 0xFF9CA0B1, 0xFF9CA0B2, 0xFF1E66F5, 0xFF052972, 0xFF40A02B, 0xFFFE640B, 0xFFD20F39);

    public static List<Palette> all() {
        List<Palette> list = new ArrayList<Palette>();
        list.add(CATPPUCCIN_MOCHA_MAUVE);
        list.add(CATPPUCCIN_MOCHA_BLUE);
        list.add(CATPPUCCIN_MOCHA_YELLOW);
        list.add(CATPPUCCIN_MOCHA_PEACH);
        list.add(CATPPUCCIN_MOCHA_GREEN);
        list.add(CATPPUCCIN_MOCHA_RED);
        list.add(CATPPUCCIN_LATTE);
        return list;
    }

    public static List<Palette> rotationPool() {
        List<Palette> list = new ArrayList<Palette>();
        list.add(CATPPUCCIN_MOCHA_MAUVE);
        list.add(CATPPUCCIN_MOCHA_BLUE);
        list.add(CATPPUCCIN_MOCHA_YELLOW);
        list.add(CATPPUCCIN_MOCHA_PEACH);
        list.add(CATPPUCCIN_MOCHA_GREEN);
        list.add(CATPPUCCIN_MOCHA_RED);
        return list;
    }

    public static Palette byId(String id) {
        List<Palette> list = all();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                return list.get(i);
            }
        }
        return CATPPUCCIN_MOCHA_MAUVE;
    }
}
