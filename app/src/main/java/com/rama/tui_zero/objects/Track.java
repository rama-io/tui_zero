package com.rama.tui_zero.objects;

import android.net.Uri;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class Track {
    public final File file;
    public final List<String> artists;
    public final String title;
    public final List<String> countries;
    public final List<String> languages;
    public final String ext;
    public final String normalizedName;
    public final Uri contentUri;
    public final long durationMs;

    public Track(File file, List<String> artists, String title, List<String> countries, List<String> languages, String ext, Uri contentUri, long durationMs) {
        this.file = file;
        this.artists = Collections.unmodifiableList(new ArrayList<String>(artists));
        this.title = title;
        this.countries = Collections.unmodifiableList(new ArrayList<String>(countries));
        this.languages = Collections.unmodifiableList(new ArrayList<String>(languages));
        this.ext = ext;
        this.normalizedName = normalize(nameWithoutExtension(file));
        this.contentUri = contentUri;
        this.durationMs = durationMs;
    }

    public static Track fromFile(File file) {
        String name = nameWithoutExtension(file);
        String[] parts = name.split(" - ", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        boolean hasArtist = parts.length > 1;
        List<String> artists = hasArtist ? splitComma(parts[0]) : new ArrayList<String>();
        String title = hasArtist ? parts[1] : name;
        List<String> countries = parts.length > 2 ? splitComma(parts[2]) : new ArrayList<String>();
        List<String> languages = parts.length > 3 ? splitComma(parts[3]) : new ArrayList<String>();
        return new Track(file, artists, title, countries, languages, extensionOf(file), null, 0L);
    }

    public Track withDuration(long durationMs) {
        return new Track(file, artists, title, countries, languages, ext, contentUri, durationMs);
    }

    public Track withMediaStore(String newTitle, List<String> newArtists, Uri uri, long durationMs) {
        return new Track(file, newArtists, newTitle, countries, languages, ext, uri, durationMs);
    }

    public String displayArtists() {
        return join(artists);
    }

    public String displayCountries() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < countries.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(localeCountry(countries.get(i)));
        }
        return sb.toString();
    }

    public String displayLanguages() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < languages.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(localeLanguage(languages.get(i)));
        }
        return sb.toString();
    }

    public String displayDuration() {
        if (durationMs <= 0) return "";
        long totalSeconds = durationMs / 1000;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    public String folder() {
        String parent = file.getParent();
        return parent == null ? "" : parent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Track)) return false;
        return file.getPath().equals(((Track) o).file.getPath());
    }

    @Override
    public int hashCode() {
        return file.getPath().hashCode();
    }

    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static String extensionOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static String nameWithoutExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static List<String> splitComma(String value) {
        List<String> result = new ArrayList<String>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.length() > 0) result.add(trimmed);
        }
        return result;
    }

    private static String localeCountry(String code) {
        String name = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
        return name.trim().length() > 0 && !name.equals(code) ? name : code;
    }

    private static String localeLanguage(String code) {
        String name = new Locale(code).getDisplayLanguage(Locale.ENGLISH);
        return name.trim().length() > 0 && !name.equals(code) ? name : code;
    }
}
