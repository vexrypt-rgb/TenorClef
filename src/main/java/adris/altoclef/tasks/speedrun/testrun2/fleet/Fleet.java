package adris.altoclef.tasks.speedrun.testrun2.fleet;

import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Linked TenorClef names. File: altoclef/fleet.txt one IGN per line. */
public final class Fleet {

    public static final String FILE = "fleet.txt";

    private static final Set<String> NAMES = new LinkedHashSet<>();

    private Fleet() {}

    public static void load() {
        NAMES.clear();
        String raw = GameFiles.read(FILE);
        if (raw == null) return;
        for (String line : raw.split("\n")) {
            String n = line.trim();
            if (n.isEmpty() || n.startsWith("#")) continue;
            NAMES.add(n);
        }
    }

    public static void save() {
        StringBuilder b = new StringBuilder("# TenorClef fleet — one IGN per line\n");
        for (String n : NAMES) b.append(n).append('\n');
        GameFiles.write(FILE, b.toString());
    }

    public static void add(String name) {
        if (name == null || name.isBlank()) return;
        load();
        NAMES.add(name.trim());
        save();
    }

    public static void remove(String name) {
        load();
        NAMES.remove(name);
        save();
    }

    public static List<String> members() {
        load();
        return new ArrayList<>(NAMES);
    }

    public static boolean known(String name) {
        load();
        for (String n : NAMES) {
            if (n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
