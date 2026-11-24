package com.rama.tui_zero.helpers;

import android.content.Context;

import com.rama.tui_zero.objects.Track;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TrackCache {
    private static final int MAGIC = 0x54554931;
    private static final int VERSION = 1;
    private static final String DELIM = "␟";

    public static final class Entry {
        public final String path;
        public final long size;
        public final long lastModified;
        public final String ext;
        public final String title;
        public final String artists;
        public final String countries;
        public final String languages;
        public final long durationMs;

        Entry(String path, long size, long lastModified, String ext, String title, String artists, String countries, String languages, long durationMs) {
            this.path = path;
            this.size = size;
            this.lastModified = lastModified;
            this.ext = ext;
            this.title = title;
            this.artists = artists;
            this.countries = countries;
            this.languages = languages;
            this.durationMs = durationMs;
        }

        public static Entry from(File file, Track base, long durationMs) {
            return new Entry(file.getAbsolutePath(), file.length(), file.lastModified(), base.ext, base.title,
                    pack(base.artists), pack(base.countries), pack(base.languages), durationMs);
        }

        public boolean matches(File file) {
            return size == file.length() && lastModified == file.lastModified();
        }

        public boolean isPlayable() {
            return durationMs > 0;
        }

        public Track toTrack() {
            return new Track(new File(path), unpack(artists), title, unpack(countries), unpack(languages), ext, null, durationMs);
        }
    }

    private final File file;

    public TrackCache(Context context) {
        file = new File(context.getApplicationContext().getFilesDir(), "tracks.cache");
    }

    public Map<String, Entry> load() {
        Map<String, Entry> result = new HashMap<String, Entry>();
        if (!file.exists()) return result;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return result;
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                Entry e = new Entry(in.readUTF(), in.readLong(), in.readLong(), in.readUTF(), in.readUTF(),
                        in.readUTF(), in.readUTF(), in.readUTF(), in.readLong());
                result.put(e.path, e);
            }
        } catch (Exception e) {
            result.clear();
        }
        return result;
    }

    public void save(Map<String, Entry> entries) {
        File tmp = new File(file.getPath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(entries.size());
            for (Entry e : entries.values()) {
                out.writeUTF(e.path);
                out.writeLong(e.size);
                out.writeLong(e.lastModified);
                out.writeUTF(e.ext);
                out.writeUTF(e.title);
                out.writeUTF(e.artists);
                out.writeUTF(e.countries);
                out.writeUTF(e.languages);
                out.writeLong(e.durationMs);
            }
        } catch (Exception e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(file)) {
            file.delete();
            if (!tmp.renameTo(file)) tmp.delete();
        }
    }

    private static String pack(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(DELIM);
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static List<String> unpack(String value) {
        List<String> result = new ArrayList<String>();
        if (value.length() == 0) return result;
        for (String part : value.split(DELIM)) result.add(part);
        return result;
    }
}
