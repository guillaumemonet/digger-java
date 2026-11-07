package org.digger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** INI file I/O, ported from ini.c (Windows-style profile files). */
public final class IniFile {
    private IniFile() {}

    public static String getString(String section, String key, String def, String filename) {
        File f = Hw.file(filename);
        if (!f.exists()) {
            return def;
        }
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.ISO_8859_1);
            boolean inSect = false;
            String sect = "[" + section + "]";
            String keyEq = key + "=";
            for (String line : lines) {
                String s = line.trim();
                if (s.equalsIgnoreCase(sect)) {
                    inSect = true;
                } else if (s.startsWith("[")) {
                    inSect = false;
                } else if (inSect && s.regionMatches(true, 0, keyEq, 0, keyEq.length())) {
                    return s.substring(keyEq.length());
                }
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    public static void writeString(String section, String key, String value, String filename) {
        File f = Hw.file(filename);
        List<String> lines = new ArrayList<>();
        try {
            if (f.exists()) {
                lines = Files.readAllLines(f.toPath(), StandardCharsets.ISO_8859_1);
            }
        } catch (Exception ignored) {
        }
        String sect = "[" + section + "]";
        String keyEq = key + "=";
        int sectIdx = -1, keyIdx = -1, sectEnd = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i).trim();
            if (s.equalsIgnoreCase(sect)) {
                sectIdx = i;
                sectEnd = lines.size();
                for (int j = i + 1; j < lines.size(); j++) {
                    String t = lines.get(j).trim();
                    if (t.startsWith("[")) { sectEnd = j; break; }
                    if (t.regionMatches(true, 0, keyEq, 0, keyEq.length())) { keyIdx = j; break; }
                }
                break;
            }
        }
        if (sectIdx < 0) {
            lines.add(sect);
            lines.add(keyEq + value);
        } else if (keyIdx >= 0) {
            lines.set(keyIdx, keyEq + value);
        } else {
            lines.add(sectEnd, keyEq + value);
        }
        try {
            Files.write(f.toPath(), lines, StandardCharsets.ISO_8859_1);
        } catch (Exception ignored) {
        }
    }

    public static long getInt(String section, String key, long def, String filename) {
        String s = getString(section, key, Long.toString(def), filename).trim();
        try {
            int i = 0;
            boolean neg = false;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                neg = s.charAt(i) == '-'; i++;
            }
            long v = 0;
            boolean any = false;
            for (; i < s.length() && Character.isDigit(s.charAt(i)); i++) {
                v = v * 10 + (s.charAt(i) - '0'); any = true;
            }
            if (!any) return def;
            return neg ? -v : v;
        } catch (Exception e) {
            return def;
        }
    }

    public static void writeInt(String section, String key, long value, String filename) {
        writeString(section, key, Long.toString(value), filename);
    }

    public static boolean getBool(String section, String key, boolean def, String filename) {
        String s = getString(section, key, def ? "-1" : "0", filename).trim().toUpperCase();
        if (s.isEmpty()) return def;
        if (s.charAt(0) == 'T') return true;
        return getInt(section, key, def ? 1 : 0, filename) != 0;
    }

    public static void writeBool(String section, String key, boolean value, String filename) {
        writeString(section, key, value ? "True" : "False", filename);
    }
}
